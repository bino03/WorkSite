/**
 * Para onde voltar depois do login, guardado pela página de picar (`/picar?c=…`) antes
 * de mandar para `/login`. `sessionStorage` e não estado do router: o fluxo de sessão
 * expirada do `api.ts` faz `window.location.href = '/login'`, que perde o estado.
 */
export const AFTER_LOGIN_KEY = "after_login";
