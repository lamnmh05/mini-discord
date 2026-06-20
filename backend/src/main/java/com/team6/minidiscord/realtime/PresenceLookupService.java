package com.team6.minidiscord.realtime;

import org.bson.types.ObjectId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PresenceLookupService {
    private final StringRedisTemplate redisTemplate;

    public PresenceLookupService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String status(ObjectId userId) {
        Long size = redisTemplate.opsForSet().size(connectionsKey(userId.toHexString()));
        return size != null && size > 0 ? "ONLINE" : "OFFLINE";
    }

    String connectionsKey(String userId) {
        return "presence:user:" + userId + ":connections";
    }
}
