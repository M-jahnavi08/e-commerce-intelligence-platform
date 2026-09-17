package com.commerce.intelligence.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class Account {

  @Id
  public UUID id;

  @Column(nullable = false, unique = true, length = 254)
  public String email;

  @Column(nullable = false, length = 80)
  public String displayName = "";

  @Column(nullable = false, length = 100)
  public String passwordHash;

  @Column(nullable = false, length = 20)
  public String role;

  @Column(nullable = false)
  public Instant createdAt;
}
