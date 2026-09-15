package com.commerce.intelligence.repository;

import com.commerce.intelligence.domain.Account;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {
  Optional<Account> findByEmail(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Account a where a.id = :id")
  Optional<Account> lockById(@Param("id") UUID id);
}
