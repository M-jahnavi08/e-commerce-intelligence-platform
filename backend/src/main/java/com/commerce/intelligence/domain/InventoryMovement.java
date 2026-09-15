package com.commerce.intelligence.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {

  @Id
  public UUID id;

  public UUID productId;
  public UUID actorId;
  public int delta;

  @Column(length = 300)
  public String reason;

  public Instant createdAt;
}
