package com.lab.orders.outbox;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lab.orders.config.LabProperties;
import com.lab.orders.metrics.LabMetrics;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class OutboxClaimService {

	@PersistenceContext
	private EntityManager entityManager;

	private final LabProperties properties;
	private final Clock clock;
	private final LabMetrics metrics;

	public OutboxClaimService(LabProperties properties, Clock clock, LabMetrics metrics) {
		this.properties = properties;
		this.clock = clock;
		this.metrics = metrics;
	}

	@Transactional
	public List<OutboxEvent> claimBatch() {
		Instant now = clock.instant();
		@SuppressWarnings("unchecked")
		List<OutboxEvent> rows = entityManager.createNativeQuery(
				"""
						SELECT * FROM outbox_event
						WHERE (
						        status = 'PENDING'
						        AND next_attempt_at <= :now
						      )
						   OR (
						        status = 'IN_FLIGHT'
						        AND claimed_until IS NOT NULL
						        AND claimed_until < :now
						      )
						ORDER BY created_at
						LIMIT :batch
						FOR UPDATE SKIP LOCKED
						""",
				OutboxEvent.class)
				.setParameter("now", Timestamp.from(now))
				.setParameter("batch", properties.getOutbox().getClaimBatchSize())
				.getResultList();

		Instant leaseUntil = now.plusSeconds(properties.getOutbox().getLeaseSeconds());
		for (OutboxEvent row : rows) {
			row.markInFlight(leaseUntil, row.getAttemptCount() + 1);
		}
		refreshPendingGauge();
		return rows;
	}

	private void refreshPendingGauge() {
		List<OutboxEvent> pending = entityManager.createQuery(
				"select e from OutboxEvent e where e.status = com.lab.orders.outbox.OutboxStatus.PENDING",
				OutboxEvent.class)
				.getResultList();
		Instant oldest = pending.stream().map(OutboxEvent::getCreatedAt).min(Instant::compareTo).orElse(null);
		metrics.setOutboxPending(pending.size(), oldest);
	}
}
