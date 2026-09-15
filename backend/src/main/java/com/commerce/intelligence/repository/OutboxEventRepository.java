package com.commerce.intelligence.repository;

import com.commerce.intelligence.domain.OutboxEvent;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository
  extends JpaRepository<OutboxEvent, UUID>
{
  @Query(
    value = "select * from outbox_events where published_at is null order by created_at limit 50 for update skip locked",
    nativeQuery = true
  )
  List<OutboxEvent> lockPending();
}
