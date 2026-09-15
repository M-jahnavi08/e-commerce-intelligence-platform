package com.commerce.intelligence.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.events-enabled", havingValue = "true")
public class SalesProjection {

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public SalesProjection(JdbcTemplate j, ObjectMapper o) {
    jdbc = j;
    json = o;
  }

  @KafkaListener(topics = "commerce.orders")
  @Transactional
  public void consume(String payload) throws Exception {
    var event = json.readTree(payload);
    String currency = event.path("currency").asText("USD");
    if (!currency.equals("INR") && !currency.equals("USD")) {
      throw new IllegalArgumentException("Unsupported event currency");
    }
    var lines = event.required("items");
    if (!lines.isArray() || lines.isEmpty()) throw new IllegalArgumentException("Order event must contain items");
    for (var item : lines) {
      var quantity = item.required("quantity");
      var price = item.required("unitPrice");
      if (!quantity.isIntegralNumber() || !quantity.canConvertToInt() || quantity.intValue() <= 0 ||
          !price.isNumber() || price.decimalValue().signum() < 0) {
        throw new IllegalArgumentException("Invalid order event quantity or price");
      }
    }
    UUID eventId = UUID.fromString(event.required("eventId").asText());
    int inserted = jdbc.update(
      "insert into processed_events(id,processed_at) values (?,now()) on conflict do nothing",
      eventId
    );
    if (inserted == 0) return;
    UUID order = UUID.fromString(event.required("orderId").asText());
    Timestamp time = Timestamp.from(
      Instant.parse(event.required("occurredAt").asText())
    );
    for (var item : lines) {
      int quantity = item.required("quantity").asInt();
      UUID productId = UUID.fromString(item.required("productId").asText());
      // Old events can already be queued in Kafka when the INR migration runs.
      // Their order snapshots are authoritative after redenomination.
      var unitPrice = currency.equals("INR") ? item.required("unitPrice").decimalValue() :
        jdbc.queryForObject("select unit_price from order_items where order_id=? and product_id=?",
          java.math.BigDecimal.class, order, productId);
      var revenue = unitPrice.multiply(java.math.BigDecimal.valueOf(quantity));
      jdbc.update(
        "insert into sales_facts(order_id,product_id,quantity,revenue,occurred_at) values (?,?,?,?,?) on conflict do nothing",
        order,
        productId,
        quantity,
        revenue,
        time
      );
    }
  }
}
