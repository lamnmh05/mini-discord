package com.team6.minidiscord.common.util;

import java.util.Locale;

public final class Keys {
    private Keys() {
    }

    public static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
