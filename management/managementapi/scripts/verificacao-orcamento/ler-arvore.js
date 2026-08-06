/**
 * Lê a árvore de orçamento directamente do Postgres e escreve-a no mesmo
 * formato que a API devolve, para o comparador poder consumi-la.
 *
 * Só faz SELECT. As credenciais vêm do .env do backend.
 */
const fs = require("fs");
const path = require("path");
const { Client } = require("pg");

/** `scripts/verificacao-orcamento/` → `managementapi/`, onde vive o .env. */
const API_DIR = path.resolve(__dirname, "..", "..");

function readEnv() {
  const env = fs.readFileSync(path.join(API_DIR, ".env"), "utf8");
  const get = (k) => {
    const m = env.match(new RegExp("^" + k + "=(.*)$", "m"));
    return m ? m[1].trim() : null;
  };
  // jdbc:postgresql://host:port/db?params → partes que o driver pg precisa
  const jdbc = get("DB_URL");
  const m = jdbc.match(/^jdbc:postgresql:\/\/([^:/]+):(\d+)\/([^?]+)/);
  if (!m) throw new Error("DB_URL em formato inesperado: " + jdbc);
  return {
    host: m[1],
    port: +m[2],
    database: m[3],
    user: get("DB_USER"),
    password: get("DB_PASS"),
    ssl: { rejectUnauthorized: false },
  };
}

async function main() {
  const client = new Client(readEnv());
  await client.connect();
  try {
    const enterpriseId = process.argv[2] || null;

    // Que projetos têm orçamento?
    const projects = await client.query(`
      select e.id, e.name, count(i.id)::int as items
        from worksite.enterprises e
        join worksite.construction_budget_item i on i.enterprise_id = e.id
    group by e.id, e.name
    order by count(i.id) desc
    `);

    if (!projects.rows.length) {
      console.error("Nenhum projeto tem rubricas de orçamento gravadas.");
      process.exit(1);
    }

    if (!enterpriseId) {
      console.log("Projetos com orçamento:\n");
      for (const p of projects.rows) console.log(`  ${p.id}  ${String(p.items).padStart(4)} rubricas  ${p.name}`);
      console.log("\nCorre outra vez com o id do projeto para exportar a árvore.");
      return;
    }

    const target = projects.rows.find((p) => p.id === enterpriseId);
    if (!target) throw new Error("Esse projeto não tem rubricas gravadas.");

    const items = await client.query(
      `select id, parent_id, row_kind, code, sort_order, name, unit,
              quantity, unit_price, total_price, observations, start_date, end_date
         from worksite.construction_budget_item
        where enterprise_id = $1
     order by sort_order asc`,
      [enterpriseId]
    );

    const expenses = await client.query(
      `select e.budget_item_id, e.total_price
         from worksite.construction_expense e
         join worksite.construction_budget_item i on i.id = e.budget_item_id
        where i.enterprise_id = $1`,
      [enterpriseId]
    );

    const num = (v) => (v == null ? null : Number(v));
    const byId = new Map();
    for (const r of items.rows) {
      byId.set(r.id, {
        id: r.id,
        parentId: r.parent_id,
        rowKind: r.row_kind,
        code: r.code,
        sortOrder: r.sort_order,
        name: r.name,
        unit: r.unit,
        quantity: num(r.quantity),
        unitPrice: num(r.unit_price),
        totalPrice: num(r.total_price),
        observations: r.observations,
        children: [],
      });
    }

    const roots = [];
    for (const node of byId.values()) {
      if (node.parentId && byId.has(node.parentId)) byId.get(node.parentId).children.push(node);
      else roots.push(node);
    }
    const sortRec = (nodes) => {
      nodes.sort((a, b) => a.sortOrder - b.sortOrder);
      nodes.forEach((n) => sortRec(n.children));
    };
    sortRec(roots);

    // budgetTotal = soma das folhas com preço, a mesma regra do backend
    const leafSum = (node) => {
      let childSum = 0;
      let childHasPrice = false;
      for (const c of node.children) {
        const s = leafSum(c);
        childSum += s;
        if (s !== 0 || c.totalPrice != null) childHasPrice = true;
      }
      return childHasPrice ? childSum : node.totalPrice ?? 0;
    };
    const budgetTotal = Number(roots.reduce((a, r) => a + leafSum(r), 0).toFixed(2));
    const spentTotal = Number(expenses.rows.reduce((a, e) => a + Number(e.total_price), 0).toFixed(2));

    const out = {
      enterpriseId,
      enterpriseName: target.name,
      budgetTotal,
      spentTotal,
      itemCount: items.rows.length,
      expenseCount: expenses.rows.length,
      roots,
    };

    const dest = path.resolve(process.argv[3] || path.join(process.cwd(), "arvore.json"));
    fs.writeFileSync(dest, JSON.stringify(out, null, 2), "utf8");
    console.log(`Projeto : ${target.name}`);
    console.log(`Rubricas: ${items.rows.length}   Despesas: ${expenses.rows.length}`);
    console.log(`Total   : ${budgetTotal.toFixed(2)} €`);
    console.log(`Gravado : ${dest}`);
  } finally {
    await client.end();
  }
}

main().catch((e) => {
  console.error("ERRO:", e.message);
  process.exit(1);
});
