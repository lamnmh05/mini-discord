package com.team6.minidiscord.user;

import com.team6.minidiscord.common.api.ApiResponse;
import com.team6.minidiscord.security.SecurityUtils;
import com.team6.minidiscord.user.dto.CurrentUserResponse;
import com.team6.minidiscord.user.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me() {
        return ApiResponse.ok(userService.currentProfile(SecurityUtils.currentUserId()));
    }

    @PatchMapping("/me")
    public ApiResponse<CurrentUserResponse> update(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(SecurityUtils.currentUserId(), request));
    }
}
