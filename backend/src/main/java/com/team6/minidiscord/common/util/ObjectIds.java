package com.team6.minidiscord.common.util;

import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import org.bson.types.ObjectId;

public final class ObjectIds {
    private ObjectIds() {
    }

    public static ObjectId parse(String value) {
        if (value == null || !ObjectId.isValid(value)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "ObjectId không hợp lệ.");
        }
        return new ObjectId(value);
    }
}
