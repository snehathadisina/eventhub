import { useCallback, useMemo, useState } from 'react';
import api from '../api/client';
import { clearSession, loadSession, saveSession } from '../api/session';
import { AuthContext } from './authContext';

export default function AuthProvider({ children }) {
  const [session, setSession] = useState(() => loadSession());

  const login = useCallback(async (email, password) => {
    const { data } = await api.post('/api/auth/login', { email, password });
    const next = {
      token: data.token,
      user: data.user,
      expiresAt: Date.now() + data.expiresInSeconds * 1000,
    };
    saveSession(next);
    setSession(next);
    return data.user;
  }, []);

  const register = useCallback(
    async ({ name, email, password, role }) => {
      await api.post('/api/auth/register', { name, email, password, role });
      return login(email, password);
    },
    [login],
  );

  const logout = useCallback(() => {
    clearSession();
    setSession(null);
  }, []);

  const value = useMemo(
    () => ({
      user: session?.user ?? null,
      isAuthenticated: session !== null,
      isOrganizer: session?.user?.role === 'ORGANIZER',
      login,
      register,
      logout,
    }),
    [session, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}