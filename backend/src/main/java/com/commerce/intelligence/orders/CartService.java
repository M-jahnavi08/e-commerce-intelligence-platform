package com.commerce.intelligence.orders;

import com.commerce.intelligence.api.ApiException;
import com.commerce.intelligence.catalog.CatalogService;
import com.commerce.intelligence.domain.*;
import com.commerce.intelligence.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

  private final CartItemRepository carts;
  private final AccountRepository accounts;
  private final CatalogService catalog;

  public CartService(
    CartItemRepository c,
    AccountRepository a,
    CatalogService p
  ) {
    carts = c;
    accounts = a;
    catalog = p;
  }

  public record Line(
    UUID productId,
    String name,
    String imageUrl,
    java.math.BigDecimal price,
    int quantity,
    int stock
  ) {}

  @Transactional(readOnly = true)
  public List<Line> get(UUID user) {
    return carts
      .findByUserIdOrderByProductId(user)
      .stream()
      .map(c -> {
        var p = catalog.getIncludingInactive(c.productId);
        return new Line(
          c.productId,
          p.name(),
          p.imageUrl(),
          p.price(),
          c.quantity,
          p.active() ? p.stock() : 0
        );
      })
      .toList();
  }

  @Transactional
  public List<Line> add(UUID user, UUID product) {
    accounts.lockById(user).orElseThrow(ApiException::missing);
    int quantity = carts.findByUserIdAndProductId(user, product)
      .map(item -> item.quantity).orElse(0);
    if (quantity >= 100) throw ApiException.conflict("Cart quantity cannot exceed 100");
    return set(user, product, quantity + 1);
  }

  @Transactional
  public List<Line> set(UUID user, UUID product, int quantity) {
    accounts.lockById(user).orElseThrow(ApiException::missing);
    var existing = carts.findByUserIdAndProductId(user, product);
    if (quantity == 0) {
      existing.ifPresent(carts::delete);
    } else {
      var p = catalog.get(product);
      if (quantity > p.stock()) throw ApiException.conflict(
        "Not enough inventory"
      );
      CartItem item = existing.orElseGet(() -> {
        CartItem c = new CartItem();
        c.id = UUID.randomUUID();
        c.userId = user;
        c.productId = product;
        return c;
      });
      item.quantity = quantity;
      carts.save(item);
    }
    carts.flush();
    return get(user);
  }
}
