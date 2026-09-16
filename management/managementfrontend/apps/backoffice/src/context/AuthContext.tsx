import { createContext, useContext, useState, useEffect, ReactNode, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import * as authService from '@/services/authService';

export type UserInfo = authService.UserInfo;

interface AuthContextType {
  user: UserInfo | null;
  login: (email: string, password: string) => Promise<UserInfo>;
  logout: () => Promise<void>;
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

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
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
