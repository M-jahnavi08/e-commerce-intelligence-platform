package com.commerce.intelligence.admin;

import com.commerce.intelligence.api.ApiException;
import com.commerce.intelligence.catalog.CatalogService;
import com.commerce.intelligence.domain.*;
import com.commerce.intelligence.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import org.springframework.data.domain.*;
import com.commerce.intelligence.orders.OrderService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

  private final ProductRepository products;
  private final InventoryRepository inventory;
  private final InventoryMovementRepository movements;
  private final CatalogService catalog;
  private final JdbcTemplate jdbc;
  private final PurchaseOrderRepository orders;
  private final OrderService orderService;

  public AdminController(
    ProductRepository p,
    InventoryRepository i,
    InventoryMovementRepository m,
    CatalogService c,
    JdbcTemplate j,
    PurchaseOrderRepository orders,
    OrderService orderService
  ) {
    products = p;
    inventory = i;
    movements = m;
    catalog = c;
    jdbc = j;
    this.orders = orders;
    this.orderService = orderService;
  }

  @GetMapping("/inventory")
  @Transactional(readOnly = true)
  public List<CatalogService.ProductView> stock() {
    return catalog.views(products.findAll(Sort.by("name", "id")));
  }

  private PageRequest pageRequest(int page, int size) {
    if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException();
    return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
  }

  @GetMapping("/orders")
  @Transactional(readOnly = true)
  public Page<OrderService.OrderView> orders(@RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return orders.findAll(pageRequest(page, size)).map(orderService::view);
  }

  @GetMapping("/inventory/{id}/movements")
  @Transactional(readOnly = true)
  public Page<InventoryMovement> movements(@PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    if (!products.existsById(id)) throw ApiException.missing();
    return movements.findByProductId(id, pageRequest(page, size));
  }

  public record Adjustment(
    @Min(-1000000) @Max(1000000) int delta,
    @NotBlank @Size(max = 300) String reason
  ) {}

  @PostMapping("/inventory/{id}/adjustments")
  @Transactional
  public CatalogService.ProductView adjust(
    @PathVariable UUID id,
    @AuthenticationPrincipal Jwt jwt,
    @Valid @RequestBody Adjustment r
  ) {
    var stock = inventory.lockById(id).orElseThrow(ApiException::missing);
    long next = (long) stock.quantity + r.delta();
    if (next < 0 || next > Integer.MAX_VALUE) throw ApiException.conflict(
      "Adjustment would put inventory outside its valid range"
    );
    stock.quantity = (int) next;
    InventoryMovement m = new InventoryMovement();
    m.id = UUID.randomUUID();
    m.productId = id;
    m.actorId = UUID.fromString(jwt.getSubject());
    m.delta = r.delta();
    m.reason = r.reason();
    m.createdAt = Instant.now();
    movements.save(m);
    return catalog.view(
      products.findById(id).orElseThrow(ApiException::missing)
    );
  }

  @GetMapping("/analytics")
  public Map<String, Object> analytics() {
    var summary = jdbc.queryForMap(
      "select count(*) as orders, coalesce(sum(total),0) as revenue, coalesce(avg(total),0) as average_order_value from orders where status='PAID'"
    );
    var daily = jdbc.queryForList(
      "select (created_at at time zone 'UTC')::date as date,count(*) as orders,sum(total) as revenue from orders where status='PAID' and created_at>=current_timestamp-interval '30 days' group by 1 order by 1"
    );
    var top = jdbc.queryForList(
      "select i.product_id,p.name,sum(i.quantity) as units,sum(i.quantity*i.unit_price) as revenue from order_items i join orders o on i.order_id=o.id join products p on i.product_id=p.id where o.status='PAID' group by i.product_id,p.name order by revenue desc limit 10"
    );
    return Map.of(
      "summary",
      summary,
      "daily",
      daily,
      "topProducts",
      top,
      "source",
      "paid orders",
      "currency",
      "INR"
    );
  }
}
