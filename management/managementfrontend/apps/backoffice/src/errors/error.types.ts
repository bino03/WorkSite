// src/errors/error.types.ts

// ========================
// Respostas de erro da API
// ========================

/** Erro estruturado devolvido pelo backend (GlobalExceptionHandler). */
export interface ApiErrorResponse {
  errorCode: string;
  message: string;
  status: number;
  timestamp: string;
  path: string;
  fieldErrors?: FieldError[];
}

/** Um erro de validação associado a um campo do formulário. */
export interface FieldError {
  field: string;
  message: string;
}

/**
 * Forma tolerante da resposta de erro, usada pelo `ErrorHandler` quando o
 * corpo da resposta pode vir incompleto.
 */
export interface ErrorResponse {
  errorCode?: string;
  message?: string;
  status?: number;
  timestamp?: string;
  path?: string;
  fieldErrors?: FieldError[];
}

/** Erro já normalizado para consumo pela UI (ver `utils/apiError.ts`). */
export interface AppError {
  code: string;
  message: string;
  userMessage: string;
  status: number;
  timestamp: Date;
  path: string;
  category: ErrorCategory;
  severity: ErrorSeverity;
  fieldErrors?: FieldError[];
}

/** Opções de `ErrorHandler.handle()`. */
export interface ErrorConfig {
  showNotification?: boolean;
  notificationType?: 'error' | 'warning' | 'info';
  customMessage?: string;
  logToConsole?: boolean;
}

// ========================
// Error Category & Severity
// ========================
export enum ErrorCategory {
  NOT_FOUND  = 'not_found',
  PERMISSION = 'permission',
  VALIDATION = 'validation',
  BUSINESS   = 'business',
  DATABASE   = 'database',
  STORAGE    = 'storage',
  UPLOAD     = 'upload',
  SERVER     = 'server',
}

export enum ErrorSeverity {
  INFO     = 'info',
  WARNING  = 'warning',
  ERROR    = 'error',
  CRITICAL = 'critical',
}

// ========================================================================
// Códigos de erro do backend
//
// Espelham 1:1 o `dto/error/ErrorCode.java`. Gerados a partir dele — ao
// acrescentar um código no backend, acrescenta aqui e em `errorMessages.ts`.
// ========================================================================

export enum GenericErrorCode {
  INTERNAL_SERVER_ERROR  = 'ERR_001',
  VALIDATION_ERROR       = 'ERR_002',
  RESOURCE_NOT_FOUND     = 'ERR_003',
  UNAUTHORIZED           = 'ERR_004',
  FORBIDDEN              = 'ERR_005',
  BAD_REQUEST            = 'ERR_006',
  CONFLICT               = 'ERR_007',
  METHOD_NOT_ALLOWED     = 'ERR_008',
  UNSUPPORTED_MEDIA_TYPE = 'ERR_009',
  REQUEST_TIMEOUT        = 'ERR_010',
  RATE_LIMIT_EXCEEDED    = 'ERR_011',
  SERVICE_UNAVAILABLE    = 'ERR_012',
}

export enum AssetErrorCode {
  ASSET_ENTERPRISE_NOT_FOUND = 'ASSET_030',
}

export enum MediaErrorCode {
  MEDIA_UPLOAD_ERROR              = 'MEDIA_010',
  MEDIA_UPLOAD_FAILED             = 'MEDIA_011',
  MEDIA_INVALID_TYPE              = 'MEDIA_012',
  MEDIA_SIZE_EXCEEDED             = 'MEDIA_013',
  MEDIA_EMPTY                     = 'MEDIA_014',
  MEDIA_CORRUPTED                 = 'MEDIA_015',
  MEDIA_BANNER_UPLOAD_ERROR       = 'MEDIA_020',
  MEDIA_BANNER_INVALID_TYPE       = 'MEDIA_021',
  MEDIA_BANNER_SIZE_EXCEEDED      = 'MEDIA_022',
  MEDIA_BANNER_NOT_FOUND          = 'MEDIA_023',
  MEDIA_BANNER_DELETE_ERROR       = 'MEDIA_024',
  MEDIA_GALLERY_UPLOAD_ERROR      = 'MEDIA_030',
  MEDIA_GALLERY_INVALID_TYPE      = 'MEDIA_031',
  MEDIA_GALLERY_SIZE_EXCEEDED     = 'MEDIA_032',
  MEDIA_GALLERY_LIMIT_EXCEEDED    = 'MEDIA_033',
  MEDIA_GALLERY_NOT_FOUND         = 'MEDIA_034',
  MEDIA_GALLERY_DELETE_ERROR      = 'MEDIA_035',
  MEDIA_GALLERY_METADATA_MISMATCH = 'MEDIA_036',
  MEDIA_NOT_FOUND                 = 'MEDIA_040',
  MEDIA_DELETE_ERROR              = 'MEDIA_041',
  MEDIA_UPDATE_ERROR              = 'MEDIA_042',
  MEDIA_DOWNLOAD_ERROR            = 'MEDIA_043',
  MEDIA_REORDER_ERROR             = 'MEDIA_044',
  MEDIA_ASSET_NOT_FOUND           = 'MEDIA_045',
  MEDIA_INVALID_ENTITY_TYPE       = 'MEDIA_046',
  MEDIA_FETCH_ERROR               = 'MEDIA_047',
  MEDIA_DUPLICATE_IDS             = 'MEDIA_048',
  MEDIA_NOT_IN_ASSET              = 'MEDIA_049',
  MEDIA_DUPLICATE_SORT_ORDERS     = 'MEDIA_050',
  MEDIA_INVALID_SORT_ORDER        = 'MEDIA_051',
}

export enum DocumentErrorCode {
  DOCUMENT_NOT_FOUND        = 'DOC_001',
  DOCUMENT_CREATE_ERROR     = 'DOC_002',
  DOCUMENT_UPDATE_ERROR     = 'DOC_003',
  DOCUMENT_DELETE_ERROR     = 'DOC_004',
  DOCUMENT_UPLOAD_ERROR     = 'DOC_005',
  DOCUMENT_DOWNLOAD_ERROR   = 'DOC_006',
  DOCUMENT_INVALID_TYPE     = 'DOC_007',
  DOCUMENT_SIZE_EXCEEDED    = 'DOC_008',
  DOCUMENT_ASSET_NOT_FOUND  = 'DOC_009',
  DOCUMENT_EMPTY            = 'DOC_010',
  DOCUMENT_CORRUPTED        = 'DOC_011',
  DOCUMENT_PROCESSING_ERROR = 'DOC_012',
}

export enum LicenseErrorCode {
  LICENSE_NOT_FOUND        = 'LICENSE_001',
  LICENSE_CREATE_ERROR     = 'LICENSE_002',
  LICENSE_UPDATE_ERROR     = 'LICENSE_003',
  LICENSE_DELETE_ERROR     = 'LICENSE_004',
  LICENSE_UPLOAD_ERROR     = 'LICENSE_005',
  LICENSE_ASSET_NOT_FOUND  = 'LICENSE_006',
  LICENSE_INVALID_CLASS    = 'LICENSE_007',
  LICENSE_INVALID_DATE     = 'LICENSE_008',
  LICENSE_EXPIRED          = 'LICENSE_009',
  LICENSE_DUPLICATE_NUMBER = 'LICENSE_010',
  LICENSE_INVALID_NUMBER   = 'LICENSE_011',
  LICENSE_FILE_REQUIRED    = 'LICENSE_012',
  LICENSE_PROCESSING_ERROR = 'LICENSE_013',
}

export enum FileErrorCode {
  FILE_UPLOAD_ERROR        = 'FILE_001',
  FILE_SIZE_EXCEEDED       = 'FILE_002',
  FILE_TYPE_NOT_ALLOWED    = 'FILE_003',
  FILE_NOT_FOUND           = 'FILE_004',
  FILE_DELETE_ERROR        = 'FILE_005',
  FILE_DOWNLOAD_ERROR      = 'FILE_006',
  FILE_CORRUPTED           = 'FILE_007',
  FILE_EMPTY               = 'FILE_008',
  FILE_NAME_INVALID        = 'FILE_009',
  FILE_STORAGE_ERROR       = 'FILE_010',
  FILE_COMPRESSION_ERROR   = 'FILE_011',
  FILE_EXTRACTION_ERROR    = 'FILE_012',
  FILE_MIME_TYPE_INVALID   = 'FILE_013',
  FILE_EXTENSION_INVALID   = 'FILE_014',
  FILE_READ_ERROR          = 'FILE_015',
  IMAGE_INVALID_FORMAT     = 'FILE_020',
  IMAGE_SIZE_EXCEEDED      = 'FILE_021',
  IMAGE_DIMENSIONS_INVALID = 'FILE_022',
  IMAGE_PROCESSING_ERROR   = 'FILE_023',
  VIDEO_INVALID_FORMAT     = 'FILE_030',
  VIDEO_SIZE_EXCEEDED      = 'FILE_031',
  VIDEO_DURATION_EXCEEDED  = 'FILE_032',
  VIDEO_PROCESSING_ERROR   = 'FILE_033',
}

export enum UploadRequestErrorCode {
  UPLOAD_REQUEST_NOT_FOUND           = 'UPLOAD_001',
  UPLOAD_REQUEST_INVALID_STATUS      = 'UPLOAD_002',
  UPLOAD_REQUEST_ENERGY_DATA_MISSING = 'UPLOAD_003',
}

export enum StorageErrorCode {
  STORAGE_CONNECTION_ERROR    = 'STORAGE_001',
  STORAGE_UPLOAD_ERROR        = 'STORAGE_002',
  STORAGE_DELETE_ERROR        = 'STORAGE_003',
  STORAGE_DOWNLOAD_ERROR      = 'STORAGE_004',
  STORAGE_BUCKET_NOT_FOUND    = 'STORAGE_005',
  STORAGE_FILE_NOT_FOUND      = 'STORAGE_006',
  STORAGE_QUOTA_EXCEEDED      = 'STORAGE_007',
  STORAGE_PERMISSION_DENIED   = 'STORAGE_008',
  STORAGE_SIGNED_URL_ERROR    = 'STORAGE_009',
  STORAGE_INVALID_PATH        = 'STORAGE_010',
  STORAGE_UPLOAD_TIMEOUT      = 'STORAGE_011',
  STORAGE_DOWNLOAD_TIMEOUT    = 'STORAGE_012',
  STORAGE_BUCKET_CREATE_ERROR = 'STORAGE_013',
  STORAGE_MOVE_ERROR          = 'STORAGE_014',
  STORAGE_COPY_ERROR          = 'STORAGE_015',
}

export enum EnterpriseErrorCode {
  ENTERPRISE_NOT_FOUND      = 'ENT_001',
  ENTERPRISE_ALREADY_EXISTS = 'ENT_002',
  ENTERPRISE_CREATE_ERROR   = 'ENT_003',
  ENTERPRISE_UPDATE_ERROR   = 'ENT_004',
  ENTERPRISE_DELETE_ERROR   = 'ENT_005',
  ENTERPRISE_INVALID_NAME   = 'ENT_010',
  ENTERPRISE_INVALID_NIF    = 'ENT_011',
  ENTERPRISE_INVALID_EMAIL  = 'ENT_012',
  ENTERPRISE_INVALID_PHONE  = 'ENT_013',
  ENTERPRISE_HAS_PROPERTIES = 'ENT_020',
  ENTERPRISE_HAS_BUILDINGS  = 'ENT_021',
  ENTERPRISE_HAS_USERS      = 'ENT_022',
  ENTERPRISE_DUPLICATE_NIF  = 'ENT_030',
  ENTERPRISE_INACTIVE       = 'ENT_031',
}

export enum BudgetErrorCode {
  BUDGET_ITEM_NOT_FOUND          = 'BUDGET_001',
  BUDGET_CREATE_ERROR            = 'BUDGET_002',
  BUDGET_UPDATE_ERROR            = 'BUDGET_003',
  BUDGET_DELETE_ERROR            = 'BUDGET_004',
  BUDGET_INVALID_NAME            = 'BUDGET_005',
  BUDGET_ENTERPRISE_NOT_FOUND    = 'BUDGET_006',
  BUDGET_PARENT_NOT_FOUND        = 'BUDGET_007',
  BUDGET_PARENT_OTHER_ENTERPRISE = 'BUDGET_008',
  BUDGET_CYCLE                   = 'BUDGET_009',
  BUDGET_DUPLICATE_CODE          = 'BUDGET_010',
  BUDGET_INVALID_DATES           = 'BUDGET_011',
  BUDGET_ITEM_OTHER_ENTERPRISE   = 'BUDGET_012',
  BUDGET_IMPORT_EMPTY_FILE       = 'BUDGET_020',
  BUDGET_IMPORT_INVALID_TYPE     = 'BUDGET_021',
  BUDGET_IMPORT_READ_ERROR       = 'BUDGET_022',
  BUDGET_IMPORT_NO_HEADER        = 'BUDGET_023',
  BUDGET_IMPORT_NO_ROWS          = 'BUDGET_024',
  BUDGET_IMPORT_NOT_EMPTY        = 'BUDGET_025',
}

export enum ConstructionExpenseErrorCode {
  EXPENSE_NOT_FOUND             = 'EXPENSE_001',
  EXPENSE_CREATE_ERROR          = 'EXPENSE_002',
  EXPENSE_UPDATE_ERROR          = 'EXPENSE_003',
  EXPENSE_DELETE_ERROR          = 'EXPENSE_004',
  EXPENSE_INVALID_NAME          = 'EXPENSE_005',
  EXPENSE_INVALID_PRICE         = 'EXPENSE_006',
  EXPENSE_BUDGET_ITEM_NOT_FOUND = 'EXPENSE_007',
  EXPENSE_INVOICE_UPLOAD_ERROR  = 'EXPENSE_008',
  EXPENSE_ITEM_NOT_EXPENSABLE   = 'EXPENSE_009',
  EXPENSE_ITEM_OTHER_ENTERPRISE = 'EXPENSE_010',
}

export enum LocationErrorCode {
  LOCATION_INVALID_NAME     = 'LOC_030',
  LOCATION_INVALID_CODE     = 'LOC_031',
  LOCATION_HIERARCHY_ERROR  = 'LOC_032',
  LOCATION_NOT_FOUND        = 'LOC_034',
  LOCATION_LINK_BROKEN      = 'LOC_035',
  LOCATION_PRIMARY_REQUIRED = 'LOC_036',
}

export enum UserErrorCode {
  USER_001                      = 'USER_001',
  USER_002                      = 'USER_002',
  USER_003                      = 'USER_003',
  USER_NOT_FOUND                = 'USER_004',
  USER_ALREADY_EXISTS           = 'USER_005',
  USER_CREATE_ERROR             = 'USER_006',
  USER_UPDATE_ERROR             = 'USER_007',
  USER_DELETE_ERROR             = 'USER_008',
  USER_INVALID_CREDENTIALS      = 'USER_010',
  USER_NOT_AUTHENTICATED        = 'USER_011',
  USER_TOKEN_EXPIRED            = 'USER_012',
  USER_TOKEN_INVALID            = 'USER_013',
  USER_SESSION_EXPIRED          = 'USER_014',
  USER_PROFILE_NOT_FOUND        = 'USER_015',
  USER_INSUFFICIENT_PERMISSIONS = 'USER_020',
  PROFILE_NOT_DELETED           = 'USER_024',
  PROFILE_CANNOT_BLOCK          = 'USER_028',
  PROFILE_CANNOT_UNBLOCK        = 'USER_029',
  PROFILE_CANNOT_DELETE         = 'USER_033',
  USER_ROLE_NOT_FOUND           = 'USER_021',
  USER_CANNOT_ACCESS_RESOURCE   = 'USER_022',
  USER_CANNOT_MODIFY_RESOURCE   = 'USER_023',
  USER_INVALID_EMAIL            = 'USER_030',
  USER_INVALID_PASSWORD         = 'USER_031',
  USER_INVALID_NAME             = 'USER_032',
  USER_INVALID_PHONE            = 'USER_035',
  USER_DUPLICATE_EMAIL          = 'USER_034',
  USER_INACTIVE                 = 'USER_040',
  USER_BLOCKED                  = 'USER_041',
  USER_DELETED                  = 'USER_042',
  USER_PENDING_VERIFICATION     = 'USER_043',
  USER_ENTERPRISE_NOT_FOUND     = 'USER_050',
  USER_HAS_DEPENDENCIES         = 'USER_051',
  ACCESS_DENIED                 = 'USER_025',
  USER_NO_COMPANY               = 'USER_026',
  INSUFFICIENT_PERMISSIONS      = 'USER_027',
}

export enum DatabaseErrorCode {
  DATABASE_ERROR                 = 'DB_001',
  DATABASE_CONNECTION_ERROR      = 'DB_002',
  DATABASE_CONSTRAINT_VIOLATION  = 'DB_003',
  DATABASE_DUPLICATE_KEY         = 'DB_004',
  DATABASE_FOREIGN_KEY_VIOLATION = 'DB_005',
  DATABASE_TRANSACTION_ERROR     = 'DB_006',
  DATABASE_DEADLOCK              = 'DB_007',
  DATABASE_TIMEOUT               = 'DB_008',
  DATABASE_MIGRATION_ERROR       = 'DB_009',
}

export enum SearchErrorCode {
  SEARCH_INVALID_QUERY    = 'SEARCH_001',
  SEARCH_INVALID_FILTER   = 'SEARCH_002',
  SEARCH_INVALID_SORT     = 'SEARCH_003',
  SEARCH_NO_RESULTS       = 'SEARCH_004',
  SEARCH_TOO_MANY_RESULTS = 'SEARCH_005',
}

export enum PaginationErrorCode {
  PAGINATION_INVALID_PAGE  = 'PAGE_001',
  PAGINATION_INVALID_SIZE  = 'PAGE_002',
  PAGINATION_OUT_OF_BOUNDS = 'PAGE_003',
}

export enum ActivityErrorCode {
  ACTIVITY_NOT_FOUND           = 'ACTIVITY_001',
  ACTIVITY_CREATE_ERROR        = 'ACTIVITY_002',
  ACTIVITY_INVALID_TYPE        = 'ACTIVITY_003',
  ACTIVITY_INVALID_ENTITY_TYPE = 'ACTIVITY_004',
}

export enum TaskErrorCode {
  TASK_NOT_FOUND          = 'TASK_001',
  TASK_ASSIGNEE_NOT_FOUND = 'TASK_002',
}

export type BackendErrorCode =
  | GenericErrorCode
  | AssetErrorCode
  | MediaErrorCode
  | DocumentErrorCode
  | LicenseErrorCode
  | FileErrorCode
  | UploadRequestErrorCode
  | StorageErrorCode
  | EnterpriseErrorCode
  | BudgetErrorCode
  | ConstructionExpenseErrorCode
  | LocationErrorCode
  | UserErrorCode
  | DatabaseErrorCode
  | SearchErrorCode
  | PaginationErrorCode
  | ActivityErrorCode
  | TaskErrorCode;
