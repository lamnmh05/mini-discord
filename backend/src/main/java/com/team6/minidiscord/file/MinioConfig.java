package com.team6.minidiscord.file;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {
    @Bean
    MinioClient minioClient(
            @Value("${app.minio.endpoint}") String endpoint,
            @Value("${app.minio.access-key}") String accessKey,
            @Value("${app.minio.secret-key}") String secretKey
    ) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    MinioBucketInitializer minioBucketInitializer(MinioClient client, @Value("${app.file.bucket}") String bucket) {
        return new MinioBucketInitializer(client, bucket);
    }

    public static class MinioBucketInitializer {
        public MinioBucketInitializer(MinioClient client, String bucket) {
            try {
                boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
            } catch (Exception ex) {
                throw new IllegalStateException("Cannot initialize MinIO bucket " + bucket, ex);
            }
        }
    }
}
