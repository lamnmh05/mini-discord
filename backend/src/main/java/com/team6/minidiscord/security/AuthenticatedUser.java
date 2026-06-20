package com.team6.minidiscord.security;

import org.bson.types.ObjectId;

import java.security.Principal;

public record AuthenticatedUser(ObjectId id, String username) implements Principal {
    @Override
    public String getName() {
        return id.toHexString();
    }
}
