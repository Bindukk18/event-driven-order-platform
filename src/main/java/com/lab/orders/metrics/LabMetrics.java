package com.lab.orders.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * Lab counters. Labels are coarse — no event_id / order_id.
 */
@Component
public class LabMetrics {

	public final AtomicLong eventsPublishedTotal = new AtomicLong();
	public final AtomicLong eventsConsumedTotal = new AtomicLong();
	public final AtomicLong consumerDuplicateTotal = new AtomicLong();
	public final AtomicLong consumerProcessingFailureTotal = new AtomicLong();
	public final AtomicLong consumerRetryTotal = new AtomicLong();
	public final AtomicLong deadLetterTotal = new AtomicLong();
	public final AtomicLong orderingGapTotal = new AtomicLong();
	public final AtomicLong unknownPaymentCount = new AtomicLong();
	public final AtomicLong brokerRedeliveryTotal = new AtomicLong();
	public final AtomicLong eventProcessingLatencyMsSum = new AtomicLong();
	public final AtomicLong eventProcessingCount = new AtomicLong();
	public final AtomicLong endToEndOrderEventLagMsSum = new AtomicLong();
	public final AtomicLong endToEndOrderEventLagCount = new AtomicLong();

	private volatile Instant oldestRetryAt;
	private volatile Instant oldestOutboxAt;
	private volatile long outboxPendingCount;

	public void recordProcessingLatency(Instant occurredAt, Instant now) {
		if (occurredAt != null) {
			eventProcessingLatencyMsSum.addAndGet(Math.max(0, Duration.between(occurredAt, now).toMillis()));
			eventProcessingCount.incrementAndGet();
		}
	}

	public void recordEndToEndLag(Instant orderCreatedAt, Instant now) {
		if (orderCreatedAt != null) {
			endToEndOrderEventLagMsSum.addAndGet(Math.max(0, Duration.between(orderCreatedAt, now).toMillis()));
			endToEndOrderEventLagCount.incrementAndGet();
		}
	}

	public void setOldestRetryAt(Instant instant) {
		this.oldestRetryAt = instant;
	}

	public void setOutboxPending(long count, Instant oldest) {
		this.outboxPendingCount = count;
		this.oldestOutboxAt = oldest;
	}

	public long oldestRetryAgeSeconds(Instant now) {
		if (oldestRetryAt == null) {
			return 0;
		}
		return Math.max(0, Duration.between(oldestRetryAt, now).toSeconds());
	}

	public long outboxPendingCount() {
		return outboxPendingCount;
	}

	public long outboxOldestAgeSeconds(Instant now) {
		if (oldestOutboxAt == null) {
			return 0;
		}
		return Math.max(0, Duration.between(oldestOutboxAt, now).toSeconds());
	}

	public void reset() {
		eventsPublishedTotal.set(0);
		eventsConsumedTotal.set(0);
		consumerDuplicateTotal.set(0);
		consumerProcessingFailureTotal.set(0);
		consumerRetryTotal.set(0);
		deadLetterTotal.set(0);
		orderingGapTotal.set(0);
		unknownPaymentCount.set(0);
		brokerRedeliveryTotal.set(0);
		eventProcessingLatencyMsSum.set(0);
		eventProcessingCount.set(0);
		endToEndOrderEventLagMsSum.set(0);
		endToEndOrderEventLagCount.set(0);
		oldestRetryAt = null;
		oldestOutboxAt = null;
		outboxPendingCount = 0;
	}
}
