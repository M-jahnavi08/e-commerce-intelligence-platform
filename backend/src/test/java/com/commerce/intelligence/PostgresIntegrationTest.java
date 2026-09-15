package com.commerce.intelligence;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTest extends CommerceIntegrationContract {

  @Container
  static PostgreSQLContainer<?> database = new PostgreSQLContainer<>(
    "postgres:17-alpine"
  );

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", database::getJdbcUrl);
    r.add("spring.datasource.username", database::getUsername);
    r.add("spring.datasource.password", database::getPassword);
  }
}
