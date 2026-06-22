// @vitest-environment jsdom

import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from '../../store/authStore';
import type { CurrentUser } from '../../shared/types';
import { AuthView } from './AuthView';

const apiMocks = vi.hoisted(() => ({
  post: vi.fn()
}));

vi.mock('../../shared/api/client', () => ({
  api: {
    post: apiMocks.post
  }
}));

const user: CurrentUser = {
  id: 'user-1',
  username: 'alice',
  email: 'alice@example.com',
  accountStatus: 'ACTIVE'
};

describe('AuthView', () => {
  beforeEach(() => {
    useAuthStore.getState().clear();
    apiMocks.post.mockReset();
  });

  afterEach(() => {
    cleanup();
    useAuthStore.getState().clear();
  });

  it('registers a new user and logs in with the returned access token', async () => {
    apiMocks.post.mockImplementation((url: string) => {
      if (url === '/auth/register') {
        return Promise.resolve({ data: { success: true, data: { message: 'ok' }, meta: {} } });
      }
      if (url === '/auth/login') {
        return Promise.resolve({
          data: {
            success: true,
            data: { accessToken: 'access-token', user },
            meta: {}
          }
        });
      }
      return Promise.reject(new Error(`Unexpected URL ${url}`));
    });

    render(<AuthView />);

    await userEvent.click(screen.getByRole('button', { name: 'Register' }));
    await userEvent.type(screen.getByLabelText('Username'), 'alice');
    await userEvent.type(screen.getByLabelText('Email'), 'alice@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'Password123!');

    const submitButtons = screen.getAllByRole('button', { name: 'Register' });
    await userEvent.click(submitButtons[submitButtons.length - 1]);

    await waitFor(() => {
      expect(useAuthStore.getState().accessToken).toBe('access-token');
    });

    expect(apiMocks.post).toHaveBeenNthCalledWith(1, '/auth/register', {
      username: 'alice',
      email: 'alice@example.com',
      password: 'Password123!'
    });
    expect(apiMocks.post).toHaveBeenNthCalledWith(2, '/auth/login', {
      email: 'alice@example.com',
      password: 'Password123!'
    });
    expect(useAuthStore.getState().user).toEqual(user);
  });

  it('opens forgot password form from the small link under password', async () => {
    apiMocks.post.mockResolvedValue({
      data: {
        success: true,
        data: { message: 'If the email exists, reset instructions were sent.' },
        meta: {}
      }
    });

    render(<AuthView />);

    await userEvent.click(screen.getByRole('button', { name: 'Forgot password?' }));
    await userEvent.type(screen.getByLabelText('Email'), 'alice@example.com');
    await userEvent.click(screen.getByRole('button', { name: 'Send reset link' }));

    await waitFor(() => {
      expect(apiMocks.post).toHaveBeenCalledWith('/auth/forgot-password', {
        email: 'alice@example.com'
      });
    });
    expect(screen.getByText('If the email exists, reset instructions were sent.')).toBeTruthy();
  });
});
