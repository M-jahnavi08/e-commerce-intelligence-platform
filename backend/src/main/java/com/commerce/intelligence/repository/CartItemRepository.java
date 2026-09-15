package com.commerce.intelligence.repository;

import com.commerce.intelligence.domain.CartItem;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
  List<CartItem> findByUserIdOrderByProductId(UUID userId);
  Optional<CartItem> findByUserIdAndProductId(UUID userId, UUID productId);
  void deleteByUserId(UUID userId);
}
