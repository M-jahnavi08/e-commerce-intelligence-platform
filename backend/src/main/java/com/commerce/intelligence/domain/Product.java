package com.commerce.intelligence.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

  @Id
  public UUID id;

  @Column(nullable = false, unique = true, length = 64)
  public String sku;

  @Column(nullable = false, length = 200)
  public String name;

  @Column(nullable = false, length = 4000)
  public String description;

  @Column(nullable = false)
  public UUID categoryId;

  @Column(nullable = false, precision = 12, scale = 2)
  public BigDecimal price;

  @Column(nullable = false, length = 300)
  public String imageUrl = "/images/products/placeholder.svg";

  @Column(nullable = false)
  public boolean active = true;

  @Version
  public long version;
}
