package com.team6.minidiscord.support;

import com.team6.minidiscord.common.mongo.MongoIndexInitializer;
import io.minio.MinioClient;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;

public abstract class IntegrationTestSupport {
    private static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.3"));

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8"))
            .withExposedPorts(6379);

    static {
        Startables.deepStart(MONGO, REDIS).join();
    }

    @Autowired
    protected MongoTemplate mongoTemplate;

    @Autowired
    private MongoIndexInitializer mongoIndexInitializer;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("MONGODB_URI", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        registry.add("app.jwt.secret", () -> "test-secret-change-this-test-secret-change-this");
        registry.add("app.refresh-token.secure", () -> "false");
        registry.add("app.minio.endpoint", () -> "http://localhost:9000");
    }

    protected void cleanState() {
        mongoTemplate.getCollectionNames().stream()
                .filter(name -> !name.startsWith("system."))
                .forEach(name -> mongoTemplate.remove(new Query(), name));
        mongoIndexInitializer.createIndexes();

        Set<String> keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @TestConfiguration
    public static class IntegrationTestConfig {
        @Bean
        @Primary
        MinioClient testMinioClient() {
            return Mockito.mock(MinioClient.class);
        }
    }
}
