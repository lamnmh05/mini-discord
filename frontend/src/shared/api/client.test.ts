import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { useAuthStore } from '../../store/authStore';
import type { ApiResponse, CurrentUser } from '../types';
import { api, refreshToken, unwrap } from './client';

const user: CurrentUser = {
  id: 'user-1',
  username: 'alice',
  email: 'alice@example.com',
  accountStatus: 'ACTIVE'
};

const originalAdapter = api.defaults.adapter;

function axiosResponse<T>(config: InternalAxiosRequestConfig, data: T): AxiosResponse<T> {
  return {
    data,
    status: 200,
    statusText: 'OK',
    headers: {},
    config
  };
}

function axiosError(config: InternalAxiosRequestConfig, status: number, code: string) {
  return {
    isAxiosError: true,
    config,
    response: {
      status,
      data: {
        success: false,
        data: null,
        meta: {},
        error: { code, message: code, details: [], traceId: 'test-trace' }
      }
    },
    toJSON: () => ({})
  };
}

describe('api client', () => {
  beforeEach(() => {
    useAuthStore.getState().clear();
  });

  afterEach(() => {
    api.defaults.adapter = originalAdapter;
    useAuthStore.getState().clear();
  });

  it('unwrap returns data from successful API responses', async () => {
    await expect(
      unwrap(Promise.resolve({ data: { success: true, data: { ok: true }, meta: {} } }))
    ).resolves.toEqual({ ok: true });
  });

  it('unwrap throws the backend error message', async () => {
    const response: ApiResponse<unknown> = {
      success: false,
      data: null,
      meta: {},
      error: { code: 'VALIDATION_ERROR', message: 'Invalid input', details: [], traceId: 'trace-1' }
    };

    await expect(unwrap(Promise.resolve({ data: response }))).rejects.toThrow('Invalid input');
  });

  it('refreshToken stores the new access token and user', async () => {
    api.defaults.adapter = (async (config) =>
      axiosResponse(config, {
        success: true,
        data: { accessToken: 'new-access-token', user },
        meta: {}
      })) as AxiosAdapter;

    await expect(refreshToken()).resolves.toBe('new-access-token');
    expect(useAuthStore.getState().accessToken).toBe('new-access-token');
    expect(useAuthStore.getState().user).toEqual(user);
  });

  it('retries an expired request after refreshing the access token', async () => {
    const seenAuthorization: string[] = [];
    let protectedCalls = 0;

    useAuthStore.getState().setAuth('old-access-token', user);
    api.defaults.adapter = (async (config) => {
      if (config.url === '/auth/refresh') {
        return axiosResponse(config, {
          success: true,
          data: { accessToken: 'new-access-token', user },
          meta: {}
        });
      }

      if (config.url === '/protected') {
        protectedCalls += 1;
        seenAuthorization.push(String(config.headers.Authorization ?? ''));
        if (protectedCalls === 1) {
          throw axiosError(config, 401, 'TOKEN_EXPIRED');
        }
        return axiosResponse(config, {
          success: true,
          data: { ok: true },
          meta: {}
        });
      }

      throw new Error(`Unexpected URL ${config.url}`);
    }) as AxiosAdapter;

    const response = await api.get<ApiResponse<{ ok: boolean }>>('/protected');

    expect(response.data.data.ok).toBe(true);
    expect(seenAuthorization).toEqual(['Bearer old-access-token', 'Bearer new-access-token']);
    expect(useAuthStore.getState().accessToken).toBe('new-access-token');
  });
});
