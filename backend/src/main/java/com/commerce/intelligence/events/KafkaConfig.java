package com.commerce.intelligence.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "app.events-enabled", havingValue = "true")
public class KafkaConfig {

  @org.springframework.beans.factory.annotation.Value("${app.kafka-replicas:1}")
  private int replicas;

  @Bean
  NewTopic ordersTopic() {
    return TopicBuilder.name("commerce.orders")
      .partitions(3)
      .replicas(replicas)
      .config("min.insync.replicas", replicas >= 3 ? "2" : "1")
      .build();
  }

  @Bean
  NewTopic deadLetterTopic() {
    return TopicBuilder.name("commerce.orders.DLT")
      .partitions(3)
      .replicas(replicas)
      .config("min.insync.replicas", replicas >= 3 ? "2" : "1")
      .build();
  }

  @Bean
  org.springframework.kafka.listener.DefaultErrorHandler kafkaErrorHandler(
    org.springframework.kafka.core.KafkaTemplate<String, String> template
  ) {
    var recoverer =
      new org.springframework.kafka.listener.DeadLetterPublishingRecoverer(
        template,
        (record, exception) -> new org.apache.kafka.common.TopicPartition(
          "commerce.orders.DLT", record.partition()
        )
      );
    recoverer.setFailIfSendResultIsError(true);
    return new org.springframework.kafka.listener.DefaultErrorHandler(
      recoverer,
      new org.springframework.util.backoff.FixedBackOff(1000, 3)
    );
  }
}
