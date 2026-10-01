package io.github.snipperxx.docintel.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic documentUploadedTopic(AppProperties properties) {
        return TopicBuilder.name(properties.kafka().documentUploadedTopic())
                .partitions(properties.kafka().partitions())
                .replicas(1)
                .build();
    }
}
