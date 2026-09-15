package com.commerce.intelligence.repository;

import com.commerce.intelligence.domain.OrderItem;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
  List<OrderItem> findByOrderIdOrderByProductId(UUID orderId);
}
