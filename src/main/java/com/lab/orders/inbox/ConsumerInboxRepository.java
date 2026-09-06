package com.lab.orders.inbox;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsumerInboxRepository extends JpaRepository<ConsumerInbox, ConsumerInboxId> {

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			INSERT INTO consumer_inbox (consumer_name, event_id, processed_at)
			VALUES (:consumerName, :eventId, :processedAt)
			ON CONFLICT (consumer_name, event_id) DO NOTHING
			""", nativeQuery = true)
	int insertIgnore(
			@Param("consumerName") String consumerName,
			@Param("eventId") UUID eventId,
			@Param("processedAt") java.sql.Timestamp processedAt);

	boolean existsByConsumerNameAndEventId(String consumerName, UUID eventId);

	long countByConsumerName(String consumerName);
}
