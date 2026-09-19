# Skill: Add File Upload

**Quando usar**: uma feature precisa de guardar ficheiros (fotos, PDFs, comprovativos) no Supabase Storage.

**Tempo**: ~1-2 h (validação + storage + signed URLs + limpeza ao apagar)

> 📐 Ler também [[code-best-practices]] para nomes e tratamento de erros.
>
> Reescrita a 2026-09-19. A versão anterior ensinava o `/assets/{id}/banner` do Property-Management,
> um padrão que este projeto abandonou na `V24`/`V25`. Os exemplos abaixo são **o código real** do
> Worksite: `ConstructionInvoiceService` (documentos de fatura, 0..N) e `PaymentService`
> (comprovativo de pagamento, 0..1). Copiar destes, não inventar.

---

## Os dois padrões que existem

| Situação | Modelo | Exemplo real |
|---|---|---|
| A entidade pode ter **vários** ficheiros, ou **nenhum** | Tabela própria `<entidade>_document`, FK `ON DELETE CASCADE` | `construction_invoice_document` (`V24`) — ver [[database]] |
| A entidade tem **no máximo um** ficheiro, opcional | Colunas `<x>_bucket` + `<x>_key` (+ `_filename`, `_mime`) na própria linha | `payment.proof_*` (`V31`) |

Antes de escolher, responder: *pode chegar um segundo ficheiro para a mesma coisa?* Na fatura a
resposta era sim (a foto do WhatsApp e depois o PDF; a página 1 e a página 2) e foi isso que obrigou
à `V24` — não repetir o erro de meter o ficheiro dentro da entidade "para já".

---

## Regras fundamentais

1. **Nunca devolver a URL bruta do Storage** — só signed URLs, geradas na leitura por `SignedUrlService.resolve(bucket, key)` (cache de 1 h, `EXPIRES_SECONDS = 3600`).
2. **Nunca guardar o ficheiro na base de dados** — guardar `bucket` + `storage_key`. O bucket é sempre `"documents"` neste projeto.
3. **Validar o MIME e o tamanho antes de subir** — nunca só a extensão. `file.getContentType()` com `Optional.ofNullable(...).orElse("")`.
4. **Apagar no Storage quando se apaga a linha** — `deleteQuietly(bucket, key)` (e a miniatura, se houver). Um `DELETE` que deixa o ficheiro no bucket cria lixo que ninguém mais alcança — `scripts/README.md` existe por causa disso.
5. **Se há regra de duplicado, verificar antes de subir** — o checksum sha256 calcula-se sobre os bytes originais e `rejectIfDuplicate` corre **antes** do `storageService.upload`. Já custou órfãos no bucket (2026-09-18).
6. Limite global: `spring.servlet.multipart.max-file-size = 25MB` (`application.yml`); cada feature aperta o seu.

---

## Chave de storage

```
construction-invoices/<enterpriseId>/<8 chars de uuid>_<nome sanitizado>       ← documento de fatura
construction-invoices/<enterpriseId>/thumb_<8 chars>.jpg                        ← miniatura
construction-invoices/<enterpriseId ou scope>/payments/<8 chars>_<nome>          ← comprovativo
```

- `storageService.sanitizeFileName(originalFilename)` — nunca o nome cru.
- O prefixo de 8 chars evita colisões sem esconder o nome original (útil ao olhar para o bucket).
- Faturas sem obra (`COMPANY`/`UNIDENTIFIED`) usam o `scope` em minúsculas como segmento — ver `PaymentService.attachProof`.

---

## Passo 1 — MIME e tamanhos

```java
// PaymentService — comprovativo: imagem ou PDF, 10 MB
private static final Set<String> PROOF_MIME = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");
private static final long MAX_PROOF_BYTES = 10L * 1024 * 1024;
```

Erros: tipo não permitido → código **do domínio** quando a mensagem tem de ser específica
(`INVOICE_PAYMENT_PROOF_TYPE`), senão `FILE_TYPE_NOT_ALLOWED` (`FILE_003`); tamanho →
`FileUploadException.sizeExceeded(nome, tamanho, limite)` (`FILE_002`); vazio → `FILE_EMPTY` (`FILE_008`).

## Passo 2 — Subir (o esqueleto real)

```java
// ConstructionInvoiceService.storeDocument — reduzido ao essencial
String safeName = storageService.sanitizeFileName(originalFilename);
String key = String.format("construction-invoices/%s/%s_%s",
        enterpriseId, UUID.randomUUID().toString().substring(0, 8), safeName);

try (InputStream in = new ByteArrayInputStream(content)) {
    storageService.upload(BUCKET, key, mime, in);
} catch (IOException e) {
    throw StorageException.uploadError(originalFilename, e);
}

document.setBucket(BUCKET);
document.setStorageKey(key);
document.setOriginalFilename(originalFilename);
document.setMimeType(mime);
document.setSizeBytes((long) content.length);   // o que foi para o Storage, não o upload recebido
document.setUploadedAt(OffsetDateTime.now());
authContext.currentProfileId().ifPresent(document::setUploadedBy);
```

Notas:
- Ler os bytes **uma vez** (`file.getBytes()`) e passar `byte[]`; o checksum, a compressão e o upload usam os mesmos bytes.
- Fotos: `InvoiceCompressionService` comprime antes de subir e guarda `original_size_bytes` à parte. Só faz sentido para imagens grandes — não aplicar a PDFs nem a comprovativos.
- Miniatura (`InvoiceThumbnailService.render`) é **um extra**: se falhar, `log.warn` e `thumbnail_key = null`; nunca deixar cair o upload por causa dela.

## Passo 3 — Devolver

```java
// no mapper/serviço, nunca no controller
signedUrls.resolve(document.getBucket(), document.getStorageKey())
```

Na **lista** devolver só a miniatura (`thumbnailUrl`); o documento completo só no **detalhe**
(`GET /{id}`). Assinar 20 documentos por página que ninguém abre é trabalho deitado fora — ver
[[api]] → "Faturas de obra".

## Passo 4 — Controller

```java
@PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
public ResponseEntity<InvoiceUploadResultDTO> addDocument(@PathVariable UUID id,
                                                          @RequestParam("file") MultipartFile file)
```

- Ficheiro + JSON no mesmo pedido → `@RequestPart` para cada parte (ver `POST /construction-invoices/{id}/payments`, que leva o pagamento e o comprovativo).
- Registar no [[api]] (o hook pre-commit avisa).

## Passo 5 — Apagar

```java
deleteQuietly(document.getBucket(), document.getStorageKey());
deleteQuietly(document.getBucket(), document.getThumbnailKey());
documentRepository.delete(document);
```

`deleteQuietly` engole o erro do Storage com `log.warn`: a linha desaparece de qualquer forma; um
ficheiro órfão é melhor do que uma fatura que não se consegue apagar.

---

## Checklist final

- [ ] Decidido 0..N (tabela própria) vs 0..1 (colunas na entidade) — e porquê
- [ ] Migração com `bucket` + `storage_key` (+ `original_filename`, `mime_type`, `size_bytes`), nunca URL
- [ ] Whitelist de MIME + limite de tamanho, validados **antes** de subir
- [ ] Regra de duplicado (se existir) verificada **antes** de subir
- [ ] Chave com `sanitizeFileName` + prefixo aleatório, no bucket `documents`
- [ ] Signed URL só na leitura, via `SignedUrlService`; lista = miniatura, detalhe = completo
- [ ] `deleteQuietly` no apagar da linha e da entidade-mãe
- [ ] `@PreAuthorize` no controller; rota em [[api]]
- [ ] Frontend: `AuthenticatedImage` para imagens, `InvoicePreviewModal` para PDF/foto
- [ ] Testado: tipo recusado, tamanho recusado, duplicado, apagar limpa o bucket

## Erros comuns

❌ Meter o ficheiro dentro da entidade "para já" quando pode vir um segundo
❌ Subir e só depois verificar duplicado (deixa órfãos no bucket)
❌ Guardar a URL assinada
❌ Assinar todos os documentos numa lista
❌ Apagar a linha sem apagar no Storage
❌ Deixar a miniatura derrubar o upload

## Relacionado

- [[skill-add-database-table]] · [[skill-add-backend-feature]] · [[code-best-practices]]
- [[database]] → `construction_invoice_document`, `payment` · [[api]] → "Faturas de obra", "Pagamentos"
- `management/managementapi/scripts/README.md` — apagar faturas (linha + ficheiros) à mão
