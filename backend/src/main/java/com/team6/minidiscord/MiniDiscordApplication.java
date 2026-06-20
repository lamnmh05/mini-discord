package com.team6.minidiscord;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableMongoRepositories
@EnableTransactionManagement
public class MiniDiscordApplication {
    public static void main(String[] args) {
        SpringApplication.run(MiniDiscordApplication.class, args);
    }
}
