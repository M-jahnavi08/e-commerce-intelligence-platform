package com.commerce.intelligence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.commerce.intelligence.domain.OutboxEvent;
import com.commerce.intelligence.events.OutboxPublisher;
import com.commerce.intelligence.repository.OutboxEventRepository;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class OutboxPublisherTest {

  @Test
  @SuppressWarnings("unchecked")
  void failedBrokerSendKeepsEventPending() {
    var repo = mock(OutboxEventRepository.class);
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    var e = new OutboxEvent();
    e.id = UUID.randomUUID();
    e.aggregateId = UUID.randomUUID();
    e.payload = "{}";
    when(repo.lockPending()).thenReturn(List.of(e));
    when(kafka.send(anyString(), anyString(), anyString())).thenReturn(
      CompletableFuture.failedFuture(new RuntimeException("offline"))
    );
    new OutboxPublisher(repo, kafka).publish();
    assertThat(e.publishedAt).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void marksPublishedOnlyAfterBrokerAcknowledges() {
    var repo = mock(OutboxEventRepository.class);
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    var e = new OutboxEvent();
    e.id = UUID.randomUUID();
    e.aggregateId = UUID.randomUUID();
    e.payload = "{}";
    when(repo.lockPending()).thenReturn(List.of(e));
    when(kafka.send(anyString(), anyString(), anyString())).thenReturn(
      CompletableFuture.completedFuture(mock(SendResult.class))
    );
    new OutboxPublisher(repo, kafka).publish();
    assertThat(e.publishedAt).isNotNull();
  }
  @Test
  @SuppressWarnings("unchecked")
  void batchStopsAtFailureAndPreservesUnsentEvents() {
    var repo = mock(OutboxEventRepository.class);
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    var batch = java.util.stream.IntStream.range(0, 3).mapToObj(i -> {
      var e = new OutboxEvent();
      e.id = UUID.randomUUID();
      e.aggregateId = UUID.randomUUID();
      e.payload = "{}";
      return e;
    }).toList();
    when(repo.lockPending()).thenReturn(batch);
    when(kafka.send(anyString(), anyString(), anyString()))
      .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)))
      .thenReturn(CompletableFuture.failedFuture(new RuntimeException("offline")));
    new OutboxPublisher(repo, kafka).publish();
    assertThat(batch.get(0).publishedAt).isNotNull();
    assertThat(batch.get(1).publishedAt).isNull();
    assertThat(batch.get(2).publishedAt).isNull();
    verify(kafka, times(2)).send(anyString(), anyString(), anyString());
  }
}
