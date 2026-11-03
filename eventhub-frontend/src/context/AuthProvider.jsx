import { useCallback, useMemo, useState } from 'react';
import { googleLogout } from '@react-oauth/google';
import api from '../api/client';
import { clearSession, loadSession, saveSession } from '../api/session';
import { AuthContext } from './authContext';

export default function AuthProvider({ children }) {
  const [session, setSession] = useState(() => loadSession());

  const startSession = useCallback((data) => {
    const next = {
      token: data.token,
      user: data.user,
      expiresAt: Date.now() + data.expiresInSeconds * 1000,
    };
    saveSession(next);
    setSession(next);
    return data.user;
  }, []);

  const login = useCallback(
    async (email, password) => {
      const { data } = await api.post('/api/auth/login', { email, password });
      return startSession(data);
    },
    [startSession],
  );

  const loginWithGoogle = useCallback(
    async (idToken) => {
      const { data } = await api.post('/api/auth/google', { idToken });
      return startSession(data);
    },
    [startSession],
  );

  const register = useCallback(
    async ({ name, email, password, role }) => {
      await api.post('/api/auth/register', { name, email, password, role });
      return login(email, password);
    },
    [login],
  );

  const logout = useCallback(() => {
    googleLogout(); // stops Google from silently signing the same person back in
    clearSession();
    setSession(null);
  }, []);

  const value = useMemo(
    () => ({
      user: session?.user ?? null,
      isAuthenticated: session !== null,
      isOrganizer: session?.user?.role === 'ORGANIZER',
      login,
      loginWithGoogle,
      register,
      logout,
    }),
    [session, login, loginWithGoogle, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}