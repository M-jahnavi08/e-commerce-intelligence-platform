package com.commerce.intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.commerce.intelligence.api.ErrorHandler;
import com.commerce.intelligence.events.KafkaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

class KafkaBeanConfigurationTest {
  @Test
  void eventsEnabledRegistersBothErrorHandlersWithoutOverriding() {
    new ApplicationContextRunner()
      .withAllowBeanDefinitionOverriding(false)
      .withPropertyValues("app.events-enabled=true")
      .withUserConfiguration(ErrorHandler.class, KafkaConfig.class, BrokerStub.class)
      .run(context -> {
        assertThat(context).hasNotFailed();
        assertThat(context).hasSingleBean(ErrorHandler.class);
        assertThat(context).hasSingleBean(DefaultErrorHandler.class);
        assertThat(context.getBean("errorHandler")).isInstanceOf(ErrorHandler.class);
        assertThat(context.getBean("kafkaErrorHandler")).isInstanceOf(DefaultErrorHandler.class);
      });
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void malformedRecordIsPublishedToConfiguredDeadLetterTopic() {
    new ApplicationContextRunner()
      .withAllowBeanDefinitionOverriding(false)
      .withPropertyValues("app.events-enabled=true")
      .withUserConfiguration(ErrorHandler.class, KafkaConfig.class, BrokerStub.class)
      .run(context -> {
        assertThat(context).hasNotFailed();
        KafkaTemplate<String, String> template = context.getBean(KafkaTemplate.class);
        var factory = mock(org.springframework.kafka.core.ProducerFactory.class);
        when(factory.getConfigurationProperties()).thenReturn(java.util.Map.of());
        when(template.getProducerFactory()).thenReturn(factory);
        when(template.send(any(org.apache.kafka.clients.producer.ProducerRecord.class)))
          .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(mock(org.springframework.kafka.support.SendResult.class)));
        var consumer = mock(org.apache.kafka.clients.consumer.Consumer.class);
        when(consumer.partitionsFor(anyString(), any(java.time.Duration.class))).thenAnswer(call ->
          java.util.List.of(new org.apache.kafka.common.PartitionInfo(call.getArgument(0), 2, null,
            new org.apache.kafka.common.Node[0], new org.apache.kafka.common.Node[0])));
        when(consumer.groupMetadata()).thenReturn(new org.apache.kafka.clients.consumer.ConsumerGroupMetadata("commerce-projections"));
        var record = new org.apache.kafka.clients.consumer.ConsumerRecord<String, String>("commerce.orders", 2, 42L, "key", "{\"verificationPoison\":\"regression\"}");
        var container = mock(org.springframework.kafka.listener.MessageListenerContainer.class);
        var handler = context.getBean(DefaultErrorHandler.class);
        var failure = new IllegalArgumentException("Missing items");
        for (int attempt = 0; attempt < 3; attempt++) {
          assertThat(handler.handleOne(failure, record, consumer, container)).isFalse();
        }
        assertThat(handler.handleOne(failure, record, consumer, container)).isTrue();
        var sent = org.mockito.ArgumentCaptor.forClass(org.apache.kafka.clients.producer.ProducerRecord.class);
        verify(template).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo(context.getBean("deadLetterTopic", org.apache.kafka.clients.admin.NewTopic.class).name());
        assertThat(sent.getValue().partition()).isEqualTo(2);
        assertThat(sent.getValue().value()).isEqualTo(record.value());
      });
  }

  @Configuration(proxyBeanMethods = false)
  static class BrokerStub {
    @Bean
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, String> kafkaTemplate() {
      return mock(KafkaTemplate.class);
    }
  }
}
