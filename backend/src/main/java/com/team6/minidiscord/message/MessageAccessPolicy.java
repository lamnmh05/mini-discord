package com.team6.minidiscord.message;

import com.team6.minidiscord.channel.ChannelDocument;
import com.team6.minidiscord.channel.ChannelService;
import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.direct.DirectConversationService;
import com.team6.minidiscord.membership.MemberRole;
import com.team6.minidiscord.membership.MembershipService;
import com.team6.minidiscord.membership.ServerMemberDocument;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

@Service
public class MessageAccessPolicy {
    private final ChannelService channelService;
    private final MembershipService membershipService;
    private final DirectConversationService directConversationService;

    public MessageAccessPolicy(
            ChannelService channelService,
            MembershipService membershipService,
            DirectConversationService directConversationService
    ) {
        this.channelService = channelService;
        this.membershipService = membershipService;
        this.directConversationService = directConversationService;
    }

    public ChannelDocument requireServerChannel(ObjectId userId, ObjectId channelId) {
        ChannelDocument channel = channelService.requireActiveChannel(channelId);
        membershipService.requireMember(channel.serverId, userId);
        return channel;
    }

    public void requireDirectConversation(ObjectId userId, ObjectId conversationId) {
        directConversationService.requireParticipant(conversationId, userId);
    }

    public void requireCanSendDirect(ObjectId userId, ObjectId conversationId) {
        directConversationService.requireCanMessage(conversationId, userId);
    }

    public void requireVisible(ObjectId userId, MessageDocument message) {
        if (scope(message) == MessageScope.SERVER) {
            requireServerChannel(userId, message.channelId);
        } else {
            requireDirectConversation(userId, message.conversationId);
        }
    }

    public void requireDeletable(ObjectId userId, MessageDocument message) {
        if (scope(message) == MessageScope.SERVER) {
            ChannelDocument channel = channelService.requireActiveChannel(message.channelId);
            ServerMemberDocument member = membershipService.requireMember(channel.serverId, userId);
            if (!message.senderId.equals(userId) && member.role != MemberRole.OWNER) {
                throw new ApiException(ErrorCode.RESOURCE_FORBIDDEN, "Ban khong co quyen xoa message nay.");
            }
            return;
        }
        requireDirectConversation(userId, message.conversationId);
        if (!message.senderId.equals(userId)) {
            throw new ApiException(ErrorCode.RESOURCE_FORBIDDEN, "You can only delete your own direct messages.");
        }
    }

    public MessageScope scope(MessageDocument message) {
        return message.scope == null ? MessageScope.SERVER : message.scope;
    }
}
