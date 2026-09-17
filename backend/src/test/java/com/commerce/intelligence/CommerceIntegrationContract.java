package com.commerce.intelligence;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.commerce.intelligence.events.SalesProjection;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
  properties = {
    "app.jwt-secret=test-only-secret-with-more-than-thirty-two-bytes",
    "app.ml-token=test-only-service-token-with-thirty-two-bytes",
    "app.events-enabled=false",
    "app.cache-enabled=false",
    "app.bootstrap-admin-email=",
    "app.bootstrap-admin-password=",
    "management.health.redis.enabled=false",
  }
)
@AutoConfigureMockMvc
abstract class CommerceIntegrationContract {

  @Autowired
  MockMvc mvc;

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  ObjectMapper json;

  @Autowired
  PlatformTransactionManager transactions;

  String admin;
  String customer;
  String product;

  @BeforeEach
  void setup() throws Exception {
    jdbc.execute("TRUNCATE categories, users, outbox_events, processed_events RESTART IDENTITY CASCADE");
    admin = register("admin@example.test");
    jdbc.update(
      "update users set role='ADMIN' where email='admin@example.test'"
    );
    admin = login("admin@example.test");
    customer = register("customer@example.test");
    String category = request(
      "/api/admin/categories",
      admin,
      Map.of("name", "Audio")
    )
      .get("id")
      .asText();
    product = request(
      "/api/admin/products",
      admin,
      Map.of(
        "sku",
        "HEAD-01",
        "name",
        "Studio headphones",
        "description",
        "Wireless studio headphones",
        "categoryId",
        category,
        "price",
        29.95,
        "stock",
        5
      )
    )
      .get("id")
      .asText();
  }

  String register(String email) throws Exception {
    return request(
      "/api/auth/register",
      null,
      Map.of("email", email, "password", "testing-password-123")
    )
      .get("token")
      .asText();
  }

  String login(String email) throws Exception {
    return request(
      "/api/auth/login",
      null,
      Map.of("email", email, "password", "testing-password-123")
    )
      .get("token")
      .asText();
  }

  JsonNode request(String path, String token, Object body) throws Exception {
    var req = post(path)
      .contentType("application/json")
      .content(json.writeValueAsString(body));
    if (token != null) req.header("Authorization", "Bearer " + token);
    var result = mvc.perform(req).andReturn();
    assertThat(result.getResponse().getStatus()).isBetween(200, 201);
    return json.readTree(result.getResponse().getContentAsString());
  }

  void cart(String token, int quantity) throws Exception {
    mvc
      .perform(
        put("/api/cart/" + product)
          .header("Authorization", "Bearer " + token)
          .contentType("application/json")
          .content("{\"quantity\":" + quantity + "}")
      )
      .andExpect(status().isOk());
  }

  org.springframework.test.web.servlet.ResultActions checkout(
    String token,
    String key,
    boolean decline
  ) throws Exception {
    return mvc.perform(
      post("/api/checkout")
        .header("Authorization", "Bearer " + token)
        .header("Idempotency-Key", key)
        .contentType("application/json")
        .content("{\"simulateDecline\":" + decline + "}")
    );
  }

  @Test
  void archivedProductsRemainRemovableAndStaleEditsConflict() throws Exception {
    cart(customer, 2);
    var p = json.readTree(mvc.perform(get("/api/products/" + product)).andReturn().getResponse().getContentAsString());
    var edit = Map.of("name", "Updated headphones", "description", "Updated description", "categoryId", p.get("categoryId").asText(), "price", 1499, "active", false, "version", p.get("version").asLong(), "imageUrl", "/images/products/headphones.svg");
    var unsafe = new HashMap<String, Object>(edit);
    unsafe.put("imageUrl", "https://untrusted.example/tracker.svg");
    mvc.perform(put("/api/admin/products/" + product).header("Authorization", "Bearer " + admin).contentType("application/json").content(json.writeValueAsString(unsafe))).andExpect(status().isBadRequest());
    String body = json.writeValueAsString(edit);
    mvc.perform(put("/api/admin/products/" + product).header("Authorization", "Bearer " + customer).contentType("application/json").content(body)).andExpect(status().isForbidden());
    mvc.perform(put("/api/admin/products/" + product).header("Authorization", "Bearer " + admin).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.imageUrl").value("/images/products/headphones.svg"));
    mvc.perform(put("/api/admin/products/" + product).header("Authorization", "Bearer " + admin).contentType("application/json").content(body)).andExpect(status().isConflict());
    mvc.perform(get("/api/products/" + product)).andExpect(status().isNotFound());
    mvc.perform(get("/api/cart").header("Authorization", "Bearer " + customer)).andExpect(status().isOk()).andExpect(jsonPath("$[0].stock").value(0)).andExpect(jsonPath("$[0].price").value(1499));
    checkout(customer, UUID.randomUUID().toString(), false).andExpect(status().isNotFound());
    cart(customer, 0);
    mvc.perform(get("/api/cart").header("Authorization", "Bearer " + customer)).andExpect(jsonPath("$.length()").value(0));
    var restored = new java.util.HashMap<String, Object>(edit);
    restored.put("active", true);
    restored.put("version", 1);
    mvc.perform(put("/api/admin/products/" + product).header("Authorization", "Bearer " + admin).contentType("application/json").content(json.writeValueAsString(restored))).andExpect(status().isOk());
    cart(customer, 2);
    checkout(customer, UUID.randomUUID().toString(), false).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2998));
    mvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + customer)).andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + admin)).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].items[0].unitPrice").value(1499));
    mvc.perform(get("/api/admin/orders?size=101").header("Authorization", "Bearer " + admin)).andExpect(status().isBadRequest());
    mvc.perform(get("/api/admin/inventory/" + product + "/movements").header("Authorization", "Bearer " + customer)).andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/inventory/" + product + "/movements").header("Authorization", "Bearer " + admin)).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].delta").value(-2));

  }

  @Test
  void concurrentAdditionsPreserveBothUnits() throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      CountDownLatch go = new CountDownLatch(1);
      var jobs = java.util.stream.IntStream.range(0, 2).mapToObj(i -> executor.submit(() -> {
        go.await();
        return mvc.perform(post("/api/cart/" + product)
          .header("Authorization", "Bearer " + customer)).andReturn().getResponse().getStatus();
      })).toList();
      go.countDown();
      for (var job : jobs) assertThat(job.get(15, TimeUnit.SECONDS)).isEqualTo(200);
    }
    mvc.perform(get("/api/cart").header("Authorization", "Bearer " + customer))
      .andExpect(jsonPath("$[0].quantity").value(2));
  }

  @Test
  void publicCatalogAndRoleBoundaries() throws Exception {
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    mvc
      .perform(get("/api/products").param("q", "studio"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalElements").value(1))
      .andExpect(jsonPath("$.content[0].imageUrl").value("/images/products/placeholder.svg"));
    mvc
      .perform(get("/api/products").param("q", "%"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalElements").value(0));
    mvc
      .perform(get("/api/products").param("min", "-1"))
      .andExpect(status().isBadRequest());
    mvc
      .perform(get("/api/admin/analytics"))
      .andExpect(status().isUnauthorized());
    mvc
      .perform(
        get("/api/admin/analytics").header(
          "Authorization",
          "Bearer " + customer
        )
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(
        get("/api/cart").header("Authorization", "Bearer broken.token.value")
      )
      .andExpect(status().isUnauthorized());
  }

  @Test
  void checkoutIsAtomicAndIdempotentAndAnalyticsAreReal() throws Exception {
    cart(customer, 2);
    String key = UUID.randomUUID().toString();
    String first = checkout(customer, key, false)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.total").value(59.90))
      .andExpect(jsonPath("$.items[0].imageUrl").value("/images/products/placeholder.svg"))
      .andReturn()
      .getResponse()
      .getContentAsString();
    checkout(customer, key, false)
      .andExpect(status().isOk())
      .andExpect(content().json(first));
    assertThat(
      jdbc.queryForObject("select quantity from inventory", Integer.class)
    ).isEqualTo(3);
    assertThat(
      jdbc.queryForObject("select count(*) from outbox_events", Integer.class)
    ).isEqualTo(1);
    assertThat(
      jdbc.queryForObject("select count(*) from cart_items", Integer.class)
    ).isZero();
    mvc
      .perform(
        get("/api/admin/analytics").header("Authorization", "Bearer " + admin)
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.summary.orders").value(1))
      .andExpect(jsonPath("$.summary.revenue").value(59.90));
    mvc.perform(get("/api/admin/analytics").header("Authorization", "Bearer " + admin))
      .andExpect(jsonPath("$.currency").value("INR"));
    mvc.perform(get("/api/profile").header("Authorization", "Bearer " + customer))
      .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("customer@example.test"))
      .andExpect(jsonPath("$.purchases.orders").value(1));
    mvc.perform(put("/api/profile").header("Authorization", "Bearer " + customer).contentType("application/json").content("{\"displayName\":\"Asha Rao\"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Asha Rao"));
    mvc.perform(get("/api/admin/customers").header("Authorization", "Bearer " + customer)).andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/customers").header("Authorization", "Bearer " + admin)).andExpect(jsonPath("$.totalElements").value(1));
    String oid = json.readTree(first).get("id").asText();
    mvc.perform(put("/api/admin/orders/" + oid + "/fulfillment").header("Authorization", "Bearer " + customer).contentType("application/json").content("{\"status\":\"PROCESSING\",\"version\":0}"))
      .andExpect(status().isForbidden());
    mvc.perform(put("/api/admin/orders/" + oid + "/fulfillment").header("Authorization", "Bearer " + admin).contentType("application/json").content("{\"status\":\"DELIVERED\",\"version\":0}"))
      .andExpect(status().isConflict());
    mvc.perform(put("/api/admin/orders/" + oid + "/fulfillment").header("Authorization", "Bearer " + admin).contentType("application/json").content("{\"status\":\"PROCESSING\",\"version\":0}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.fulfillmentStatus").value("PROCESSING"));
    mvc.perform(put("/api/admin/orders/" + oid + "/fulfillment").header("Authorization", "Bearer " + admin).contentType("application/json").content("{\"status\":\"SHIPPED\",\"version\":0}"))
      .andExpect(status().isConflict());
    String other = register("other@example.test");
    mvc
      .perform(get("/api/orders").header("Authorization", "Bearer " + other))
      .andExpect(status().isOk())
      .andExpect(content().json("[]"));
  }

  @Test
  void declinedPaymentPreservesCartAndStock() throws Exception {
    cart(customer, 2);
    checkout(customer, UUID.randomUUID().toString(), true)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("PAYMENT_FAILED"));
    assertThat(
      jdbc.queryForObject("select quantity from inventory", Integer.class)
    ).isEqualTo(5);
    assertThat(
      jdbc.queryForObject("select count(*) from cart_items", Integer.class)
    ).isEqualTo(1);
    assertThat(
      jdbc.queryForObject("select count(*) from outbox_events", Integer.class)
    ).isZero();
    mvc
      .perform(
        get("/api/admin/analytics").header("Authorization", "Bearer " + admin)
      )
      .andExpect(jsonPath("$.summary.revenue").value(0));
  }

  @Test
  void concurrentCustomersCannotOversell() throws Exception {
    String other = register("racer@example.test");
    cart(customer, 4);
    cart(other, 4);
    try (var executor = Executors.newFixedThreadPool(2)) {
      CountDownLatch ready = new CountDownLatch(2),
        go = new CountDownLatch(1);
      var jobs = List.of(customer, other)
        .stream()
        .map(token ->
          executor.submit(() -> {
            ready.countDown();
            go.await();
            return checkout(token, UUID.randomUUID().toString(), false)
              .andReturn()
              .getResponse()
              .getStatus();
          })
        )
        .toList();
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      assertThat(
        List.of(
          jobs.get(0).get(15, TimeUnit.SECONDS),
          jobs.get(1).get(15, TimeUnit.SECONDS)
        )
      ).containsExactlyInAnyOrder(200, 409);
    }
    assertThat(
      jdbc.queryForObject("select quantity from inventory", Integer.class)
    ).isEqualTo(1);
    assertThat(
      jdbc.queryForObject("select count(*) from orders", Integer.class)
    ).isEqualTo(1);
  }

  @Test
  void duplicateEventsAreAppliedOnce() throws Exception {
    cart(customer, 1);
    checkout(customer, UUID.randomUUID().toString(), false).andExpect(
      status().isOk()
    );
    String payload = jdbc.queryForObject(
      "select payload from outbox_events",
      String.class
    );
    assertThat(json.readTree(payload).get("currency").asText()).isEqualTo("INR");
    // A legacy message can remain in Kafka after database redenomination.
    var legacy = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(payload);
    legacy.remove("currency");
    ((com.fasterxml.jackson.databind.node.ObjectNode) legacy.get("items").get(0)).put("unitPrice", 1);
    final String legacyPayload = json.writeValueAsString(legacy);
    var projection = new SalesProjection(jdbc, json);
    var tx = new TransactionTemplate(transactions);
    for (int i = 0; i < 2; i++) tx.executeWithoutResult(s -> {
      try {
        projection.consume(legacyPayload);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
    assertThat(
      jdbc.queryForObject(
        "select count(*) from processed_events",
        Integer.class
      )
    ).isEqualTo(1);
    assertThat(jdbc.queryForObject("select revenue from sales_facts", java.math.BigDecimal.class))
      .isEqualByComparingTo("29.95");
    assertThat(
      jdbc.queryForObject("select quantity from sales_facts", Integer.class)
    ).isEqualTo(1);
  }

  @Test
  void invalidEventDoesNotBecomeProcessed() {
    var projection = new SalesProjection(jdbc, json);
    assertThatThrownBy(() -> projection.consume("{\"eventId\":\"" + UUID.randomUUID() + "\",\"items\":[]}"))
      .isInstanceOf(IllegalArgumentException.class);
    assertThat(jdbc.queryForObject("select count(*) from processed_events", Integer.class)).isZero();
  }

  @Test
  void invalidStockAdjustmentRollsBack() throws Exception {
    mvc
      .perform(
        post("/api/admin/inventory/" + product + "/adjustments")
          .header("Authorization", "Bearer " + admin)
          .contentType("application/json")
          .content("{\"delta\":-6,\"reason\":\"Damaged\"}")
      )
      .andExpect(status().isConflict());
    assertThat(
      jdbc.queryForObject("select quantity from inventory", Integer.class)
    ).isEqualTo(5);
  }
}
