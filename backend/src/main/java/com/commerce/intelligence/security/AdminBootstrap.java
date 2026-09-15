package com.commerce.intelligence.security;

import com.commerce.intelligence.domain.Account;
import com.commerce.intelligence.repository.AccountRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrap {

  @Bean
  ApplicationRunner admin(
    AccountRepository accounts,
    PasswordEncoder encoder,
    @Value("${app.bootstrap-admin-email}") String email,
    @Value("${app.bootstrap-admin-password}") String password
  ) {
    return args -> {
      if (email.isBlank()) return;
      if (
        password.length() < 12 || password.length() > 72
      ) throw new IllegalStateException(
        "Admin password must be 12-72 characters"
      );
      String normalized = email.trim().toLowerCase(Locale.ROOT);
      if (accounts.findByEmail(normalized).isEmpty()) {
        Account a = new Account();
        a.id = UUID.randomUUID();
        a.email = normalized;
        a.passwordHash = encoder.encode(password);
        a.role = "ADMIN";
        a.createdAt = Instant.now();
        accounts.save(a);
      }
    };
  }
}
