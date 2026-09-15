package com.commerce.intelligence.repository;

import com.commerce.intelligence.domain.Inventory;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from Inventory i where i.productId = :id")
  Optional<Inventory> lockById(@Param("id") UUID id);
}
