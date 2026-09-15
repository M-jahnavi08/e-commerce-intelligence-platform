package com.commerce.intelligence.repository;

import com.commerce.intelligence.domain.InventoryMovement;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface InventoryMovementRepository
  extends JpaRepository<InventoryMovement, UUID> {
  Page<InventoryMovement> findByProductId(UUID productId, Pageable pageable);
}
