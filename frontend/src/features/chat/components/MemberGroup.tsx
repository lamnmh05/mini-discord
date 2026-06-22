import { UserMinus } from 'lucide-react';
import type { Member } from '../../../shared/types';
import { initialOf } from '../chatUtils';

export function MemberGroup({ title, members, offline, isOwner, currentUserId, onKick, onRoleChange }: {
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

          {isOwner && member.userId !== currentUserId && member.role !== 'OWNER' && (
            <button
              className="kick-button"
              title="Kick Member"
              onClick={(event) => {
                event.stopPropagation();
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
