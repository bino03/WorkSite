#!/usr/bin/env node
/**
 * Apaga os ficheiros de faturas do Supabase Storage.
 *
 * Corre a seco por omissão — só apaga com `--yes`. Lista a partir do próprio
 * Storage (prefixo `construction-invoices/`) e não da base de dados, o que tem
 * duas vantagens: pode correr antes ou depois do SQL, e apanha também ficheiros
 * órfãos cuja linha já desapareceu.
 *
 * Sem dependências — Node 18+ chega (fetch nativo).
 *
 *   node scripts/purge-invoices.mjs                      # dry-run, tudo
 *   node scripts/purge-invoices.mjs --yes                # apaga tudo
 *   node scripts/purge-invoices.mjs --enterprise <uuid>  # dry-run, um projeto
 *   node scripts/purge-invoices.mjs --enterprise <uuid> --yes
 *
 * O par deste script é `purge-invoices.sql`, que trata das linhas. Ver o README
 * ao lado para a ordem e para o efeito no orçamento.
 */

import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const BUCKET = "documents";
const ROOT_PREFIX = "construction-invoices";
/** O máximo que a API de listagem do Supabase devolve de uma vez. */
const PAGE_SIZE = 100;

const here = dirname(fileURLToPath(import.meta.url));

// ── argumentos ───────────────────────────────────────────────
const args = process.argv.slice(2);
const apply = args.includes("--yes");
const enterpriseId = valueOf("--enterprise");

function valueOf(flag) {
  const i = args.indexOf(flag);
  if (i === -1) return null;
  const value = args[i + 1];
  if (!value || value.startsWith("--")) {
    fail(`${flag} precisa de um valor.`);
  }
  return value;
}

function fail(message) {
  console.error(`\n  ✖ ${message}\n`);
  process.exit(1);
}

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
    // Aceita valores entre aspas, que é como muita gente escreve chaves longas.
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

/**
 * Uma página da listagem. Entradas com `id: null` são pastas, não ficheiros —
 * é assim que o Supabase representa um nível intermédio.
 */
async function listPage(prefix, offset) {
  const res = await fetch(`${storageUrl}/object/list/${encodeURIComponent(BUCKET)}`, {
    method: "POST",
    headers: authHeaders,
    body: JSON.stringify({
      prefix,
      limit: PAGE_SIZE,
      offset,
      sortBy: { column: "name", order: "asc" },
    }),
  });

  if (!res.ok) {
    fail(`Listagem falhou (HTTP ${res.status}): ${await res.text()}`);
  }
  return res.json();
}

/** Percorre o prefixo em profundidade e devolve só as chaves de ficheiros. */
async function collectKeys(prefix) {
  const keys = [];
  let offset = 0;

  for (;;) {
    const page = await listPage(prefix, offset);
    if (page.length === 0) break;

    for (const entry of page) {
      const path = `${prefix}${entry.name}`;
      if (entry.id === null) {
        keys.push(...(await collectKeys(`${path}/`)));
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
async function deleteKeys(keys) {
  const res = await fetch(`${storageUrl}/object/${encodeURIComponent(BUCKET)}`, {
    method: "DELETE",
    headers: authHeaders,
    body: JSON.stringify({ prefixes: keys }),
  });

  if (!res.ok) {
    fail(`Remoção falhou (HTTP ${res.status}): ${await res.text()}`);
  }
  return res.json();
}

// ── execução ─────────────────────────────────────────────────
const prefix = enterpriseId ? `${ROOT_PREFIX}/${enterpriseId}/` : `${ROOT_PREFIX}/`;

console.log(`\n  bucket   ${BUCKET}`);
console.log(`  prefixo  ${prefix}`);
console.log(`  modo     ${apply ? "APAGAR" : "simulação (dry-run)"}\n`);

const keys = await collectKeys(prefix);

if (keys.length === 0) {
  console.log("  Nada a apagar — não há ficheiros neste prefixo.\n");
  process.exit(0);
}

const thumbnails = keys.filter((k) => k.split("/").pop().startsWith("thumb_")).length;
console.log(`  ${keys.length} ficheiro(s): ${keys.length - thumbnails} documento(s) + ${thumbnails} miniatura(s)\n`);
for (const key of keys.slice(0, 10)) console.log(`    ${key}`);
if (keys.length > 10) console.log(`    … mais ${keys.length - 10}`);

if (!apply) {
  console.log("\n  Simulação — não foi apagado nada. Repete com --yes.\n");
  process.exit(0);
}

// Lotes para não enviar um corpo gigante num pedido só.
const BATCH = 100;
let removed = 0;
for (let i = 0; i < keys.length; i += BATCH) {
  const batch = keys.slice(i, i + BATCH);
  const result = await deleteKeys(batch);
  removed += Array.isArray(result) ? result.length : batch.length;
  console.log(`  apagados ${Math.min(i + BATCH, keys.length)}/${keys.length}`);
}

console.log(`\n  ✔ ${removed} ficheiro(s) apagados do Storage.`);
console.log("  Falta a tabela — corre agora purge-invoices.sql.\n");
