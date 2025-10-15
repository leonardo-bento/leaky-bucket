package org.example.leakybucket.config;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Configuration
public class SqsContainerConfig {

    private final SqsAsyncClient sqsAsyncClient;

    // Inject the property value using @Value
    @Value("${app.sqs.max-messages-per-poll:1}") // Defaulting to 10 is common
    private int maxMessagesPerPoll;

    public SqsContainerConfig(SqsAsyncClient sqsAsyncClient) {
        this.sqsAsyncClient = sqsAsyncClient;
    }

    /**
     * Creates a custom SqsMessageListenerContainerFactory to control polling.
     * This bean replaces the default auto-configured factory.
     */
    @Bean
    public SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory() {

        return SqsMessageListenerContainerFactory.builder()
            .configure(options -> options
                .maxMessagesPerPoll(maxMessagesPerPoll)
                .maxConcurrentMessages(maxMessagesPerPoll)
            )
            .sqsAsyncClient(this.sqsAsyncClient)
            .build();
    }
}