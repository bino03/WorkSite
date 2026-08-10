// src/errors/errorMessages.ts
import { ErrorCategory, ErrorSeverity } from './error.types';

export const ERROR_MESSAGES: Record<string, string> = {
  // ── Genéricos ──────────────────────────────────────────────────────────────
  'ERR_001': 'Ocorreu um erro inesperado. Por favor, tente novamente.',
  'ERR_002': 'Os dados fornecidos são inválidos. Verifique os campos assinalados.',
  'ERR_003': 'O recurso solicitado não foi encontrado.',
  'ERR_004': 'Sessão expirada. Por favor, faça login novamente.',
  'ERR_005': 'Não tem permissão para realizar esta ação.',
  'ERR_006': 'Pedido inválido. Verifique os dados enviados.',
  'ERR_007': 'Conflito de dados. O registo já pode existir.',
  'ERR_008': 'Operação não suportada.',
  'ERR_009': 'Formato de conteúdo não suportado.',
  'ERR_010': 'O pedido demorou demasiado. Tente novamente.',
  'ERR_011': 'Demasiados pedidos. Aguarde um momento.',
  'ERR_012': 'Serviço temporariamente indisponível. Tente mais tarde.',

  // ── Assets / Propriedades ──────────────────────────────────────────────────
  'ASSET_030': 'Empresa não encontrada.',

  // ── Media ──────────────────────────────────────────────────────────────────
  'MEDIA_010': 'Erro ao fazer upload do ficheiro. Tente novamente.',
  'MEDIA_011': 'O upload falhou. Verifique a ligação e tente de novo.',
  'MEDIA_012': 'Tipo de ficheiro não suportado.',
  'MEDIA_013': 'O ficheiro excede o tamanho máximo permitido.',
  'MEDIA_014': 'O ficheiro está vazio.',
  'MEDIA_015': 'O ficheiro está corrompido e não pode ser processado.',
  'MEDIA_020': 'Erro ao fazer upload da imagem de capa.',
  'MEDIA_021': 'Tipo de ficheiro inválido para a imagem de capa.',
  'MEDIA_022': 'A imagem de capa excede o tamanho máximo.',
  'MEDIA_023': 'Imagem de capa não encontrada.',
  'MEDIA_024': 'Não foi possível eliminar a imagem de capa.',
  'MEDIA_030': 'Erro ao fazer upload de fotos para a galeria.',
  'MEDIA_031': 'Tipo de ficheiro inválido para a galeria.',
  'MEDIA_032': 'Uma ou mais fotos excedem o tamanho máximo.',
  'MEDIA_033': 'Limite máximo de fotos na galeria atingido.',
  'MEDIA_034': 'Foto da galeria não encontrada.',
  'MEDIA_035': 'Não foi possível eliminar a foto da galeria.',
  'MEDIA_036': 'Erro no upload: o número de ficheiros não corresponde aos dados enviados.',
  'MEDIA_040': 'Ficheiro de media não encontrado.',
  'MEDIA_041': 'Não foi possível eliminar o ficheiro.',
  'MEDIA_042': 'Não foi possível atualizar o ficheiro.',
  'MEDIA_043': 'Não foi possível descarregar o ficheiro.',
  'MEDIA_044': 'Não foi possível reordenar as fotos.',
  'MEDIA_045': 'Propriedade da media não encontrada.',
  'MEDIA_046': 'Tipo de entidade inválido.',
  'MEDIA_047': 'Não foi possível carregar as fotos.',
  'MEDIA_048': 'Existem IDs de foto duplicados no pedido.',
  'MEDIA_049': 'Uma ou mais fotos não pertencem a esta propriedade.',
  'MEDIA_050': 'Existem ordens de apresentação duplicadas.',
  'MEDIA_051': 'A ordem de apresentação não pode ser negativa.',

  // ── Documentos ─────────────────────────────────────────────────────────────
  'DOC_001': 'Documento não encontrado.',
  'DOC_002': 'Não foi possível criar o documento.',
  'DOC_003': 'Não foi possível atualizar o documento.',
  'DOC_004': 'Não foi possível eliminar o documento.',
  'DOC_005': 'Erro ao fazer upload do documento.',
  'DOC_006': 'Não foi possível descarregar o documento.',
  'DOC_007': 'Tipo de documento não suportado.',
  'DOC_008': 'O documento excede o tamanho máximo permitido.',
  'DOC_009': 'Propriedade do documento não encontrada.',
  'DOC_010': 'O documento está vazio.',
  'DOC_011': 'O documento está corrompido.',
  'DOC_012': 'Erro ao processar o documento.',

  // ── Licenças ───────────────────────────────────────────────────────────────
  'LICENSE_001': 'Licença não encontrada.',
  'LICENSE_002': 'Não foi possível criar a licença.',
  'LICENSE_003': 'Não foi possível atualizar a licença.',
  'LICENSE_004': 'Não foi possível eliminar a licença.',
  'LICENSE_005': 'Erro ao fazer upload do documento da licença.',
  'LICENSE_006': 'Propriedade da licença não encontrada.',
  'LICENSE_007': 'Classe energética inválida.',
  'LICENSE_008': 'Data da licença inválida.',
  'LICENSE_009': 'Esta licença já expirou.',
  'LICENSE_010': 'Este número de licença já está registado.',
  'LICENSE_011': 'Número de licença inválido.',
  'LICENSE_012': 'O ficheiro PDF da licença é obrigatório.',
  'LICENSE_013': 'Erro ao processar a licença.',

  // ── Ficheiros ──────────────────────────────────────────────────────────────
  'FILE_001': 'Erro no upload do ficheiro. Tente novamente.',
  'FILE_002': 'O ficheiro excede o tamanho máximo permitido.',
  'FILE_003': 'Tipo de ficheiro não permitido. Verifique os formatos aceites.',
  'FILE_004': 'Ficheiro não encontrado.',
  'FILE_005': 'Não foi possível eliminar o ficheiro.',
  'FILE_006': 'Não foi possível descarregar o ficheiro.',
  'FILE_007': 'O ficheiro está corrompido.',
  'FILE_008': 'O ficheiro está vazio.',
  'FILE_009': 'Nome do ficheiro inválido.',
  'FILE_010': 'Erro ao guardar o ficheiro.',
  'FILE_011': 'Erro ao comprimir o ficheiro.',
  'FILE_012': 'Erro ao extrair o ficheiro.',
  'FILE_013': 'Tipo MIME do ficheiro inválido.',
  'FILE_014': 'Extensão do ficheiro inválida.',
  'FILE_015': 'Não foi possível ler o ficheiro.',
  'FILE_020': 'Formato de imagem inválido. Formatos aceites: JPEG, PNG, WEBP, AVIF.',
  'FILE_021': 'A imagem excede o tamanho máximo (15 MB).',
  'FILE_022': 'Dimensões da imagem inválidas.',
  'FILE_023': 'Erro ao processar a imagem.',
  'FILE_030': 'Formato de vídeo inválido. Formatos aceites: MP4, WEBM.',
  'FILE_031': 'O vídeo excede o tamanho máximo (250 MB).',
  'FILE_032': 'A duração do vídeo excede o limite permitido.',
  'FILE_033': 'Erro ao processar o vídeo.',

  // ── Upload Requests ────────────────────────────────────────────────────────
  'UPLOAD_001': 'Pedido de upload não encontrado.',
  'UPLOAD_002': 'Estado do pedido de upload inválido.',
  'UPLOAD_003': 'Os dados energéticos do imóvel são obrigatórios.',

  // ── Storage ────────────────────────────────────────────────────────────────
  'STORAGE_001': 'Erro de ligação ao servidor de armazenamento.',
  'STORAGE_002': 'Não foi possível guardar o ficheiro. Tente novamente.',
  'STORAGE_003': 'Não foi possível eliminar o ficheiro do servidor.',
  'STORAGE_004': 'Não foi possível descarregar o ficheiro do servidor.',
  'STORAGE_005': 'Destino de armazenamento não encontrado.',
  'STORAGE_006': 'Ficheiro não encontrado no servidor.',
  'STORAGE_007': 'Espaço de armazenamento esgotado. Contacte o suporte.',
  'STORAGE_008': 'Sem permissão para aceder ao ficheiro.',
  'STORAGE_009': 'Não foi possível gerar o link de acesso ao ficheiro.',
  'STORAGE_010': 'Caminho de ficheiro inválido.',
  'STORAGE_011': 'O upload demorou demasiado. Tente com um ficheiro menor.',
  'STORAGE_012': 'O download demorou demasiado. Tente novamente.',
  'STORAGE_013': 'Não foi possível criar o destino de armazenamento.',
  'STORAGE_014': 'Não foi possível mover o ficheiro.',
  'STORAGE_015': 'Não foi possível copiar o ficheiro.',

  // ── Empresas ───────────────────────────────────────────────────────────────
  'ENT_001': 'Empresa não encontrada.',
  'ENT_002': 'Esta empresa já está registada.',
  'ENT_003': 'Não foi possível criar a empresa.',
  'ENT_004': 'Não foi possível atualizar a empresa.',
  'ENT_005': 'Não foi possível eliminar a empresa.',
  'ENT_010': 'O nome da empresa é inválido.',
  'ENT_011': 'O NIF indicado é inválido.',
  'ENT_012': 'O email da empresa é inválido.',
  'ENT_013': 'O telefone da empresa é inválido.',
  'ENT_020': 'Não é possível eliminar: a empresa tem propriedades associadas.',
  'ENT_021': 'Não é possível eliminar: a empresa tem edifícios associados.',
  'ENT_022': 'Não é possível eliminar: a empresa tem utilizadores associados.',
  'ENT_030': 'Este NIF já está registado noutra empresa.',
  'ENT_031': 'Esta empresa está inativa.',

  // ── Localização ────────────────────────────────────────────────────────────
  'LOC_030': 'Nome da localização inválido.',
  'LOC_031': 'Código da localização inválido.',
  'LOC_032': 'Erro na hierarquia de localizações.',
  'LOC_034': 'Localização não encontrada.',
  'LOC_035': 'Link de localização corrompido. Contacte o suporte.',
  'LOC_036': 'É necessário definir uma localização principal.',

  // ── Utilizadores ───────────────────────────────────────────────────────────
  'USER_001': 'Credenciais inválidas. Verifique o email e a password.',
  'USER_002': 'Token inválido ou expirado. Faça login novamente.',
  'USER_003': 'A sua sessão expirou. Faça login novamente.',
  'USER_004': 'Utilizador não encontrado.',
  'USER_005': 'Este utilizador já está registado.',
  'USER_006': 'Não foi possível criar o utilizador.',
  'USER_007': 'Não foi possível atualizar o utilizador.',
  'USER_008': 'Não foi possível eliminar o utilizador.',
  'USER_010': 'Email ou password incorretos.',
  'USER_011': 'Não está autenticado. Faça login para continuar.',
  'USER_012': 'A sua sessão expirou. Faça login novamente.',
  'USER_013': 'Token de acesso inválido.',
  'USER_014': 'Sessão expirada. Faça login novamente.',
  'USER_015': 'Perfil do utilizador não encontrado.',
  'USER_020': 'Não tem permissões suficientes para esta ação.',
  'USER_021': 'Função do utilizador não encontrada.',
  'USER_022': 'Não tem acesso a este recurso.',
  'USER_023': 'Não tem permissão para modificar este recurso.',
  'USER_024': 'Utilizador não encontrado ou não está no estado eliminado.',
  'USER_025': 'Sem permissão para aceder a este recurso.',
  'USER_026': 'O utilizador não tem empresa associada.',
  'USER_027': 'Permissões insuficientes para esta ação.',
  'USER_028': 'O utilizador não pode ser bloqueado (já está bloqueado ou eliminado).',
  'USER_029': 'O utilizador não pode ser desbloqueado (não está bloqueado).',
  'USER_030': 'O email indicado é inválido.',
  'USER_031': 'A password não cumpre os requisitos mínimos.',
  'USER_032': 'O nome do utilizador é inválido.',
  'USER_033': 'O utilizador não pode ser eliminado (já foi eliminado ou não existe).',
  'USER_034': 'Este email já está registado.',
  'USER_035': 'O telefone indicado é inválido.',
  'USER_040': 'Esta conta está inativa.',
  'USER_041': 'Esta conta está bloqueada. Contacte o suporte.',
  'USER_042': 'Esta conta foi eliminada.',
  'USER_043': 'A conta ainda não foi verificada. Verifique o seu email.',
  'USER_050': 'Empresa do utilizador não encontrada.',
  'USER_051': 'Não é possível eliminar: o utilizador tem dados associados.',

  // ── Registo de atividade ───────────────────────────────────────────────────
  'ACTIVITY_001': 'Registo de atividade não encontrado.',
  'ACTIVITY_002': 'Não foi possível criar o registo de atividade.',
  'ACTIVITY_003': 'Tipo de atividade inválido.',
  'ACTIVITY_004': 'Tipo de entidade inválido.',

  // ── Base de dados ──────────────────────────────────────────────────────────
  'DB_001': 'Erro ao aceder à base de dados.',
  'DB_002': 'Não foi possível ligar à base de dados.',
  'DB_003': 'Conflito de dados: o registo pode já existir ou violar uma restrição.',
  'DB_004': 'Registo duplicado na base de dados.',
  'DB_005': 'Não é possível eliminar: existem registos dependentes.',
  'DB_006': 'Erro na transação. Tente novamente.',
  'DB_007': 'Conflito interno na base de dados. Tente novamente.',
  'DB_008': 'A operação demorou demasiado. Tente novamente.',
  'DB_009': 'Erro na migração da base de dados. Contacte o suporte.',

  // ── Pesquisa ───────────────────────────────────────────────────────────────
  'SEARCH_001': 'Critério de pesquisa inválido.',
  'SEARCH_002': 'Filtro de pesquisa inválido.',
  'SEARCH_003': 'Ordenação inválida.',
  'SEARCH_004': 'Nenhum resultado encontrado.',
  'SEARCH_005': 'Demasiados resultados. Refine a pesquisa.',

  // ── Paginação ──────────────────────────────────────────────────────────────
  'PAGE_001': 'Número de página inválido.',
  'PAGE_002': 'Tamanho de página inválido.',
  'PAGE_003': 'Página fora dos limites disponíveis.',

  // ── Orçamento de construção ───────────────────────────────────────────────
  'BUDGET_001': 'Rubrica do orçamento não encontrada.',
  'BUDGET_002': 'Não foi possível criar a rubrica.',
  'BUDGET_003': 'Não foi possível atualizar a rubrica.',
  'BUDGET_004': 'Não foi possível eliminar a rubrica.',
  'BUDGET_005': 'A descrição da rubrica é inválida.',
  'BUDGET_006': 'Projeto do orçamento não encontrado.',
  'BUDGET_007': 'Rubrica-mãe não encontrada.',
  'BUDGET_008': 'A rubrica-mãe pertence a outro projeto.',
  'BUDGET_009': 'Uma rubrica não pode ficar dentro de si própria.',
  'BUDGET_010': 'Já existe uma rubrica com este índice neste projeto.',
  'BUDGET_011': 'A data de fim não pode ser anterior à data de início.',
  'BUDGET_012': 'Uma rubrica não pode mudar de projeto.',

  // ── Importação de orçamento (.xlsx) ───────────────────────────────────────
  'BUDGET_020': 'O ficheiro de orçamento está vazio.',
  'BUDGET_021': 'O ficheiro tem de ser um Excel (.xlsx).',
  'BUDGET_022': 'Não foi possível ler o ficheiro de orçamento.',
  'BUDGET_023': 'Não foi encontrada a linha de cabeçalho (coluna "Art") no Excel.',
  'BUDGET_024': 'O Excel não tem rubricas para importar.',
  'BUDGET_025': 'Este projeto já tem orçamento. Elimine-o primeiro ou importe com substituição.',

  // ── Despesas de construção ────────────────────────────────────────────────
  'EXPENSE_001': 'Despesa não encontrada.',
  'EXPENSE_002': 'Não foi possível criar a despesa.',
  'EXPENSE_003': 'Não foi possível atualizar a despesa.',
  'EXPENSE_004': 'Não foi possível eliminar a despesa.',
  'EXPENSE_005': 'O nome da despesa é inválido.',
  'EXPENSE_006': 'O preço da despesa é inválido.',
  'EXPENSE_007': 'Rubrica da despesa não encontrada.',
  'EXPENSE_008': 'Não foi possível carregar a fatura da despesa.',
  'EXPENSE_009': 'Só é possível lançar despesas em rubricas — títulos e notas não aceitam despesas.',
  'EXPENSE_010': 'A rubrica indicada pertence a outro projeto.',

  // Faturas de obra
  'INVOICE_001': 'Fatura não encontrada.',
  'INVOICE_002': 'Projeto da fatura não encontrado.',
  'INVOICE_003': 'Não foi possível carregar o ficheiro da fatura.',
  'INVOICE_004': 'Esta fatura já está associada a uma rubrica — desassocie-a primeiro.',
  'INVOICE_005': 'Esta fatura não está associada a nenhuma rubrica.',
  'INVOICE_006': 'Preencha a data e o total da fatura antes de a associar a uma rubrica.',
  'INVOICE_007': 'A rubrica indicada pertence a outro projeto.',
  'INVOICE_008': 'Não foi possível ler o QR da AT neste documento — os campos têm de ser preenchidos à mão.',
  'INVOICE_009': 'Não foi possível obter o ficheiro original da fatura.',
  'INVOICE_010': 'Já existe uma fatura com este ATCUD neste projeto.',
  'INVOICE_011': 'Já existe uma fatura deste fornecedor com este número neste projeto.',
  'INVOICE_012': 'Este ficheiro já foi carregado neste projeto — é igual, byte a byte, a uma fatura existente.',

  // ── Tarefas ────────────────────────────────────────────────────────────────
  'TASK_001': 'Tarefa não encontrada. Pode ter sido removida.',
  'TASK_002': 'Um ou mais utilizadores atribuídos não foram encontrados.',

  // ── Fallback ───────────────────────────────────────────────────────────────
  'DEFAULT': 'Ocorreu um erro. Por favor, tente novamente.',
};

/** Retorna a mensagem user-friendly para um errorCode */
export function getUserFriendlyMessage(errorCode?: string): string {
  if (!errorCode) return ERROR_MESSAGES['DEFAULT'];
  return ERROR_MESSAGES[errorCode] ?? ERROR_MESSAGES['DEFAULT'];
}

/** Determina a categoria de erro com base no HTTP status */
export function getCategoryFromStatus(status: number): ErrorCategory {
  if (status === 404) return ErrorCategory.NOT_FOUND;
  if (status === 401 || status === 403) return ErrorCategory.PERMISSION;
  if (status === 409) return ErrorCategory.DATABASE;
  if (status >= 500) return ErrorCategory.SERVER;
  if (status === 400) return ErrorCategory.BUSINESS;
  return ErrorCategory.BUSINESS;
}

/** Determina a severidade a partir da categoria */
export function getSeverityFromCategory(category: ErrorCategory): ErrorSeverity {
  switch (category) {
    case ErrorCategory.SERVER:
    case ErrorCategory.DATABASE:
    case ErrorCategory.STORAGE:
      return ErrorSeverity.CRITICAL;
    case ErrorCategory.PERMISSION:
      return ErrorSeverity.ERROR;
    case ErrorCategory.NOT_FOUND:
      return ErrorSeverity.WARNING;
    default:
      return ErrorSeverity.ERROR;
  }
}

/** Determina a categoria com base no prefixo do errorCode */
export function getCategoryFromCode(errorCode: string): ErrorCategory {
  if (errorCode.startsWith('ERR_004') || errorCode.startsWith('ERR_005') || errorCode.startsWith('USER_02')) {
    return ErrorCategory.PERMISSION;
  }
  if (errorCode.startsWith('STORAGE_')) return ErrorCategory.STORAGE;
  if (errorCode.startsWith('DB_')) return ErrorCategory.DATABASE;
  if (errorCode.startsWith('FILE_') || errorCode.startsWith('UPLOAD_') || errorCode.startsWith('MEDIA_0')) {
    return ErrorCategory.UPLOAD;
  }
  if (errorCode.endsWith('_NOT_FOUND') || errorCode === 'ERR_003') return ErrorCategory.NOT_FOUND;
  if (errorCode === 'ERR_002') return ErrorCategory.VALIDATION;
  if (errorCode === 'ERR_001' || errorCode.startsWith('DB_00')) return ErrorCategory.SERVER;
  return ErrorCategory.BUSINESS;
}
