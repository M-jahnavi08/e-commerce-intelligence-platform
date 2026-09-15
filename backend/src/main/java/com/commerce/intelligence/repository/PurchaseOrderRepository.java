package com.commerce.intelligence.repository;

import com.commerce.intelligence.domain.PurchaseOrder;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository
  extends JpaRepository<PurchaseOrder, UUID>
{
  Optional<PurchaseOrder> findByUserIdAndIdempotencyKey(
    UUID userId,
    UUID idempotencyKey
  );
  List<PurchaseOrder> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
