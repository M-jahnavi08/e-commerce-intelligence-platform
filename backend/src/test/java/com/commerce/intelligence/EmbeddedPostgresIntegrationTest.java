package com.commerce.intelligence;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfSystemProperty(named = "embedded.postgres", matches = "true")
class EmbeddedPostgresIntegrationTest extends CommerceIntegrationContract {

  static EmbeddedPostgres database;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) throws Exception {
    database = EmbeddedPostgres.builder()
      .setCleanDataDirectory(true)
      .setDataDirectory("target/embedded-pg-data")
      .start();
    Runtime.getRuntime().addShutdownHook(
      new Thread(() -> {
        try {
          database.close();
        } catch (Exception ignored) {}
      })
    );
    r.add("spring.datasource.url", () ->
      database.getJdbcUrl("postgres", "postgres")
    );
    r.add("spring.datasource.username", () -> "postgres");
    r.add("spring.datasource.password", () -> "");
  }
}
