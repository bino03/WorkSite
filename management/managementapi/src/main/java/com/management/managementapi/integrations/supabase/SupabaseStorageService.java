package com.management.managementapi.integrations.supabase;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.Okio;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class SupabaseStorageService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageService.class);

    private final SupabaseProperties props;
    private final OkHttpClient http;
    private static final Logger logger = LoggerFactory.getLogger(SupabaseStorageService.class);


    public SupabaseStorageService(SupabaseProperties props) {
        this.props = Objects.requireNonNull(props, "props");
        this.http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofMinutes(5))
                .writeTimeout(Duration.ofMinutes(5))
                .callTimeout(Duration.ofMinutes(5))
                .build();
    }

    @PostConstruct
    void checkConfig() {
        log.info("[supabase] base={} (storage={})", props.getUrl(), baseStorageUrl());
    }

    /* ===================== API PÚBLICA ===================== */

    /** Upload por stream (sem carregar ficheiro inteiro em memória). */
    public void upload(String bucket, String path, String contentType, InputStream in) throws IOException {
        ensureArgs(bucket, path, in);
        final String url = baseStorageUrl() + "/object/"
                + encodeSegment(bucket) + "/"
                + encodePath(path)
                + "?upsert=true";

        RequestBody body = new InputStreamRequestBody(contentType, in);
        Request req = addAuth(new Request.Builder()
                .url(url)
                .put(body))
                .header("X-Client-Info", "GestaoAPI/StorageService")
                .build();

        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful()) {
                String error = bodyStringSafe(res);
                throw new IOException("Upload falhou: HTTP " + res.code() + " - " + res.message()
                        + (error.isEmpty() ? "" : " - " + error));
            }
        }
    }


    /**
     * Descarrega o objeto para memória.
     *
     * Os restantes downloads fazem stream directo para o cliente; este existe
     * para o servidor voltar a <b>ler</b> um ficheiro que já guardou — hoje,
     * reler o QR de uma fatura arquivada. Carregar tudo em memória é aceitável
     * porque o teto do upload são 25 MB.
     */
    public byte[] download(String bucket, String path) throws IOException {
        ensureArgs(bucket, path);
        final String url = baseStorageUrl() + "/object/"
                + encodeSegment(bucket) + "/"
                + encodePath(path);

        Request req = addAuth(new Request.Builder()
                .url(url)
                .get())
                .header("X-Client-Info", "GestaoAPI/StorageService")
                .build();

        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful()) {
                handleDownloadError(res, path);
            }
            ResponseBody body = res.body();
            if (body == null) {
                throw new IOException("Resposta sem corpo ao descarregar: " + path);
            }
            return body.bytes();
        }
    }

public void streamDownload(String bucket, String path, HttpServletResponse clientRes) throws IOException {
    ensureArgs(bucket, path, clientRes);
    final String url = baseStorageUrl() + "/object/"
            + encodeSegment(bucket) + "/"
            + encodePath(path);

    Request req = addAuth(new Request.Builder()
            .url(url)
            .get())
            .header("X-Client-Info", "GestaoAPI/StorageService")
            .build();

    try (Response res = http.newCall(req).execute()) {
        if (!res.isSuccessful()) {
            String error = bodyStringSafe(res);
            throw new IOException("Download falhou: HTTP " + res.code() + " - " + res.message()
                    + (error.isEmpty() ? "" : " - " + error));
        }

        // 🔥 FORÇAR visualização do PDF no navegador
        String fileName = extractFileName(path);
        boolean isPdf = fileName.toLowerCase().endsWith(".pdf");
        
        if (isPdf) {
            clientRes.setContentType("application/pdf");
            clientRes.setHeader("Content-Disposition", "inline; filename=\"" + fileName + "\"");
        } else {
            // Para outros tipos de arquivo, manter comportamento original
            String contentType = res.header("Content-Type", "application/octet-stream");
            clientRes.setContentType(contentType);
        }

        String contentLength = res.header("Content-Length");
        if (contentLength != null) {
            clientRes.setHeader("Content-Length", contentLength);
        }

        // Headers importantes
        clientRes.setHeader("X-Content-Type-Options", "nosniff");

        try (InputStream supabaseStream = Objects.requireNonNull(res.body()).byteStream();
                OutputStream out = clientRes.getOutputStream()) {
            supabaseStream.transferTo(out);
        }
    }
}

/**
 * Stream download COM mime type da BD (RECOMENDADO)
 */
/**
 * Stream download COM mime type da BD (RECOMENDADO)
 * @param bucket Nome do bucket
 * @param path Caminho do ficheiro
 * @param mimeType Mime type da base de dados
 * @param clientRes Response HTTP
 */
public void streamDownload(String bucket, String path, String mimeType, HttpServletResponse clientRes) 
    throws IOException {
    ensureArgs(bucket, path, clientRes);
    final String url = baseStorageUrl() + "/object/"
            + encodeSegment(bucket) + "/"
            + encodePath(path);

    Request req = addAuth(new Request.Builder()
            .url(url)
            .get())
            .header("X-Client-Info", "GestaoAPI/StorageService")
            .build();

    try (Response res = http.newCall(req).execute()) {
        if (!res.isSuccessful()) {
            handleDownloadError(res, path);
        }

        // 🔥 USA O MIME TYPE DA BD
        configureResponseHeadersWithMimeType(clientRes, path, mimeType, res);

        try (InputStream supabaseStream = Objects.requireNonNull(res.body()).byteStream();
             OutputStream out = clientRes.getOutputStream()) {
            supabaseStream.transferTo(out);
        }
    }
}
/**
 * Configura headers usando o mime type da BD
 */
private void configureResponseHeadersWithMimeType(HttpServletResponse clientRes, String path, 
                                                   String mimeType, Response supabaseRes) {
    String fileName = extractFileName(path);
    
    // Usa o mime type da BD (ou fallback)
    String contentType = (mimeType != null && !mimeType.trim().isEmpty()) 
        ? mimeType 
        : "application/octet-stream";
    
    // Determina se deve mostrar inline baseado no mime type
    boolean shouldInline = shouldDisplayInlineByMimeType(contentType);
    
    clientRes.setContentType(contentType);
    
    String disposition = shouldInline ? "inline" : "attachment";
    clientRes.setHeader("Content-Disposition", disposition + "; filename=\"" + fileName + "\"");
    
    // Content-Length
    String contentLength = supabaseRes.header("Content-Length");
    if (contentLength != null) {
        clientRes.setHeader("Content-Length", contentLength);
    }
    
    clientRes.setHeader("X-Content-Type-Options", "nosniff");
    clientRes.setHeader("Cache-Control", "private, max-age=3600");
}


/**
 * Determina se deve exibir inline baseado no MIME type
 */
private boolean shouldDisplayInlineByMimeType(String mimeType) {
    if (mimeType == null) return false;
    
    return mimeType.equals("application/pdf") ||
           mimeType.startsWith("image/") ||
           mimeType.equals("text/plain") ||
           mimeType.startsWith("video/") ||
           mimeType.startsWith("audio/");
}

/**
 * Trata erros de download
 */

private void handleDownloadError(Response res, String path) throws IOException {
    String error = bodyStringSafe(res);
    
    if (res.code() == 404) {
        throw new IOException("Ficheiro não encontrado: " + path);
    } else if (res.code() == 403) {
        throw new IOException("Acesso negado ao ficheiro: " + path);
    } else if (res.code() == 401) {
        throw new IOException("Autenticação inválida");
    }
    
    throw new IOException("Download falhou: HTTP " + res.code() + " - " + res.message()
            + (error.isEmpty() ? "" : " - " + error));
}

    /** Cria um URL assinado temporário (para objetos privados). */
    public String createSignedUrl(String bucket, String path, int expiresSec) throws IOException {
        ensureArgs(bucket, path);

        final String url = baseStorageUrl() + "/object/sign/"
                + encodeSegment(bucket) + "/"
                + encodePath(path);

        String json = "{\"expiresIn\":" + expiresSec + "}";

        Request req = addAuth(new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, MediaType.parse("application/json"))))
                .header("X-Client-Info", "GestaoAPI/StorageService")
                .build();

        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful()) {
                String error = bodyStringSafe(res);
                throw new IOException("Sign falhou: HTTP " + res.code() + " - " + res.message()
                        + (error.isEmpty() ? "" : " - " + error));
            }
            String body = bodyStringSafe(res);
            String marker = "\"signedURL\":\"";
            int i = body.indexOf(marker);
            if (i < 0)
                throw new IOException("signedURL não encontrado");
            int j = body.indexOf("\"", i + marker.length());
            String signedPath = body.substring(i + marker.length(), j);

            // retorna URL completo
            return baseStorageUrl().replaceAll("/+$", "") + signedPath;
        }
    }

    /** Apaga um ficheiro do bucket. */
public void delete(String bucket, String path) throws IOException {
    ensureArgs(bucket, path);
    
    // 🔍 LOG: O que estamos a tentar apagar
    logger.info("🗑️ DELETE Storage - Bucket: {}, Path: {}", bucket, path);
    
    final String url = baseStorageUrl() + "/object/"
            + encodeSegment(bucket) + "/"
            + encodePath(path);
    
    // 🔍 LOG: URL completa
    logger.info("🗑️ DELETE URL: {}", url);

    Request req = addAuth(new Request.Builder()
            .url(url)
            .delete())
            .header("X-Client-Info", "GestaoAPI/StorageService")
            .build();

    try (Response res = http.newCall(req).execute()) {
        // 🔍 LOG: Response status
        logger.info("🗑️ DELETE Response: HTTP {}", res.code());
        
        if (!res.isSuccessful() && res.code() != 404) {
            String error = bodyStringSafe(res);
            logger.error("❌ DELETE FALHOU - HTTP {}: {}, Body: {}", 
                res.code(), res.message(), error);
            throw new IOException("Falha ao apagar ficheiro: HTTP " + res.code() + " - " + res.message()
                    + (error.isEmpty() ? "" : " - " + error));
        }
        
        if (res.code() == 404) {
            logger.warn("⚠️ DELETE 404 - Ficheiro não existe: {}/{}", bucket, path);
        } else {
            logger.info("✅ DELETE OK - HTTP {}", res.code());
        }
    }
}

    /** 🔹 NOVO: Gera o URL público (para objetos em buckets públicos). */
    public String getPublicUrl(String bucket, String path) {
        if (bucket == null || path == null)
            return null;
        String base = props.getUrl();
        if (base.endsWith("/"))
            base = base.substring(0, base.length() - 1);
        return base + "/storage/v1/object/public/" + bucket + "/" + path;
    }

    /* ===================== HELPERS ===================== */

    private Request.Builder addAuth(Request.Builder b) {
        String key = props.getServiceRoleKey();
        return b.header("Authorization", "Bearer " + key)
                .header("apikey", key);
    }

    private String baseStorageUrl() {
        String base = props.getUrl();
        if (base.endsWith("/"))
            base = base.substring(0, base.length() - 1);
        return base + "/storage/v1";
    }

    private static void ensureArgs(Object... args) {
        for (Object a : args)
            if (a == null)
                throw new IllegalArgumentException("Argumento obrigatório é null");
    }

    private static String encodeSegment(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Faz URL-encode por segmentos para preservar as barras do path. */
    private static String encodePath(String path) {
        String[] parts = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0)
                sb.append('/');
            sb.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static String bodyStringSafe(Response res) {
        try {
            ResponseBody rb = res.body();
            return rb != null ? rb.string() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** Faz stream de um InputStream sem carregar tudo na memória. */
    static class InputStreamRequestBody extends RequestBody {
        private final String contentType;
        private final InputStream in;

        InputStreamRequestBody(String contentType, InputStream in) {
            this.contentType = (contentType == null || contentType.isBlank())
                    ? "application/octet-stream"
                    : contentType;
            this.in = in;
        }

        @Override
        public MediaType contentType() {
            return MediaType.parse(contentType);
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            try (InputStream src = in) {
                sink.writeAll(Okio.source(src));
            }
        }
    }

    /**
     * Obtém o JWT do usuário autenticado atual
     */
    public Optional<Jwt> getCurrentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated())
            return Optional.empty();
        if (!(auth.getPrincipal() instanceof Jwt jwt))
            return Optional.empty();

        return Optional.of(jwt);
    }

    /**
     * Obtém o token JWT como string
     */
    public Optional<String> getCurrentJwtToken() {
        return getCurrentJwt().map(Jwt::getTokenValue);
    }

    /**
     * Stream download com JWT opcional (usa service role se não fornecido)
     */
    public void streamDownload(String bucket, String path, HttpServletResponse clientRes, String userJwt)
        throws IOException {
    ensureArgs(bucket, path, clientRes);
    final String url = baseStorageUrl() + "/object/"
            + encodeSegment(bucket) + "/"
            + encodePath(path);

    Request.Builder requestBuilder = new Request.Builder()
            .url(url)
            .get()
            .header("X-Client-Info", "GestaoAPI/StorageService");

    Request req = (userJwt != null)
            ? addUserAuth(requestBuilder, userJwt).build()
            : addAuth(requestBuilder).build();

    try (Response res = http.newCall(req).execute()) {
        if (!res.isSuccessful()) {
            String error = bodyStringSafe(res);
            if (res.code() == 404) {
                throw new IOException("Ficheiro não encontrado: " + path);
            } else if (res.code() == 403) {
                throw new IOException("Acesso negado ao ficheiro: " + path);
            }
            throw new IOException("Download falhou: HTTP " + res.code() + " - " + res.message()
                    + (error.isEmpty() ? "" : " - " + error));
        }

        // 🔥 MESMA CORREÇÃO AQUI
        String contentType = res.header("Content-Type");
        String contentLength = res.header("Content-Length");
        String contentDisposition = res.header("Content-Disposition");
        
        if (path.toLowerCase().endsWith(".pdf")) {
            contentType = "application/pdf";
            contentDisposition = "inline; filename=\"" + extractFileName(path) + "\"";
        }

        clientRes.setContentType(contentType);
        if (contentLength != null) {
            clientRes.setHeader("Content-Length", contentLength);
        }
        if (contentDisposition != null) {
            clientRes.setHeader("Content-Disposition", contentDisposition);
        }
        
        // Headers adicionais
        clientRes.setHeader("X-Content-Type-Options", "nosniff");
        clientRes.setHeader("Cache-Control", "private, max-age=3600");

        try (InputStream supabaseStream = Objects.requireNonNull(res.body()).byteStream();
                OutputStream out = clientRes.getOutputStream()) {
            supabaseStream.transferTo(out);
        }
    }
}

    /**
     * Adiciona autenticação com JWT do usuário
     */
   private Request.Builder addUserAuth(Request.Builder b, String userJwt) {
    // Validação do userJwt
    if (userJwt == null || userJwt.trim().isEmpty()) {
        throw new IllegalArgumentException("User JWT token cannot be null or empty");
    }
    
    // Validação da anonKey
    String anonKey = props.getAnonKey();
    if (anonKey == null || anonKey.trim().isEmpty()) {
        log.error("Supabase anonKey is not configured properly");
        throw new IllegalStateException("Supabase anonKey configuration is missing");
    }
    
    return b.header("Authorization", "Bearer " + userJwt)
            .header("apikey", anonKey);
}


    /**
     * Extrai um nome de ficheiro seguro a partir do path completo
     */

private String extractFileName(String path) {
    if (path == null || path.isEmpty()) {
        return "documento.bin";
    }
    
    // Remove barras finais
    path = path.replaceAll("/+$", "");
    
    // Extrai o nome após a última barra
    int lastSlash = path.lastIndexOf('/');
    String fileName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    
    if (fileName.isEmpty()) {
        return "documento.bin";
    }
    
    // 🔥 SOLUÇÃO: Não sanitiza se já tiver extensão válida
    // Apenas faz decode de URL encoding se existir
    try {
        fileName = java.net.URLDecoder.decode(fileName, "UTF-8");
    } catch (Exception e) {
        // Se falhar decode, usa o nome original
    }
    
    // Verifica se tem extensão
    int lastDot = fileName.lastIndexOf('.');
    if (lastDot <= 0) {
        return sanitizeName(fileName);
    }
    
    String name = fileName.substring(0, lastDot);
    String extension = fileName.substring(lastDot + 1);
    
    // Só sanitiza se tiver caracteres realmente problemáticos
    String safeName = sanitizeNameLight(name);
    String safeExtension = extension.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    
    return safeName + "." + safeExtension;
}

/**
 * Sanitiza o nome do ficheiro (versão completa) - remove acentos e caracteres especiais
 * Permite apenas: letras (sem acentos), números, espaços, pontos, underscores e hífens
 */
public String sanitizeName(String name) {
    if (name == null || name.isEmpty()) {
        return "documento";
    }
    
    // Remove acentos usando Normalizer (mais robusto)
    name = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    
    // Converte para lowercase para consistência
    name = name.toLowerCase();
    
    // Remove todos os caracteres que não são: letras, números, espaços, pontos, underscores, hífens
    name = name.replaceAll("[^a-z0-9 ._-]", "");
    
    // Remove espaços múltiplos e trim
    name = name.replaceAll("\\s+", " ").trim();
    
    // Remove pontos e underscores consecutivos
    name = name.replaceAll("[\\._]{2,}", "_");
    
    // Remove pontos e underscores no início/final
    name = name.replaceAll("^[\\._]+|[\\._]+$", "");
    
    // Limita o tamanho máximo (255 caracteres)
    if (name.length() > 255) {
        name = name.substring(0, 255);
    }
    
    return name.isEmpty() ? "documento" : name;
}

/**
 * Sanitiza o nome completo do ficheiro (nome + extensão)
 * Mantém a extensão original mas sanitiza o nome
 */
public String sanitizeFileName(String fileName) {
    if (fileName == null || fileName.isEmpty()) {
        return "documento.pdf";
    }
    
    // Encontra a última ocorrência do ponto para separar nome e extensão
    int lastDotIndex = fileName.lastIndexOf('.');
    String namePart, extensionPart;
    
    if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
        namePart = fileName.substring(0, lastDotIndex);
        extensionPart = fileName.substring(lastDotIndex); // inclui o ponto
    } else {
        namePart = fileName;
        extensionPart = "";
    }
    
    // Sanitiza o nome (sem a extensão)
    String sanitizedName = sanitizeName(namePart);
    
    // Sanitiza a extensão - permite apenas letras e números
    if (!extensionPart.isEmpty()) {
        String ext = extensionPart.substring(1); // remove o ponto
        ext = ext.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (!ext.isEmpty()) {
            extensionPart = "." + ext;
        } else {
            extensionPart = ".pdf"; // extensão padrão se a original for inválida
        }
    } else {
        extensionPart = ".pdf"; // extensão padrão se não houver extensão
    }
    
    return sanitizedName + extensionPart;
}


/**
 * Sanitiza o nome do ficheiro (versão light) - remove apenas caracteres perigosos
 * Permite: letras, números, espaços, acentos, pontos, underscores, hífens e parênteses
 */
private String sanitizeNameLight(String name) {
    if (name == null || name.isEmpty()) {
        return "documento";
    }
    
    // Remove apenas caracteres perigosos: \ / : * ? " < > | + & % $ # @ ! ~ ` = [ ] { } ;
    name = name.replaceAll("[\\\\/:*?\"<>|+&%$#@!~`=\\[\\]{};]", "");
    
    // Remove espaços múltiplos e trim
    name = name.replaceAll("\\s+", " ").trim();
    
    // Limita o tamanho máximo (255 caracteres)
    if (name.length() > 255) {
        name = name.substring(0, 255);
    }
    
    return name.isEmpty() ? "documento" : name;
}
}
