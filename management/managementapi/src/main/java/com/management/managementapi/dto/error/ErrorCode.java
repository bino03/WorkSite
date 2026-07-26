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

    // ========================================================================
    // ASSETS/PROPRIEDADES (ASSET_xxx)
    // ========================================================================

    // CRUD básico
    ASSET_NOT_FOUND("ASSET_001", "Asset não encontrado"),
    ASSET_ALREADY_EXISTS("ASSET_002", "Asset já existe"),
    ASSET_CREATE_ERROR("ASSET_003", "Erro ao criar asset"),
    ASSET_UPDATE_ERROR("ASSET_004", "Erro ao atualizar asset"),
    ASSET_DELETE_ERROR("ASSET_005", "Erro ao eliminar asset"),
    ASSET_NOT_DELETED("ASSET_006", "Asset não está no estado eliminado"),

    // Validação de dados básicos
    ASSET_INVALID_NAME("ASSET_010", "Nome do asset inválido"),
    ASSET_INVALID_PRICE("ASSET_011", "Preço do asset inválido"),
    ASSET_INVALID_AREA("ASSET_012", "Área do asset inválida"),
    ASSET_INVALID_STATUS("ASSET_013", "Estado do asset inválido"),
    ASSET_INVALID_BUSINESS_NATURE("ASSET_014", "Natureza do negócio inválida"),
    ASSET_INVALID_COORDINATES("ASSET_015", "Coordenadas do asset inválidas"),
    ASSET_INVALID_YEAR("ASSET_016", "Ano de construção inválido"),

    // Relações/Associações - Agency
    ASSET_AGENCY_NOT_FOUND("ASSET_020", "Agência não encontrada"),
    ASSET_INVALID_AGENCY("ASSET_021", "Agência inválida"),

    // Relações/Associações - Type/Subtype
    ASSET_TYPE_NOT_FOUND("ASSET_025", "Tipo de asset não encontrado"),
    ASSET_SUBTYPE_NOT_FOUND("ASSET_026", "Subtipo de asset não encontrado"),
    ASSET_INVALID_TYPE_SUBTYPE_COMBINATION("ASSET_027", "Combinação de tipo e subtipo inválida"),

    // Relações/Associações - Enterprise/Building
    ASSET_ENTERPRISE_NOT_FOUND("ASSET_030", "Empresa não encontrada"),
    ASSET_BUILDING_NOT_FOUND("ASSET_031", "Edifício não encontrado"),
    ASSET_BUILDING_ENTERPRISE_MISMATCH("ASSET_032", "Edifício não pertence à empresa especificada"),
    ASSET_ALREADY_HAS_BUILDING("ASSET_033", "Asset já está associado a um edifício"),
    ASSET_BUILDING_REQUIRED("ASSET_034", "Edifício é obrigatório para este tipo de asset"),

    // Relações/Associações - Development
    ASSET_DEVELOPMENT_NOT_FOUND("ASSET_035", "Empreendimento não encontrado"),

    // Relações/Associações - Location
    ASSET_LOCATION_NOT_FOUND("ASSET_040", "Localização não encontrada"),
    ASSET_LOCATION_CREATE_ERROR("ASSET_041", "Erro ao criar localização"),
    ASSET_LOCATION_INVALID("ASSET_042", "Dados de localização inválidos"),
    ASSET_MULTIPLE_PRIMARY_LOCATIONS("ASSET_043", "Asset não pode ter múltiplas localizações primárias"),

    // Relações/Associações - Contact
    ASSET_CONTACT_NOT_FOUND("ASSET_045", "Contacto não encontrado"),
    ASSET_CONTACT_CREATE_ERROR("ASSET_046", "Erro ao criar contacto"),
    ASSET_CONTACT_ALREADY_LINKED("ASSET_047", "Contacto já está associado ao asset"),

    // Description
    ASSET_DESCRIPTION_CREATE_ERROR("ASSET_050", "Erro ao criar descrição"),
    ASSET_DESCRIPTION_UPDATE_ERROR("ASSET_051", "Erro ao atualizar descrição"),
    ASSET_DESCRIPTION_NOT_FOUND("ASSET_052", "Descrição não encontrada"),

    // Tags
    ASSET_TAG_NOT_FOUND("ASSET_055", "Tag não encontrada"),
    ASSET_TAG_CREATE_ERROR("ASSET_056", "Erro ao criar tag"),
    ASSET_TAG_ALREADY_LINKED("ASSET_057", "Tag já está associada ao asset"),
    ASSET_TAG_LIMIT_EXCEEDED("ASSET_058", "Limite de tags excedido"),
    TAG_INVALID("ASSET_059", "Tag inválida: precisa de label ou ID"),

    // Divisions (tipologias/divisões)
    ASSET_DIVISION_CREATE_ERROR("ASSET_060", "Erro ao criar divisão"),
    ASSET_DIVISION_UPDATE_ERROR("ASSET_061", "Erro ao atualizar divisão"),
    ASSET_DIVISION_DELETE_ERROR("ASSET_062", "Erro ao eliminar divisão"),
    ASSET_TYPOLOGY_NOT_FOUND("ASSET_063", "Tipologia não encontrada"),
    DIVISION_NOT_FOUND("ASSET_064", "Divisão não encontrada"),
    DIVISION_EMPTY_LIST("ASSET_065", "Lista de divisões vazia"),

    // Settings
    ASSET_SETTINGS_CREATE_ERROR("ASSET_066", "Erro ao criar definições do asset"),
    ASSET_SETTINGS_UPDATE_ERROR("ASSET_067", "Erro ao atualizar definições do asset"),
    ASSET_SETTINGS_NOT_FOUND("ASSET_068", "Definições do asset não encontradas"),

    // Estado/Status
    ASSET_INACTIVE("ASSET_070", "Asset está inativo"),
    ASSET_ARCHIVED("ASSET_071", "Asset está arquivado"),
    ASSET_CANNOT_CHANGE_STATUS("ASSET_072", "Não é possível alterar o estado do asset"),
    ASSET_STATUS_TRANSITION_INVALID("ASSET_073", "Transição de estado inválida"),
    ASSET_TYPE_INACTIVE("ASSET_074", "Tipo de asset está inativo"),
    TYPOLOGY_DUPLICATE_SLUG("ASSET_075", "Já existe uma tipologia com este slug para este tipo de imóvel"),

    // Operações específicas
    ASSET_DUPLICATE_REFERENCE("ASSET_080", "Referência interna duplicada"),
    ASSET_HAS_DEPENDENCIES("ASSET_081", "Asset tem dependências associadas"),
    ASSET_CANNOT_DELETE_WITH_AGENTS("ASSET_082", "Não é possível eliminar asset com angariadores associados"),
    ASSET_CANNOT_DELETE_WITH_MEDIA("ASSET_083", "Não é possível eliminar asset com media associada"),

    // ========================================================================
    // PROPERTY AGENTS (AGENT_xxx)
    // ========================================================================
    AGENT_NOT_FOUND("AGENT_001", "Angariador não encontrado"),
    AGENT_PROFILE_NOT_FOUND("AGENT_002", "Perfil do angariador não encontrado"),
    AGENT_ALREADY_ASSIGNED("AGENT_003", "Angariador já está associado a este asset"),
    AGENT_NOT_ASSIGNED_TO_ASSET("AGENT_004", "Angariador não está associado a este asset"),
    AGENT_INVALID_ROLE("AGENT_005", "Role do angariador inválido"),
    AGENT_INVALID_COMMISSION("AGENT_006", "Percentagem de comissão inválida"),
    AGENT_CREATE_ERROR("AGENT_007", "Erro ao associar angariador"),
    AGENT_DELETE_ERROR("AGENT_008", "Erro ao remover angariador"),
    AGENT_LIMIT_EXCEEDED("AGENT_009", "Limite de angariadores excedido"),

    // ========================================================================
    // CONTACTOS (CONTACT_xxx)
    // ========================================================================
    CONTACT_NOT_FOUND("CONTACT_001", "Contacto não encontrado"),
    CONTACT_EMPTY_LIST("CONTACT_002", "Lista de contactos vazia"),
    CONTACT_ROLE_INVALID("CONTACT_003", "Role de contacto inválida"),
    CONTACT_ALREADY_PRIMARY("CONTACT_004", "Já existe um contacto primário"),

    // ========================================================================
    // MEDIA (Assets) (MEDIA_xxx)
    // ========================================================================

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
    PROPERTY_DOCUMENT_NOT_FOUND("DOC_013", "Documento de propriedade não encontrado"),
    PROPERTY_DOCUMENT_TYPE_ALREADY_EXISTS("DOC_014", "Já existe um documento deste tipo para esta propriedade"),
    PROPERTY_DOCUMENT_TYPE_INVALID("DOC_015", "Tipo de documento inválido"),

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
    // CARACTERÍSTICAS (CHAR_xxx)
    // ========================================================================
    CHARACTERISTIC_NOT_FOUND("CHAR_001", "Característica não encontrada"),
    CHARACTERISTIC_CREATE_ERROR("CHAR_002", "Erro ao criar característica"),
    CHARACTERISTIC_UPDATE_ERROR("CHAR_003", "Erro ao atualizar característica"),
    CHARACTERISTIC_DELETE_ERROR("CHAR_004", "Erro ao eliminar característica"),
    CHARACTERISTIC_INVALID_TYPE("CHAR_005", "Tipo de característica inválido"),
    CHARACTERISTIC_INVALID_VALUE("CHAR_006", "Valor da característica inválido"),
    CHARACTERISTIC_DUPLICATE("CHAR_007", "Característica duplicada"),
    CHARACTERISTIC_ASSET_NOT_FOUND("CHAR_008", "Asset da característica não encontrado"),
    CHARACTERISTIC_DELETE_ALL_ERROR("CHAR_009", "Erro ao eliminar todas as características"),

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
    PDF_INVALID_FORMAT("FILE_040", "Formato de PDF inválido"),
    PDF_SIZE_EXCEEDED("FILE_041", "Tamanho do PDF excedido"),
    PDF_CORRUPTED("FILE_042", "PDF corrompido"),
    PDF_PROCESSING_ERROR("FILE_043", "Erro ao processar PDF"),

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
    // EDIFÍCIOS (BUILD_xxx)
    // ========================================================================
    BUILDING_NOT_FOUND("BUILD_001", "Edifício não encontrado"),
    BUILDING_ALREADY_EXISTS("BUILD_002", "Edifício já existe"),
    BUILDING_CREATE_ERROR("BUILD_003", "Erro ao criar edifício"),
    BUILDING_UPDATE_ERROR("BUILD_004", "Erro ao atualizar edifício"),
    BUILDING_DELETE_ERROR("BUILD_005", "Erro ao eliminar edifício"),
    BUILDING_INVALID_NAME("BUILD_010", "Nome do edifício inválido"),
    BUILDING_INVALID_ADDRESS("BUILD_011", "Morada do edifício inválida"),
    BUILDING_INVALID_FLOORS("BUILD_012", "Número de pisos inválido"),
    BUILDING_INVALID_YEAR("BUILD_013", "Ano de construção inválido"),
    BUILDING_HAS_PROPERTIES("BUILD_020", "Edifício tem propriedades associadas"),
    BUILDING_ENTERPRISE_NOT_FOUND("BUILD_021", "Empresa do edifício não encontrada"),
    BUILDING_LOCATION_NOT_FOUND("BUILD_022", "Localização do edifício não encontrada"),
    BUILDING_DUPLICATE_REFERENCE("BUILD_030", "Referência do edifício duplicada"),
    BUILDING_CAPACITY_EXCEEDED("BUILD_031", "Capacidade do edifício excedida"),
    BUILDING_ID_REQUIRED("BUILD_032", "ID do edifício é obrigatório"),
    BUILDING_NOT_ASSOCIATED("BUILD_033", "Propriedade não tem edifício associado"),

    // ========================================================================
    // ETAPAS DE CONSTRUÇÃO (STAGE_xxx)
    // ========================================================================
    STAGE_NOT_FOUND("STAGE_001", "Etapa não encontrada"),
    STAGE_CREATE_ERROR("STAGE_002", "Erro ao criar etapa"),
    STAGE_UPDATE_ERROR("STAGE_003", "Erro ao atualizar etapa"),
    STAGE_DELETE_ERROR("STAGE_004", "Erro ao eliminar etapa"),
    STAGE_INVALID_NAME("STAGE_005", "Nome da etapa inválido"),
    STAGE_ENTERPRISE_NOT_FOUND("STAGE_006", "Empresa da etapa não encontrada"),

    // ========================================================================
    // SUB-ETAPAS DE CONSTRUÇÃO (SUBSTAGE_xxx)
    // ========================================================================
    SUBSTAGE_NOT_FOUND("SUBSTAGE_001", "Sub-etapa não encontrada"),
    SUBSTAGE_CREATE_ERROR("SUBSTAGE_002", "Erro ao criar sub-etapa"),
    SUBSTAGE_UPDATE_ERROR("SUBSTAGE_003", "Erro ao atualizar sub-etapa"),
    SUBSTAGE_DELETE_ERROR("SUBSTAGE_004", "Erro ao eliminar sub-etapa"),
    SUBSTAGE_INVALID_NAME("SUBSTAGE_005", "Nome da sub-etapa inválido"),
    SUBSTAGE_STAGE_NOT_FOUND("SUBSTAGE_006", "Etapa da sub-etapa não encontrada"),

    // ========================================================================
    // DESPESAS DE CONSTRUÇÃO (EXPENSE_xxx)
    // ========================================================================
    EXPENSE_NOT_FOUND("EXPENSE_001", "Despesa não encontrada"),
    EXPENSE_CREATE_ERROR("EXPENSE_002", "Erro ao criar despesa"),
    EXPENSE_UPDATE_ERROR("EXPENSE_003", "Erro ao atualizar despesa"),
    EXPENSE_DELETE_ERROR("EXPENSE_004", "Erro ao eliminar despesa"),
    EXPENSE_INVALID_NAME("EXPENSE_005", "Nome da despesa inválido"),
    EXPENSE_INVALID_PRICE("EXPENSE_006", "Preço da despesa inválido"),
    EXPENSE_SUBSTAGE_NOT_FOUND("EXPENSE_007", "Sub-etapa da despesa não encontrada"),
    EXPENSE_INVOICE_UPLOAD_ERROR("EXPENSE_008", "Erro ao carregar a fatura da despesa"),

    // ========================================================================
    // LOCALIZAÇÕES (LOC_xxx)
    // ========================================================================
    DISTRICT_NOT_FOUND("LOC_001", "Distrito não encontrado"),
    DISTRICT_ALREADY_EXISTS("LOC_002", "Distrito já existe"),
    DISTRICT_CREATE_ERROR("LOC_003", "Erro ao criar distrito"),
    DISTRICT_DELETE_ERROR("LOC_004", "Erro ao eliminar distrito"),
    DISTRICT_HAS_COUNTIES("LOC_005", "Distrito tem concelhos associados"),
    COUNTY_NOT_FOUND("LOC_010", "Concelho não encontrado"),
    COUNTY_ALREADY_EXISTS("LOC_011", "Concelho já existe"),
    COUNTY_CREATE_ERROR("LOC_012", "Erro ao criar concelho"),
    COUNTY_DELETE_ERROR("LOC_013", "Erro ao eliminar concelho"),
    COUNTY_HAS_PARISHES("LOC_014", "Concelho tem freguesias associadas"),
    COUNTY_DISTRICT_NOT_FOUND("LOC_015", "Distrito do concelho não encontrado"),
    PARISH_NOT_FOUND("LOC_020", "Freguesia não encontrada"),
    PARISH_ALREADY_EXISTS("LOC_021", "Freguesia já existe"),
    PARISH_CREATE_ERROR("LOC_022", "Erro ao criar freguesia"),
    PARISH_DELETE_ERROR("LOC_023", "Erro ao eliminar freguesia"),
    PARISH_HAS_PROPERTIES("LOC_024", "Freguesia tem propriedades associadas"),
    PARISH_COUNTY_NOT_FOUND("LOC_025", "Concelho da freguesia não encontrado"),
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
    USER_UPDATE_ERROR("USER_004", "Erro ao atualizar utilizador"),
    USER_DELETE_ERROR("USER_005", "Erro ao eliminar utilizador"),
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
    USER_ROLE_NOT_FOUND("USER_021", "Role do utilizador não encontrado"),
    USER_CANNOT_ACCESS_RESOURCE("USER_022", "Utilizador não pode aceder a este recurso"),
    USER_CANNOT_MODIFY_RESOURCE("USER_023", "Utilizador não pode modificar este recurso"),
    USER_INVALID_EMAIL("USER_030", "Email do utilizador inválido"),
    USER_INVALID_PASSWORD("USER_031", "Password inválida"),
    USER_INVALID_NAME("USER_032", "Nome do utilizador inválido"),
    USER_INVALID_PHONE("USER_033", "Telefone do utilizador inválido"),
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
    // TRANSAÇÕES (TRANSACTION_xxx)
    // ========================================================================
    TRANSACTION_001("TRANSACTION_001", "Propriedade já tem transação ativa em curso"),
    TRANSACTION_002("TRANSACTION_002", "Valor da transação deve ser positivo"),
    TRANSACTION_003("TRANSACTION_003", "Preço de venda é obrigatório para vendas"),
    TRANSACTION_004("TRANSACTION_004", "Preço de renda mensal é obrigatório para alugueres"),
    TRANSACTION_005("TRANSACTION_005", "Não é possível cancelar transação já completada"),
    TRANSACTION_006("TRANSACTION_006", "Data de conclusão não pode ser anterior à data da proposta"),
    TRANSACTION_007("TRANSACTION_007", "Transação não encontrada"),
    TRANSACTION_008("TRANSACTION_008", "Propriedade não encontrada"),
    TRANSACTION_009("TRANSACTION_009", "Contacto comprador não encontrado"),
    TRANSACTION_010("TRANSACTION_010", "Agente responsável não encontrado"),

    // ========================================================================
    // PAGAMENTOS (PAYMENT_xxx)
    // ========================================================================
    PAYMENT_001("PAYMENT_001", "Montante do pagamento deve ser positivo"),
    PAYMENT_002("PAYMENT_002", "Pagamento já foi marcado como pago"),
    PAYMENT_003("PAYMENT_003", "Pagamento não encontrado"),
    PAYMENT_004("PAYMENT_004", "Data de vencimento não pode ser no passado"),
    PAYMENT_005("PAYMENT_005", "Método de pagamento inválido"),

    // ========================================================================
    // COMISSÕES (COMMISSION_xxx)
    // ========================================================================
    COMMISSION_001("COMMISSION_001", "Comissão total não pode exceder 100%"),
    COMMISSION_002("COMMISSION_002", "Percentagem de comissão inválida"),

    // ========================================================================
    // AUDITORIA / ATIVIDADES (ACTIVITY_xxx)
    // ========================================================================
    ACTIVITY_NOT_FOUND("ACTIVITY_001", "Registo de atividade não encontrado"),
    ACTIVITY_CREATE_ERROR("ACTIVITY_002", "Erro ao criar registo de atividade"),
    ACTIVITY_INVALID_TYPE("ACTIVITY_003", "Tipo de atividade inválido"),
    ACTIVITY_INVALID_ENTITY_TYPE("ACTIVITY_004", "Tipo de entidade inválido"),

    // ========================================================================
    // LEADS (LEAD_xxx)
    // ========================================================================
    LEAD_NOT_FOUND("LEAD_001", "Lead não encontrado"),
    LEAD_ALREADY_ASSIGNED("LEAD_002", "Esta lead já tem um angariador associado"),
    LEAD_NOT_ASSIGNED("LEAD_003", "Esta lead não tem nenhum angariador associado"),

    // ========================================================================
    // BANNERS (BANNER_xxx)
    // ========================================================================
    BANNER_NOT_FOUND("BANNER_001", "Banner não encontrado"),
    BANNER_DESCRIPTION_REQUIRED("BANNER_002", "A descrição do banner é obrigatória"),
    BANNER_IMAGE_REQUIRED("BANNER_003", "A imagem do banner é obrigatória"),

    // ========================================================================
    // TAREFAS (TASK_xxx)
    // ========================================================================
    TASK_NOT_FOUND("TASK_001", "Tarefa não encontrada"),
    TASK_ASSIGNEE_NOT_FOUND("TASK_002", "Um ou mais utilizadores atribuídos não foram encontrados");

    // ========================================================================
    // CAMPOS E CONSTRUTOR
    // ========================================================================

    private final String code;
    private final String defaultMessage;

    // Construtor do enum - privado por padrão
    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    // ========================================================================
    // GETTERS
    // ========================================================================

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    // ========================================================================
    // MÉTODOS HELPER
    // ========================================================================

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