/**
 * Confronta o Excel do orçamento com a árvore que ficou gravada.
 *
 * Lê o .xlsx directamente (unzip + XML, sem dependências) em vez de reutilizar a
 * heurística do BudgetExcelImportService — se o parser do backend tiver um bug,
 * reutilizá-lo esconderia-o.
 *
 *   node verificar-importacao.js <ficheiro.xlsx> <arvore.json>
 */
const fs = require("fs");
const zlib = require("zlib");

/* ───────── leitura do .xlsx ───────── */

function unzip(file) {
  const buf = fs.readFileSync(file);
  const out = {};
  // percorre os cabeçalhos locais do ZIP
  for (let i = 0; i < buf.length - 4; i++) {
    if (buf.readUInt32LE(i) !== 0x04034b50) continue;
    const method = buf.readUInt16LE(i + 8);
    const compSize = buf.readUInt32LE(i + 18);
    const nameLen = buf.readUInt16LE(i + 26);
    const extraLen = buf.readUInt16LE(i + 28);
    const name = buf.slice(i + 30, i + 30 + nameLen).toString("utf8");
    const start = i + 30 + nameLen + extraLen;
    if (!compSize) continue;
    const data = buf.slice(start, start + compSize);
    try {
      out[name] = method === 8 ? zlib.inflateRawSync(data) : data;
    } catch { /* entrada com tamanho no descritor — ignorada */ }
  }
  return out;
}

function decodeEntities(s) {
  return s
    .replace(/&lt;/g, "<").replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"').replace(/&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, d) => String.fromCharCode(+d))
    .replace(/&amp;/g, "&");
}

function readSheet(file) {
  const files = unzip(file);
  const ssXml = files["xl/sharedStrings.xml"]?.toString("utf8") ?? "";
  const shared = [...ssXml.matchAll(/<si>([\s\S]*?)<\/si>/g)].map((m) =>
    decodeEntities([...m[1].matchAll(/<t[^>]*>([\s\S]*?)<\/t>/g)].map((t) => t[1]).join(""))
  );

  const sheet = files["xl/worksheets/sheet1.xml"].toString("utf8");
  const rows = new Map();
  for (const rm of sheet.matchAll(/<row[^>]*r="(\d+)"[^>]*>([\s\S]*?)<\/row>/g)) {
    const rowNum = +rm[1];
    const cells = {};
    // As células vazias vêm auto-fechadas (`<c r="C13" s="2"/>`), sem `</c>`.
    // Exigir o fecho faria a captura saltar para a célula seguinte e atribuir
    // o valor à coluna errada.
    for (const cm of rm[2].matchAll(/<c([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
      const attrs = cm[1];
      const body = cm[2] ?? "";
      const colMatch = attrs.match(/r="([A-Z]+)\d+"/);
      if (!colMatch) continue;
      const col = colMatch[1];
      const vMatch = body.match(/<v>([\s\S]*?)<\/v>/);
      const isMatch = body.match(/<is>[\s\S]*?<t[^>]*>([\s\S]*?)<\/t>/);
      let value = null;
      if (isMatch) value = decodeEntities(isMatch[1]);
      else if (vMatch) {
        value = /t="s"/.test(attrs) ? shared[+vMatch[1]] : vMatch[1];
      }
      if (value != null && value !== "") cells[col] = value;
    }
    rows.set(rowNum, cells);
  }
  return rows;
}

/* ───────── normalização (tem de bater com o backend) ───────── */

const normCode = (raw) => {
  if (!raw) return null;
  let c = String(raw).trim().replace(/\s+/g, "");
  while (c.endsWith(".")) c = c.slice(0, -1);
  return /^\d+(\.\d+)*$/.test(c) ? c : null;
};
const normUnit = (raw) => {
  if (!raw) return null;
  let u = String(raw).trim().toLowerCase();
  while (u.endsWith(".")) u = u.slice(0, -1);
  return u || null;
};
const num = (raw, scale) => {
  if (raw == null || raw === "") return null;
  const n = Number(String(raw).replace(",", "."));
  return Number.isFinite(n) ? Number(n.toFixed(scale)) : null;
};
const NO_DESC = "Sem descrição";

/* ───────── Excel → lista na ordem de leitura ───────── */

function parseExcel(file) {
  const rows = readSheet(file);
  const nums = [...rows.keys()].sort((a, b) => a - b);

  const headerRow = nums.find((n) => /^art/i.test((rows.get(n).A ?? "").trim()));
  if (!headerRow) throw new Error('Não encontrei a linha de cabeçalho ("Art" na coluna A).');

  const out = [];
  let excelTotal = null;
  for (const n of nums) {
    if (n <= headerRow) continue;
    const c = rows.get(n);
    const rawCode = c.A;
    const name = (c.B ?? "").trim();

    if (!rawCode && name.toUpperCase() === "TOTAL") {
      excelTotal = num(c.F, 2);
      break;
    }

    const code = normCode(rawCode);
    const quantity = num(c.D, 3);
    const unitPrice = num(c.E, 2);
    const totalPrice = num(c.F, 2);

    if (!name) {
      // O backend só guarda estas se tiverem índice ou preço.
      if (code == null && totalPrice == null) {
        if (quantity != null || unitPrice != null)
          out.push({ excelRow: n, skipped: true, code, name: "(vazia)", quantity, unitPrice, totalPrice });
        continue;
      }
      out.push({ excelRow: n, code, name: NO_DESC, unit: normUnit(c.C), quantity, unitPrice, totalPrice });
      continue;
    }

    out.push({ excelRow: n, code, name, unit: normUnit(c.C), quantity, unitPrice, totalPrice });
  }
  return { rows: out, excelTotal };
}

/* ───────── árvore gravada → lista em profundidade ───────── */

function flatten(tree) {
  const out = [];
  const walk = (nodes) => {
    for (const n of nodes) {
      out.push(n);
      walk(n.children ?? []);
    }
  };
  walk(tree.roots ?? []);
  return out;
}

/* ───────── comparação ───────── */

const eq = (a, b) => (a == null && b == null) || Number(a) === Number(b);
const money = (v) => (v == null ? "—" : Number(v).toFixed(2) + " €");
/** Há descrições com quebras de linha dentro da célula — comparar em cru daria falsos negativos. */
const normName = (s) => (s ?? "").replace(/\s+/g, " ").trim();

function main() {
  const [xlsx, json] = process.argv.slice(2);
  const { rows: excel, excelTotal } = parseExcel(xlsx);
  const tree = JSON.parse(fs.readFileSync(json, "utf8"));
  const db = flatten(tree);

  const kept = excel.filter((r) => !r.skipped);
  const skipped = excel.filter((r) => r.skipped);

  console.log("═".repeat(72));
  console.log("PROJETO :", tree.enterpriseName ?? tree.enterpriseId);
  console.log("Excel   :", kept.length, "linhas na tabela  (" + skipped.length + " ignoradas por não terem índice nem preço)");
  console.log("Gravado :", db.length, "rubricas");
  console.log("─".repeat(72));
  console.log("TOTAL no Excel     :", money(excelTotal));
  console.log("Soma das folhas BD :", money(tree.budgetTotal));
  const diff = excelTotal == null ? null : Number((tree.budgetTotal - excelTotal).toFixed(2));
  console.log("Diferença          :", diff == null ? "—" : money(diff),
    diff != null && Math.abs(diff) <= 1 ? "(arredondamento, ok)" : diff == null ? "" : "  ⚠");
  console.log("═".repeat(72));

  /* 1 — índices */
  // O Excel repete alguns índices (8.2 na 76 e 77, 13.2.1 na 148 e 150). O
  // backend guarda a primeira com índice e a segunda sem — por isso comparamos
  // sempre contra a PRIMEIRA ocorrência e listamos as repetidas à parte.
  const byCodeExcel = new Map();
  const duplicated = [];
  for (const r of kept) {
    if (!r.code) continue;
    if (byCodeExcel.has(r.code)) duplicated.push({ ...r, firstAt: byCodeExcel.get(r.code).excelRow });
    else byCodeExcel.set(r.code, r);
  }
  const byCodeDb = new Map(db.filter((n) => n.code).map((n) => [n.code, n]));

  const missing = [...byCodeExcel.keys()].filter((c) => !byCodeDb.has(c));
  const extra = [...byCodeDb.keys()].filter((c) => !byCodeExcel.has(c));

  console.log("\n▸ ÍNDICES");
  console.log("  no Excel:", byCodeExcel.size, " · gravados:", byCodeDb.size);
  if (missing.length) console.log("  ⚠ no Excel mas NÃO gravados:", missing.join(", "));
  if (extra.length) console.log("  ⚠ gravados mas não no Excel:", extra.join(", "));
  if (!missing.length && !extra.length) console.log("  ✓ todos os índices correspondem");
  if (duplicated.length) {
    console.log("  ℹ índices repetidos no Excel — importados sem índice, como esperado:");
    for (const d of duplicated)
      console.log(`      "${d.code}" na linha ${d.excelRow} (já usado na ${d.firstAt}) — ${money(d.totalPrice)}`);
  }

  /* 2 — valores das rubricas com índice */
  console.log("\n▸ VALORES (rubricas com índice)");
  const bad = [];
  for (const [code, e] of byCodeExcel) {
    const d = byCodeDb.get(code);
    if (!d) continue;
    const problems = [];
    if (normName(e.name) !== normName(d.name)) problems.push("descrição");
    if ((e.unit ?? null) !== (d.unit ?? null)) problems.push(`un. "${e.unit}" ≠ "${d.unit}"`);
    if (!eq(e.quantity, d.quantity)) problems.push(`quant. ${e.quantity} ≠ ${d.quantity}`);
    if (!eq(e.unitPrice, d.unitPrice)) problems.push(`preço un. ${e.unitPrice} ≠ ${d.unitPrice}`);
    if (!eq(e.totalPrice, d.totalPrice)) problems.push(`total ${e.totalPrice} ≠ ${d.totalPrice}`);
    if (problems.length) bad.push({ code, excelRow: e.excelRow, problems });
  }
  if (!bad.length) console.log("  ✓ os", byCodeExcel.size, "índices batem certo em un./quant./preços/descrição");
  else {
    console.log("  ⚠", bad.length, "com diferenças:");
    for (const b of bad) console.log(`    ${b.code.padEnd(10)} (linha ${b.excelRow}): ${b.problems.join(" · ")}`);
  }

  /* 3 — linhas sem índice */
  const noCodeExcel = kept.filter((r) => !r.code);
  const noCodeDb = db.filter((n) => !n.code);
  console.log("\n▸ LINHAS SEM ÍNDICE (títulos, notas, alternativas)");
  console.log("  no Excel:", noCodeExcel.length, " · gravadas:", noCodeDb.length);
  const namesDb = new Set(noCodeDb.map((n) => normName(n.name)));
  const lost = noCodeExcel.filter((r) => !namesDb.has(normName(r.name)));
  if (!lost.length) console.log("  ✓ todas foram gravadas");
  else {
    console.log("  ⚠ não encontradas na base de dados:");
    for (const l of lost) console.log(`    linha ${l.excelRow}: "${l.name.slice(0, 60)}" ${money(l.totalPrice)}`);
  }

  /* 4 — dinheiro em linhas sem índice */
  const moneyNoCode = noCodeDb.filter((n) => n.totalPrice != null);
  if (moneyNoCode.length) {
    const sum = moneyNoCode.reduce((a, n) => a + Number(n.totalPrice), 0);
    console.log("\n▸ ALTERNATIVAS (sem índice, mas com preço) —", moneyNoCode.length, "linhas,", money(sum));
    for (const n of moneyNoCode) console.log(`    ${money(n.totalPrice).padStart(14)}  ${n.name.slice(0, 55)}`);
  }

  /* 5 — linhas ignoradas */
  if (skipped.length) {
    console.log("\n▸ IGNORADAS (sem índice, sem descrição e sem preço total)");
    for (const s of skipped)
      console.log(`    linha ${s.excelRow}: quant=${s.quantity} preçoUn=${s.unitPrice}`);
  }

  console.log("\n" + "═".repeat(72));
  const okAll = !missing.length && !extra.length && !bad.length && !lost.length;
  console.log(okAll ? "RESULTADO: importação confere com o Excel." : "RESULTADO: há diferenças a rever (acima).");
}

main();
