import { createContext, useContext, useState, useEffect, ReactNode, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import * as authService from '@/services/authService';

export type UserInfo = authService.UserInfo;

interface AuthContextType {
  user: UserInfo | null;
  login: (email: string, password: string) => Promise<UserInfo>;
  logout: () => Promise<void>;
  /**
   * Recupera a sessão a partir dos cookies quando o `sessionStorage` vem vazio — é o
   * caso de um separador novo aberto pela câmara ao ler o QR da obra. Sem isto o
   * funcionário fazia login a cada picagem, com os cookies ainda válidos.
   */
  restoreSession: () => Promise<UserInfo | null>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const navigate = useNavigate();
  // Load user synchronously from sessionStorage — no async needed
  const [user, setUser] = useState<UserInfo | null>(() => authService.loadUser());

  // On mount: refresh user info from /auth/me.
  useEffect(() => {
    if (!user) return;
    authService.fetchMe().then(setUser).catch(() => {});
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const login = useCallback(async (email: string, password: string): Promise<UserInfo> => {
    const userInfo = await authService.login(email, password);
    setUser(userInfo);
    return userInfo;
  }, []);

  const logout = useCallback(async () => {
    setUser(null);
    await authService.logout();
    navigate('/login');
  }, [navigate]);

  const restoreSession = useCallback(async (): Promise<UserInfo | null> => {
    try {
      const userInfo = await authService.fetchMe();
      setUser(userInfo);
      return userInfo;
    } catch {
      return null;
    }
  }, []);

  return (
    <AuthContext.Provider value={{ user, login, logout, restoreSession }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuthContext = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuthContext must be used within AuthProvider');
  }
  return context;
};
