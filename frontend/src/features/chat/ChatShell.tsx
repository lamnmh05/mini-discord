import { FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Bell,
  Check,
  ChevronDown,
  Clipboard,
  Hash,
  LogOut,
  Plus,
  Search,
  Send,
  Settings,
  Trash2,
  UserPlus,
  Users,
  X
} from 'lucide-react';
import { api, unwrap } from '../../shared/api/client';
import { disconnectStomp, stompClient } from '../../shared/api/ws';
import { queryClient } from '../../app/queryClient';
import { useAuthStore } from '../../store/authStore';
import type {
  ApiResponse,
  Attachment,
  Channel,
  CurrentUser,
  InviteCode,
  Member,
  Message,
  Notification,
  Server,
  ServerInvite,
  WebSocketEvent
} from '../../shared/types';

type ModalMode = 'profile' | 'create-server' | 'join-server' | 'edit-server'| 'create-channel' | 'invite-user' | 'invite-code' | 'edit-channel' | null;
type SendMessageVariables = { channelId: string; content: string; clientRequestId: string };

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

function removeMessage(messages: Message[] | undefined, messageId: string) {
  return (messages ?? []).filter((item) => item.id !== messageId);
}

export function ChatShell() {
  const auth = useAuthStore();
  const messageListRef = useRef<HTMLDivElement>(null);
  const avatarInputRef = useRef<HTMLInputElement>(null);
  const [serverId, setServerId] = useState<string>();
  const [channelId, setChannelId] = useState<string>();
  const [message, setMessage] = useState('');
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

  // edit server
  const [serverIconUrl, setServerIconUrl] = useState('');
  const [serverEditError, setServerEditError] = useState('');
  const [serverIconUploadError, setServerIconUploadError] = useState('');
  const serverIconInputRef = useRef<HTMLInputElement>(null);

  const servers = useQuery({
    queryKey: ['servers'],
    queryFn: () => unwrap(api.get<ApiResponse<Server[]>>('/servers'))
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
  const searchEnabled = Boolean(serverId && messageSearch.trim().length >= 2);

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

  const visibleMessages = searchEnabled ? searchMessages.data ?? [] : messages.data ?? [];
  const renderedMessages = useMemo(() => sortMessages(visibleMessages), [visibleMessages]);
  const lastRenderedMessageId = renderedMessages[renderedMessages.length - 1]?.id;
  const onlineMembers = useMemo(() => members.data?.filter((member) => member.presenceStatus === 'ONLINE') ?? [], [members.data]);
  const offlineMembers = useMemo(() => members.data?.filter((member) => member.presenceStatus !== 'ONLINE') ?? [], [members.data]);

  useEffect(() => {
    if (searchEnabled) return;
    const list = messageListRef.current;
    if (!list) return;
    const frame = window.requestAnimationFrame(() => {
      list.scrollTop = list.scrollHeight;
    });
    return () => window.cancelAnimationFrame(frame);
  }, [channelId, lastRenderedMessageId, renderedMessages.length, searchEnabled]);

  useEffect(() => {
    if (!channelId || !serverId) return;
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
    };
    if (client.connected) {
      subscribe();
    } else {
      client.onConnect = subscribe;
    }
    return () => disposers.forEach((dispose) => dispose());
  }, [channelId, serverId]);

  const createServer = useMutation({
    mutationFn: () => unwrap(api.post<ApiResponse<Server>>('/servers', { name: serverName.trim() })),
    onSuccess: (server) => {
      setServerName('');
      setModal(null);
      queryClient.invalidateQueries({ queryKey: ['servers'] });
      setServerId(server.id);
      setChannelId(server.defaultChannelId);
    }
  });

  const leaveServer = useMutation({
    mutationFn: () => unwrap(api.post<ApiResponse<{ message: string }>>(`/servers/${serverId}/leave`)),
    onSuccess: () => {
      setServerId(undefined);
      setChannelId(undefined);
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
    mutationFn: ({ channelId, content, clientRequestId }: SendMessageVariables) =>
        unwrap(
            api.post<ApiResponse<Message>>(`/channels/${channelId}/messages`, {
              content,
              attachments: [],
              clientRequestId
            })
        ),
    onMutate: () => setMessage(''),
    onSuccess: (created, variables) => {
      queryClient.setQueryData<Message[]>(['messages', variables.channelId], (old) => upsertMessage(old, created));
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
      queryClient.invalidateQueries({ queryKey: ['servers'] });
      setServerId(server.id);
      setChannelId(server.defaultChannelId);
    }
  });

  const rejectInvite = useMutation({
    mutationFn: (inviteId: string) => unwrap(api.post<ApiResponse<{ message: string }>>(`/server-invites/${inviteId}/reject`)),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['received-invites'] })
  });

  const markAllRead = useMutation({
    mutationFn: () => unwrap(api.patch<ApiResponse<{ message: string }>>('/notifications/read-all')),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] })
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
    setServerId(server.id);
    setChannelId(server.defaultChannelId);
    setServerMenuOpen(false);
    setNotificationOpen(false);
  }

  async function logout() {
    await api.post('/auth/logout').catch(() => undefined);
    disconnectStomp();
    auth.clear();
  }

  function submitMessage(event: FormEvent) {
    event.preventDefault();
    const content = message.trim();
    if (content && channelId) {
      setMessageSearch('');
      sendMessage.mutate({ channelId, content, clientRequestId: crypto.randomUUID() });
    }
  }

  const isOwner = activeServer?.currentRole === 'OWNER';

  return (
      <main className="discord-shell">
        <aside className="server-rail">
          <button className="server-pill home" title="Profile" type="button" onClick={() => openModal('profile')}>
            {auth.user?.avatarUrl ? <img src={auth.user.avatarUrl} alt="" /> : initialOf(auth.user?.username)}
          </button>
          <span className="server-divider" />

          <div className="server-list">
            {servers.data?.map((server) => (
                <button
                    key={server.id}
                    className={`server-pill ${server.id === serverId ? 'active' : ''}`}
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
                  <button className="channel-select" type="button" onClick={() => setChannelId(channel.id)}>
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

        <section className="chat-panel">
          <header className="channel-header">
            <div className="channel-title">
              <Hash size={23} />
              <strong>{activeChannel?.name ?? 'general'}</strong>
              {activeServer && (
                  <>
                    <span className="title-separator" />
                    <span>{activeServer.name}</span>
                  </>
              )}
            </div>

            <div className="header-actions">
              <button className="header-icon" title="Notifications" type="button" onClick={() => setNotificationOpen((open) => !open)}>
                <Bell size={20} />
                {(notifications.data?.length ?? 0) + (receivedInvites.data?.length ?? 0) > 0 && (
                    <span className="badge">{(notifications.data?.length ?? 0) + (receivedInvites.data?.length ?? 0)}</span>
                )}
              </button>
              <Users size={20} />
              <label className="message-search">
                <input value={messageSearch} onChange={(event) => setMessageSearch(event.target.value)} placeholder="Search" />
                <Search size={16} />
              </label>

              {notificationOpen && (
                  <div className="notification-popover">
                    <div className="popover-head">
                      <strong>Notifications</strong>
                      <button type="button" onClick={() => markAllRead.mutate()} disabled={!notifications.data?.length}>
                        Mark read
                      </button>
                    </div>
                    {notifications.data?.length ? (
                        notifications.data.map((item) => (
                            <article key={item.id} className="notification-item">
                              <strong>{item.title}</strong>
                              <p>{item.body}</p>
                            </article>
                        ))
                    ) : (
                        <p className="empty-copy">No unread notifications</p>
                    )}

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
            {!messages.isLoading && !searchEnabled && renderedMessages.length === 0 && <div className="message-empty">No messages yet</div>}
            {!searchEnabled && messages.isError && <div className="message-empty error">Could not load messages</div>}
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
                    {item.content && <p>{item.content}</p>}
                    {(item.attachments ?? []).length > 0 && (
                        <div className="attachments">
                          {(item.attachments ?? []).map((file) => (
                              <a key={file.storageKey} href={file.fileUrl} target="_blank" rel="noreferrer">
                                {file.originalName}
                              </a>
                          ))}
                        </div>
                    )}
                    {(item.reactions ?? []).length > 0 && (
                        <div className="reaction-list">
                          {(item.reactions ?? []).map((reaction) => (
                              <span key={reaction.emoji} className="reaction-chip">
                        {reaction.emoji} {reaction.userIds.length}
                      </span>
                          ))}
                        </div>
                    )}
                  </div>
                </article>
            ))}
          </div>

          <form className="composer" onSubmit={submitMessage}>
            <div className="composer-input">
              <input value={message} onChange={(event) => setMessage(event.target.value)} placeholder={`Message #${activeChannel?.name ?? ''}`} />
            </div>
            <button title="Send" type="submit" disabled={!message.trim()}>
              <Send size={20} />
            </button>
          </form>
        </section>

        <aside className="member-column">
          <MemberGroup title={`Online - ${onlineMembers.length}`} members={onlineMembers} />
          <MemberGroup title={`Offline - ${offlineMembers.length}`} members={offlineMembers} offline />
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
                      <div className="server-edit-avatar">
                        {serverIconUrl.trim() ? <img src={serverIconUrl.trim()} alt="" /> : initialOf(serverName || activeServer?.name)}
                      </div>
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
                        <div className="server-edit-avatar">
                          {profileAvatarUrl.trim() ? <img src={profileAvatarUrl.trim()} alt="" /> : initialOf(profileUsername || auth.user?.username)}
                        </div>
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

function MemberGroup({ title, members, offline }: { title: string; members: Member[]; offline?: boolean }) {
  return (
      <section className="member-group">
        <span>{title}</span>
        {members.map((member) => (
            <div className={`member-row ${offline ? 'offline' : ''}`} key={member.userId}>
              <div className="small-avatar">{member.avatarUrl ? <img src={member.avatarUrl} alt="" /> : initialOf(member.username)}</div>
              <strong>{member.displayName ?? member.username}</strong>
              {member.role === 'OWNER' && <em>OWNER</em>}
            </div>
        ))}
      </section>
  );
}