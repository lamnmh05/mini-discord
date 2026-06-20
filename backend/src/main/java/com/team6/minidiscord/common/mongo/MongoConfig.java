package com.team6.minidiscord.common.mongo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

@Configuration
public class MongoConfig {
    @Bean
    MongoDatabaseFactory mongoDatabaseFactory(@Value("${MONGODB_URI:mongodb://localhost:27017/mini_discord?directConnection=true&replicaSet=rs0}") String mongoUri) {
        return new SimpleMongoClientDatabaseFactory(mongoUri);
    }

    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }
}
