package com.commerce.intelligence.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory")
public class Inventory {

  @Id
  public UUID productId;

  public int quantity;

  @Version
  public long version;
}
