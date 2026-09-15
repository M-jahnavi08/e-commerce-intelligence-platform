package com.commerce.intelligence.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class OrderController {

  private final CartService cart;
  private final OrderService orders;

  public OrderController(CartService c, OrderService o) {
    cart = c;
    orders = o;
  }

  public record SetQuantity(@Min(0) @Max(100) int quantity) {}

  public record Payment(boolean simulateDecline) {}

  @GetMapping("/cart")
  public List<CartService.Line> cart(@AuthenticationPrincipal Jwt jwt) {
    return cart.get(UUID.fromString(jwt.getSubject()));
  }

  @PutMapping("/cart/{product}")
  public List<CartService.Line> set(
    @AuthenticationPrincipal Jwt jwt,
    @PathVariable UUID product,
    @Valid @RequestBody SetQuantity r
  ) {
    return cart.set(UUID.fromString(jwt.getSubject()), product, r.quantity());
  }

  @PostMapping("/cart/{product}")
  public List<CartService.Line> add(
    @AuthenticationPrincipal Jwt jwt,
    @PathVariable UUID product
  ) {
    return cart.add(UUID.fromString(jwt.getSubject()), product);
  }

  @PostMapping("/checkout")
  public OrderService.OrderView checkout(
    @AuthenticationPrincipal Jwt jwt,
    @RequestHeader("Idempotency-Key") UUID key,
    @RequestBody Payment p
  ) {
    return orders.checkout(
      UUID.fromString(jwt.getSubject()),
      key,
      p.simulateDecline()
    );
  }

  @GetMapping("/orders")
  public List<OrderService.OrderView> orders(@AuthenticationPrincipal Jwt jwt) {
    return orders.list(UUID.fromString(jwt.getSubject()));
  }
}
