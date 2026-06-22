import { Check, MessageCircle, UserMinus, X } from 'lucide-react';
import type { Friend, FriendRequest, FriendUser } from '../../../shared/types';
import { initialOf } from '../chatUtils';
import type { FriendTab } from '../types';

export function FriendsHome({
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
