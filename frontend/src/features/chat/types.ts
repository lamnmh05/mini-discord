import type { Attachment } from '../../shared/types';

export type ActiveView = 'home' | 'server' | 'direct';
export type FriendTab = 'all' | 'online' | 'pending' | 'add';
export type MessageTarget = { type: 'server'; id: string } | { type: 'direct'; id: string };
export type SendMessageVariables = {
  target: MessageTarget;
  content: string;
  clientRequestId: string;
  attachments: Attachment[];
};
export type TypingPayload = { userId: string; username: string; typing: boolean };
export type TypingUser = { userId: string; username: string };
