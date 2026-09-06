package com.lab.orders.dlq;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterRepository extends JpaRepository<DeadLetter, UUID> {

	List<DeadLetter> findByEventId(UUID eventId);

	long countByConsumerName(String consumerName);
}
