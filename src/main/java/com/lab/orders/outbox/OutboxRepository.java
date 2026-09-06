package com.lab.orders.outbox;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

	List<OutboxEvent> findByAggregateIdOrderByCreatedAtAsc(UUID aggregateId);

	long countByStatus(OutboxStatus status);

	long countByEventType(String eventType);

	List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
