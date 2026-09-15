package com.commerce.intelligence.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class Category {

  @Id
  public UUID id;

  @Column(nullable = false, unique = true, length = 100)
  public String name;
}
