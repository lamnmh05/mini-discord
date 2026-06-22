package com.team6.minidiscord.message;

import com.team6.minidiscord.common.api.CursorPage;
import com.team6.minidiscord.message.dto.CreateMessageRequest;
import com.team6.minidiscord.message.dto.EditMessageRequest;
import com.team6.minidiscord.message.dto.MessageResponse;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

@Service
public class MessageService {
    private final MessageQueryService queryService;
    private final MessageCommandService commandService;

    public MessageService(MessageQueryService queryService, MessageCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    public CursorPage<MessageResponse> history(ObjectId userId, String channelIdValue, String cursor, Integer limit) {
        return queryService.channelHistory(userId, channelIdValue, cursor, limit);
    }

    public CursorPage<MessageResponse> directHistory(ObjectId userId, String conversationIdValue, String cursor, Integer limit) {
        return queryService.directHistory(userId, conversationIdValue, cursor, limit);
    }

    public CursorPage<MessageResponse> search(ObjectId userId, String serverIdValue, String q, String channelIdValue, String cursor, Integer limit) {
        return queryService.search(userId, serverIdValue, q, channelIdValue, cursor, limit);
    }

    public MessageResponse send(ObjectId userId, String channelIdValue, CreateMessageRequest request) {
        return commandService.sendToChannel(userId, channelIdValue, request);
    }

    public MessageResponse sendDirect(ObjectId userId, String conversationIdValue, CreateMessageRequest request) {
        return commandService.sendDirect(userId, conversationIdValue, request);
    }

    public MessageResponse edit(ObjectId userId, String messageIdValue, EditMessageRequest request) {
        return commandService.edit(userId, messageIdValue, request);
    }

    public void softDelete(ObjectId userId, String messageIdValue) {
        commandService.softDelete(userId, messageIdValue);
    }

    public MessageResponse addReaction(ObjectId userId, String messageIdValue, String emoji) {
        return commandService.addReaction(userId, messageIdValue, emoji);
    }

    public MessageResponse removeReaction(ObjectId userId, String messageIdValue, String emoji) {
        return commandService.removeReaction(userId, messageIdValue, emoji);
    }
}
