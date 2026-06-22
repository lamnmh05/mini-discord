import axios from 'axios';
import { useAuthStore } from '../../store/authStore';
import type { ApiResponse, CurrentUser } from '../types';

export const api = axios.create({
  baseURL: '/api/v1',
  withCredentials: true
});

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let refreshPromise: Promise<string | undefined> | undefined;

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;
    const code = error.response?.data?.error?.code;
    const publicAuthRequest =
      original?.url?.includes('/auth/refresh') ||
      original?.url?.includes('/auth/forgot-password') ||
      original?.url?.includes('/auth/reset-password');
    if ((code === 'TOKEN_EXPIRED' || error.response?.status === 401) && !original?._retry && !publicAuthRequest) {
      original._retry = true;
      refreshPromise ??= refreshToken().finally(() => {
        refreshPromise = undefined;
      });
      const token = await refreshPromise;
      if (token) {
        original.headers.Authorization = `Bearer ${token}`;
        return api(original);
      }
    }
    throw error;
  }
);

export async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const response = await promise;
  if (!response.data.success) {
    throw new Error(response.data.error?.message ?? 'Request failed');
  }
  return response.data.data;
}

export async function refreshToken() {
  try {
    const response = await api.post<ApiResponse<{ accessToken: string; user: CurrentUser }>>('/auth/refresh');
    const auth = response.data.data;
    useAuthStore.getState().setAuth(auth.accessToken, auth.user);
    return auth.accessToken;
  } catch {
    useAuthStore.getState().clear();
    return undefined;
  }
}
