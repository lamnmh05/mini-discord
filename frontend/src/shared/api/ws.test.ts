// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from '../../store/authStore';
import { currentStompClient, disconnectStomp, stompClient } from './ws';

const stompMocks = vi.hoisted(() => {
  const instances: any[] = [];
  const Client = vi.fn((config) => {
    const instance = {
      active: false,
      connected: false,
      config,
      activate: vi.fn(() => {
        instance.active = true;
      }),
      deactivate: vi.fn(() => {
        instance.active = false;
        return Promise.resolve();
      })
    };
    instances.push(instance);
    return instance;
  });
  return { Client, instances };
});

vi.mock('@stomp/stompjs', () => ({
  Client: stompMocks.Client
}));

const user = {
  id: 'user-1',
  username: 'alice',
  email: 'alice@example.com',
  accountStatus: 'ACTIVE' as const
};

describe('stomp api client', () => {
  beforeEach(() => {
    vi.stubGlobal('location', { protocol: 'https:', host: 'chat.example.test' });
    useAuthStore.getState().setAuth('access-token', user);
    stompMocks.Client.mockClear();
    stompMocks.instances.length = 0;
  });

  afterEach(() => {
    disconnectStomp();
    useAuthStore.getState().clear();
    vi.unstubAllGlobals();
  });

  it('creates an authenticated websocket client for the current origin', () => {
    const client = stompClient();

    expect(stompMocks.Client).toHaveBeenCalledWith({
      brokerURL: 'wss://chat.example.test/ws',
      connectHeaders: { Authorization: 'Bearer access-token' },
      reconnectDelay: 2500,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000
    });
    expect(client.activate).toHaveBeenCalledTimes(1);
    expect(currentStompClient()).toBe(client);
  });

  it('reuses an active client and disconnects it explicitly', () => {
    const first = stompClient();
    const second = stompClient();

    expect(second).toBe(first);
    expect(stompMocks.Client).toHaveBeenCalledTimes(1);

    disconnectStomp();

    expect(first.deactivate).toHaveBeenCalledTimes(1);
    expect(currentStompClient()).toBeUndefined();
  });
});
