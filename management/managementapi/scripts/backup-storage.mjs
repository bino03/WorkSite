#!/usr/bin/env node
/**
 * Copia buckets do Supabase Storage para o disco.
 *
 * Cada ficheiro fica em `backups/storage/<data>/<bucket>/<chave>` com a chave
 * original como caminho — o restore é o inverso, sem renomear nada. Ao lado
 * fica um `manifest.json` com bucket, chave, tamanho e `updated_at` de cada
 * objeto, para conferir a cópia sem a abrir.
 *
 * Só lê — não apaga nem escreve no Storage. Sem dependências (Node 18+).
 *
 *   node scripts/backup-storage.mjs                       # bucket documents
 *   node scripts/backup-storage.mjs --bucket media
 *   node scripts/backup-storage.mjs --all                 # todos os buckets
 *   node scripts/backup-storage.mjs --out D:\backups      # outra pasta destino
 *
 * `documents` é o único bucket que a app não sabe reconstruir (faturas e
 * provas de pagamento); `media` e `private` são banners, fotos e avatares.
 * Ver docs/operations.md.
 */

import { createWriteStream, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";
import { fileURLToPath } from "node:url";

/** O máximo que a API de listagem do Supabase devolve de uma vez. */
const PAGE_SIZE = 100;
/** Downloads em paralelo — chega para saturar a ligação sem irritar a API. */
const PARALLEL = 4;

const here = dirname(fileURLToPath(import.meta.url));

// ── argumentos ───────────────────────────────────────────────
const args = process.argv.slice(2);
const wantsAll = args.includes("--all");
const oneBucket = valueOf("--bucket") ?? (wantsAll ? null : "documents");
const outRoot = resolve(valueOf("--out") ?? join(here, "..", "backups", "storage"));

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

if (wantsAll && valueOf("--bucket")) fail("--all e --bucket são mutuamente exclusivos.");

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

/** Percorre o prefixo em profundidade e devolve os objetos com metadados. */
async function collectObjects(bucket, pathPrefix) {
  const objects = [];
  let offset = 0;

  for (;;) {
    const page = await listPage(bucket, pathPrefix, offset);
    if (page.length === 0) break;

    for (const entry of page) {
      const key = `${pathPrefix}${entry.name}`;
      if (entry.id === null) {
        objects.push(...(await collectObjects(bucket, `${key}/`)));
      } else {
        objects.push({
          key,
          size: entry.metadata?.size ?? null,
          mimetype: entry.metadata?.mimetype ?? null,
          updated_at: entry.updated_at ?? null,
        });
      }
    }

    if (page.length < PAGE_SIZE) break;
    offset += PAGE_SIZE;
  }

  return objects;
}

/** Faz stream do objeto para o disco; a chave vira caminho relativo. */
async function download(bucket, key, destRoot) {
  const url = `${storageUrl}/object/${encodeURIComponent(bucket)}/${key
    .split("/")
    .map(encodeURIComponent)
    .join("/")}`;
  const res = await fetch(url, { headers: authHeaders });
  if (!res.ok || !res.body) {
    throw new Error(`${bucket}/${key}: HTTP ${res.status} ${await res.text()}`);
  }

  const dest = join(destRoot, bucket, ...key.split("/"));
  mkdirSync(dirname(dest), { recursive: true });
  await pipeline(Readable.fromWeb(res.body), createWriteStream(dest));
}

// ── execução ─────────────────────────────────────────────────
const buckets = await listBuckets();
if (buckets.length === 0) {
  console.log("\n  Não há buckets neste projeto Supabase.\n");
  process.exit(0);
}

const targets = wantsAll ? buckets.map((b) => b.name) : [oneBucket];
if (!wantsAll && !buckets.some((b) => b.name === oneBucket)) {
  fail(`O bucket "${oneBucket}" não existe. Buckets: ${buckets.map((b) => b.name).join(", ")}`);
}

const stamp = new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19);
const destRoot = join(outRoot, stamp);

console.log(`\n  alvo     ${wantsAll ? "TODOS os buckets" : oneBucket}`);
console.log(`  destino  ${destRoot}\n`);

const manifest = [];
let failures = 0;

for (const bucket of targets) {
  const objects = await collectObjects(bucket, "");
  const bytes = objects.reduce((sum, o) => sum + (o.size ?? 0), 0);
  console.log(`  ${bucket.padEnd(16)} ${String(objects.length).padStart(5)} ficheiro(s)   ${(bytes / 1_048_576).toFixed(1)} MB`);

  // Lotes de PARALLEL: simples, e o suficiente para não abrir 200 ligações.
  for (let i = 0; i < objects.length; i += PARALLEL) {
    const batch = objects.slice(i, i + PARALLEL);
    const results = await Promise.allSettled(batch.map((o) => download(bucket, o.key, destRoot)));
    results.forEach((r, n) => {
      const o = batch[n];
      if (r.status === "fulfilled") {
        manifest.push({ bucket, ...o });
      } else {
        failures += 1;
        console.error(`    ✖ ${r.reason.message}`);
      }
    });
  }
}

mkdirSync(destRoot, { recursive: true });
writeFileSync(
  join(destRoot, "manifest.json"),
  JSON.stringify({ supabase_url: supabaseUrl, taken_at: new Date().toISOString(), files: manifest }, null, 2),
);

console.log(`\n  ✔ ${manifest.length} ficheiro(s) copiados para ${destRoot}`);
if (failures > 0) {
  console.error(`  ✖ ${failures} falharam — repete o script; os que já existem são reescritos.\n`);
  process.exit(1);
}
console.log();
