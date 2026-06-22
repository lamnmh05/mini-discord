import { describe, expect, it } from 'vitest';
import type { Message } from '../../shared/types';
import { initialOf, messageQueryKey, removeMessage, sortMessages, typingSummary, upsertMessage } from './chatUtils';

function message(id: string, createdAt: string): Message {
  return {
    id,
    scope: 'SERVER',
    serverId: 'server-1',
    channelId: 'channel-1',
    senderId: 'user-1',
    senderSnapshot: { username: 'alice' },
    content: id,
    messageType: 'TEXT',
    attachments: [],
    reactions: [],
    createdAt
  };
}

describe('chatUtils', () => {
  it('formats initials defensively', () => {
    expect(initialOf(' alice ')).toBe('A');
    expect(initialOf('')).toBe('?');
    expect(initialOf(undefined)).toBe('?');
  });

  it('sorts messages from oldest to newest', () => {
    expect(sortMessages([
      message('new', '2026-01-02T00:00:00.000Z'),
      message('old', '2026-01-01T00:00:00.000Z')
    ]).map((item) => item.id)).toEqual(['old', 'new']);
  });

  it('upserts and removes messages without mutating the original array', () => {
    const original = [message('one', '2026-01-01T00:00:00.000Z')];
    const updated = upsertMessage(original, { ...original[0], content: 'edited' });
    const appended = upsertMessage(updated, message('two', '2026-01-02T00:00:00.000Z'));

    expect(original[0].content).toBe('one');
    expect(updated).toHaveLength(1);
    expect(updated[0].content).toBe('edited');
    expect(appended.map((item) => item.id)).toEqual(['two', 'one']);
    expect(removeMessage(appended, 'one').map((item) => item.id)).toEqual(['two']);
  });

  it('uses distinct query keys for server and direct targets', () => {
    expect(messageQueryKey({ type: 'server', id: 'channel-1' })).toEqual(['messages', 'channel-1']);
    expect(messageQueryKey({ type: 'direct', id: 'dm-1' })).toEqual(['direct-messages', 'dm-1']);
  });

  it('summarizes typing users', () => {
    expect(typingSummary([])).toBe('');
    expect(typingSummary(['Alice'])).toBe('Alice is typing...');
    expect(typingSummary(['Alice', 'Bob'])).toBe('Alice and Bob are typing...');
    expect(typingSummary(['Alice', 'Bob', 'Carol'])).toBe('Alice, Bob and 1 others are typing...');
  });
});
