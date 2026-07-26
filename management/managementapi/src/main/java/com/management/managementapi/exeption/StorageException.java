package com.management.managementapi.exeption;

import com.management.managementapi.dto.error.ErrorCode;

/**
 * Exceção lançada quando ocorre erro no Supabase Storage.
 * Resulta em HTTP 500 ou 503 dependendo do erro.
 */
public class StorageException extends BusinessException {
    
    public StorageException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public StorageException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
    
    public StorageException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(errorCode, customMessage, cause);
    }
    
    // Helper methods
    
    public static StorageException uploadError(String fileName, Throwable cause) {
        return new StorageException(
            ErrorCode.STORAGE_UPLOAD_ERROR,
            "Erro ao fazer upload de '" + fileName + "' para o storage",
            cause
        );
    }
    
    public static StorageException deleteError(String filePath, Throwable cause) {
        return new StorageException(
            ErrorCode.STORAGE_DELETE_ERROR,
            "Erro ao eliminar '" + filePath + "' do storage",
            cause
        );
    }
    
    public static StorageException downloadError(String filePath, Throwable cause) {
        return new StorageException(
            ErrorCode.STORAGE_DOWNLOAD_ERROR,
            "Erro ao descarregar '" + filePath + "' do storage",
            cause
        );
    }
    
    public static StorageException fileNotFound(String filePath) {
        return new StorageException(
            ErrorCode.STORAGE_FILE_NOT_FOUND,
            "Ficheiro '" + filePath + "' não encontrado no storage"
        );
    }
    
    public static StorageException bucketNotFound(String bucketName) {
        return new StorageException(
            ErrorCode.STORAGE_BUCKET_NOT_FOUND,
            "Bucket '" + bucketName + "' não encontrado no storage"
        );
    }
    
    public static StorageException signedUrlError(String filePath, Throwable cause) {
        return new StorageException(
            ErrorCode.STORAGE_SIGNED_URL_ERROR,
            "Erro ao gerar URL assinado para '" + filePath + "'",
            cause
        );
    }
    
    public static StorageException connectionError(Throwable cause) {
        return new StorageException(
            ErrorCode.STORAGE_CONNECTION_ERROR,
            "Erro de conexão com o Supabase Storage",
            cause
        );
    }
    
    public static StorageException quotaExceeded() {
        return new StorageException(
            ErrorCode.STORAGE_QUOTA_EXCEEDED,
            "Quota de armazenamento excedida"
        );
    }
    
    public static StorageException permissionDenied(String operation, String path) {
        return new StorageException(
            ErrorCode.STORAGE_PERMISSION_DENIED,
            String.format("Permissão negada para %s em '%s'", operation, path)
        );
    }
    
    public static StorageException invalidPath(String path) {
        return new StorageException(
            ErrorCode.STORAGE_INVALID_PATH,
            "Caminho de storage inválido: '" + path + "'"
        );
    }
    
    public static StorageException uploadTimeout(String fileName) {
        return new StorageException(
            ErrorCode.STORAGE_UPLOAD_TIMEOUT,
            "Timeout ao fazer upload de '" + fileName + "'"
        );
    }
}