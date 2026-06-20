package com.team6.minidiscord.membership.dto;

import com.team6.minidiscord.membership.MemberRole;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
        @NotNull
        MemberRole role
) {
}
