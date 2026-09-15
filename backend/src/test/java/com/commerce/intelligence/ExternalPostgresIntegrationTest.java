package com.commerce.intelligence;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Opt-in disposable database. Never point the truncating contract at application data. */
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+/commerce_verification")
class ExternalPostgresIntegrationTest extends CommerceIntegrationContract {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
    registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USER"));
    registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
  }
}
