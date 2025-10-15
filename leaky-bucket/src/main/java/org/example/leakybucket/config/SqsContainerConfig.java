package org.example.leakybucket.config;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Configuration
public class SqsContainerConfig {

    private static final int MAX_MESSAGES_PER_POLL = 1;
    private static final int MAX_CONCURRENT_MESSAGES = 1;

    private final SqsAsyncClient sqsAsyncClient;

    public SqsContainerConfig(SqsAsyncClient sqsAsyncClient) {
        this.sqsAsyncClient = sqsAsyncClient;
    }

    @Bean
    public SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory() {

        return SqsMessageListenerContainerFactory.builder()
            .configure(options -> options
                .maxMessagesPerPoll(MAX_MESSAGES_PER_POLL)
                .maxConcurrentMessages(MAX_CONCURRENT_MESSAGES)
            )
            .sqsAsyncClient(this.sqsAsyncClient)
            .build();
    }
}