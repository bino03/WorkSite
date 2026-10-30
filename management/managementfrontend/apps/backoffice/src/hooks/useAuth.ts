import { useAuthContext } from '@/context/AuthContext';

/**
 * Hook to get current user authentication info.
 * Uses AuthContext (React Context) instead of localStorage.
 * Cookies are handled automatically by the browser.
 */
export function useAuth() {
  const { user } = useAuthContext();

  const userRole = user?.role ?? null;
  const userName = user?.name ?? null;
  const userId = user?.authUserId ?? null;
  const profileId = user?.profileId || null;

  // Helper functions to check permissions
  const isAdmin = () => userRole === 'ADMIN';
  const isEmployee = () => userRole === 'EMPLOYEE';
  const isAccountant = () => userRole === 'ACCOUNTANT';
  /**
   * No espaço Obras a contabilidade faz o que o ADMIN faz (os `@PreAuthorize` de
   * `enterprises/controller` aceitam as duas). `isAdmin()` continua a guardar o resto:
   * Equipa, Definições, contas/convites e gerir tarefas.
   */
  const canManageWorks = () => isAdmin() || isAccountant();
  const hasRole = (role: string) => userRole === role;

  return {
    userRole,
    userName,
    userId,
    profileId,
    isAdmin,
    isEmployee,
    isAccountant,
    canManageWorks,
    hasRole,
  };
}