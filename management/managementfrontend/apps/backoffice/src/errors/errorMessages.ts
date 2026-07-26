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
  'ASSET_001': 'Propriedade não encontrada. Pode ter sido removida.',
  'ASSET_002': 'Esta propriedade já existe no sistema.',
  'ASSET_003': 'Não foi possível criar a propriedade. Verifique os dados.',
  'ASSET_004': 'Não foi possível atualizar a propriedade.',
  'ASSET_005': 'Não foi possível eliminar a propriedade.',
  'ASSET_006': 'A propriedade não está no estado eliminado.',
  'ASSET_010': 'O nome da propriedade é inválido.',
  'ASSET_011': 'O preço indicado é inválido.',
  'ASSET_012': 'A área indicada é inválida.',
  'ASSET_013': 'O estado da propriedade é inválido.',
  'ASSET_014': 'A natureza do negócio é inválida.',
  'ASSET_015': 'As coordenadas geográficas são inválidas.',
  'ASSET_016': 'O ano de construção é inválido.',
  'ASSET_020': 'Agência não encontrada.',
  'ASSET_021': 'A agência indicada é inválida.',
  'ASSET_025': 'Tipo de propriedade não encontrado.',
  'ASSET_026': 'Subtipo de propriedade não encontrado.',
  'ASSET_027': 'A combinação de tipo e subtipo não é válida.',
  'ASSET_030': 'Empresa não encontrada.',
  'ASSET_031': 'Edifício não encontrado.',
  'ASSET_032': 'O edifício não pertence à empresa selecionada.',
  'ASSET_033': 'Esta propriedade já está associada a um edifício.',
  'ASSET_034': 'É necessário selecionar um edifício para este tipo de propriedade.',
  'ASSET_035': 'Empreendimento não encontrado.',
  'ASSET_040': 'Localização da propriedade não encontrada.',
  'ASSET_041': 'Não foi possível criar a localização.',
  'ASSET_042': 'Os dados de localização são inválidos.',
  'ASSET_043': 'Uma propriedade não pode ter múltiplas localizações principais.',
  'ASSET_045': 'Contacto da propriedade não encontrado.',
  'ASSET_046': 'Não foi possível adicionar o contacto.',
  'ASSET_047': 'Este contacto já está associado à propriedade.',
  'ASSET_050': 'Não foi possível criar a descrição.',
  'ASSET_051': 'Não foi possível atualizar a descrição.',
  'ASSET_052': 'Descrição não encontrada.',
  'ASSET_055': 'Tag não encontrada.',
  'ASSET_056': 'Não foi possível criar a tag.',
  'ASSET_057': 'Esta tag já está associada à propriedade.',
  'ASSET_058': 'Limite máximo de tags atingido.',
  'ASSET_059': 'Tag inválida: indique o nome ou o ID.',
  'ASSET_060': 'Não foi possível criar a divisão.',
  'ASSET_061': 'Não foi possível atualizar a divisão.',
  'ASSET_062': 'Não foi possível eliminar a divisão.',
  'ASSET_063': 'Tipologia não encontrada.',
  'ASSET_064': 'Divisão não encontrada.',
  'ASSET_065': 'A lista de divisões não pode estar vazia.',
  'ASSET_066': 'Não foi possível guardar as definições.',
  'ASSET_067': 'Não foi possível atualizar as definições.',
  'ASSET_068': 'Definições da propriedade não encontradas.',
  'ASSET_070': 'Esta propriedade está inativa.',
  'ASSET_071': 'Esta propriedade está arquivada.',
  'ASSET_072': 'Não é possível alterar o estado desta propriedade.',
  'ASSET_073': 'Transição de estado não permitida.',
  'ASSET_074': 'O tipo de propriedade está inativo.',
  'ASSET_075': 'Já existe uma tipologia com este identificador.',
  'ASSET_080': 'Referência interna duplicada. Utilize outra referência.',
  'ASSET_081': 'Não é possível eliminar: a propriedade tem dados associados.',
  'ASSET_082': 'Não é possível eliminar: existem angariadores associados.',
  'ASSET_083': 'Não é possível eliminar: existem fotos/vídeos associados.',

  // ── Angariadores ───────────────────────────────────────────────────────────
  'AGENT_001': 'Angariador não encontrado.',
  'AGENT_002': 'Perfil do angariador não encontrado.',
  'AGENT_003': 'Este angariador já está associado à propriedade.',
  'AGENT_004': 'Este angariador não está associado à propriedade.',
  'AGENT_005': 'Função do angariador inválida.',
  'AGENT_006': 'Percentagem de comissão inválida.',
  'AGENT_007': 'Não foi possível associar o angariador.',
  'AGENT_008': 'Não foi possível remover o angariador.',
  'AGENT_009': 'Limite máximo de angariadores atingido.',

  // ── Contactos ──────────────────────────────────────────────────────────────
  'CONTACT_001': 'Contacto não encontrado.',
  'CONTACT_002': 'A lista de contactos está vazia.',
  'CONTACT_003': 'Função de contacto inválida.',
  'CONTACT_004': 'Já existe um contacto principal. Substitua-o antes de continuar.',

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
  'DOC_013': 'Documento da propriedade não encontrado.',
  'DOC_014': 'Já existe um documento deste tipo. Apaga o existente primeiro.',
  'DOC_015': 'Tipo de documento inválido.',

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

  // ── Características ────────────────────────────────────────────────────────
  'CHAR_001': 'Característica não encontrada.',
  'CHAR_002': 'Não foi possível criar a característica.',
  'CHAR_003': 'Não foi possível atualizar a característica.',
  'CHAR_004': 'Não foi possível eliminar a característica.',
  'CHAR_005': 'Tipo de característica inválido.',
  'CHAR_006': 'Valor da característica inválido.',
  'CHAR_007': 'Esta característica já existe.',
  'CHAR_008': 'Propriedade da característica não encontrada.',
  'CHAR_009': 'Não foi possível eliminar as características.',

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
  'FILE_040': 'Formato de PDF inválido.',
  'FILE_041': 'O PDF excede o tamanho máximo permitido.',
  'FILE_042': 'O PDF está corrompido.',
  'FILE_043': 'Erro ao processar o PDF.',

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

  // ── Edifícios ──────────────────────────────────────────────────────────────
  'BUILD_001': 'Edifício não encontrado.',
  'BUILD_002': 'Este edifício já está registado.',
  'BUILD_003': 'Não foi possível criar o edifício.',
  'BUILD_004': 'Não foi possível atualizar o edifício.',
  'BUILD_005': 'Não foi possível eliminar o edifício.',
  'BUILD_010': 'O nome do edifício é inválido.',
  'BUILD_011': 'A morada do edifício é inválida.',
  'BUILD_012': 'Piso inválido para este edifício. O andar indicado está fora do intervalo permitido.',
  'BUILD_013': 'O ano de construção é inválido.',
  'BUILD_020': 'Não é possível eliminar: o edifício tem propriedades associadas.',
  'BUILD_021': 'Empresa do edifício não encontrada.',
  'BUILD_022': 'Localização do edifício não encontrada.',
  'BUILD_030': 'Referência do edifício duplicada.',
  'BUILD_031': 'Capacidade máxima do edifício excedida.',
  'BUILD_032': 'O ID do edifício é obrigatório.',
  'BUILD_033': 'Esta propriedade não está associada a nenhum edifício.',

  // ── Localização ────────────────────────────────────────────────────────────
  'LOC_001': 'Distrito não encontrado.',
  'LOC_002': 'Este distrito já existe.',
  'LOC_003': 'Não foi possível criar o distrito.',
  'LOC_004': 'Não foi possível eliminar o distrito.',
  'LOC_005': 'Não é possível eliminar: o distrito tem concelhos associados.',
  'LOC_010': 'Concelho não encontrado.',
  'LOC_011': 'Este concelho já existe.',
  'LOC_012': 'Não foi possível criar o concelho.',
  'LOC_013': 'Não foi possível eliminar o concelho.',
  'LOC_014': 'Não é possível eliminar: o concelho tem freguesias associadas.',
  'LOC_015': 'Distrito do concelho não encontrado.',
  'LOC_020': 'Freguesia não encontrada.',
  'LOC_021': 'Esta freguesia já existe.',
  'LOC_022': 'Não foi possível criar a freguesia.',
  'LOC_023': 'Não foi possível eliminar a freguesia.',
  'LOC_024': 'Não é possível eliminar: a freguesia tem propriedades associadas.',
  'LOC_025': 'Concelho da freguesia não encontrado.',
  'LOC_030': 'Nome da localização inválido.',
  'LOC_031': 'Código da localização inválido.',
  'LOC_032': 'Erro na hierarquia de localizações.',
  'LOC_034': 'Localização não encontrada.',
  'LOC_035': 'Link de localização corrompido. Contacte o suporte.',
  'LOC_036': 'É necessário definir uma localização principal.',

  // ── Utilizadores ───────────────────────────────────────────────────────────
  'USER_001': 'Utilizador não encontrado.',
  'USER_002': 'Este utilizador já está registado.',
  'USER_003': 'Não foi possível criar o utilizador.',
  'USER_004': 'Não foi possível atualizar o utilizador.',
  'USER_005': 'Não foi possível eliminar o utilizador.',
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
  'USER_030': 'O email indicado é inválido.',
  'USER_031': 'A password não cumpre os requisitos mínimos.',
  'USER_032': 'O nome do utilizador é inválido.',
  'USER_033': 'O telefone indicado é inválido.',
  'USER_034': 'Este email já está registado.',
  'USER_040': 'Esta conta está inativa.',
  'USER_041': 'Esta conta está bloqueada. Contacte o suporte.',
  'USER_042': 'Esta conta foi eliminada.',
  'USER_043': 'A conta ainda não foi verificada. Verifique o seu email.',
  'USER_050': 'Empresa do utilizador não encontrada.',
  'USER_051': 'Não é possível eliminar: o utilizador tem dados associados.',

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

  // ── Transações e Pagamentos ───────────────────────────────────────────────
  'TRANSACTION_001': 'A propriedade já tem uma transação ativa. Não é possível criar uma nova.',
  'TRANSACTION_002': 'O valor da transação deve ser positivo.',
  'TRANSACTION_003': 'O valor da comissão não pode exceder 100%.',
  'TRANSACTION_004': 'Data de conclusão não pode ser anterior à data da proposta.',
  'TRANSACTION_005': 'Não é possível cancelar uma transação já concluída.',
  'TRANSACTION_006': 'Razão de cancelamento é obrigatória se a transação será cancelada.',
  'TRANSACTION_007': 'Transação não encontrada.',
  'TRANSACTION_008': 'Não foi possível criar a transação. Verifique os dados fornecidos.',
  'TRANSACTION_009': 'Não foi possível atualizar a transação.',
  'TRANSACTION_010': 'Não foi possível eliminar a transação.',

  'PAYMENT_001': 'O valor do pagamento deve ser positivo.',
  'PAYMENT_002': 'Este pagamento já foi marcado como pago.',
  'PAYMENT_003': 'Pagamento não encontrado.',
  'PAYMENT_004': 'Data de vencimento não pode ser no passado para pagamentos pendentes.',
  'PAYMENT_005': 'Não foi possível criar o pagamento. Verifique os dados.',
  'PAYMENT_006': 'Não foi possível atualizar o pagamento.',
  'PAYMENT_007': 'Não foi possível eliminar o pagamento.',
  'PAYMENT_008': 'Não foi possível marcar o pagamento como pago.',

  // ── Etapas de construção ─────────────────────────────────────────────────
  'STAGE_001': 'Etapa não encontrada.',
  'STAGE_002': 'Não foi possível criar a etapa.',
  'STAGE_003': 'Não foi possível atualizar a etapa.',
  'STAGE_004': 'Não foi possível eliminar a etapa.',
  'STAGE_005': 'O nome da etapa é inválido.',
  'STAGE_006': 'Empresa da etapa não encontrada.',

  // ── Sub-etapas de construção ──────────────────────────────────────────────
  'SUBSTAGE_001': 'Sub-etapa não encontrada.',
  'SUBSTAGE_002': 'Não foi possível criar a sub-etapa.',
  'SUBSTAGE_003': 'Não foi possível atualizar a sub-etapa.',
  'SUBSTAGE_004': 'Não foi possível eliminar a sub-etapa.',
  'SUBSTAGE_005': 'O nome da sub-etapa é inválido.',
  'SUBSTAGE_006': 'Etapa da sub-etapa não encontrada.',

  // ── Despesas de construção ────────────────────────────────────────────────
  'EXPENSE_001': 'Despesa não encontrada.',
  'EXPENSE_002': 'Não foi possível criar a despesa.',
  'EXPENSE_003': 'Não foi possível atualizar a despesa.',
  'EXPENSE_004': 'Não foi possível eliminar a despesa.',
  'EXPENSE_005': 'O nome da despesa é inválido.',
  'EXPENSE_006': 'O preço da despesa é inválido.',
  'EXPENSE_007': 'Sub-etapa da despesa não encontrada.',
  'EXPENSE_008': 'Não foi possível carregar a fatura da despesa.',

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
