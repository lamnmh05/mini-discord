export type ApiResponse<T> = {
  success: boolean;
  data: T;
  meta: { nextCursor?: string };
  error?: { code: string; message: string; details: string[]; traceId: string };
};

export type CurrentUser = {
  id: string;
  username: string;
  email: string;
  displayName?: string;
  avatarUrl?: string;
  customStatus?: string;
  accountStatus: 'ACTIVE' | 'LOCKED';
  lastSeenAt?: string;
};

export type Server = {
  id: string;
  name: string;
  iconUrl?: string;
  defaultChannelId: string;
  currentRole: 'OWNER' | 'MEMBER';
};

export type Channel = {
  id: string;
  serverId: string;
  name: string;
  type: 'TEXT';
  position: number;
  defaultChannel: boolean;
};

export type Member = {
  userId: string;
  username: string;
  displayName?: string;
  avatarUrl?: string;
  customStatus?: string;
  presenceStatus: 'ONLINE' | 'OFFLINE';
  role: 'OWNER' | 'MEMBER';
};

export type Message = {
  id: string;
  scope: 'SERVER' | 'DIRECT';
  serverId?: string;
  channelId?: string;
  conversationId?: string;
  senderId: string;
  senderSnapshot: { username: string; displayName?: string; avatarUrl?: string };
  content?: string;
  messageType: 'TEXT' | 'FILE' | 'MIXED';
  attachments: Attachment[];
  reactions: { emoji: string; userIds: string[] }[];
  editedAt?: string;
  createdAt: string;
};

export type Attachment = {
  storageKey: string;
  fileUrl: string;
  originalName: string;
  mimeType: string;
  fileSize: number;
};

export type Notification = {
  id: string;
  type: string;
  title: string;
  body: string;
  serverInviteId?: string;
  isRead: boolean;
  createdAt: string;
};

export type ServerInvite = {
  id: string;
  serverId: string;
  serverName: string;
  inviterUsername: string;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED' | 'CANCELLED';
};

export type InviteCode = {
  id: string;
  serverId: string;
  code: string;
  maxUses?: number | null;
  useCount: number;
  expiresAt?: string | null;
  revokedAt?: string | null;
  createdAt: string;
};

export type WebSocketEvent<T> = {
  version: number;
  eventId: string;
  eventType: string;
  occurredAt: string;
  serverId?: string;
  channelId?: string;
  conversationId?: string;
  data: T;
};

export type FriendUser = {
  userId: string;
  username: string;
  displayName?: string;
  avatarUrl?: string;
  customStatus?: string;
  lastSeenAt?: string;
  presenceStatus: 'ONLINE' | 'OFFLINE';
};

export type Friend = {
  friendshipId: string;
  user: FriendUser;
  friendsSince?: string;
};

export type FriendRequest = {
  id: string;
  user: FriendUser;
  direction: 'INCOMING' | 'OUTGOING';
  requestedAt: string;
};

export type DirectConversation = {
  id: string;
  recipient: FriendUser;
  lastMessagePreview?: string;
  lastMessageAt?: string;
  unreadCount: number;
  createdAt: string;
  updatedAt: string;
};
