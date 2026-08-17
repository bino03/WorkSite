package com.management.managementapi.exeption;

import com.management.managementapi.dto.error.ErrorCode;

/**
 * Exceção lançada quando um recurso não é encontrado.
 * Resulta em HTTP 404.
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    // Helper methods para facilitar uso

    public static ResourceNotFoundException enterprise(String enterpriseId) {
        return new ResourceNotFoundException(
            ErrorCode.ASSET_ENTERPRISE_NOT_FOUND,
            "Projeto com ID " + enterpriseId + " não encontrado"
        );
    }

    public static ResourceNotFoundException media(String mediaId) {
        return new ResourceNotFoundException(
            ErrorCode.MEDIA_NOT_FOUND,
            "Media com ID " + mediaId + " não encontrado"
        );
    }

    public static ResourceNotFoundException profile(String userId) {
        return new ResourceNotFoundException(
            ErrorCode.USER_PROFILE_NOT_FOUND,
            "Perfil com ID " + userId + " não encontrado"
        );
    }

    public static ResourceNotFoundException budgetItem(String itemId) {
        return new ResourceNotFoundException(
            ErrorCode.BUDGET_ITEM_NOT_FOUND,
            "Rubrica com ID " + itemId + " não encontrada"
        );
    }

    public static ResourceNotFoundException constructionExpense(String expenseId) {
        return new ResourceNotFoundException(
            ErrorCode.EXPENSE_NOT_FOUND,
            "Despesa com ID " + expenseId + " não encontrada"
        );
    }

    public static ResourceNotFoundException constructionInvoice(String invoiceId) {
        return new ResourceNotFoundException(
            ErrorCode.INVOICE_NOT_FOUND,
            "Fatura com ID " + invoiceId + " não encontrada"
        );
    }

    public static ResourceNotFoundException supplier(String supplierId) {
        return new ResourceNotFoundException(
            ErrorCode.SUPPLIER_NOT_FOUND,
            "Fornecedor com ID " + supplierId + " não encontrado"
        );
    }

    public static ResourceNotFoundException task(String taskId) {
        return new ResourceNotFoundException(
            ErrorCode.TASK_NOT_FOUND,
            "Tarefa com ID " + taskId + " não encontrada"
        );
    }
}
