#!/usr/bin/env node
/**
 * Descarrega os ficheiros das faturas de obra por enviar à contabilidade,
 * arrumados em pastas de N faturas.
 *
 * "Por enviar" é `construction_invoice.sent_to_accountant = false`, e "de obra"
 * é `scope = 'PROJECT'` — as despesas da empresa e a quarentena ficam de fora.
 * As obras `is_test` também. Conta-se por **fatura**, não por ficheiro: os
 * anexos de uma fatura ficam juntos, por isso uma pasta pode ter mais ficheiros
 * do que N. A ordem é obra → data → número.
 *
 * Ao lado das pastas fica um `lista.csv` (`;` e BOM, abre direto no Excel) com
 * a pasta de cada fatura, e no fim as que estão por enviar mas não têm ficheiro
 * nenhum — essas não há como descarregar.
 *
 * Só lê: a sessão da base de dados é aberta em read-only e o Storage só leva
 * GETs. **Não marca nada como enviado** — isso faz-se na app.
 *
 *   node scripts/download-unsent-invoices.mjs --out "C:\Users\eu\Desktop\Por enviar"
 *   node scripts/download-unsent-invoices.mjs --out <pasta> --per 20
 *   node scripts/download-unsent-invoices.mjs --out <pasta> --skip "Destino Teste Claude"
 *
 * Ao contrário dos outros scripts desta pasta, este precisa de falar com a base
 * de dados (a lista do que falta enviar não está no Storage), e por isso do
 * `pg`. Instala-se uma vez, sem tocar em nenhum package.json:
 *
 *   npm i --prefix scripts --no-save --no-package-lock pg
 *
 * Ver docs/operations.md.
 */

import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, extname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));

// ── argumentos ───────────────────────────────────────────────
const args = process.argv.slice(2);
const outRoot = valueOf("--out");
const perFolder = Number(valueOf("--per") ?? 10);
const skipped = allValuesOf("--skip");

function valueOf(flag) {
  return allValuesOf(flag).at(-1) ?? null;
}

function allValuesOf(flag) {
  const values = [];
  args.forEach((arg, i) => {
    if (arg !== flag) return;
    const value = args[i + 1];
    if (!value || value.startsWith("--")) fail(`${flag} precisa de um valor.`);
    values.push(value);
  });
  return values;
}

function fail(message) {
  console.error(`\n  ✖ ${message}\n`);
  process.exit(1);
}

if (!outRoot) fail("Falta --out <pasta destino>. Fora do repo: são faturas reais.");
if (!Number.isInteger(perFolder) || perFolder < 1) fail("--per tem de ser um inteiro positivo.");

// ── credenciais ──────────────────────────────────────────────
/** Lê o .env à mão para não precisar do dotenv só por causa disto. */
function loadEnv() {
  const path = resolve(here, "..", ".env");
  let raw;
  try {
    raw = readFileSync(path, "utf8");
  } catch {
    fail(`Não encontrei o .env em ${path}`);
  }

  const env = {};
  for (const line of raw.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const eq = trimmed.indexOf("=");
    if (eq < 1) continue;
    env[trimmed.slice(0, eq).trim()] = trimmed
      .slice(eq + 1)
      .trim()
      .replace(/^["']|["']$/g, "");
  }
  return env;
}

const env = loadEnv();
for (const name of ["DB_URL", "DB_USER", "DB_PASS", "SUPABASE_URL", "SUPABASE_SERVICE_ROLE_KEY"]) {
  if (!env[name]) fail(`Falta ${name} no .env.`);
}

// ── base de dados ────────────────────────────────────────────
let pg;
try {
  pg = (await import("pg")).default;
} catch {
  fail("Falta o pacote `pg`: npm i --prefix scripts --no-save --no-package-lock pg");
}

/** O DB_URL é JDBC (`jdbc:postgresql://host:porta/base?…`); o pg quer as partes. */
const jdbc = env.DB_URL.match(/\/\/([^:/]+):(\d+)\/([^?]+)/);
if (!jdbc) fail("Não consegui ler host/porta/base do DB_URL.");

const db = new pg.Client({
  host: jdbc[1],
  port: Number(jdbc[2]),
  database: jdbc[3],
  user: env.DB_USER,
  password: env.DB_PASS,
  ssl: { rejectUnauthorized: false },
});
await db.connect();
await db.query("set default_transaction_read_only = on");

const { rows } = await db.query(
  `select i.id, e.name as obra, i.supplier_name, i.invoice_number,
          to_char(i.invoice_date, 'YYYY-MM-DD') as invoice_date,
          i.total_amount, i.document_type::text as document_type,
          i.document_status::text as document_status,
          coalesce(json_agg(json_build_object(
                     'bucket', d.bucket, 'key', d.storage_key, 'name', d.original_filename,
                     'mime', d.mime_type, 'size', d.size_bytes)
                   order by d.page_number nulls first, d.created_at)
                   filter (where d.id is not null), '[]') as docs
     from worksite.construction_invoice i
     join worksite.enterprises e on e.id = i.enterprise_id
     left join worksite.construction_invoice_document d on d.invoice_id = i.id
    where i.scope = 'PROJECT'
      and i.sent_to_accountant = false
      and e.is_test = false
      and e.name <> all($1::text[])
    group by i.id, e.name
    order by e.name, i.invoice_date nulls last, i.invoice_number, i.id`,
  [skipped],
);
await db.end();

// ── descarga ─────────────────────────────────────────────────
const headers = {
  Authorization: `Bearer ${env.SUPABASE_SERVICE_ROLE_KEY}`,
  apikey: env.SUPABASE_SERVICE_ROLE_KEY,
};

function fetchObject(bucket, key) {
  const path = key.split("/").map(encodeURIComponent).join("/");
  return fetch(`${env.SUPABASE_URL}/storage/v1/object/${encodeURIComponent(bucket)}/${path}`, { headers });
}

/** Tira o que o Windows não aceita num nome de ficheiro. */
function clean(text) {
  return (text ?? "")
    .replace(/[\\/:*?"<>|\r\n\t]+/g, "-")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/[. ]+$/, "");
}

function extensionOf(doc) {
  const known = { "application/pdf": ".pdf", "image/jpeg": ".jpg", "image/png": ".png" };
  return (extname(doc.name ?? "") || extname(doc.key) || known[doc.mime] || "").toLowerCase();
}

const withFile = rows.filter((row) => row.docs.length > 0);
const withoutFile = rows.filter((row) => row.docs.length === 0);
const folderCount = Math.ceil(withFile.length / perFolder);
const digits = Math.max(2, String(folderCount).length);

const csv = [["Pasta", "Obra", "Fornecedor", "Nº fatura", "Data", "Total", "Tipo", "Ficheiros"]];
const taken = new Set();
const failures = [];
const perObra = {};
let files = 0;
let bytes = 0;
let plusKeys = 0;

mkdirSync(outRoot, { recursive: true });

for (let n = 0; n < withFile.length; n++) {
  const row = withFile[n];
  const folder = `Pasta ${String(Math.floor(n / perFolder) + 1).padStart(digits, "0")}`;
  mkdirSync(join(outRoot, folder), { recursive: true });
  perObra[row.obra] = (perObra[row.obra] ?? 0) + 1;

  const base = clean(
    [
      row.obra,
      row.invoice_date ?? "sem data",
      row.supplier_name ?? "sem fornecedor",
      row.invoice_number ?? "sem número",
    ].join(" - "),
  ).slice(0, 150);

  const names = [];
  for (let k = 0; k < row.docs.length; k++) {
    const doc = row.docs[k];
    const part = row.docs.length > 1 ? ` (${k + 1} de ${row.docs.length})` : "";
    let name = `${base}${part}${extensionOf(doc)}`;
    // duas faturas sem número do mesmo dia e fornecedor dão o mesmo nome
    for (let dup = 2; taken.has(`${folder}/${name}`.toLowerCase()); dup++) {
      name = `${base}${part} [${dup}]${extensionOf(doc)}`;
    }
    taken.add(`${folder}/${name}`.toLowerCase());

    let res = await fetchObject(doc.bucket, doc.key);
    // Há chaves com espaço na BD cujo objeto no Storage ficou com "+"
    // (notes/bugs.md, 2026-10-12). Sem isto esses ficheiros dão NoSuchKey.
    if (!res.ok && doc.key.includes(" ")) {
      res = await fetchObject(doc.bucket, doc.key.replaceAll(" ", "+"));
      if (res.ok) plusKeys++;
    }
    if (!res.ok) {
      failures.push(`${folder}/${name} — HTTP ${res.status} (${doc.key})`);
      continue;
    }

    const body = Buffer.from(await res.arrayBuffer());
    if (doc.size != null && Number(doc.size) !== body.length) {
      failures.push(`${folder}/${name} — ${body.length} bytes, a BD diz ${doc.size}`);
    }
    writeFileSync(join(outRoot, folder, name), body);
    names.push(name);
    files++;
    bytes += body.length;
  }

  csv.push([folder, ...csvCells(row), names.join(" | ")]);
}

for (const row of withoutFile) csv.push(["(sem ficheiro)", ...csvCells(row), row.document_status]);

function csvCells(row) {
  return [
    row.obra,
    row.supplier_name,
    row.invoice_number,
    row.invoice_date,
    row.total_amount?.replace(".", ","),
    row.document_type,
  ];
}

writeFileSync(
  join(outRoot, "lista.csv"),
  "\uFEFF" +
    csv
      .map((line) => line.map((cell) => `"${String(cell ?? "").replace(/"/g, '""')}"`).join(";"))
      .join("\r\n"),
);

// ── resumo ───────────────────────────────────────────────────
console.log(`\n  ${withFile.length} faturas, ${files} ficheiros, ${(bytes / 1048576).toFixed(1)} MB`);
console.log(`  ${folderCount} pastas de ${perFolder} em ${resolve(outRoot)}\n`);
for (const [obra, count] of Object.entries(perObra)) console.log(`    ${String(count).padStart(4)}  ${obra}`);
if (withoutFile.length) {
  console.log(`\n  ${withoutFile.length} por enviar sem ficheiro (só no lista.csv)`);
}
if (plusKeys) console.log(`  ${plusKeys} ficheiros encontrados com "+" em vez de espaço na chave`);
if (failures.length) {
  console.error(`\n  ✖ ${failures.length} falhas:`);
  for (const failure of failures) console.error(`    ${failure}`);
  process.exit(1);
}
console.log("");
