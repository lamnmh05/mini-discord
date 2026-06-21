import { Client } from '@stomp/stompjs';
import { useAuthStore } from '../../store/authStore';

let client: Client | undefined;

export function stompClient() {
  const token = useAuthStore.getState().accessToken;
  if (!client || !client.active) {
    client = new Client({
      brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`,
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 2500,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000
    });
    client.activate();
  }
  return client;
}

export function disconnectStomp() {
  client?.deactivate();
  client = undefined;
}

export function currentStompClient() {
  return client;
}
