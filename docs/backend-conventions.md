# ⚙️ Convenções do backend

Decisões do `managementapi` que não se deduzem a olhar para o código, e cujo motivo se perde se
não ficar escrito. O schema está em [[database]], as rotas em [[api]], a autenticação e o CORS em
[[security]].

## MapStruct + Lombok

A ordem dos annotation processors no `pom.xml` é intencional: **o Lombok tem de correr antes do
MapStruct**. Não reordenar — o MapStruct precisa dos getters/setters que o Lombok gera.

## Tratamento de erros

- **`dto/error/ErrorCode.java`** — enum único, organizado por módulo de domínio (`INVOICE_xxx`,
  `BUDGET_xxx`, `SUPPLIER_xxx`, `USER_xxx`, `NOTIF_xxx`).
- **`exeption/`** — o nome do pacote tem mesmo essa gralha; mantida por consistência com o
  original, e mudá-la agora é ruído em todos os imports.
  - `BusinessException` — violação de regra de negócio
  - `ResourceNotFoundException` — 404
  - `FileUploadException` / `StorageException` — upload e Supabase Storage
  - `ForbiddenException` — 403
- **`GlobalExceptionHandler`** — o **único** `@RestControllerAdvice` da aplicação. O projeto de
  origem tinha um segundo em `infra/`, com um handler duplicado de
  `HttpMediaTypeNotSupportedException`; foi descartado na cópia. Se aparecer um segundo, é bug.

## Ficheiros lidos, não só guardados

Dependências que existem para **ler** o que os clientes enviam:

- **Apache POI** (`poi-ooxml`) — `BudgetExcelImportService` reconstrói a árvore de rubricas a
  partir do `.xlsx` do empreiteiro.
- **ZXing + PDFBox** — `AtInvoiceQrService` lê o QR da AT. O PDFBox rasteriza a página quando é
  PDF, o ZXing descodifica. Preferido a OCR por ser determinístico e correr offline: **nada sai
  do servidor**. Sem QR legível devolve vazio e o preenchimento segue manual — nunca é erro.
- **OpenCV/WeChat QRCode** (`org.bytedeco:opencv`, via JavaCPP) — `WeChatQrCodeService`, o último
  degrau da escalada. Só corre quando o ZXing (mosaico incluído) não encontra QR nenhum: CNN
  detetora + super-resolução, treinada para QR pequeno/desfocado dentro de uma foto maior — o
  caso que mata o ZXing (fotos de WhatsApp). Medido contra 19 faturas reais: 14/19 para 15/19.

### Armadilhas do OpenCV que não dão erro à compilação

- Modelos (~1,1 MB) em `src/main/resources/models/wechat-qrcode/`, de
  [WeChatCV/opencv_3rdparty](https://github.com/WeChatCV/opencv_3rdparty/tree/wechat_qrcode)
  (Apache 2.0). Extraídos para uma pasta temporária no arranque (`@Async` +
  `ApplicationReadyEvent`), para a primeira fatura real não pagar o carregamento.
- **Duas dependências de classifier por versão** (`opencv` e `openblas`, cada uma
  `windows-x86_64` + `linux-x86_64`). **Nunca usar `opencv-platform` sozinho**: arrasta
  android/iOS/macOS e o jar final passa de ~200 MB para ~460 MB.
- **Esquecer o classifier do `openblas`** (usado pelo DNN por baixo do OpenCV) **não dá erro à
  compilação** — só `UnsatisfiedLinkError` na primeira chamada real. Se isto rebentar depois de
  mudar a versão, é a primeira coisa a verificar.
- Se a biblioteca nativa não carregar nesta máquina, o degrau desliga-se em silêncio — nunca é a
  fatura a falhar por causa disto.

## Compressão HTTP

Ligada em `application.yml` (`server.compression`); vem desligada por omissão no Spring Boot. As
respostas desta API repetem os mesmos nomes de campo centenas de vezes — a árvore de orçamento
traz ~198 nós com ~25 chaves cada. Não afeta as faturas: são PDF/imagem, já comprimidas, e
servidas directamente pelo Supabase por signed URL.

## Compressão de imagens de faturas

`InvoiceCompressionService` (`ImageIO` + `Graphics2D`, mesmo padrão do `InvoiceThumbnailService` —
sem dependências novas). Corre **depois** de o QR ser lido e **só quando a leitura teve sucesso**.
Nunca antes: comprimir primeiro já tirou legibilidade a QR que liam bem em qualidade total. Sem QR
legível, o original fica intacto em Storage — a melhor hipótese para revisão manual ou para um
`/rescan` mais tarde. Por isso o cliente **não comprime nada** antes do upload.

## URLs de fotos nas respostas da API

Sempre que um endpoint devolve um URL de foto/media, **gerar um signed URL do Supabase** com
`SupabaseStorageService.createSignedUrl(bucket, key, expiresInSeconds)`. Nunca devolver a chave
guardada em cru.

```java
private String resolvePhotoUrl(Profile profile) {
    if (profile == null || profile.getPhotoKey() == null) return null;
    try {
        String bucket = profile.getPhotoBucket();
        String key = profile.getPhotoKey().startsWith("/") ? profile.getPhotoKey().substring(1) : profile.getPhotoKey();
        return storage.createSignedUrl(bucket, key, 3600);
    } catch (Exception e) {
        log.warn("Nao foi possivel gerar signed URL: {}", e.getMessage());
        return null;
    }
}
```

- Tirar a `/` inicial da chave antes de chamar `createSignedUrl`.
- Devolver `null` sem estardalhaço se não houver foto ou se a chamada falhar.
- Implementação de referência: `POST /profile/photo-url` no `ProfileController`.

## Uploads

Máximo 25 MB (`application.yml`). Os documentos vão para o `SupabaseStorageService`, bucket
`documents`. Ver [[skills/backend/skill-add-file-upload]].

## Email

`spring-boot-starter-mail`, usado **só** no fluxo de convite de admin (`AdminAuthController` →
`EmailService`). A configuração SMTP vem da tabela `settings.email_providers`, que não tem
interface — ver [[environment]].

## Infraestrutura e diagnóstico (`infra/`)

- `DataSourceDiagnostics` — imprime as propriedades efetivas do datasource e testa a ligação no
  arranque. É o primeiro sítio a olhar quando a app arranca mas não fala com a base de dados.
- `StartupFailureLogger` — regista falhas de inicialização.

## Relacionado

- [[database]] · [[api]] · [[security]] · [[architecture]]
- [[commands]] — como correr e testar
- [[skills/backend/skill-add-backend-feature]] — checklist para uma feature nova
