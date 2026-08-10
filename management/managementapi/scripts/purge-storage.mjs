#!/usr/bin/env node
/**
 * Esvazia buckets do Supabase Storage.
 *
 * Esvazia, não remove: os buckets ficam lá, vazios. Removê-los partia os
 * uploads da app, que assumem que já existem.
 *
 * Corre a seco por omissão — só apaga com `--yes`. Sem dependências (Node 18+).
 *
 *   node scripts/purge-storage.mjs --list                 # que buckets há e com quanto
 *   node scripts/purge-storage.mjs --bucket documents     # simulação de um bucket
 *   node scripts/purge-storage.mjs --bucket documents --yes
 *   node scripts/purge-storage.mjs --all                  # simulação de todos
 *   node scripts/purge-storage.mjs --all --yes            # esvazia TUDO
 *   node scripts/purge-storage.mjs --bucket media --prefix enterprises/ --yes
 *
 * ⚠️  Só o bucket `documents` (faturas) tem limpeza correspondente na base de
 *     dados — ver purge-invoices.sql. Os outros deixam linhas a apontar para
 *     ficheiros que já não existem. Ler o README antes.
 */

import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

/** O máximo que a API de listagem do Supabase devolve de uma vez. */
const PAGE_SIZE = 100;
const DELETE_BATCH = 100;

const here = dirname(fileURLToPath(import.meta.url));

// ── argumentos ───────────────────────────────────────────────
const args = process.argv.slice(2);
const apply = args.includes("--yes");
const wantsList = args.includes("--list");
const wantsAll = args.includes("--all");
const oneBucket = valueOf("--bucket");
const prefix = valueOf("--prefix") ?? "";

function valueOf(flag) {
  const i = args.indexOf(flag);
  if (i === -1) return null;
  const value = args[i + 1];
  if (!value || value.startsWith("--")) fail(`${flag} precisa de um valor.`);
  return value;
}

function fail(message) {
  console.error(`\n  ✖ ${message}\n`);
  process.exit(1);
}

if (!wantsList && !wantsAll && !oneBucket) {
  fail("Escolhe o âmbito: --list, --bucket <nome> ou --all.");
}
if (wantsAll && oneBucket) fail("--all e --bucket são mutuamente exclusivos.");
if (wantsAll && prefix) fail("--prefix só faz sentido com --bucket.");

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
const supabaseUrl = (env.SUPABASE_URL || "").replace(/\/+$/, "");
const serviceKey = env.SUPABASE_SERVICE_ROLE_KEY;

if (!supabaseUrl) fail("SUPABASE_URL em falta no .env");
if (!serviceKey) fail("SUPABASE_SERVICE_ROLE_KEY em falta no .env");

const storageUrl = `${supabaseUrl}/storage/v1`;
const authHeaders = {
  Authorization: `Bearer ${serviceKey}`,
  apikey: serviceKey,
  "Content-Type": "application/json",
};

// ── Storage ──────────────────────────────────────────────────
async function listBuckets() {
  const res = await fetch(`${storageUrl}/bucket`, { headers: authHeaders });
  if (!res.ok) fail(`Não consegui listar os buckets (HTTP ${res.status}): ${await res.text()}`);
  return res.json();
}

/**
 * Uma página da listagem. Entradas com `id: null` são pastas, não ficheiros —
 * é assim que o Supabase representa um nível intermédio.
 */
async function listPage(bucket, pathPrefix, offset) {
  const res = await fetch(`${storageUrl}/object/list/${encodeURIComponent(bucket)}`, {
    method: "POST",
    headers: authHeaders,
    body: JSON.stringify({
      prefix: pathPrefix,
      limit: PAGE_SIZE,
      offset,
      sortBy: { column: "name", order: "asc" },
    }),
  });

  if (!res.ok) fail(`Listagem de "${bucket}" falhou (HTTP ${res.status}): ${await res.text()}`);
  return res.json();
}

/** Percorre o prefixo em profundidade e devolve só as chaves de ficheiros. */
async function collectKeys(bucket, pathPrefix) {
  const keys = [];
  let offset = 0;

  for (;;) {
    const page = await listPage(bucket, pathPrefix, offset);
    if (page.length === 0) break;

    for (const entry of page) {
      const path = `${pathPrefix}${entry.name}`;
      if (entry.id === null) {
        keys.push(...(await collectKeys(bucket, `${path}/`)));
      } else {
        keys.push(path);
      }
    }

    if (page.length < PAGE_SIZE) break;
    offset += PAGE_SIZE;
  }

  return keys;
}

/** O endpoint de remoção aceita lote; enviar tudo de uma vez é um pedido só. */
async function deleteKeys(bucket, keys) {
  const res = await fetch(`${storageUrl}/object/${encodeURIComponent(bucket)}`, {
    method: "DELETE",
    headers: authHeaders,
    body: JSON.stringify({ prefixes: keys }),
  });

  if (!res.ok) fail(`Remoção em "${bucket}" falhou (HTTP ${res.status}): ${await res.text()}`);
  return res.json();
}

// ── execução ─────────────────────────────────────────────────
const buckets = await listBuckets();

if (buckets.length === 0) {
  console.log("\n  Não há buckets neste projeto Supabase.\n");
  process.exit(0);
}

if (wantsList) {
  console.log("\n  Buckets:\n");
  for (const b of buckets) {
    const keys = await collectKeys(b.name, "");
    const visibility = b.public ? "público" : "privado";
    console.log(`    ${b.name.padEnd(16)} ${String(keys.length).padStart(5)} ficheiro(s)   ${visibility}`);
  }
  console.log();
  process.exit(0);
}

const targets = wantsAll ? buckets.map((b) => b.name) : [oneBucket];

if (!wantsAll && !buckets.some((b) => b.name === oneBucket)) {
  fail(`O bucket "${oneBucket}" não existe. Buckets: ${buckets.map((b) => b.name).join(", ")}`);
}

console.log(`\n  alvo   ${wantsAll ? "TODOS os buckets" : oneBucket}${prefix ? ` (prefixo "${prefix}")` : ""}`);
console.log(`  modo   ${apply ? "APAGAR" : "simulação (dry-run)"}\n`);

let grandTotal = 0;
const plan = [];

for (const bucket of targets) {
  const keys = await collectKeys(bucket, prefix);
  grandTotal += keys.length;
  plan.push({ bucket, keys });
  console.log(`  ${bucket.padEnd(16)} ${String(keys.length).padStart(5)} ficheiro(s)`);
}

if (grandTotal === 0) {
  console.log("\n  Nada a apagar.\n");
  process.exit(0);
}

console.log(`\n  Total: ${grandTotal} ficheiro(s).`);

if (!apply) {
  console.log("  Simulação — não foi apagado nada. Repete com --yes.\n");
  process.exit(0);
}

for (const { bucket, keys } of plan) {
  if (keys.length === 0) continue;
  let removed = 0;
  for (let i = 0; i < keys.length; i += DELETE_BATCH) {
    const batch = keys.slice(i, i + DELETE_BATCH);
    const result = await deleteKeys(bucket, batch);
    removed += Array.isArray(result) ? result.length : batch.length;
  }
  console.log(`  ✔ ${bucket}: ${removed} apagado(s)`);
}

console.log(`\n  ✔ ${grandTotal} ficheiro(s) apagados. Os buckets ficaram vazios, não removidos.`);
console.log("  Lembra-te das linhas que ficaram a apontar para o vazio — ver README.md.\n");
