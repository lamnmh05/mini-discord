package com.team6.minidiscord.user;

import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.common.util.Keys;
import com.team6.minidiscord.user.dto.CurrentUserResponse;
import com.team6.minidiscord.user.dto.UpdateProfileRequest;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserDocument getActiveUser(ObjectId userId) {
        UserDocument user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User không tồn tại."));
        if (user.accountStatus != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.RESOURCE_FORBIDDEN, "Tài khoản không hoạt động.");
        }
        return user;
    }

    public CurrentUserResponse currentProfile(ObjectId userId) {
        return UserMapper.current(getActiveUser(userId));
    }

    public CurrentUserResponse updateProfile(ObjectId userId, UpdateProfileRequest request) {
        UserDocument user = getActiveUser(userId);
        if (request.username() != null && !request.username().equals(user.username)) {
            String usernameKey = Keys.normalize(request.username());
            userRepository.findByUsernameKey(usernameKey)
                    .filter(existing -> !existing.id.equals(userId))
                    .ifPresent(existing -> {
                        throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Username đã được sử dụng.");
                    });
            user.username = request.username().trim();
            user.usernameKey = usernameKey;
        }
        if (request.displayName() != null) {
            user.displayName = request.displayName().trim();
        }
        if (request.customStatus() != null) {
            user.customStatus = request.customStatus().trim();
        }
        if (request.avatarUrl() != null) {
            user.avatarUrl = request.avatarUrl();
        }
        user.updatedAt = Instant.now();
        return UserMapper.current(userRepository.save(user));
    }
}
