package com.commerce.intelligence.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class PurchaseOrder {

  @Id
  public UUID id;

  public UUID userId;
  public UUID idempotencyKey;

  @Column(length = 30)
  public String status;

  @Column(precision = 14, scale = 2)
  public BigDecimal total;

  public Instant createdAt;
}
