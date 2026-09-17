package com.commerce.intelligence.orders;

import com.commerce.intelligence.api.ApiException;
import com.commerce.intelligence.domain.*;
import com.commerce.intelligence.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

  private final AccountRepository accounts;
  private final CartItemRepository carts;
  private final ProductRepository products;
  private final InventoryRepository inventory;
  private final PurchaseOrderRepository orders;
  private final OrderItemRepository items;
  private final OutboxEventRepository outbox;
  private final ObjectMapper json;
  private final InventoryMovementRepository movements;

  public OrderService(
    AccountRepository a,
    CartItemRepository c,
    ProductRepository p,
    InventoryRepository i,
    PurchaseOrderRepository o,
    OrderItemRepository l,
    OutboxEventRepository e,
    ObjectMapper j,
    InventoryMovementRepository m
  ) {
    accounts = a;
    carts = c;
    products = p;
    inventory = i;
    orders = o;
    items = l;
    outbox = e;
    json = j;
    movements = m;
  }

  public record OrderView(
    UUID id,
    String status,
    BigDecimal total,
    Instant createdAt,
    List<OrderItem> items,
    String fulfillmentStatus,
    long version
  ) {}

  public OrderView view(PurchaseOrder o) {
    return new OrderView(
      o.id,
      o.status,
      o.total,
      o.createdAt,
      items.findByOrderIdOrderByProductId(o.id),
      o.fulfillmentStatus,
      o.version
    );
  }

  @Transactional(readOnly = true)
  public List<OrderView> list(UUID user) {
    return orders
      .findByUserIdOrderByCreatedAtDesc(user)
      .stream()
      .map(this::view)
      .toList();
  }

  @Transactional
  public OrderView checkout(UUID user, UUID key, boolean decline) {
    accounts.lockById(user).orElseThrow(ApiException::missing);
    var previous = orders.findByUserIdAndIdempotencyKey(user, key);
    if (previous.isPresent()) {
      if (
        previous.get().status.equals("PAYMENT_FAILED") != decline
      ) throw ApiException.conflict(
        "Idempotency key already used for a different payment request"
      );
      return view(previous.get());
    }
    var cart = carts.findByUserIdOrderByProductId(user);
    if (cart.isEmpty()) throw ApiException.conflict("Your cart is empty");
    PurchaseOrder order = new PurchaseOrder();
    order.id = UUID.randomUUID();
    order.userId = user;
    order.idempotencyKey = key;
    // PostgreSQL stores microseconds; keep first response and retries identical.
    order.createdAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    order.status = decline ? "PAYMENT_FAILED" : "PAID";
    order.total = BigDecimal.ZERO;
    List<OrderItem> lines = new ArrayList<>();
    for (var c : cart) {
      Product p = products
        .lockById(c.productId)
        .filter(x -> x.active)
        .orElseThrow(ApiException::missing);
      Inventory stock = inventory
        .lockById(p.id)
        .orElseThrow(ApiException::missing);
      if (stock.quantity < c.quantity) throw ApiException.conflict(
        "Insufficient inventory for " + p.name
      );
      OrderItem line = new OrderItem();
      line.id = UUID.randomUUID();
      line.orderId = order.id;
      line.productId = p.id;
      line.productName = p.name;
      line.imageUrl = p.imageUrl;
      line.quantity = c.quantity;
      line.unitPrice = p.price;
      lines.add(line);
      order.total = order.total.add(
        p.price.multiply(BigDecimal.valueOf(c.quantity))
      );
      if (!decline) {
        stock.quantity -= c.quantity;
        InventoryMovement m = new InventoryMovement();
        m.id = UUID.randomUUID();
        m.productId = p.id;
        m.actorId = user;
        m.delta = -c.quantity;
        m.reason = "Order " + order.id;
        m.createdAt = order.createdAt;
        movements.save(m);
      }
    }
    orders.save(order);
    items.saveAll(lines);
    if (!decline) {
      carts.deleteByUserId(user);
      OutboxEvent event = new OutboxEvent();
      event.id = UUID.randomUUID();
      event.aggregateId = order.id;
      event.eventType = "OrderPaid";
      event.createdAt = order.createdAt;
      try {
        event.payload = json.writeValueAsString(
          Map.of(
            "currency", "INR",
            "eventId",
            event.id,
            "orderId",
            order.id,
            "occurredAt",
            order.createdAt,
            "items",
            lines
          )
        );
      } catch (Exception ex) {
        throw new IllegalStateException("Cannot serialize order event", ex);
      }
      outbox.save(event);
    }
    return new OrderView(
      order.id,
      order.status,
      order.total,
      order.createdAt,
      lines,
      order.fulfillmentStatus,
      order.version
    );
  }
}
