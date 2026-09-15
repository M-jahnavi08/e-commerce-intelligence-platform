package com.commerce.intelligence.repository;

import com.commerce.intelligence.domain.Product;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProductRepository
  extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product>
{
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Product p where p.id = :id")
  Optional<Product> lockById(@Param("id") UUID id);

  Page<Product> findByActiveTrue(Pageable page);
}
