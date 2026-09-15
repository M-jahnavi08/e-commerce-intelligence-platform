package com.commerce.intelligence.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

  @Id
  public UUID id;

  public UUID aggregateId;

  @Column(length = 80)
  public String eventType;

  @Column(columnDefinition = "text")
  public String payload;

  public Instant createdAt;
  public Instant publishedAt;
}
