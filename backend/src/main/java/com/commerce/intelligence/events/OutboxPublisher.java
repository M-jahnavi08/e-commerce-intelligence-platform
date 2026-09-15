package com.commerce.intelligence.events;

import com.commerce.intelligence.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.events-enabled", havingValue = "true")
public class OutboxPublisher {

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(
    OutboxPublisher.class
  );
  private final OutboxEventRepository events;
  private final KafkaTemplate<String, String> kafka;

  public OutboxPublisher(
    OutboxEventRepository e,
    KafkaTemplate<String, String> k
  ) {
    events = e;
    kafka = k;
  }

  @Scheduled(fixedDelayString = "${app.outbox-delay:3000}")
  @Transactional
  public void publish() {
    for (var e : events.lockPending()) {
      try {
        kafka
          .send("commerce.orders", e.aggregateId.toString(), e.payload)
          .get(12, TimeUnit.SECONDS);
        e.publishedAt = Instant.now();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception ex) {
        log.warn(
          "Outbox delivery failed for event {}; it will be retried",
          e.id
        );
        return;
      }
    }
  }
}
