import { beforeEach, describe, expect, it } from 'vitest';
import { useAuthStore } from './authStore';
import type { CurrentUser } from '../shared/types';

const user: CurrentUser = {
  id: 'user-1',
  username: 'alice',
  email: 'alice@example.com',
  accountStatus: 'ACTIVE'
};

describe('authStore', () => {
  beforeEach(() => {
    useAuthStore.getState().clear();
  });

  it('stores and clears the authenticated user', () => {
    useAuthStore.getState().setAuth('access-token', user);

    expect(useAuthStore.getState().accessToken).toBe('access-token');
    expect(useAuthStore.getState().user).toEqual(user);

    useAuthStore.getState().clear();

    expect(useAuthStore.getState().accessToken).toBeUndefined();
    expect(useAuthStore.getState().user).toBeUndefined();
  });
});
