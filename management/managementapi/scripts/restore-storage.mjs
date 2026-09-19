#!/usr/bin/env node
/**
 * Devolve ao Supabase Storage uma cópia feita pelo backup-storage.mjs.
 *
 * Lê o `manifest.json` da pasta do backup e envia cada ficheiro para o mesmo
 * bucket e a mesma chave de onde veio — as tabelas apontam para
 * `bucket + storage_key`, por isso a chave tem de ficar igual ao byte.
 * Um objeto que já exista com essa chave é substituído (`x-upsert`).
 *
 * Corre a seco por omissão — só envia com `--yes`. Sem dependências (Node 18+).
 *
 *   node scripts/restore-storage.mjs --from backups/storage/2026-09-19T03-18-20
 *   node scripts/restore-storage.mjs --from <pasta> --yes
 *   node scripts/restore-storage.mjs --from <pasta> --bucket documents --yes
 *   node scripts/restore-storage.mjs --from <pasta> --prefix construction-invoices/<uuid>/ --yes
 *
 * Ver docs/operations.md.
 */

import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

/** Uploads em paralelo. */
const PARALLEL = 4;

const here = dirname(fileURLToPath(import.meta.url));

// ── argumentos ───────────────────────────────────────────────
const args = process.argv.slice(2);
const apply = args.includes("--yes");
const from = valueOf("--from");
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

if (!from) fail("--from <pasta do backup> é obrigatório.");
const fromRoot = resolve(from);

let manifest;
try {
  manifest = JSON.parse(readFileSync(join(fromRoot, "manifest.json"), "utf8"));
} catch {
  fail(`Não encontrei um manifest.json em ${fromRoot}`);
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

// O backup foi tirado de um projeto; enviar para outro é legítimo (restore
// num projeto novo), mas tem de ser à vista.
if (manifest.supabase_url && manifest.supabase_url !== supabaseUrl) {
  console.warn(`\n  ⚠ O backup veio de ${manifest.supabase_url}; o .env aponta para ${supabaseUrl}.`);
}

// ── Storage ──────────────────────────────────────────────────
async function upload(file) {
  const body = readFileSync(join(fromRoot, file.bucket, ...file.key.split("/")));
  const url = `${storageUrl}/object/${encodeURIComponent(file.bucket)}/${file.key
    .split("/")
    .map(encodeURIComponent)
    .join("/")}`;
  const res = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${serviceKey}`,
      apikey: serviceKey,
      "Content-Type": file.mimetype ?? "application/octet-stream",
      "x-upsert": "true",
    },
    body,
  });
  if (!res.ok) throw new Error(`${file.bucket}/${file.key}: HTTP ${res.status} ${await res.text()}`);
}

// ── execução ─────────────────────────────────────────────────
const files = manifest.files.filter(
  (f) => (!oneBucket || f.bucket === oneBucket) && f.key.startsWith(prefix),
);

console.log(`\n  origem   ${fromRoot}`);
console.log(`  alvo     ${oneBucket ?? "todos os buckets do manifesto"}${prefix ? ` (prefixo "${prefix}")` : ""}`);
console.log(`  modo     ${apply ? "ENVIAR" : "simulação (dry-run)"}`);
console.log(`  ficheiros ${files.length}\n`);

if (files.length === 0) {
  console.log("  Nada a enviar.\n");
  process.exit(0);
}

if (!apply) {
  console.log("  Simulação — não foi enviado nada. Repete com --yes.\n");
  process.exit(0);
}

let sent = 0;
let failures = 0;
for (let i = 0; i < files.length; i += PARALLEL) {
  const batch = files.slice(i, i + PARALLEL);
  const results = await Promise.allSettled(batch.map(upload));
  for (const r of results) {
    if (r.status === "fulfilled") sent += 1;
    else {
      failures += 1;
      console.error(`    ✖ ${r.reason.message}`);
    }
  }
}

console.log(`\n  ✔ ${sent} ficheiro(s) enviados.`);
if (failures > 0) {
  console.error(`  ✖ ${failures} falharam — repete o script; os que já lá estão são substituídos.\n`);
  process.exit(1);
}
console.log();
