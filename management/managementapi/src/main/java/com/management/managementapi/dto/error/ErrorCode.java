package com.management.managementapi.dto.error;
// TRATAMENTOS DE ERROS ✅✅

/**
 * Enum centralizado para todos os códigos de erro da aplicação.
 * Organizado por módulos e tipos de operação.
 */
public enum ErrorCode {

    // ========================================================================
    // ERROS GENÉRICOS (ERR_xxx)
    // ========================================================================
    INTERNAL_SERVER_ERROR("ERR_001", "Erro interno do servidor"),
    VALIDATION_ERROR("ERR_002", "Erro de validação"),
    RESOURCE_NOT_FOUND("ERR_003", "Recurso não encontrado"),
    UNAUTHORIZED("ERR_004", "Não autorizado"),
    FORBIDDEN("ERR_005", "Acesso negado"),
    BAD_REQUEST("ERR_006", "Pedido inválido"),
    CONFLICT("ERR_007", "Conflito de dados"),
    METHOD_NOT_ALLOWED("ERR_008", "Método não permitido"),
    UNSUPPORTED_MEDIA_TYPE("ERR_009", "Tipo de media não suportado"),
    REQUEST_TIMEOUT("ERR_010", "Tempo de pedido esgotado"),
    RATE_LIMIT_EXCEEDED("ERR_011", "Limite de pedidos excedido"),
    SERVICE_UNAVAILABLE("ERR_012", "Serviço temporariamente indisponível"),

    // CRUD básico

    // Validação de dados básicos

    // Relações/Associações - Agency

    // Relações/Associações - Type/Subtype

    // Relações/Associações - Enterprise/Building
    ASSET_ENTERPRISE_NOT_FOUND("ASSET_030", "Empresa não encontrada"),

    // Relações/Associações - Development

    // Relações/Associações - Location

    // Relações/Associações - Contact

    // Description

    // Tags

    // Divisions (tipologias/divisões)

    // Settings

    // Estado/Status

    // Operações específicas

    // Upload geral
    MEDIA_UPLOAD_ERROR("MEDIA_010", "Erro ao fazer upload de media"),
    MEDIA_UPLOAD_FAILED("MEDIA_011", "Falha no upload de media"),
    MEDIA_INVALID_TYPE("MEDIA_012", "Tipo de media inválido"),
    MEDIA_SIZE_EXCEEDED("MEDIA_013", "Tamanho do ficheiro de media excedido"),
    MEDIA_EMPTY("MEDIA_014", "Ficheiro de media vazio"),
    MEDIA_CORRUPTED("MEDIA_015", "Ficheiro de media corrompido"),

    // Banner específico
    MEDIA_BANNER_UPLOAD_ERROR("MEDIA_020", "Erro ao fazer upload do banner"),
    MEDIA_BANNER_INVALID_TYPE("MEDIA_021", "Tipo de ficheiro de banner inválido"),
    MEDIA_BANNER_SIZE_EXCEEDED("MEDIA_022", "Tamanho do banner excedido"),
    MEDIA_BANNER_NOT_FOUND("MEDIA_023", "Banner não encontrado"),
    MEDIA_BANNER_DELETE_ERROR("MEDIA_024", "Erro ao eliminar banner"),

    // Gallery
    MEDIA_GALLERY_UPLOAD_ERROR("MEDIA_030", "Erro ao fazer upload da galeria"),
    MEDIA_GALLERY_INVALID_TYPE("MEDIA_031", "Tipo de ficheiro de galeria inválido"),
    MEDIA_GALLERY_SIZE_EXCEEDED("MEDIA_032", "Tamanho de ficheiro da galeria excedido"),
    MEDIA_GALLERY_LIMIT_EXCEEDED("MEDIA_033", "Limite de fotos na galeria excedido"),
    MEDIA_GALLERY_NOT_FOUND("MEDIA_034", "Item da galeria não encontrado"),
    MEDIA_GALLERY_DELETE_ERROR("MEDIA_035", "Erro ao eliminar item da galeria"),
    MEDIA_GALLERY_METADATA_MISMATCH("MEDIA_036", "Metadados da galeria não correspondem aos ficheiros"),

    // Operações em media
    MEDIA_NOT_FOUND("MEDIA_040", "Media não encontrado"),
    MEDIA_DELETE_ERROR("MEDIA_041", "Erro ao eliminar media"),
    MEDIA_UPDATE_ERROR("MEDIA_042", "Erro ao atualizar media"),
    MEDIA_DOWNLOAD_ERROR("MEDIA_043", "Erro ao descarregar media"),
    MEDIA_REORDER_ERROR("MEDIA_044", "Erro ao reordenar media"),
    MEDIA_ASSET_NOT_FOUND("MEDIA_045", "Asset da media não encontrado"),
    MEDIA_INVALID_ENTITY_TYPE("MEDIA_046", "Tipo de entidade inválido"),
    MEDIA_FETCH_ERROR("MEDIA_047", "Erro ao buscar media"),
    MEDIA_DUPLICATE_IDS("MEDIA_048", "Existem IDs de foto duplicados"),
    MEDIA_NOT_IN_ASSET("MEDIA_049", "Foto não pertence ao asset"),
    MEDIA_DUPLICATE_SORT_ORDERS("MEDIA_050", "Existem sort_orders duplicados"),
    MEDIA_INVALID_SORT_ORDER("MEDIA_051", "Sort order não pode ser negativo"),

    // ========================================================================
    // DOCUMENTOS (DOC_xxx)
    // ========================================================================
    DOCUMENT_NOT_FOUND("DOC_001", "Documento não encontrado"),
    DOCUMENT_CREATE_ERROR("DOC_002", "Erro ao criar documento"),
    DOCUMENT_UPDATE_ERROR("DOC_003", "Erro ao atualizar documento"),
    DOCUMENT_DELETE_ERROR("DOC_004", "Erro ao eliminar documento"),
    DOCUMENT_UPLOAD_ERROR("DOC_005", "Erro ao fazer upload do documento"),
    DOCUMENT_DOWNLOAD_ERROR("DOC_006", "Erro ao descarregar documento"),
    DOCUMENT_INVALID_TYPE("DOC_007", "Tipo de documento inválido"),
    DOCUMENT_SIZE_EXCEEDED("DOC_008", "Tamanho do documento excedido"),
    DOCUMENT_ASSET_NOT_FOUND("DOC_009", "Asset do documento não encontrado"),
    DOCUMENT_EMPTY("DOC_010", "Documento vazio"),
    DOCUMENT_CORRUPTED("DOC_011", "Documento corrompido"),
    DOCUMENT_PROCESSING_ERROR("DOC_012", "Erro ao processar documento"),

    // ========================================================================
    // LICENÇAS/CERTIFICADOS ENERGÉTICOS (LICENSE_xxx)
    // ========================================================================
    LICENSE_NOT_FOUND("LICENSE_001", "Licença não encontrada"),
    LICENSE_CREATE_ERROR("LICENSE_002", "Erro ao criar licença"),
    LICENSE_UPDATE_ERROR("LICENSE_003", "Erro ao atualizar licença"),
    LICENSE_DELETE_ERROR("LICENSE_004", "Erro ao eliminar licença"),
    LICENSE_UPLOAD_ERROR("LICENSE_005", "Erro ao fazer upload da licença"),
    LICENSE_ASSET_NOT_FOUND("LICENSE_006", "Asset da licença não encontrado"),
    LICENSE_INVALID_CLASS("LICENSE_007", "Classe energética inválida"),
    LICENSE_INVALID_DATE("LICENSE_008", "Data da licença inválida"),
    LICENSE_EXPIRED("LICENSE_009", "Licença expirada"),
    LICENSE_DUPLICATE_NUMBER("LICENSE_010", "Número de licença duplicado"),
    LICENSE_INVALID_NUMBER("LICENSE_011", "Número de licença inválido"),
    LICENSE_FILE_REQUIRED("LICENSE_012", "Ficheiro de licença é obrigatório"),
    LICENSE_PROCESSING_ERROR("LICENSE_013", "Erro ao processar licença"),

    // ========================================================================
    // FICHEIROS/UPLOAD (FILE_xxx)
    // ========================================================================
    FILE_UPLOAD_ERROR("FILE_001", "Erro no upload do ficheiro"),
    FILE_SIZE_EXCEEDED("FILE_002", "Tamanho do ficheiro excedido"),
    FILE_TYPE_NOT_ALLOWED("FILE_003", "Tipo de ficheiro não permitido"),
    FILE_NOT_FOUND("FILE_004", "Ficheiro não encontrado"),
    FILE_DELETE_ERROR("FILE_005", "Erro ao eliminar ficheiro"),
    FILE_DOWNLOAD_ERROR("FILE_006", "Erro ao descarregar ficheiro"),
    FILE_CORRUPTED("FILE_007", "Ficheiro corrompido"),
    FILE_EMPTY("FILE_008", "Ficheiro vazio"),
    FILE_NAME_INVALID("FILE_009", "Nome do ficheiro inválido"),
    FILE_STORAGE_ERROR("FILE_010", "Erro no armazenamento do ficheiro"),
    FILE_COMPRESSION_ERROR("FILE_011", "Erro ao comprimir ficheiro"),
    FILE_EXTRACTION_ERROR("FILE_012", "Erro ao extrair ficheiro"),
    FILE_MIME_TYPE_INVALID("FILE_013", "Tipo MIME do ficheiro inválido"),
    FILE_EXTENSION_INVALID("FILE_014", "Extensão do ficheiro inválida"),
    FILE_READ_ERROR("FILE_015", "Erro ao ler ficheiro"),

    // ========================================================================
    // UPLOAD REQUESTS (UPLOAD_xxx)
    // ========================================================================

    UPLOAD_REQUEST_NOT_FOUND("UPLOAD_001", "Pedido de upload não encontrado"),
    UPLOAD_REQUEST_INVALID_STATUS("UPLOAD_002", "Estado do pedido de upload inválido"),
    UPLOAD_REQUEST_ENERGY_DATA_MISSING("UPLOAD_003", "Dados energéticos do imóvel em falta"),

    // Imagens
    IMAGE_INVALID_FORMAT("FILE_020", "Formato de imagem inválido"),
    IMAGE_SIZE_EXCEEDED("FILE_021", "Tamanho da imagem excedido"),
    IMAGE_DIMENSIONS_INVALID("FILE_022", "Dimensões da imagem inválidas"),
    IMAGE_PROCESSING_ERROR("FILE_023", "Erro ao processar imagem"),

    // Vídeos
    VIDEO_INVALID_FORMAT("FILE_030", "Formato de vídeo inválido"),
    VIDEO_SIZE_EXCEEDED("FILE_031", "Tamanho do vídeo excedido"),
    VIDEO_DURATION_EXCEEDED("FILE_032", "Duração do vídeo excedida"),
    VIDEO_PROCESSING_ERROR("FILE_033", "Erro ao processar vídeo"),

    // PDFs

    // ========================================================================
    // STORAGE (SUPABASE) (STORAGE_xxx)
    // ========================================================================
    STORAGE_CONNECTION_ERROR("STORAGE_001", "Erro de conexão com o storage"),
    STORAGE_UPLOAD_ERROR("STORAGE_002", "Erro ao fazer upload para o storage"),
    STORAGE_DELETE_ERROR("STORAGE_003", "Erro ao eliminar do storage"),
    STORAGE_DOWNLOAD_ERROR("STORAGE_004", "Erro ao descarregar do storage"),
    STORAGE_BUCKET_NOT_FOUND("STORAGE_005", "Bucket do storage não encontrado"),
    STORAGE_FILE_NOT_FOUND("STORAGE_006", "Ficheiro não encontrado no storage"),
    STORAGE_QUOTA_EXCEEDED("STORAGE_007", "Quota de storage excedida"),
    STORAGE_PERMISSION_DENIED("STORAGE_008", "Permissão negada no storage"),
    STORAGE_SIGNED_URL_ERROR("STORAGE_009", "Erro ao gerar URL assinado"),
    STORAGE_INVALID_PATH("STORAGE_010", "Caminho de storage inválido"),
    STORAGE_UPLOAD_TIMEOUT("STORAGE_011", "Timeout no upload para storage"),
    STORAGE_DOWNLOAD_TIMEOUT("STORAGE_012", "Timeout no download do storage"),
    STORAGE_BUCKET_CREATE_ERROR("STORAGE_013", "Erro ao criar bucket"),
    STORAGE_MOVE_ERROR("STORAGE_014", "Erro ao mover ficheiro no storage"),
    STORAGE_COPY_ERROR("STORAGE_015", "Erro ao copiar ficheiro no storage"),

    // ========================================================================
    // EMPRESAS (ENT_xxx)
    // ========================================================================
    ENTERPRISE_NOT_FOUND("ENT_001", "Empresa não encontrada"),
    ENTERPRISE_ALREADY_EXISTS("ENT_002", "Empresa já existe"),
    ENTERPRISE_CREATE_ERROR("ENT_003", "Erro ao criar empresa"),
    ENTERPRISE_UPDATE_ERROR("ENT_004", "Erro ao atualizar empresa"),
    ENTERPRISE_DELETE_ERROR("ENT_005", "Erro ao eliminar empresa"),
    ENTERPRISE_INVALID_NAME("ENT_010", "Nome da empresa inválido"),
    ENTERPRISE_INVALID_NIF("ENT_011", "NIF da empresa inválido"),
    ENTERPRISE_INVALID_EMAIL("ENT_012", "Email da empresa inválido"),
    ENTERPRISE_INVALID_PHONE("ENT_013", "Telefone da empresa inválido"),
    ENTERPRISE_HAS_PROPERTIES("ENT_020", "Empresa tem propriedades associadas"),
    ENTERPRISE_HAS_BUILDINGS("ENT_021", "Empresa tem edifícios associados"),
    ENTERPRISE_HAS_USERS("ENT_022", "Empresa tem utilizadores associados"),
    ENTERPRISE_DUPLICATE_NIF("ENT_030", "NIF da empresa já está registado"),
    ENTERPRISE_INACTIVE("ENT_031", "Empresa está inativa"),

    // ========================================================================
    // ORÇAMENTO DE CONSTRUÇÃO (BUDGET_xxx)
    // ========================================================================
    BUDGET_ITEM_NOT_FOUND("BUDGET_001", "Rubrica do orçamento não encontrada"),
    BUDGET_CREATE_ERROR("BUDGET_002", "Erro ao criar rubrica"),
    BUDGET_UPDATE_ERROR("BUDGET_003", "Erro ao atualizar rubrica"),
    BUDGET_DELETE_ERROR("BUDGET_004", "Erro ao eliminar rubrica"),
    BUDGET_INVALID_NAME("BUDGET_005", "Descrição da rubrica inválida"),
    BUDGET_ENTERPRISE_NOT_FOUND("BUDGET_006", "Projeto do orçamento não encontrado"),
    BUDGET_PARENT_NOT_FOUND("BUDGET_007", "Rubrica-mãe não encontrada"),
    BUDGET_PARENT_OTHER_ENTERPRISE("BUDGET_008", "A rubrica-mãe pertence a outro projeto"),
    BUDGET_CYCLE("BUDGET_009", "Uma rubrica não pode ficar dentro de si própria"),
    BUDGET_DUPLICATE_CODE("BUDGET_010", "Já existe uma rubrica com este índice neste projeto"),
    BUDGET_INVALID_DATES("BUDGET_011", "A data de fim não pode ser anterior à data de início"),
    BUDGET_ITEM_OTHER_ENTERPRISE("BUDGET_012", "Uma rubrica não pode mudar de projeto"),

    // Importação de Excel (BUDGET_02x)
    BUDGET_IMPORT_EMPTY_FILE("BUDGET_020", "Ficheiro de orçamento vazio"),
    BUDGET_IMPORT_INVALID_TYPE("BUDGET_021", "O ficheiro tem de ser um Excel (.xlsx)"),
    BUDGET_IMPORT_READ_ERROR("BUDGET_022", "Não foi possível ler o ficheiro de orçamento"),
    BUDGET_IMPORT_NO_HEADER("BUDGET_023", "Não foi encontrada a linha de cabeçalho (coluna \"Art\") no Excel"),
    BUDGET_IMPORT_NO_ROWS("BUDGET_024", "O Excel não tem rubricas para importar"),
    BUDGET_IMPORT_NOT_EMPTY("BUDGET_025", "O projeto já tem orçamento — elimine-o antes de importar ou use replace=true"),

    // ========================================================================
    // DESPESAS DE CONSTRUÇÃO (EXPENSE_xxx)
    // ========================================================================
    EXPENSE_NOT_FOUND("EXPENSE_001", "Despesa não encontrada"),
    EXPENSE_CREATE_ERROR("EXPENSE_002", "Erro ao criar despesa"),
    EXPENSE_UPDATE_ERROR("EXPENSE_003", "Erro ao atualizar despesa"),
    EXPENSE_DELETE_ERROR("EXPENSE_004", "Erro ao eliminar despesa"),
    EXPENSE_INVALID_NAME("EXPENSE_005", "Nome da despesa inválido"),
    EXPENSE_INVALID_PRICE("EXPENSE_006", "Preço da despesa inválido"),
    EXPENSE_BUDGET_ITEM_NOT_FOUND("EXPENSE_007", "Rubrica da despesa não encontrada"),
    EXPENSE_INVOICE_UPLOAD_ERROR("EXPENSE_008", "Erro ao carregar a fatura da despesa"),
    EXPENSE_ITEM_NOT_EXPENSABLE("EXPENSE_009", "Só é possível lançar despesas em rubricas — títulos e notas não aceitam despesas"),
    EXPENSE_ITEM_OTHER_ENTERPRISE("EXPENSE_010", "A rubrica indicada pertence a outro projeto"),

    // ========================================================================
    // FATURAS DE OBRA (INVOICE_xxx)
    // ========================================================================
    INVOICE_NOT_FOUND("INVOICE_001", "Fatura não encontrada"),
    INVOICE_ENTERPRISE_NOT_FOUND("INVOICE_002", "Projeto da fatura não encontrado"),
    INVOICE_UPLOAD_ERROR("INVOICE_003", "Erro ao carregar o ficheiro da fatura"),
    INVOICE_ALREADY_ALLOCATED("INVOICE_004", "Esta fatura já está associada a uma rubrica — desassocie-a primeiro"),
    INVOICE_NOT_ALLOCATED("INVOICE_005", "Esta fatura não está associada a nenhuma rubrica"),
    INVOICE_INCOMPLETE("INVOICE_006", "Preencha a data e o total da fatura antes de a associar a uma rubrica"),
    INVOICE_ITEM_OTHER_ENTERPRISE("INVOICE_007", "A rubrica indicada pertence a outro projeto"),
    INVOICE_QR_UNREADABLE("INVOICE_008", "Não foi possível ler o QR da AT neste documento — os campos têm de ser preenchidos à mão"),
    INVOICE_FILE_UNAVAILABLE("INVOICE_009", "Não foi possível obter o ficheiro original da fatura"),
    INVOICE_DUPLICATE_ATCUD("INVOICE_010", "Já existe uma fatura com este ATCUD neste projeto"),
    INVOICE_DUPLICATE_DOCUMENT("INVOICE_011", "Já existe uma fatura deste fornecedor com este número neste projeto"),
    INVOICE_DUPLICATE_FILE("INVOICE_012", "Este ficheiro já foi carregado neste projeto — é byte a byte igual a uma fatura existente"),

    // ========================================================================
    // FORNECEDORES (SUPPLIER_xxx)
    // ========================================================================
    SUPPLIER_NOT_FOUND("SUPPLIER_001", "Fornecedor não encontrado"),
    SUPPLIER_NIF_ALREADY_EXISTS("SUPPLIER_002", "Já existe um fornecedor com este NIF"),

    // ========================================================================
    // NOTIFICAÇÕES (NOTIF_xxx)
    // ========================================================================
    // Também é o que se devolve quando a notificação existe mas é de outra
    // pessoa: distinguir "não é tua" de "não existe" diria a um estranho que ela
    // existe.
    NOTIFICATION_NOT_FOUND("NOTIF_001", "Notificação não encontrada"),

    // ========================================================================
    // LOCALIZAÇÕES (LOC_xxx)
    // ========================================================================
    LOCATION_INVALID_NAME("LOC_030", "Nome da localização inválido"),
    LOCATION_INVALID_CODE("LOC_031", "Código da localização inválido"),
    LOCATION_HIERARCHY_ERROR("LOC_032", "Erro na hierarquia de localizações"),
    LOCATION_NOT_FOUND("LOC_034", "Localização não encontrada"),
    LOCATION_LINK_BROKEN("LOC_035", "Link de localização corrompido na base de dados"),
    LOCATION_PRIMARY_REQUIRED("LOC_036", "É necessário marcar uma localização como principal"),

    // ========================================================================
    // AUTENTICAÇÃO (USER_001 a USER_003 - Auth específica)
    // ========================================================================
    USER_001("USER_001", "Credenciais inválidas. Verifica o email e password."),
    USER_002("USER_002", "Token inválido ou expirado. Faz login novamente."),
    USER_003("USER_003", "Sessão expirada. Faz login novamente."),

    // ========================================================================
    // UTILIZADORES (USER_xxx)
    // ========================================================================
    USER_NOT_FOUND("USER_004", "Utilizador não encontrado"),
    USER_ALREADY_EXISTS("USER_005", "Utilizador já existe"),
    USER_CREATE_ERROR("USER_006", "Erro ao criar utilizador"),
    // 007/008 e não 004/005: estes dois colidiam com USER_NOT_FOUND e
    // USER_ALREADY_EXISTS, que são os que o código emite.
    USER_UPDATE_ERROR("USER_007", "Erro ao atualizar utilizador"),
    USER_DELETE_ERROR("USER_008", "Erro ao eliminar utilizador"),
    USER_INVALID_CREDENTIALS("USER_010", "Credenciais inválidas"),
    USER_NOT_AUTHENTICATED("USER_011", "Utilizador não autenticado"),
    USER_TOKEN_EXPIRED("USER_012", "Token expirado"),
    USER_TOKEN_INVALID("USER_013", "Token inválido"),
    USER_SESSION_EXPIRED("USER_014", "Sessão expirada"),
    USER_PROFILE_NOT_FOUND("USER_015", "Perfil do utilizador não encontrado"),
    USER_INSUFFICIENT_PERMISSIONS("USER_020", "Permissões insuficientes"),
    PROFILE_NOT_DELETED("USER_024", "Utilizador não existe ou não está no estado eliminado"),
    PROFILE_CANNOT_BLOCK("USER_028", "Utilizador não pode ser bloqueado (já bloqueado ou eliminado)"),
    PROFILE_CANNOT_UNBLOCK("USER_029", "Utilizador não pode ser desbloqueado (não está bloqueado)"),
    PROFILE_CANNOT_DELETE("USER_033", "Utilizador não pode ser eliminado (já eliminado ou não existe)"),
    PROFILE_CANNOT_DELETE_SELF("USER_036", "Não pode eliminar a sua própria conta"),
    USER_ROLE_NOT_FOUND("USER_021", "Role do utilizador não encontrado"),
    USER_CANNOT_ACCESS_RESOURCE("USER_022", "Utilizador não pode aceder a este recurso"),
    USER_CANNOT_MODIFY_RESOURCE("USER_023", "Utilizador não pode modificar este recurso"),
    USER_INVALID_EMAIL("USER_030", "Email do utilizador inválido"),
    USER_INVALID_PASSWORD("USER_031", "Password inválida"),
    USER_INVALID_NAME("USER_032", "Nome do utilizador inválido"),
    // 035 e não 033: colidia com PROFILE_CANNOT_DELETE, que é o que o código emite.
    USER_INVALID_PHONE("USER_035", "Telefone do utilizador inválido"),
    USER_DUPLICATE_EMAIL("USER_034", "Email já está registado"),
    USER_INACTIVE("USER_040", "Utilizador inativo"),
    USER_BLOCKED("USER_041", "Utilizador bloqueado"),
    USER_DELETED("USER_042", "Utilizador eliminado"),
    USER_PENDING_VERIFICATION("USER_043", "Utilizador pendente de verificação"),
    USER_ENTERPRISE_NOT_FOUND("USER_050", "Empresa do utilizador não encontrada"),
    USER_HAS_DEPENDENCIES("USER_051", "Utilizador tem dependências associadas"),
    ACCESS_DENIED("USER_025", "Sem permissão para aceder a este recurso"),
    USER_NO_COMPANY("USER_026", "Utilizador não tem empresa associada"),
    INSUFFICIENT_PERMISSIONS("USER_027", "Permissões insuficientes para esta ação"),

    // ========================================================================
    // BASE DE DADOS (DB_xxx)
    // ========================================================================
    DATABASE_ERROR("DB_001", "Erro na base de dados"),
    DATABASE_CONNECTION_ERROR("DB_002", "Erro de conexão à base de dados"),
    DATABASE_CONSTRAINT_VIOLATION("DB_003", "Violação de constraint na base de dados"),
    DATABASE_DUPLICATE_KEY("DB_004", "Chave duplicada na base de dados"),
    DATABASE_FOREIGN_KEY_VIOLATION("DB_005", "Violação de chave estrangeira"),
    DATABASE_TRANSACTION_ERROR("DB_006", "Erro na transação da base de dados"),
    DATABASE_DEADLOCK("DB_007", "Deadlock na base de dados"),
    DATABASE_TIMEOUT("DB_008", "Timeout na base de dados"),
    DATABASE_MIGRATION_ERROR("DB_009", "Erro na migração da base de dados"),

    // ========================================================================
    // PESQUISA/FILTROS (SEARCH_xxx)
    // ========================================================================
    SEARCH_INVALID_QUERY("SEARCH_001", "Query de pesquisa inválida"),
    SEARCH_INVALID_FILTER("SEARCH_002", "Filtro de pesquisa inválido"),
    SEARCH_INVALID_SORT("SEARCH_003", "Ordenação inválida"),
    SEARCH_NO_RESULTS("SEARCH_004", "Sem resultados para a pesquisa"),
    SEARCH_TOO_MANY_RESULTS("SEARCH_005", "Demasiados resultados, refine a pesquisa"),

    // ========================================================================
    // PAGINAÇÃO (PAGE_xxx)
    // ========================================================================
    PAGINATION_INVALID_PAGE("PAGE_001", "Número de página inválido"),
    PAGINATION_INVALID_SIZE("PAGE_002", "Tamanho da página inválido"),
    PAGINATION_OUT_OF_BOUNDS("PAGE_003", "Página fora dos limites"),

    // ========================================================================
    // AUDITORIA / ATIVIDADES (ACTIVITY_xxx)
    // ========================================================================
    ACTIVITY_NOT_FOUND("ACTIVITY_001", "Registo de atividade não encontrado"),
    ACTIVITY_CREATE_ERROR("ACTIVITY_002", "Erro ao criar registo de atividade"),
    ACTIVITY_INVALID_TYPE("ACTIVITY_003", "Tipo de atividade inválido"),
    ACTIVITY_INVALID_ENTITY_TYPE("ACTIVITY_004", "Tipo de entidade inválido"),

    // ========================================================================
    // TAREFAS (TASK_xxx)
    // ========================================================================
    TASK_NOT_FOUND("TASK_001", "Tarefa não encontrada"),
    TASK_ASSIGNEE_NOT_FOUND("TASK_002", "Um ou mais utilizadores atribuídos não foram encontrados");

    private final String code;
    private final String defaultMessage;

    // Construtor do enum - privado por padrão
    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * Retorna o ErrorCode correspondente ao código fornecido.
     * Útil para testes e validações.
     * 
     * @param code Código a procurar (ex: "ASSET_001")
     * @return ErrorCode correspondente ou null se não encontrado
     */
    public static ErrorCode fromCode(String code) {
        if (code == null) {
            return null;
        }

        for (ErrorCode errorCode : ErrorCode.values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }

        return null;
    }

    /**
     * Verifica se um código existe no enum.
     * 
     * @param code Código a verificar
     * @return true se o código existe, false caso contrário
     */
    public static boolean exists(String code) {
        return fromCode(code) != null;
    }

    /**
     * Retorna todos os códigos de erro.
     * Útil para debugging e testes.
     * 
     * @return Array com todos os ErrorCode
     */
    public static ErrorCode[] getAllCodes() {
        return ErrorCode.values();
    }
}