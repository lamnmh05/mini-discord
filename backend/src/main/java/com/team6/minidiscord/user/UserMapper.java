package com.team6.minidiscord.user;

import com.team6.minidiscord.user.dto.CurrentUserResponse;

public final class UserMapper {
    private UserMapper() {
    }

    public static CurrentUserResponse current(UserDocument user) {
        return new CurrentUserResponse(
                user.id.toHexString(),
                user.username,
                user.email,
                user.displayName,
                user.avatarUrl,
                user.customStatus,
                user.accountStatus,
                user.lastSeenAt
        );
    }
}
