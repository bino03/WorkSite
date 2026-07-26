package com.management.managementapi.exeption;


import com.management.managementapi.dto.error.ErrorCode;

/**
 * Exceção lançada quando ocorre erro no upload de ficheiros.
 * Resulta em HTTP 400 (Bad Request).
 */
public class FileUploadException extends BusinessException {
    
    public FileUploadException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public FileUploadException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
    
    public FileUploadException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(errorCode, customMessage, cause);
    }
    
    // Helper methods específicos
    
    public static FileUploadException invalidType(String fileName, String receivedType, String expectedTypes) {
        return new FileUploadException(
            ErrorCode.FILE_TYPE_NOT_ALLOWED,
            String.format("Tipo de ficheiro '%s' não permitido para '%s'. Tipos aceites: %s", 
                receivedType, fileName, expectedTypes)
        );
    }
    
    public static FileUploadException sizeExceeded(String fileName, long receivedSize, long maxSize) {
        return new FileUploadException(
            ErrorCode.FILE_SIZE_EXCEEDED,
            String.format("Ficheiro '%s' excede o tamanho máximo (%.2f MB). Máximo permitido: %.2f MB",
                fileName, receivedSize / (1024.0 * 1024.0), maxSize / (1024.0 * 1024.0))
        );
    }
    
    public static FileUploadException empty(String fileName) {
        return new FileUploadException(
            ErrorCode.FILE_EMPTY,
            "Ficheiro '" + fileName + "' está vazio"
        );
    }
    
    public static FileUploadException corrupted(String fileName) {
        return new FileUploadException(
            ErrorCode.FILE_CORRUPTED,
            "Ficheiro '" + fileName + "' está corrompido ou ilegível"
        );
    }
    
    // Específicos para imagens
    public static FileUploadException invalidImageFormat(String fileName) {
        return new FileUploadException(
            ErrorCode.IMAGE_INVALID_FORMAT,
            "Formato de imagem inválido para '" + fileName + "'. Formatos aceites: JPEG, PNG, WEBP, AVIF"
        );
    }
    
    public static FileUploadException imageSizeExceeded(String fileName, long size) {
        return new FileUploadException(
            ErrorCode.IMAGE_SIZE_EXCEEDED,
            String.format("Imagem '%s' excede o tamanho máximo de 15 MB (tamanho: %.2f MB)",
                fileName, size / (1024.0 * 1024.0))
        );
    }
    
    // Específicos para vídeos
    public static FileUploadException invalidVideoFormat(String fileName) {
        return new FileUploadException(
            ErrorCode.VIDEO_INVALID_FORMAT,
            "Formato de vídeo inválido para '" + fileName + "'. Formatos aceites: MP4, WEBM"
        );
    }
    
    public static FileUploadException videoSizeExceeded(String fileName, long size) {
        return new FileUploadException(
            ErrorCode.VIDEO_SIZE_EXCEEDED,
            String.format("Vídeo '%s' excede o tamanho máximo de 250 MB (tamanho: %.2f MB)",
                fileName, size / (1024.0 * 1024.0))
        );
    }
    
    // Específicos para banner
    public static FileUploadException bannerUploadError(String fileName, Throwable cause) {
        return new FileUploadException(
            ErrorCode.MEDIA_BANNER_UPLOAD_ERROR,
            "Erro ao fazer upload do banner '" + fileName + "'",
            cause
        );
    }
    
    // Específicos para galeria
    public static FileUploadException galleryUploadError(String fileName, Throwable cause) {
        return new FileUploadException(
            ErrorCode.MEDIA_GALLERY_UPLOAD_ERROR,
            "Erro ao fazer upload da imagem da galeria '" + fileName + "'",
            cause
        );
    }
    
    public static FileUploadException galleryLimitExceeded(int received, int max) {
        return new FileUploadException(
            ErrorCode.MEDIA_GALLERY_LIMIT_EXCEEDED,
            String.format("Limite de fotos na galeria excedido (%d fotos). Máximo permitido: %d", 
                received, max)
        );
    }
    
    public static FileUploadException galleryMetadataMismatch(int filesCount, int metadataCount) {
        return new FileUploadException(
            ErrorCode.MEDIA_GALLERY_METADATA_MISMATCH,
            String.format("Número de ficheiros (%d) não corresponde aos metadados (%d)",
                filesCount, metadataCount)
        );
    }
    
    // Específico para documentos
    public static FileUploadException documentUploadError(String fileName, Throwable cause) {
        return new FileUploadException(
            ErrorCode.DOCUMENT_UPLOAD_ERROR,
            "Erro ao fazer upload do documento '" + fileName + "'",
            cause
        );
    }
    
    // Específico para licenças
    public static FileUploadException licenseUploadError(String fileName, Throwable cause) {
        return new FileUploadException(
            ErrorCode.LICENSE_UPLOAD_ERROR,
            "Erro ao fazer upload da licença energética '" + fileName + "'",
            cause
        );
    }
}