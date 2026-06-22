import { FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Bell,
  Check,
  ChevronDown,
  Clipboard,
  Hash,
  Home,
  LogOut,
  MessageCircle,
  Pencil,
  Plus,
  Search,
  Send,
  Smile,
  Settings,
  Trash2,
  UserPlus,
  UserMinus,
  Users,
  X
} from 'lucide-react';
import { api, unwrap } from '../../shared/api/client';
import { currentStompClient, disconnectStomp, stompClient } from '../../shared/api/ws';
import { queryClient } from '../../app/queryClient';
import { useAuthStore } from '../../store/authStore';
import type {
  ApiResponse,
  Attachment,
  Channel,
  CurrentUser,
  DirectConversation,
  Friend,
  FriendRequest,
  FriendUser,
  InviteCode,
  Member,
  Message,
  Notification,
  Server,
  ServerInvite,
  WebSocketEvent
} from '../../shared/types';

type ModalMode = 'profile' | 'create-server' | 'join-server' | 'edit-server'| 'create-channel' | 'invite-user' | 'invite-code' | 'edit-channel' | null;

type ActiveView = 'home' | 'server' | 'direct';
type FriendTab = 'all' | 'online' | 'pending' | 'add';
type MessageTarget = { type: 'server'; id: string } | { type: 'direct'; id: string };
type SendMessageVariables = { target: MessageTarget; content: string; clientRequestId: string; attachments: Attachment[] };

type TypingPayload = { userId: string; username: string; typing: boolean };
type TypingUser = { userId: string; username: string };

function initialOf(value?: string) {
  return value?.trim().slice(0, 1).toUpperCase() || '?';
}

function formatTime(value: string) {
  return new Date(value).toLocaleString();
}

function messageTime(message: Message) {
  return new Date(message.createdAt).getTime();
}

function sortMessages(messages: Message[]) {
  return [...messages].sort((left, right) => messageTime(left) - messageTime(right));
}

function upsertMessage(messages: Message[] | undefined, next: Message) {
  const current = messages ?? [];
  const exists = current.some((item) => item.id === next.id);
  return exists ? current.map((item) => (item.id === next.id ? next : item)) : [next, ...current];
}

function messageQueryKey(target: MessageTarget) {
  return target.type === 'direct' ? ['direct-messages', target.id] : ['messages', target.id];
}

function removeMessage(messages: Message[] | undefined, messageId: string) {
  return (messages ?? []).filter((item) => item.id !== messageId);
}

function typingSummary(names: string[]) {
  if (names.length === 0) return '';
  if (names.length === 1) return `${names[0]} is typing...`;
  if (names.length === 2) return `${names[0]} and ${names[1]} are typing...`;
  return `${names[0]}, ${names[1]} and ${names.length - 2} others are typing...`;
}

export function ChatShell() {
  const auth = useAuthStore();
  const messageListRef = useRef<HTMLDivElement>(null);
  const avatarInputRef = useRef<HTMLInputElement>(null);
  const isEditingRef = useRef(false);
  const typingStartedRef = useRef(false);
  const typingStopTimerRef = useRef<number | undefined>(undefined);
  const typingUserTimersRef = useRef<Record<string, number>>({});
  const typingTargetRef = useRef<MessageTarget | undefined>(undefined);
  const [activeView, setActiveView] = useState<ActiveView>('home');
  const [serverId, setServerId] = useState<string>();
  const [channelId, setChannelId] = useState<string>();
  const [directConversationId, setDirectConversationId] = useState<string>();
  const [friendTab, setFriendTab] = useState<FriendTab>('all');
  const [friendUsername, setFriendUsername] = useState('');
  const [friendError, setFriendError] = useState('');
  const [message, setMessage] = useState('');
  const [editingMessageId, setEditingMessageId] = useState<string | null>(null);
  const [reactionMenuId, setReactionMenuId] = useState<string | null>(null);
  const [editContent, setEditContent] = useState('');
  const [messageSearch, setMessageSearch] = useState('');
  const [serverName, setServerName] = useState('');
  const [channelName, setChannelName] = useState('');
  const [joinCode, setJoinCode] = useState('');
  const [joinError, setJoinError] = useState('');
  const [inviteeUsername, setInviteeUsername] = useState('');
  const [inviteMaxUses, setInviteMaxUses] = useState('');
  const [profileUsername, setProfileUsername] = useState('');
  const [profileDisplayName, setProfileDisplayName] = useState('');
  const [profileCustomStatus, setProfileCustomStatus] = useState('');
  const [profileAvatarUrl, setProfileAvatarUrl] = useState('');
  const [profileError, setProfileError] = useState('');
  const [avatarUploadError, setAvatarUploadError] = useState('');
  const [modal, setModal] = useState<ModalMode>(null);
  const [selectedChannel, setSelectedChannel] = useState<Channel>();
  const [serverMenuOpen, setServerMenuOpen] = useState(false);
  const [notificationOpen, setNotificationOpen] = useState(false);
  const [typingUsers, setTypingUsers] = useState<Record<string, TypingUser>>({});

  // edit server
  const [serverIconUrl, setServerIconUrl] = useState('');
  const [serverEditError, setServerEditError] = useState('');
  const [serverIconUploadError, setServerIconUploadError] = useState('');
  const serverIconInputRef = useRef<HTMLInputElement>(null);

  const [chatAttachments, setChatAttachments] = useState<Attachment[]>([]);
  const chatFileInputRef = useRef<HTMLInputElement>(null);


  const servers = useQuery({
    queryKey: ['servers'],
    queryFn: () => unwrap(api.get<ApiResponse<Server[]>>('/servers'))
  });

  const friends = useQuery({
    queryKey: ['friends'],
    queryFn: () => unwrap(api.get<ApiResponse<Friend[]>>('/friends')),
    refetchInterval: 15000
  });

  const friendRequests = useQuery({
    queryKey: ['friend-requests'],
    queryFn: () => unwrap(api.get<ApiResponse<FriendRequest[]>>('/friends/requests')),
    refetchInterval: 5000
  });

  const directConversations = useQuery({
    queryKey: ['direct-conversations'],
    queryFn: () => unwrap(api.get<ApiResponse<DirectConversation[]>>('/direct-conversations')),
    refetchInterval: 5000
  });

  useEffect(() => {
    if (!serverId && servers.data?.length) {
      setServerId(servers.data[0].id);
      setChannelId(servers.data[0].defaultChannelId);
    }
  }, [servers.data, serverId]);

  useEffect(() => {
    setMessageSearch('');
  }, [channelId]);

  const channels = useQuery({
    queryKey: ['channels', serverId],
    queryFn: () => unwrap(api.get<ApiResponse<Channel[]>>(`/servers/${serverId}/channels`)),
    enabled: Boolean(serverId)
  });

  const members = useQuery({
    queryKey: ['members', serverId],
    queryFn: () => unwrap(api.get<ApiResponse<Member[]>>(`/servers/${serverId}/members`)),
    enabled: Boolean(serverId)
  });

  const messages = useQuery({
    queryKey: ['messages', channelId],
    queryFn: () => unwrap(api.get<ApiResponse<Message[]>>(`/channels/${channelId}/messages`)),
    enabled: Boolean(channelId)
  });

  const directMessages = useQuery({
    queryKey: ['direct-messages', directConversationId],
    queryFn: () => unwrap(api.get<ApiResponse<Message[]>>(`/direct-conversations/${directConversationId}/messages`)),
    enabled: Boolean(activeView === 'direct' && directConversationId)
  });

  const notifications = useQuery({
    queryKey: ['notifications'],
    queryFn: () => unwrap(api.get<ApiResponse<Notification[]>>('/notifications?isRead=false'))
  });

  const receivedInvites = useQuery({
    queryKey: ['received-invites'],
    queryFn: () => unwrap(api.get<ApiResponse<ServerInvite[]>>('/server-invites/received'))
  });

  const currentProfile = useQuery({
    queryKey: ['profile'],
    queryFn: () => unwrap(api.get<ApiResponse<CurrentUser>>('/users/me')),
    enabled: modal === 'profile'
  });

  const inviteCodes = useQuery({
    queryKey: ['invite-codes', serverId],
    queryFn: () => unwrap(api.get<ApiResponse<InviteCode[]>>(`/servers/${serverId}/invite-codes`)),
    enabled: Boolean(serverId && modal === 'invite-code')
  });

  const activeServer = useMemo(() => servers.data?.find((server) => server.id === serverId), [serverId, servers.data]);
  const activeChannel = useMemo(() => channels.data?.find((channel) => channel.id === channelId), [channelId, channels.data]);
  const activeDirectConversation = useMemo(
    () => directConversations.data?.find((conversation) => conversation.id === directConversationId),
    [directConversationId, directConversations.data]
  );
  const activeMessageTarget = useMemo<MessageTarget | undefined>(() => {
    if (activeView === 'direct' && directConversationId) return { type: 'direct', id: directConversationId };
    if (activeView === 'server' && channelId) return { type: 'server', id: channelId };
    return undefined;
  }, [activeView, channelId, directConversationId]);
  const activeMessageCacheKey = activeMessageTarget ? messageQueryKey(activeMessageTarget) : undefined;
  const searchEnabled = Boolean(activeView === 'server' && serverId && messageSearch.trim().length >= 2);

  const searchMessages = useQuery({
    queryKey: ['message-search', serverId, channelId, messageSearch],
    queryFn: () =>
        unwrap(
            api.get<ApiResponse<Message[]>>(`/servers/${serverId}/messages/search`, {
              params: { q: messageSearch.trim(), channelId }
            })
        ),
    enabled: searchEnabled
  });

  const visibleMessages = activeView === 'direct'
    ? directMessages.data ?? []
    : searchEnabled ? searchMessages.data ?? [] : messages.data ?? [];
  const renderedMessages = useMemo(() => sortMessages(visibleMessages), [visibleMessages]);
  const messageLoading = activeView === 'direct' ? directMessages.isLoading : messages.isLoading;
  const messageError = activeView === 'direct' ? directMessages.isError : messages.isError;
  const lastRenderedMessageId = renderedMessages[renderedMessages.length - 1]?.id;
  const onlineMembers = useMemo(() => members.data?.filter((member) => member.presenceStatus === 'ONLINE') ?? [], [members.data]);
  const offlineMembers = useMemo(() => members.data?.filter((member) => member.presenceStatus !== 'ONLINE') ?? [], [members.data]);
  const incomingFriendRequests = useMemo(
    () => (friendRequests.data ?? []).filter((request) => request.direction === 'INCOMING'),
    [friendRequests.data]
  );
  const outgoingFriendRequests = useMemo(
    () => (friendRequests.data ?? []).filter((request) => request.direction === 'OUTGOING'),
    [friendRequests.data]
  );
  const visibleFriends = useMemo(
    () => (friends.data ?? []).filter((friend) => friendTab !== 'online' || friend.user.presenceStatus === 'ONLINE'),
    [friendTab, friends.data]
  );
  const visibleNotifications = useMemo(
    () => (notifications.data ?? []).filter((notification) => notification.type !== 'SERVER_INVITE' && !notification.serverInviteId),
    [notifications.data]
  );
  const unreadInviteNotificationIds = useMemo(
    () =>
      new Set(
        (notifications.data ?? [])
          .filter((notification) => notification.type === 'SERVER_INVITE' && notification.serverInviteId)
          .map((notification) => notification.serverInviteId)
      ),
    [notifications.data]
  );
  const unreadInviteCount = useMemo(
    () => (receivedInvites.data ?? []).filter((invite) => unreadInviteNotificationIds.has(invite.id)).length,
    [receivedInvites.data, unreadInviteNotificationIds]
  );
  const unreadNotificationCount = notifications.data?.length ?? 0;
  const notificationCount = visibleNotifications.length + unreadInviteCount;
  const typingText = useMemo(() => {
    const names = Object.values(typingUsers).map((typingUser) => {
      const member = members.data?.find((item) => item.userId === typingUser.userId);
      const friend = friends.data?.find((item) => item.user.userId === typingUser.userId);
      const directRecipient = activeDirectConversation?.recipient.userId === typingUser.userId ? activeDirectConversation.recipient : undefined;
      return member?.displayName ?? member?.username ?? friend?.user.displayName ?? friend?.user.username
        ?? directRecipient?.displayName ?? directRecipient?.username ?? typingUser.username;
    });
    return typingSummary(names);
  }, [activeDirectConversation, friends.data, members.data, typingUsers]);

  useEffect(() => {
    if (searchEnabled) return;
    const list = messageListRef.current;
    if (!list) return;
    const frame = window.requestAnimationFrame(() => {
      list.scrollTop = list.scrollHeight;
    });
    return () => window.cancelAnimationFrame(frame);
  }, [activeView, channelId, directConversationId, lastRenderedMessageId, renderedMessages.length, searchEnabled]);

  useEffect(() => {
    if (activeView !== 'direct' || !directConversationId || directMessages.isLoading) return;
    api.patch(`/direct-conversations/${directConversationId}/read`).then(() => {
      queryClient.invalidateQueries({ queryKey: ['direct-conversations'] });
    }).catch(() => undefined);
  }, [activeView, directConversationId, directMessages.isLoading, lastRenderedMessageId]);

  useEffect(() => {
    if (activeView !== 'server' || !channelId || !serverId) return;
    const client = stompClient();
    const disposers: Array<() => void> = [];
    const subscribe = () => {
      disposers.push(
          client.subscribe(`/topic/channels/${channelId}/messages`, (frame) => {
            const event = JSON.parse(frame.body) as WebSocketEvent<Message | { messageId: string }>;
            const eventChannelId = event.channelId ?? channelId;
            if (event.eventType === 'MESSAGE_CREATED') {
              queryClient.setQueryData<Message[]>(['messages', eventChannelId], (old) => upsertMessage(old, event.data as Message));
            } else if (event.eventType === 'MESSAGE_UPDATED' || event.eventType === 'REACTION_UPDATED') {
              queryClient.setQueryData<Message[]>(['messages', eventChannelId], (old) => upsertMessage(old, event.data as Message));
            } else if (event.eventType === 'MESSAGE_DELETED') {
              queryClient.setQueryData<Message[]>(['messages', eventChannelId], (old) => removeMessage(old, (event.data as { messageId: string }).messageId));
            } else {
              queryClient.invalidateQueries({ queryKey: ['messages', eventChannelId] });
            }
          }).unsubscribe
      );
      disposers.push(
          client.subscribe(`/topic/channels/${channelId}/typing`, (frame) => {
            const event = JSON.parse(frame.body) as WebSocketEvent<TypingPayload>;
            const typing = event.data;
            if (!typing?.userId || typing.userId === auth.user?.id) return;

            window.clearTimeout(typingUserTimersRef.current[typing.userId]);

            if (typing.typing) {
              setTypingUsers((current) => ({
                ...current,
                [typing.userId]: { userId: typing.userId, username: typing.username }
              }));
              typingUserTimersRef.current[typing.userId] = window.setTimeout(() => {
                setTypingUsers((current) => {
                  const next = { ...current };
                  delete next[typing.userId];
                  return next;
                });
                delete typingUserTimersRef.current[typing.userId];
              }, 5000);
            } else {
              setTypingUsers((current) => {
                const next = { ...current };
                delete next[typing.userId];
                return next;
              });
              delete typingUserTimersRef.current[typing.userId];
            }
          }).unsubscribe
      );
      disposers.push(
          client.subscribe(`/topic/servers/${serverId}/presence`, () => {
            queryClient.invalidateQueries({ queryKey: ['members', serverId] });
          }).unsubscribe
      );
      disposers.push(
          client.subscribe('/user/queue/notifications', () => {
            queryClient.invalidateQueries({ queryKey: ['notifications'] });
            queryClient.invalidateQueries({ queryKey: ['received-invites'] });
          }).unsubscribe
      );
      if (typingStartedRef.current) {
        publishTyping(true, { type: 'server', id: channelId }, false);
      }
    };
    if (client.connected) {
      subscribe();
    } else {
      client.onConnect = subscribe;
    }
    return () => {
      disposers.forEach((dispose) => dispose());
      Object.values(typingUserTimersRef.current).forEach((timer) => window.clearTimeout(timer));
      typingUserTimersRef.current = {};
      setTypingUsers({});
    };
  }, [activeView, channelId, serverId, auth.user?.id]);

  useEffect(() => {
    if (activeView !== 'direct' || !directConversationId) return;
    const client = stompClient();
    const disposers: Array<() => void> = [];
    const subscribe = () => {
      disposers.push(
          client.subscribe(`/topic/direct-conversations/${directConversationId}/messages`, (frame) => {
            const event = JSON.parse(frame.body) as WebSocketEvent<Message | { messageId: string }>;
            const eventConversationId = event.conversationId ?? directConversationId;
            if (event.eventType === 'DIRECT_MESSAGE_CREATED') {
              queryClient.setQueryData<Message[]>(['direct-messages', eventConversationId], (old) => upsertMessage(old, event.data as Message));
              queryClient.invalidateQueries({ queryKey: ['direct-conversations'] });
            } else if (event.eventType === 'DIRECT_MESSAGE_UPDATED' || event.eventType === 'DIRECT_REACTION_UPDATED') {
              queryClient.setQueryData<Message[]>(['direct-messages', eventConversationId], (old) => upsertMessage(old, event.data as Message));
              queryClient.invalidateQueries({ queryKey: ['direct-conversations'] });
            } else if (event.eventType === 'DIRECT_MESSAGE_DELETED') {
              queryClient.setQueryData<Message[]>(['direct-messages', eventConversationId], (old) => removeMessage(old, (event.data as { messageId: string }).messageId));
              queryClient.invalidateQueries({ queryKey: ['direct-conversations'] });
            } else {
              queryClient.invalidateQueries({ queryKey: ['direct-messages', eventConversationId] });
              queryClient.invalidateQueries({ queryKey: ['direct-conversations'] });
            }
          }).unsubscribe
      );
      disposers.push(
          client.subscribe(`/topic/direct-conversations/${directConversationId}/typing`, (frame) => {
            const event = JSON.parse(frame.body) as WebSocketEvent<TypingPayload>;
            const typing = event.data;
            if (!typing?.userId || typing.userId === auth.user?.id) return;

            window.clearTimeout(typingUserTimersRef.current[typing.userId]);

            if (typing.typing) {
              setTypingUsers((current) => ({
                ...current,
                [typing.userId]: { userId: typing.userId, username: typing.username }
              }));
              typingUserTimersRef.current[typing.userId] = window.setTimeout(() => {
                setTypingUsers((current) => {
                  const next = { ...current };
                  delete next[typing.userId];
                  return next;
                });
                delete typingUserTimersRef.current[typing.userId];
              }, 5000);
            } else {
              setTypingUsers((current) => {
                const next = { ...current };
                delete next[typing.userId];
                return next;
              });
              delete typingUserTimersRef.current[typing.userId];
            }
          }).unsubscribe
      );
      if (typingStartedRef.current) {
        publishTyping(true, { type: 'direct', id: directConversationId }, false);
      }
    };
    if (client.connected) {
      subscribe();
    } else {
      client.onConnect = subscribe;
    }
    return () => {
      disposers.forEach((dispose) => dispose());
      Object.values(typingUserTimersRef.current).forEach((timer) => window.clearTimeout(timer));
      typingUserTimersRef.current = {};
      setTypingUsers({});
    };
  }, [activeView, directConversationId, auth.user?.id]);

  useEffect(() => {
    setTypingUsers({});
    return () => stopTyping();
  }, [activeView, channelId, directConversationId]);

  const createServer = useMutation({
    mutationFn: () => unwrap(api.post<ApiResponse<Server>>('/servers', { name: serverName.trim() })),
    onSuccess: (server) => {
      setServerName('');
      setModal(null);
      queryClient.invalidateQueries({ queryKey: ['servers'] });
      setServerId(server.id);
      setChannelId(server.defaultChannelId);
      setActiveView('server');
    }
  });

  const leaveServer = useMutation({
    mutationFn: () => unwrap(api.post<ApiResponse<{ message: string }>>(`/servers/${serverId}/leave`)),
    onSuccess: () => {
      setServerId(undefined);
      setChannelId(undefined);
      setActiveView('home');
      setServerMenuOpen(false);
      queryClient.invalidateQueries({ queryKey: ['servers'] });
    },
    onError: (error: any) => {
      alert(error.response?.data?.error?.message ?? 'Không thể rời server.');
    }
  });
// DELETE SERVER (UC12) ===
  const deleteServer = useMutation({
    mutationFn: () => unwrap(api.delete<ApiResponse<{ message: string }>>(`/servers/${serverId}`)),
    onSuccess: () => {
      setServerId(undefined);
      setChannelId(undefined);
      setActiveView('home');
      setServerMenuOpen(false);
      queryClient.invalidateQueries({ queryKey: ['servers'] });
    },
    onError: (error: any) => {
      alert(error.response?.data?.error?.message ?? 'Không thể xóa server.');
    }
  });

  // Mutation đảm nhận cập nhật tên & icon server (UC11 Normal Flow)
  const updateServer = useMutation({
    mutationFn: () =>
      unwrap(
        api.patch<ApiResponse<Server>>(`/servers/${serverId}`, {
          name: serverName.trim(),
          iconUrl: serverIconUrl.trim() || null
        })
      ),
    onMutate: () => setServerEditError(''),
    onSuccess: () => {
      setModal(null);
      queryClient.invalidateQueries({ queryKey: ['servers'] }); // Làm mới sidebar hiển thị ngay tên mới (POST-1)
    },
    onError: (error: any) => {
      setServerEditError(error.response?.data?.error?.message ?? 'Không thể cập nhật cấu hình server.');
    }
  });

  // Mutation hỗ trợ upload file làm server icon từ máy tính
  const uploadServerIcon = useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append('file', file);
      form.append('purpose', 'server-icon');
      return unwrap(api.post<ApiResponse<Attachment>>('/files', form));
    },
    onMutate: () => {
      setServerIconUploadError('');
      setServerEditError('');
    },
    onSuccess: (file) => {
      setServerIconUrl(file.fileUrl);
    },
    onError: (error: any) => {
      setServerIconUploadError(error.response?.data?.error?.message ?? 'Upload ảnh thất bại.');
    }
  });


  const createChannel = useMutation({
    mutationFn: () => unwrap(api.post<ApiResponse<Channel>>(`/servers/${serverId}/channels`, { name: channelName.trim() })),
    onSuccess: (channel) => {
      setChannelName('');
      setModal(null);
      queryClient.invalidateQueries({ queryKey: ['channels', serverId] });
      setChannelId(channel.id);
      setActiveView('server');
    }
  });

  const updateChannel = useMutation({
    mutationFn: () => unwrap(api.patch<ApiResponse<Channel>>(`/channels/${selectedChannel?.id}`, { name: channelName.trim() })),
    onSuccess: (channel) => {
      setModal(null);
      setSelectedChannel(undefined);
      queryClient.invalidateQueries({ queryKey: ['channels', serverId] });
      setChannelId(channel.id);
    }
  });

  const deleteChannel = useMutation({
    mutationFn: () => unwrap(api.delete<ApiResponse<{ message: string }>>(`/channels/${selectedChannel?.id}`)),
    onSuccess: () => {
      setModal(null);
      setSelectedChannel(undefined);
      queryClient.invalidateQueries({ queryKey: ['channels', serverId] });
      setChannelId(activeServer?.defaultChannelId);
    }
  });

  const sendMessage = useMutation({
    mutationFn: ({ target, content, clientRequestId, attachments }: SendMessageVariables) =>
        unwrap(
            api.post<ApiResponse<Message>>(target.type === 'direct' ? `/direct-conversations/${target.id}/messages` : `/channels/${target.id}/messages`, {
              content,
              attachments, // <-- Thay vì truyền mảng [] rỗng, truyền dữ liệu file ở đây
              clientRequestId
            })
        ),
    onMutate: () => setMessage(''),
    onSuccess: (created, variables) => {
      queryClient.setQueryData<Message[]>(messageQueryKey(variables.target), (old) => upsertMessage(old, created));
      if (variables.target.type === 'direct') {
        queryClient.invalidateQueries({ queryKey: ['direct-conversations'] });
      }
      setChatAttachments([]); // <-- Xóa danh sách file đính kèm tạm thời sau khi gửi thành công
    }
  });

  const uploadChatFile = useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append('file', file);
      form.append('purpose', 'message'); // Đánh dấu mục đích upload cho chat message
      return unwrap(api.post<ApiResponse<Attachment>>('/files', form));
    },
    onSuccess: (uploadedAttachment) => {
      setChatAttachments((prev) => {
        // Kiểm tra quy định nghiệp vụ BR-FILE-002: Không quá 10 file/tin nhắn
        if (prev.length >= 10) {
          alert('Mỗi tin nhắn chỉ được đính kèm tối đa 10 tệp tin.');
          return prev;
        }
        return [...prev, uploadedAttachment];
      });
    },
    onError: (error: any) => {
      alert(error.response?.data?.error?.message ?? 'Tải tệp tin lên thất bại.');
    }
  });

  // Hàm xử lý kiểm tra kích thước file phía Client (Normal Flow 2)
  function handleChatFileChange(file?: File) {
    if (!file) return;
    // Kiểm tra quy định nghiệp vụ BR-FILE-002: File không vượt quá 10 MB
    if (file.size > 10 * 1024 * 1024) {
      alert('Dung lượng tệp đính kèm vượt quá giới hạn cho phép (Tối đa 10 MB).');
      return;
    }
    uploadChatFile.mutate(file);
    if (chatFileInputRef.current) chatFileInputRef.current.value = ''; // Reset input
  }

  const editMessage = useMutation({
    mutationFn: ({ messageId, content }: { messageId: string, content: string }) =>
        unwrap(
            api.patch<ApiResponse<Message>>(`/messages/${messageId}`, {
              content
            })
        ),
    onSuccess: (updatedMessage) => {
      if (activeMessageCacheKey) {
        queryClient.setQueryData<Message[]>(activeMessageCacheKey, (old) => upsertMessage(old, updatedMessage));
      }
      setEditingMessageId(null);
      setEditContent('');
    },
    onError: (error: any) => {
      alert(error.response?.data?.error?.message ?? 'Không thể sửa tin nhắn.');
    }
  });


  const deleteMessage = useMutation({
    mutationFn: (messageId: string) =>
        unwrap(api.delete<ApiResponse<{ message: string }>>(`/messages/${messageId}`)),
    onSuccess: (_, messageId) => {
      if (activeMessageCacheKey) {
        queryClient.setQueryData<Message[]>(activeMessageCacheKey, (old) => removeMessage(old, messageId));
      }
      if (activeView === 'direct') {
        queryClient.invalidateQueries({ queryKey: ['direct-conversations'] });
      }
    },
    onError: (error: any) => {
      alert(error.response?.data?.error?.message ?? 'Không thể xóa tin nhắn.');
    }
  });

  const toggleReaction = useMutation({
    mutationFn: ({ messageId, emoji, isReacted }: { messageId: string, emoji: string, isReacted: boolean }) => {
      const path = `/messages/${messageId}/reactions/${encodeURIComponent(emoji)}`;
      return isReacted
          ? unwrap(api.delete<ApiResponse<Message>>(path))
          : unwrap(api.put<ApiResponse<Message>>(path));
    },
    onSuccess: (updatedMessage, variables) => {
      if (activeMessageCacheKey) {
        queryClient.setQueryData<Message[]>(activeMessageCacheKey, (old) => upsertMessage(old, updatedMessage));
      }
    },
    onError: (error: any) => {
      alert(error.response?.data?.error?.message ?? 'Không thể thao tác cảm xúc.');
    }
  });

  const joinByCode = useMutation({
    mutationFn: () => unwrap(api.post<ApiResponse<Server>>(`/invite-codes/${joinCode.trim()}/join`)),
    onMutate: () => setJoinError(''),
    onSuccess: (server) => {
      setJoinCode('');
      setJoinError('');
      setModal(null);
      queryClient.invalidateQueries({ queryKey: ['servers'] });
      setServerId(server.id);
      setChannelId(server.defaultChannelId);
      setActiveView('server');
    },
    onError: (error: any) => {
      const errorCode = error?.response?.data?.error?.code;
      const errorMessage = error?.response?.data?.error?.message;

      if (errorCode === 'DUPLICATE_RESOURCE') {
        setJoinError('Bạn đã là thành viên của server này.');
      }
      else if (errorMessage) {
        setJoinError(errorMessage);
      }
      else {
        setJoinError('Không thể tham gia server. Mã mời không hợp lệ.');
      }
    }
  });

  const directInvite = useMutation({
    mutationFn: () => unwrap(api.post<ApiResponse<ServerInvite>>(`/servers/${serverId}/direct-invites`, { inviteeUsername: inviteeUsername.trim() })),
    onSuccess: () => {
      setInviteeUsername('');
      setModal(null);
    }
  });

  const createInviteCode = useMutation({
    mutationFn: () =>
        unwrap(
            api.post<ApiResponse<InviteCode>>(`/servers/${serverId}/invite-codes`, {
              maxUses: inviteMaxUses.trim() ? Number(inviteMaxUses) : undefined
            })
        ),
    onSuccess: () => {
      setInviteMaxUses('');
      queryClient.invalidateQueries({ queryKey: ['invite-codes', serverId] });
    }
  });

  const acceptInvite = useMutation({
    mutationFn: (inviteId: string) => unwrap(api.post<ApiResponse<Server>>(`/server-invites/${inviteId}/accept`)),
    onSuccess: (server) => {
      queryClient.invalidateQueries({ queryKey: ['received-invites'] });
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
      queryClient.invalidateQueries({ queryKey: ['servers'] });
      setServerId(server.id);
      setChannelId(server.defaultChannelId);
      setActiveView('server');
    }
  });

  const rejectInvite = useMutation({
    mutationFn: (inviteId: string) => unwrap(api.post<ApiResponse<{ message: string }>>(`/server-invites/${inviteId}/reject`)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['received-invites'] });
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    }
  });


  // Xóa member khỏi server
  const kickMember = useMutation({
    mutationFn: (targetUserId: string) =>
        unwrap(api.delete<ApiResponse<{ message: string }>>(`/servers/${serverId}/members/${targetUserId}`)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['members', serverId] }); // Tải lại danh sách members
    },
    onError: (error: any) => {
      alert(error.response?.data?.error?.message ?? 'Không thể kick member.');
    }
  });

  const changeMemberRole = useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: Member['role'] }) =>
        unwrap(api.patch<ApiResponse<Member>>(`/servers/${serverId}/members/${userId}/role`, { role })),
    onSuccess: (updatedMember) => {
      queryClient.setQueryData<Member[]>(['members', serverId], (old = []) =>
          old.map((member) => (member.userId === updatedMember.userId ? updatedMember : member))
      );
      queryClient.invalidateQueries({ queryKey: ['members', serverId] });
      queryClient.invalidateQueries({ queryKey: ['servers'] });
    },
    onError: (error: any) => {
      alert(error.response?.data?.error?.message ?? 'Khong the doi role member.');
    }
  });

  const markAllRead = useMutation({
    mutationFn: () => unwrap(api.patch<ApiResponse<{ message: string }>>('/notifications/read-all')),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
      queryClient.invalidateQueries({ queryKey: ['received-invites'] });
    }
  });

  const sendFriendRequest = useMutation({
    mutationFn: () => unwrap(api.post<ApiResponse<FriendRequest>>('/friends/requests', { username: friendUsername.trim() })),
    onMutate: () => setFriendError(''),
    onSuccess: () => {
      setFriendUsername('');
      queryClient.invalidateQueries({ queryKey: ['friend-requests'] });
    },
    onError: (error: any) => {
      setFriendError(error.response?.data?.error?.message ?? 'Could not send friend request.');
    }
  });

  const acceptFriendRequest = useMutation({
    mutationFn: (requestId: string) => unwrap(api.post<ApiResponse<Friend>>(`/friends/requests/${requestId}/accept`)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['friends'] });
      queryClient.invalidateQueries({ queryKey: ['friend-requests'] });
    }
  });

  const rejectFriendRequest = useMutation({
    mutationFn: (requestId: string) => unwrap(api.post<ApiResponse<{ message: string }>>(`/friends/requests/${requestId}/reject`)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['friend-requests'] });
    }
  });

  const removeFriend = useMutation({
    mutationFn: (userId: string) => unwrap(api.delete<ApiResponse<{ message: string }>>(`/friends/${userId}`)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['friends'] });
      queryClient.invalidateQueries({ queryKey: ['direct-conversations'] });
      if (activeView === 'direct') {
        setActiveView('home');
        setDirectConversationId(undefined);
      }
    }
  });

  const openDirectConversation = useMutation({
    mutationFn: (userId: string) => unwrap(api.post<ApiResponse<DirectConversation>>('/direct-conversations', { userId })),
    onSuccess: (conversation) => {
      queryClient.setQueryData<DirectConversation[]>(['direct-conversations'], (old = []) => {
        const exists = old.some((item) => item.id === conversation.id);
        return exists ? old.map((item) => (item.id === conversation.id ? conversation : item)) : [conversation, ...old];
      });
      setActiveView('direct');
      setDirectConversationId(conversation.id);
      setServerMenuOpen(false);
      setNotificationOpen(false);
      setMessageSearch('');
    },
    onError: (error: any) => {
      alert(error.response?.data?.error?.message ?? 'Could not open direct message.');
    }
  });

  const updateProfile = useMutation({
    mutationFn: () =>
        unwrap(
            api.patch<ApiResponse<CurrentUser>>('/users/me', {
              username: profileUsername.trim(),
              displayName: profileDisplayName.trim(),
              customStatus: profileCustomStatus.trim(),
              avatarUrl: profileAvatarUrl.trim()
            })
        ),
    onMutate: () => setProfileError(''),
    onSuccess: (user) => {
      if (auth.accessToken) {
        auth.setAuth(auth.accessToken, user);
      }
      queryClient.setQueryData<CurrentUser>(['profile'], user);
      queryClient.invalidateQueries({ queryKey: ['members'] });
      queryClient.invalidateQueries({ queryKey: ['friends'] });
      queryClient.invalidateQueries({ queryKey: ['direct-conversations'] });
      setModal(null);
    },
    onError: (error: any) => {
      setProfileError(error.response?.data?.error?.message ?? error.message ?? 'Could not update profile');
    }
  });

  const uploadAvatar = useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append('file', file);
      form.append('purpose', 'avatar');
      return unwrap(api.post<ApiResponse<Attachment>>('/files', form));
    },
    onMutate: () => {
      setAvatarUploadError('');
      setProfileError('');
    },
    onSuccess: (file) => {
      setProfileAvatarUrl(file.fileUrl);
    },
    onError: (error: any) => {
      setAvatarUploadError(error.response?.data?.error?.message ?? error.message ?? 'Could not upload avatar');
    }
  });

  useEffect(() => {
    if (modal === 'profile' && currentProfile.data) {
      fillProfileForm(currentProfile.data);
    }
  }, [currentProfile.data, modal]);

  function fillProfileForm(user?: CurrentUser) {
    setProfileUsername(user?.username ?? '');
    setProfileDisplayName(user?.displayName ?? '');
    setProfileCustomStatus(user?.customStatus ?? '');
    setProfileAvatarUrl(user?.avatarUrl ?? '');
    setProfileError('');
    setAvatarUploadError('');
    if (avatarInputRef.current) {
      avatarInputRef.current.value = '';
    }
  }

  function uploadAvatarFile(file?: File) {
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      setAvatarUploadError('Please choose an image file.');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setAvatarUploadError('Avatar image must be 10 MB or smaller.');
      return;
    }
    uploadAvatar.mutate(file);
  }

  function openModal(mode: Exclude<ModalMode, null>, channel?: Channel) {
    setModal(mode);
    setServerMenuOpen(false);
    setNotificationOpen(false);
    setSelectedChannel(channel);

    if (mode === 'create-server') setServerName('');
    if (mode === 'join-server') setJoinCode('');
    if (mode === 'create-channel') setChannelName('');
    if (mode === 'invite-user') setInviteeUsername('');
    if (mode === 'invite-code') setInviteMaxUses('');
    if (mode === 'edit-channel') setChannelName(channel?.name ?? '');
    if (mode === 'profile') fillProfileForm(currentProfile.data ?? auth.user);
    if (mode === 'edit-server') {
      setServerName(activeServer?.name ?? '');
      setServerIconUrl(activeServer?.iconUrl ?? '');
      setServerEditError('');
      setServerIconUploadError('');
      if (serverIconInputRef.current) {
        serverIconInputRef.current.value = '';
      }
    }
  }

  function closeModal() {
    setModal(null);
    setSelectedChannel(undefined);
  }

  function selectServer(server: Server) {
    stopTyping();
    setActiveView('server');
    setServerId(server.id);
    setChannelId(server.defaultChannelId);
    setDirectConversationId(undefined);
    setServerMenuOpen(false);
    setNotificationOpen(false);
  }

  function selectHome() {
    stopTyping();
    setActiveView('home');
    setDirectConversationId(undefined);
    setServerMenuOpen(false);
    setNotificationOpen(false);
    setMessageSearch('');
  }

  function selectDirectConversation(conversation: DirectConversation) {
    stopTyping();
    setActiveView('direct');
    setDirectConversationId(conversation.id);
    setServerMenuOpen(false);
    setNotificationOpen(false);
    setMessageSearch('');
  }

  async function logout() {
    stopTyping();
    await api.post('/auth/logout').catch(() => undefined);
    disconnectStomp();
    auth.clear();
  }

  function publishTyping(isTyping: boolean, target = activeMessageTarget, ensureActive = true) {
    if (!target) return;
    const client = ensureActive ? stompClient() : currentStompClient();
    if (!client?.connected) return;
    client.publish({
      destination: target.type === 'direct' ? `/app/direct-conversations/${target.id}/typing` : `/app/channels/${target.id}/typing`,
      body: JSON.stringify({ typing: isTyping })
    });
  }

  function stopTyping(target = typingTargetRef.current ?? activeMessageTarget) {
    if (typingStopTimerRef.current) {
      window.clearTimeout(typingStopTimerRef.current);
      typingStopTimerRef.current = undefined;
    }
    if (!typingStartedRef.current) return;
    typingStartedRef.current = false;
    publishTyping(false, target, false);
    typingTargetRef.current = undefined;
  }

  function startTyping() {
    const target = activeMessageTarget;
    if (!target) return;
    const previous = typingTargetRef.current;
    if (previous && (previous.type !== target.type || previous.id !== target.id)) {
      stopTyping(previous);
    }
    if (!typingStartedRef.current) {
      typingStartedRef.current = true;
      typingTargetRef.current = target;
      publishTyping(true, target);
    }
    if (typingStopTimerRef.current) {
      window.clearTimeout(typingStopTimerRef.current);
    }
    const timerTarget = target;
    typingStopTimerRef.current = window.setTimeout(() => {
      stopTyping(timerTarget);
    }, 2000);
  }

  function handleComposerChange(value: string) {
    setMessage(value);
    if (value.trim()) {
      startTyping();
    } else {
      stopTyping();
    }
  }

  function submitMessage(event: FormEvent) {
    event.preventDefault();
    const content = message.trim();
    // Chấp nhận gửi nếu có nội dung chữ HOẶC có file đính kèm
    if ((content || chatAttachments.length > 0) && activeMessageTarget) {
      setMessageSearch('');
      sendMessage.mutate({
        target: activeMessageTarget,
        content,
        clientRequestId: crypto.randomUUID(),
        attachments: chatAttachments // <-- Gửi kèm danh sách metadata tệp tin lên Backend
      });
    }
  }

  const isOwner = activeServer?.currentRole === 'OWNER';

  return (
      <main className="discord-shell">
        <aside className="server-rail">
          <button className={`server-pill home ${activeView === 'home' || activeView === 'direct' ? 'active' : ''}`} title="Friends" type="button" onClick={selectHome}>
            <Home size={24} />
          </button>
          <span className="server-divider" />

          <div className="server-list">
            {servers.data?.map((server) => (
                <button
                    key={server.id}
                    className={`server-pill ${activeView === 'server' && server.id === serverId ? 'active' : ''}`}
                    title={server.name}
                    type="button"
                    onClick={() => selectServer(server)}
                >
                  {server.iconUrl ? <img src={server.iconUrl} alt="" /> : initialOf(server.name)}
                </button>
            ))}
          </div>

          <button className="server-pill action" title="Create server" type="button" onClick={() => openModal('create-server')}>
            <Plus size={24} />
          </button>
          <button className="server-pill action" title="Join server" type="button" onClick={() => openModal('join-server')}>
            <UserPlus size={22} />
          </button>
        </aside>

        <aside className="channel-column">
          {activeView === 'server' ? (
            <>
          <header className="server-bar">
            <button className="server-name-button" type="button" onClick={() => setServerMenuOpen((open) => !open)}>
              <span>{activeServer?.name ?? 'Mini Discord'}</span>
              <ChevronDown size={20} />
            </button>


            {serverMenuOpen && (
                <div className="server-menu">
                  <button type="button" onClick={() => openModal('create-server')}>
                    Create server
                  </button>
                  <button type="button" onClick={() => openModal('join-server')}>
                    Join by invite code
                  </button>
                  {serverId && (
                      <button
                          type="button"
                          style={{ color: '#fa777c' }}
                          onClick={() => {
                            if (window.confirm(`Bạn có chắc chắn muốn rời khỏi server ${activeServer?.name} không?`)) {
                              leaveServer.mutate();
                            }
                          }}
                          disabled={leaveServer.isPending}
                      >
                        {leaveServer.isPending ? 'Đang xử lý...' : 'Leave server'}
                      </button>
                  )}
                  {isOwner && (
                      <>
                        <button type="button" onClick={() => openModal('edit-server')}>
                          Server settings
                        </button>

                        <button type="button" onClick={() => openModal('create-channel')}>
                          Create channel
                        </button>
                        <button type="button" onClick={() => openModal('invite-code')}>
                          Invite codes
                        </button>
                        <button type="button" onClick={() => openModal('invite-user')}>
                          Direct invite
                        </button>

                        <button
                        type="button"
                        style={{ color: '#fa777c', fontWeight: 'bold' }}
                        onClick={() => {
                          if (window.confirm(`⚠️ CẢNH BÁO NGUY HIỂM:\nBạn có chắc chắn muốn XÓA HOÀN TOÀN server "${activeServer?.name}" không?\nHành động này sẽ hủy kích hoạt toàn bộ các Channel và Invite Code liên quan và không thể hoàn tác!`)) {
                            deleteServer.mutate();
                          }
                        }}
                        disabled={deleteServer.isPending}
                      >
                        {deleteServer.isPending ? 'Đang xóa...' : 'Delete server'}
                      </button>

                      </>
                  )}
                </div>
            )}
          </header>

          <nav className="channel-list">
            <div className="channel-category">
              <span>Text channels</span>
              {isOwner && (
                  <button title="Create channel" type="button" onClick={() => openModal('create-channel')}>
                    <Plus size={18} />
                  </button>
              )}
            </div>

            {channels.data?.map((channel) => (
                <div key={channel.id} className={`channel-row ${channel.id === channelId ? 'active' : ''}`}>
                  <button className="channel-select" type="button" onClick={() => { setActiveView('server'); setChannelId(channel.id); }}>
                <span className="channel-main">
                  <Hash size={20} />
                  <span>{channel.name}</span>
                </span>
                  </button>
                  {isOwner && (
                      <span className="channel-tools">
                  <button title="Direct invite" type="button" onClick={() => openModal('invite-user', channel)}>
                    <UserPlus size={16} />
                  </button>


                  <button title="Channel settings" type="button" onClick={() => openModal('edit-channel', channel)}>
                    <Settings size={16} />
                  </button>
                </span>
                  )}
                </div>
            ))}
          </nav>
            </>
          ) : (
            <>
              <header className="server-bar">
                <button className="server-name-button" type="button" onClick={selectHome}>
                  <span>Friends</span>
                  <MessageCircle size={19} />
                </button>
              </header>

              <nav className="channel-list">
                <button className={`home-nav-row ${activeView === 'home' ? 'active' : ''}`} type="button" onClick={selectHome}>
                  <Users size={18} />
                  <span>Friends</span>
                  {incomingFriendRequests.length > 0 && (
                    <small>{incomingFriendRequests.length}</small>
                  )}
                </button>

                <div className="channel-category direct-category">
                  <span>Direct messages</span>
                </div>

                {directConversations.data?.length ? (
                  directConversations.data.map((conversation) => (
                    <button
                      key={conversation.id}
                      className={`dm-row ${conversation.id === directConversationId ? 'active' : ''}`}
                      type="button"
                      onClick={() => selectDirectConversation(conversation)}
                    >
                      <div className="small-avatar">
                        {conversation.recipient.avatarUrl ? <img src={conversation.recipient.avatarUrl} alt="" /> : initialOf(conversation.recipient.username)}
                      </div>
                      <span>{conversation.recipient.displayName ?? conversation.recipient.username}</span>
                      {conversation.unreadCount > 0 && <small>{conversation.unreadCount}</small>}
                    </button>
                  ))
                ) : (
                  <p className="empty-copy dm-empty">No direct messages</p>
                )}
              </nav>
            </>
          )}

          <footer className="account-panel">
            <button className="account-user" type="button" onClick={() => openModal('profile')}>
              <div className="small-avatar">{auth.user?.avatarUrl ? <img src={auth.user.avatarUrl} alt="" /> : initialOf(auth.user?.username)}</div>
              <div>
                <strong>{auth.user?.displayName ?? auth.user?.username}</strong>
                <span>{auth.user?.customStatus || (auth.user?.username ? `@${auth.user.username}` : 'online')}</span>
              </div>
            </button>
            <button title="Logout" type="button" onClick={logout}>
              <LogOut size={18} />
            </button>
          </footer>
        </aside>

        <section className={`chat-panel ${activeMessageTarget ? '' : 'home-panel'}`}>
          <header className="channel-header">
            <div className="channel-title">
              {activeView === 'direct' ? (
                  <>
                    <div className="small-avatar">
                      {activeDirectConversation?.recipient.avatarUrl
                        ? <img src={activeDirectConversation.recipient.avatarUrl} alt="" />
                        : initialOf(activeDirectConversation?.recipient.username)}
                    </div>
                    <strong>{activeDirectConversation?.recipient.displayName ?? activeDirectConversation?.recipient.username ?? 'Direct message'}</strong>
                    <span className="title-separator" />
                    <span>{activeDirectConversation?.recipient.presenceStatus?.toLowerCase() ?? 'offline'}</span>
                  </>
              ) : activeView === 'home' ? (
                  <>
                    <Users size={23} />
                    <strong>Friends</strong>
                  </>
              ) : (
                  <>
                    <Hash size={23} />
                    <strong>{activeChannel?.name ?? 'general'}</strong>
                  </>
              )}
              {activeView === 'server' && activeServer && (
                  <>
                    <span className="title-separator" />
                    <span>{activeServer.name}</span>
                  </>
              )}
            </div>

            <div className="header-actions">
              <button className="header-icon" title="Notifications" type="button" onClick={() => setNotificationOpen((open) => !open)}>
                <Bell size={20} />
                {notificationCount > 0 && (
                    <span className="badge">{notificationCount}</span>
                )}
              </button>
              {activeView === 'server' && (
                  <>
                    <Users size={20} />
                    <label className="message-search">
                      <input value={messageSearch} onChange={(event) => setMessageSearch(event.target.value)} placeholder="Search" />
                      <Search size={16} />
                    </label>
                  </>
              )}

              {notificationOpen && (
                  <div className="notification-popover">
                    <div className="popover-head">
                      <strong>Notifications</strong>
                      <button type="button" onClick={() => markAllRead.mutate()} disabled={!unreadNotificationCount || markAllRead.isPending}>
                        Mark read
                      </button>
                    </div>
                    {visibleNotifications.length ? (
                        visibleNotifications.map((item) => (
                            <article key={item.id} className="notification-item">
                              <strong>{item.title}</strong>
                              <p>{item.body}</p>
                            </article>
                        ))
                    ) : (receivedInvites.data?.length ?? 0) === 0 ? (
                        <p className="empty-copy">No unread notifications</p>
                    ) : null}

                    {(receivedInvites.data?.length ?? 0) > 0 && (
                        <>
                          <span className="popover-label">Invites</span>
                          {receivedInvites.data?.map((invite) => (
                              <article key={invite.id} className="notification-item invite-item">
                                <strong>{invite.serverName}</strong>
                                <p>From {invite.inviterUsername}</p>
                                <div>
                                  <button type="button" onClick={() => acceptInvite.mutate(invite.id)}>
                                    <Check size={15} /> Accept
                                  </button>
                                  <button type="button" onClick={() => rejectInvite.mutate(invite.id)}>
                                    <X size={15} /> Reject
                                  </button>
                                </div>
                              </article>
                          ))}
                        </>
                    )}
                  </div>
              )}
            </div>
          </header>

          <div className="message-list" ref={messageListRef}>
            {activeView === 'home' ? (
              <FriendsHome
                tab={friendTab}
                onTabChange={setFriendTab}
                friends={visibleFriends}
                incomingRequests={incomingFriendRequests}
                outgoingRequests={outgoingFriendRequests}
                username={friendUsername}
                onUsernameChange={setFriendUsername}
                error={friendError}
                isSending={sendFriendRequest.isPending}
                onSendRequest={() => sendFriendRequest.mutate()}
                onOpenMessage={(userId) => openDirectConversation.mutate(userId)}
                onAccept={(requestId) => acceptFriendRequest.mutate(requestId)}
                onReject={(requestId) => rejectFriendRequest.mutate(requestId)}
                onRemove={(userId, name) => {
                  if (window.confirm(`Remove ${name} from friends?`)) {
                    removeFriend.mutate(userId);
                  }
                }}
              />
            ) : (
              <>
            {!messageLoading && !searchEnabled && renderedMessages.length === 0 && <div className="message-empty">No messages yet</div>}
            {!searchEnabled && messageError && <div className="message-empty error">Could not load messages</div>}
            {renderedMessages.map((item) => (
                <article className="message-row" key={item.id}>
                  <div className="message-avatar">
                    {item.senderSnapshot?.avatarUrl ? <img src={item.senderSnapshot.avatarUrl} alt="" /> : initialOf(item.senderSnapshot?.username)}
                  </div>
                  <div className="message-body">
                    <div className="message-meta">
                      <strong>{item.senderSnapshot?.displayName ?? item.senderSnapshot?.username ?? 'Unknown user'}</strong>
                      <time>{formatTime(item.createdAt)}</time>
                      {item.editedAt && <span>(edited)</span>}
                    </div>

                    {editingMessageId === item.id ? (
                        <div className="message-edit-composer">
                          <input
                              autoFocus
                              value={editContent}
                              onChange={(e) => setEditContent(e.target.value)}
                              onKeyDown={(e) => {
                                if (e.nativeEvent.isComposing) return;

                                if (e.key === 'Enter' && !e.shiftKey) {
                                  e.preventDefault();

                                  if (isEditingRef.current) return;

                                  if (editContent.trim() && editContent.trim() !== item.content) {

                                    isEditingRef.current = true;

                                    editMessage.mutate(
                                        { messageId: item.id, content: editContent.trim() },
                                        {

                                          onSettled: () => {
                                            isEditingRef.current = false;
                                          }
                                        }
                                    );
                                  } else {
                                    setEditingMessageId(null);
                                  }
                                } else if (e.key === 'Escape') {
                                  setEditingMessageId(null);
                                }
                              }}
                          />
                          <small>Nhấn <b>Enter</b> để lưu, <b>Escape</b> để hủy.</small>
                        </div>
                    ) : (
                        <>
                          {item.content && <p>{item.content}</p>}
                          {(item.attachments ?? []).length > 0 && (
                            <div className="attachments" style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '8px' }}>
                              {(item.attachments ?? []).map((file) => {
                                // Kiểm tra xem file đính kèm có phải là hình ảnh không dựa trên mimeType hoặc đuôi mở rộng
                                const isImage = file.mimeType?.startsWith('image/') ||
                                                /\.(jpg|jpeg|png|gif|webp)$/i.test(file.fileUrl || file.originalName);

                                return isImage ? (
                                  /* Nếu là ảnh: Hiển thị Preview ảnh trực quan theo yêu cầu UC25 */
                                  <div
                                    key={file.storageKey}
                                    className="attachment-image-preview"
                                    style={{
                                      maxWidth: '320px',
                                      maxHeight: '240px',
                                      overflow: 'hidden',
                                      borderRadius: '6px',
                                      border: '1px solid rgba(255, 255, 255, 0.1)'
                                    }}
                                  >
                                    <img
                                      src={file.fileUrl}
                                      alt={file.originalName}
                                      style={{ width: '100%', height: '100%', objectFit: 'contain', display: 'block' }}
                                    />
                                  </div>
                                ) : (
                                  /* Nếu là file tài liệu khác (.docx, .pdf...): Hiển thị link tải về */
                                  <a key={file.storageKey} href={file.fileUrl} target="_blank" rel="noreferrer">
                                    📄 {file.originalName}
                                  </a>
                                );
                              })}
                            </div>
                        )}
                          {(item.reactions ?? []).length > 0 && (
                              <div className="reaction-list">
                                {(item.reactions ?? []).map((reaction) => {
                                  const isReacted = reaction.userIds.includes(auth.user?.id ?? '');
                                  return (
                                      <button
                                          key={reaction.emoji}
                                          className="reaction-chip"
                                          type="button"
                                          onClick={() => toggleReaction.mutate({ messageId: item.id, emoji: reaction.emoji, isReacted })}
                                          style={{
                                            cursor: 'pointer',
                                            background: isReacted ? 'rgba(88, 101, 242, 0.2)' : 'var(--secondary)',
                                            border: isReacted ? '1px solid var(--discord)' : '1px solid rgba(255, 255, 255, 0.08)'
                                          }}
                                      >
                                        {reaction.emoji} {reaction.userIds.length}
                                      </button>
                                  );
                                })}
                              </div>
                          )}
                        </>
                    )}
                  </div>

                  {!editingMessageId && (
                      <div className="message-actions" style={{ display: 'flex', alignItems: 'center', marginTop: '6px' }}>

                        <div style={{ position: 'relative', display: 'inline-block' }}>
                          {reactionMenuId === item.id && (
                              <div className="emoji-picker" style={{
                                position: 'absolute',

                                top: '50%',
                                right: '100%',
                                transform: 'translateY(-50%)',
                                marginLeft: '8px',
                                display: 'flex',
                                gap: '4px',
                                background: 'var(--tertiary)',
                                padding: '6px',
                                borderRadius: '8px',
                                border: '1px solid var(--secondary)',
                                zIndex: 50,
                                boxShadow: '0 4px 14px rgba(0,0,0,0.3)'
                              }}>
                                {['👍', '❤️', '😂', '🔥', '😢', '🙏'].map(emoji => (
                                    <button
                                        key={emoji}
                                        type="button"
                                        style={{ background: 'transparent', border: 'none', fontSize: '18px', cursor: 'pointer', padding: '4px' }}
                                        onClick={() => {
                                          const isReacted = item.reactions?.some(r => r.emoji === emoji && r.userIds.includes(auth.user?.id ?? ''));
                                          toggleReaction.mutate({ messageId: item.id, emoji, isReacted: !!isReacted });
                                          setReactionMenuId(null);
                                        }}
                                    >
                                      {emoji}
                                    </button>
                                ))}
                              </div>
                          )}
                          <button
                              title="Thêm cảm xúc"
                              type="button"
                              onClick={() => setReactionMenuId(reactionMenuId === item.id ? null : item.id)}
                          >
                            <Smile size={15} />
                          </button>
                        </div>

                        {item.senderId === auth.user?.id && (
                            <button
                                title="Sửa tin nhắn"
                                type="button"
                                style={{ marginLeft: '8px' }}
                                onClick={() => {
                                  setEditingMessageId(item.id);
                                  setEditContent(item.content || '');
                                }}
                            >
                              <Pencil size={15} />
                            </button>
                        )}

                        {(item.senderId === auth.user?.id || (activeView === 'server' && isOwner)) && (
                            <button
                                title={item.senderId === auth.user?.id ? "Xóa tin nhắn của tôi" : "Xóa tin nhắn member (Quyền OWNER)"}
                                type="button"
                                onClick={() => {
                                  if (window.confirm('Bạn có chắc chắn muốn xóa tin nhắn này không?')) {
                                    deleteMessage.mutate(item.id);
                                  }
                                }}
                                disabled={deleteMessage.isPending}
                                style={{ color: '#fa777c', marginLeft: '8px' }}
                            >
                              <Trash2 size={15} />
                            </button>
                        )}

                      </div>
                  )}
                </article>
            ))}
              </>
            )}
          </div>

          {activeMessageTarget && (
            <>
          <div className="typing-indicator" aria-live="polite">
            {typingText}
          </div>

          <form className="composer" onSubmit={submitMessage}>
            <div style={{ display: 'flex', flexDirection: 'column', width: '100%' }}>

              {/* VÙNG CHỨA CỐ ĐỊNH: Giúp giữ vị trí của input bên dưới luôn ổn định */}
              <div className="chat-attachments-wrapper">
                {chatAttachments.length > 0 && (
                  <div className="attachments" style={{ marginBottom: '8px', padding: '0 8px' }}>
                    {chatAttachments.map((file, idx) => (
                      <span
                        key={file.storageKey || idx}
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '8px',
                          background: 'var(--tertiary)',
                          padding: '4px 8px',
                          borderRadius: '4px',
                          fontSize: '13px'
                        }}
                      >
                        <span style={{ maxWidth: '160px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: 'var(--white)' }}>
                          {file.originalName}
                        </span>
                        <button
                          type="button"
                          onClick={() => setChatAttachments(prev => prev.filter((_, i) => i !== idx))}
                          style={{ border: 0, background: 'transparent', color: '#fa777c', padding: 0, display: 'flex' }}
                        >
                          <X size={14} />
                        </button>
                      </span>
                    ))}
                  </div>
                )}
              </div>

              {/* HỘP NHẬP LIỆU: Luôn là con thứ hai cố định, không bao giờ bị remount */}
              <div className="composer-input" style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <input
                  ref={chatFileInputRef}
                  type="file"
                  style={{ display: 'none' }}
                  onChange={(event) => handleChatFileChange(event.currentTarget.files?.[0])}
                />

                <button
                  type="button"
                  title="Upload file"
                  onClick={() => chatFileInputRef.current?.click()}
                  disabled={uploadChatFile.isPending}
                  style={{ border: 0, background: 'transparent', color: 'var(--gray)', padding: 0, display: 'flex', alignItems: 'center' }}
                >
                  <Plus size={20} style={{ background: 'var(--primary)', borderRadius: '50%', padding: '2px', color: 'var(--white)' }} />
                </button>

                <input
                  value={message}
                  onChange={(event) => handleComposerChange(event.target.value)}
                  placeholder={uploadChatFile.isPending ? "Đang tải tệp đính kèm..." : `Message #${activeChannel?.name ?? ''}`}
                  disabled={uploadChatFile.isPending}
                />
              </div>
            </div>

            <button
              title="Send"
              type="submit"
              disabled={(!message.trim() && chatAttachments.length === 0) || uploadChatFile.isPending}
            >
              <Send size={20} />
            </button>
          </form>
            </>
          )}
        </section>

        <aside className="member-column">
          {activeView === 'server' ? (
            <>
          <MemberGroup
              title={`Online - ${onlineMembers.length}`}
              members={onlineMembers}
              isOwner={isOwner}
              currentUserId={auth.user?.id}
              onKick={(targetId, targetName) => {
                if (window.confirm(`Bạn có chắc chắn muốn kick ${targetName} khỏi server không?`)) {
                  kickMember.mutate(targetId);
                }
              }}
              onRoleChange={(targetId, targetName, role) => {
                if (window.confirm(`Change ${targetName} role to ${role}?`)) {
                  changeMemberRole.mutate({ userId: targetId, role });
                }
              }}
          />
          <MemberGroup
              title={`Offline - ${offlineMembers.length}`}
              members={offlineMembers}
              offline
              isOwner={isOwner}
              currentUserId={auth.user?.id}
              onKick={(targetId, targetName) => {
                if (window.confirm(`Bạn có chắc chắn muốn kick ${targetName} khỏi server không?`)) {
                  kickMember.mutate(targetId);
                }
              }}
              onRoleChange={(targetId, targetName, role) => {
                if (window.confirm(`Change ${targetName} role to ${role}?`)) {
                  changeMemberRole.mutate({ userId: targetId, role });
                }
              }}
          />
            </>
          ) : (
            <section className="member-group friend-summary">
              <span>{activeView === 'direct' ? 'Direct message' : 'Friends'}</span>
              {activeView === 'direct' && activeDirectConversation ? (
                <div className="profile-card compact-profile">
                  <div className="profile-avatar">
                    {activeDirectConversation.recipient.avatarUrl
                      ? <img src={activeDirectConversation.recipient.avatarUrl} alt="" />
                      : initialOf(activeDirectConversation.recipient.username)}
                  </div>
                  <div>
                    <strong>{activeDirectConversation.recipient.displayName ?? activeDirectConversation.recipient.username}</strong>
                    <span>{activeDirectConversation.recipient.customStatus || activeDirectConversation.recipient.presenceStatus.toLowerCase()}</span>
                  </div>
                </div>
              ) : (
                <>
                  <div className="summary-row"><strong>{friends.data?.length ?? 0}</strong><span>friends</span></div>
                  <div className="summary-row"><strong>{incomingFriendRequests.length}</strong><span>incoming</span></div>
                  <div className="summary-row"><strong>{directConversations.data?.length ?? 0}</strong><span>direct messages</span></div>
                </>
              )}
            </section>
          )}
        </aside>
        {modal && (
            <div className="modal-backdrop" role="presentation" onMouseDown={closeModal}>
              <div className="modal" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
                <button className="modal-close" type="button" onClick={closeModal}>
                  <X size={20} />
                </button>
                {modal === 'create-server' && (
                    <form onSubmit={(event) => { event.preventDefault(); if (serverName.trim()) createServer.mutate(); }}>
                      <h2>Create server</h2>
                      <input value={serverName} onChange={(event) => setServerName(event.target.value)} placeholder="Server name" autoFocus />
                      <button className="modal-primary" disabled={!serverName.trim() || createServer.isPending}>Create</button>
                    </form>
                )}

                {modal === 'edit-server' && (
                  <form onSubmit={(event) => { event.preventDefault(); if (serverName.trim()) updateServer.mutate(); }}>
                    <h2>Server settings</h2>

                    <div className="profile-card">
                      <button
                          type="button"
                          className="server-edit-avatar"
                          onClick={() => serverIconInputRef.current?.click()}
                          title="Thay đổi Icon"
                      >
                        {serverIconUrl.trim() ? <img src={serverIconUrl.trim()} alt="" /> : initialOf(serverName || activeServer?.name)}
                      </button>
                      <div>
                        <strong>{serverName.trim() || activeServer?.name}</strong>
                        <span>Server Owner Cài đặt</span>
                      </div>
                    </div>

                    <label>
                      Server name
                      <input
                        value={serverName}
                        onChange={(event) => setServerName(event.target.value)}
                        maxLength={100}
                        required
                        autoFocus
                      />
                    </label>

                    <label>
                      Server Icon URL
                      <input
                        value={serverIconUrl}
                        onChange={(event) => setServerIconUrl(event.target.value)}
                        placeholder="https://..."
                      />
                    </label>

                    <div className="avatar-upload-row">
                      <input
                        ref={serverIconInputRef}
                        type="file"
                        accept="image/png,image/jpeg,image/gif,image/webp"
                        onChange={(event) => {
                          const file = event.currentTarget.files?.[0];
                          if (file) uploadServerIcon.mutate(file);
                        }}
                      />
                      <button
                        className="secondary-button"
                        type="button"
                        onClick={() => serverIconInputRef.current?.click()}
                        disabled={uploadServerIcon.isPending}
                      >
                        {uploadServerIcon.isPending ? 'Uploading...' : 'Upload Icon'}
                      </button>
                      <span>PNG, JPG, GIF, WebP tối đa 10 MB</span>
                    </div>

                    {serverIconUploadError && <p className="form-error">{serverIconUploadError}</p>}
                    {serverEditError && <p className="form-error">{serverEditError}</p>}

                    <button className="modal-primary" disabled={!serverName.trim() || updateServer.isPending}>
                      {updateServer.isPending ? 'Saving...' : 'Save changes'}
                    </button>
                  </form>
                )}

                {modal === 'profile' && (
                    <form onSubmit={(event) => { event.preventDefault(); if (profileUsername.trim()) updateProfile.mutate(); }}>
                      <h2>Profile</h2>
                      <div className="profile-card">
                        <button
                            type="button"
                            className="profile-avatar"
                            onClick={() => avatarInputRef.current?.click()}
                            title="Thay đổi Avatar"
                        >
                          {profileAvatarUrl.trim() ? <img src={profileAvatarUrl.trim()} alt="" /> : initialOf(profileUsername || auth.user?.username)}
                        </button>
                        <div>
                          <strong>{profileDisplayName.trim() || profileUsername || auth.user?.username}</strong>
                          <span>{profileCustomStatus.trim() || auth.user?.email}</span>
                        </div>
                      </div>
                      <label>
                        Username
                        <input
                            value={profileUsername}
                            onChange={(event) => setProfileUsername(event.target.value)}
                            minLength={3}
                            maxLength={32}
                            pattern="^[a-zA-Z0-9_.-]+$"
                            required
                            autoFocus
                        />
                      </label>
                      <label>
                        Display name
                        <input value={profileDisplayName} onChange={(event) => setProfileDisplayName(event.target.value)} maxLength={80} placeholder="Display name" />
                      </label>
                      <label>
                        Custom status
                        <input value={profileCustomStatus} onChange={(event) => setProfileCustomStatus(event.target.value)} maxLength={180} placeholder="Status" />
                      </label>
                      <label>
                        Avatar URL
                        <input value={profileAvatarUrl} onChange={(event) => setProfileAvatarUrl(event.target.value)} placeholder="https://..." />
                      </label>
                      <div className="avatar-upload-row">
                        <input
                            ref={avatarInputRef}
                            type="file"
                            accept="image/png,image/jpeg,image/gif,image/webp"
                            onChange={(event) => uploadAvatarFile(event.currentTarget.files?.[0])}
                        />
                        <button className="secondary-button" type="button" onClick={() => avatarInputRef.current?.click()} disabled={uploadAvatar.isPending}>
                          {uploadAvatar.isPending ? 'Uploading' : 'Upload image'}
                        </button>
                        <span>{uploadAvatar.data?.originalName ?? 'PNG, JPG, GIF, WebP up to 10 MB'}</span>
                      </div>
                      {avatarUploadError && <p className="form-error">{avatarUploadError}</p>}
                      <div className="profile-meta">
                        <span>Email</span>
                        <strong>{auth.user?.email}</strong>
                        <span>Account</span>
                        <strong>{auth.user?.accountStatus}</strong>
                      </div>
                      {profileError && <p className="form-error">{profileError}</p>}
                      <button className="modal-primary" disabled={!profileUsername.trim() || updateProfile.isPending || currentProfile.isLoading}>
                        {updateProfile.isPending ? 'Saving' : 'Save changes'}
                      </button>
                    </form>
                )}

                {modal === 'join-server' && (
                    <form onSubmit={(event) => { event.preventDefault(); if (joinCode.trim()) joinByCode.mutate(); }}>
                      <h2>Join server</h2>
                      <input value={joinCode} onChange={(event) => setJoinCode(event.target.value)} placeholder="Invite code" autoFocus />
                      {joinError && <p className="form-error" style={{ color: '#fa777c', marginTop: '8px' }}>{joinError}</p>}

                      <button className="modal-primary" disabled={!joinCode.trim() || joinByCode.isPending}>
                        {joinByCode.isPending ? 'Đang vào...' : 'Join'}
                      </button>
                    </form>
                )}

                {modal === 'create-channel' && (
                    <form onSubmit={(event) => { event.preventDefault(); if (channelName.trim()) createChannel.mutate(); }}>
                      <h2>Create text channel</h2>
                      <input value={channelName} onChange={(event) => setChannelName(event.target.value)} placeholder="channel-name" autoFocus />
                      <button className="modal-primary" disabled={!channelName.trim() || createChannel.isPending}>Create</button>
                    </form>
                )}

                {modal === 'invite-user' && (
                    <form onSubmit={(event) => { event.preventDefault(); if (inviteeUsername.trim()) directInvite.mutate(); }}>
                      <h2>Direct invite</h2>
                      <input value={inviteeUsername} onChange={(event) => setInviteeUsername(event.target.value)} placeholder="Username" autoFocus />
                      <button className="modal-primary" disabled={!inviteeUsername.trim() || directInvite.isPending}>Invite</button>
                    </form>
                )}

                {modal === 'invite-code' && (
                    <form onSubmit={(event) => { event.preventDefault(); createInviteCode.mutate(); }}>
                      <h2>Invite codes</h2>
                      <div className="inline-form">
                        <input value={inviteMaxUses} onChange={(event) => setInviteMaxUses(event.target.value)} placeholder="Max uses (optional)" inputMode="numeric" />
                        <button className="modal-primary" disabled={createInviteCode.isPending}>Create</button>
                      </div>
                      <div className="invite-code-list">
                        {inviteCodes.data?.map((code) => (
                            <button key={code.id} type="button" onClick={() => navigator.clipboard?.writeText(code.code)}>
                              <span>{code.code}</span>
                              <small>{code.useCount}{code.maxUses ? `/${code.maxUses}` : ''} uses</small>
                              <Clipboard size={15} />
                            </button>
                        ))}
                      </div>
                    </form>
                )}

                {modal === 'edit-channel' && (
                    <form onSubmit={(event) => { event.preventDefault(); if (channelName.trim()) updateChannel.mutate(); }}>
                      <h2>Channel settings</h2>
                      <input value={channelName} onChange={(event) => setChannelName(event.target.value)} placeholder="channel-name" autoFocus />
                      <div className="modal-actions">
                        <button className="modal-primary" disabled={!channelName.trim() || updateChannel.isPending}>Save</button>
                        {!selectedChannel?.defaultChannel && (
                            <button className="danger-button" type="button" onClick={() => deleteChannel.mutate()} disabled={deleteChannel.isPending}>
                              <Trash2 size={16} /> Delete
                            </button>
                        )}
                      </div>
                    </form>
                )}
              </div>
            </div>
        )}
      </main>
  );
}

function FriendsHome({
  tab,
  onTabChange,
  friends,
  incomingRequests,
  outgoingRequests,
  username,
  onUsernameChange,
  error,
  isSending,
  onSendRequest,
  onOpenMessage,
  onAccept,
  onReject,
  onRemove
}: {
  tab: FriendTab;
  onTabChange: (tab: FriendTab) => void;
  friends: Friend[];
  incomingRequests: FriendRequest[];
  outgoingRequests: FriendRequest[];
  username: string;
  onUsernameChange: (value: string) => void;
  error?: string;
  isSending?: boolean;
  onSendRequest: () => void;
  onOpenMessage: (userId: string) => void;
  onAccept: (requestId: string) => void;
  onReject: (requestId: string) => void;
  onRemove: (userId: string, name: string) => void;
}) {
  return (
    <div className="friends-home">
      <div className="friends-tabs">
        <button className={tab === 'all' ? 'active' : ''} type="button" onClick={() => onTabChange('all')}>All</button>
        <button className={tab === 'online' ? 'active' : ''} type="button" onClick={() => onTabChange('online')}>Online</button>
        <button className={tab === 'pending' ? 'active' : ''} type="button" onClick={() => onTabChange('pending')}>
          Pending
          {incomingRequests.length > 0 && <small>{incomingRequests.length}</small>}
        </button>
        <button className={tab === 'add' ? 'active' : ''} type="button" onClick={() => onTabChange('add')}>Add Friend</button>
      </div>

      {tab === 'add' && (
        <form className="add-friend-panel" onSubmit={(event) => { event.preventDefault(); if (username.trim()) onSendRequest(); }}>
          <label>
            Add by username
            <div className="inline-form">
              <input value={username} onChange={(event) => onUsernameChange(event.target.value)} placeholder="username" autoFocus />
              <button className="modal-primary" type="submit" disabled={!username.trim() || isSending}>
                Send
              </button>
            </div>
          </label>
          {error && <p className="form-error">{error}</p>}
        </form>
      )}

      {tab === 'pending' && (
        <div className="friend-section">
          <span>Incoming - {incomingRequests.length}</span>
          {incomingRequests.length === 0 && <p className="empty-copy">No incoming requests</p>}
          {incomingRequests.map((request) => (
            <FriendRequestRow key={request.id} request={request} onAccept={onAccept} onReject={onReject} />
          ))}

          <span>Outgoing - {outgoingRequests.length}</span>
          {outgoingRequests.length === 0 && <p className="empty-copy">No outgoing requests</p>}
          {outgoingRequests.map((request) => (
            <FriendRequestRow key={request.id} request={request} />
          ))}
        </div>
      )}

      {(tab === 'all' || tab === 'online') && (
        <div className="friend-section">
          <span>{tab === 'online' ? `Online - ${friends.length}` : `All friends - ${friends.length}`}</span>
          {friends.length === 0 && <p className="empty-copy">No friends to show</p>}
          {friends.map((friend) => (
            <FriendRow
              key={friend.user.userId}
              user={friend.user}
              onOpenMessage={onOpenMessage}
              onRemove={onRemove}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function FriendRow({ user, onOpenMessage, onRemove }: {
  user: FriendUser;
  onOpenMessage: (userId: string) => void;
  onRemove: (userId: string, name: string) => void;
}) {
  const name = user.displayName ?? user.username;
  return (
    <article className="friend-row">
      <div className="small-avatar">{user.avatarUrl ? <img src={user.avatarUrl} alt="" /> : initialOf(user.username)}</div>
      <div>
        <strong>{name}</strong>
        <span>{user.customStatus || user.presenceStatus.toLowerCase()}</span>
      </div>
      <button title="Message" type="button" onClick={() => onOpenMessage(user.userId)}>
        <MessageCircle size={18} />
      </button>
      <button title="Remove friend" type="button" onClick={() => onRemove(user.userId, name)}>
        <UserMinus size={18} />
      </button>
    </article>
  );
}

function FriendRequestRow({ request, onAccept, onReject }: {
  request: FriendRequest;
  onAccept?: (requestId: string) => void;
  onReject?: (requestId: string) => void;
}) {
  const name = request.user.displayName ?? request.user.username;
  return (
    <article className="friend-row">
      <div className="small-avatar">{request.user.avatarUrl ? <img src={request.user.avatarUrl} alt="" /> : initialOf(request.user.username)}</div>
      <div>
        <strong>{name}</strong>
        <span>{request.direction.toLowerCase()}</span>
      </div>
      {request.direction === 'INCOMING' && (
        <>
          <button title="Accept" type="button" onClick={() => onAccept?.(request.id)}>
            <Check size={18} />
          </button>
          <button title="Reject" type="button" onClick={() => onReject?.(request.id)}>
            <X size={18} />
          </button>
        </>
      )}
    </article>
  );
}

function MemberGroup({title, members, offline, isOwner, currentUserId, onKick, onRoleChange}: {
  title: string;
  members: Member[];
  offline?: boolean;
  isOwner?: boolean;
  currentUserId?: string;
  onKick?: (userId: string, username: string) => void;
  onRoleChange?: (userId: string, username: string, role: Member['role']) => void;
}) {
  return (
      <section className="member-group">
        <span>{title}</span>
        {members.map((member) => (
            <div className={`member-row ${offline ? 'offline' : ''}`} key={member.userId}>
              <div className="small-avatar">{member.avatarUrl ? <img src={member.avatarUrl} alt="" /> : initialOf(member.username)}</div>
              <strong>{member.displayName ?? member.username}</strong>

              {member.role === 'OWNER' && <em>OWNER</em>}

              {isOwner && member.userId !== currentUserId && (
                  <select
                      className="role-select"
                      title="Change role"
                      value={member.role}
                      onChange={(event) => {
                        const nextRole = event.target.value as Member['role'];
                        if (nextRole !== member.role) {
                          onRoleChange?.(member.userId, member.displayName ?? member.username, nextRole);
                        }
                      }}
                  >
                    <option value="MEMBER">MEMBER</option>
                    <option value="OWNER">OWNER</option>
                  </select>
              )}

              {/* Nút Kick chỉ hiện cho OWNER, không hiện ở chính bản thân mình và không được kick OWNER khác */}
              {isOwner && member.userId !== currentUserId && member.role !== 'OWNER' && (
                  <button
                      className="kick-button"
                      title="Kick Member"
                      onClick={(e) => {
                        e.stopPropagation();
                        onKick?.(member.userId, member.displayName ?? member.username);
                      }}
                  >
                    <UserMinus size={16} />
                  </button>
              )}
            </div>
        ))}
      </section>
  );
}
