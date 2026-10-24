import axios from 'axios';
import { clearSession, loadSession } from './session';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const session = loadSession();
  if (session) {
    config.headers.Authorization = `Bearer ${session.token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const url = error.config?.url ?? '';
    // A 401 on a normal API call means the token is no longer valid.
    // (A 401 on login/register just means wrong credentials, so we leave it alone.)
    if (status === 401 && !url.startsWith('/api/auth/')) {
      clearSession();
      window.location.assign('/login');
    }
    return Promise.reject(error);
  },
);

export default api;