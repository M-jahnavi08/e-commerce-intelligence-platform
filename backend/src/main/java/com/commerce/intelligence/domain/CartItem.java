package com.commerce.intelligence.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cart_items")
public class CartItem {

  @Id
  public UUID id;

  public UUID userId;
  public UUID productId;
  public int quantity;
}
