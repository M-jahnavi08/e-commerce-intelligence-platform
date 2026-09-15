package com.commerce.intelligence.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItem {

  @Id
  public UUID id;

  public UUID orderId;
  public UUID productId;

  @Column(length = 200)
  public String productName;

  @Column(nullable = false, length = 300)
  public String imageUrl = "/images/products/placeholder.svg";

  public int quantity;

  @Column(precision = 12, scale = 2)
  public BigDecimal unitPrice;
}
