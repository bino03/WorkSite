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
  const hasRole = (role: string) => userRole === role;

  return {
    userRole,
    userName,
    userId,
    profileId,
    isAdmin,
    isEmployee,
    hasRole,
  };
}