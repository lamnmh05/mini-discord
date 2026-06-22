import type { Message } from '../../shared/types';
import type { MessageTarget } from './types';

export function initialOf(value?: string) {
  return value?.trim().slice(0, 1).toUpperCase() || '?';
}

export function formatTime(value: string) {
  return new Date(value).toLocaleString();
}

export function sortMessages(messages: Message[]) {
  return [...messages].sort((left, right) => new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime());
}

export function upsertMessage(messages: Message[] | undefined, next: Message) {
  const current = messages ?? [];
  const exists = current.some((item) => item.id === next.id);
  return exists ? current.map((item) => (item.id === next.id ? next : item)) : [next, ...current];
}

export function removeMessage(messages: Message[] | undefined, messageId: string) {
  return (messages ?? []).filter((item) => item.id !== messageId);
}

export function messageQueryKey(target: MessageTarget) {
  return target.type === 'direct' ? ['direct-messages', target.id] : ['messages', target.id];
}

export function typingSummary(names: string[]) {
  if (names.length === 0) return '';
  if (names.length === 1) return `${names[0]} is typing...`;
  if (names.length === 2) return `${names[0]} and ${names[1]} are typing...`;
  return `${names[0]}, ${names[1]} and ${names.length - 2} others are typing...`;
}
