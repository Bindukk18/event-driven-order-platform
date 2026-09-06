package com.lab.orders.messaging;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.lab.orders.metrics.LabMetrics;

/**
 * LAB / TEST BROKER — not Kafka, not RabbitMQ, not production.
 *
 * In-process at-least-once double: publish fans out one delivery per
 * subscribed consumer. Supports duplicate delivery, delayed retry,
 * redelivery after a missed ACK, and an in-memory DLQ view. Reliability
 * semantics in this lab are broker-neutral.
 */
@Component
public class LabBroker {

	private final Map<String, List<String>> subscriptions = new ConcurrentHashMap<>();
	private final List<EventEnvelope> published = new CopyOnWriteArrayList<>();
	private final List<BrokerDelivery> deliveries = new CopyOnWriteArrayList<>();
	private final AtomicInteger failNextPublish = new AtomicInteger(0);
	private final LabMetrics metrics;

	public LabBroker(LabMetrics metrics) {
		this.metrics = metrics;
	}

	public synchronized void subscribe(String topic, String consumerName) {
		subscriptions.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>());
		if (!subscriptions.get(topic).contains(consumerName)) {
			subscriptions.get(topic).add(consumerName);
		}
	}

	public synchronized void publish(String topic, EventEnvelope event) {
		if (failNextPublish.get() > 0) {
			failNextPublish.decrementAndGet();
			throw new PublishFailedException("TEST: injected broker publish failure");
		}
		published.add(event);
		metrics.eventsPublishedTotal.incrementAndGet();
		for (String consumerName : subscriptions.getOrDefault(topic, List.of())) {
			deliveries.add(new BrokerDelivery(UUID.randomUUID(), topic, consumerName, event, Instant.EPOCH));
		}
	}

	public synchronized Optional<BrokerDelivery> poll(String consumerName, Instant now) {
		return deliveries.stream()
				.filter(delivery -> delivery.getConsumerName().equals(consumerName))
				.filter(delivery -> delivery.getStatus() == DeliveryStatus.PENDING)
				.filter(delivery -> !delivery.getNextAttemptAt().isAfter(now))
				.min(Comparator.comparing(BrokerDelivery::getNextAttemptAt));
	}

	public synchronized void ack(UUID deliveryId) {
		delivery(deliveryId).ack();
	}

	public synchronized void leaveUnacked(UUID deliveryId) {
		BrokerDelivery delivery = delivery(deliveryId);
		delivery.scheduleRetry(Instant.EPOCH, "unacked_crash");
		metrics.brokerRedeliveryTotal.incrementAndGet();
	}

	public synchronized void scheduleRetry(UUID deliveryId, Instant nextAttemptAt, String error) {
		BrokerDelivery delivery = delivery(deliveryId);
		delivery.scheduleRetry(nextAttemptAt, error);
	}

	public synchronized void deadLetter(UUID deliveryId, String error) {
		delivery(deliveryId).deadLetter(error);
	}

	/**
	 * Duplicate delivery of an already-published event to one consumer.
	 */
	public synchronized void deliverTo(String topic, String consumerName, EventEnvelope event) {
		deliveries.add(new BrokerDelivery(UUID.randomUUID(), topic, consumerName, event, Instant.EPOCH));
	}

	public synchronized BrokerDelivery duplicate(String consumerName, UUID eventId) {
		BrokerDelivery original = deliveries.stream()
				.filter(delivery -> delivery.getConsumerName().equals(consumerName))
				.filter(delivery -> delivery.getEvent().eventId().equals(eventId))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("no delivery of " + eventId + " to " + consumerName));
		BrokerDelivery copy = new BrokerDelivery(
				UUID.randomUUID(),
				original.getTopic(),
				consumerName,
				original.getEvent(),
				Instant.EPOCH);
		deliveries.add(copy);
		metrics.brokerRedeliveryTotal.incrementAndGet();
		return copy;
	}

	public void failNextPublish(int n) {
		failNextPublish.set(n);
	}

	public List<EventEnvelope> recorded() {
		return List.copyOf(published);
	}

	public List<EventEnvelope> recorded(String eventType) {
		return published.stream().filter(event -> eventType.equals(event.eventType())).toList();
	}

	public List<BrokerDelivery> deadLetters() {
		return deliveries.stream().filter(delivery -> delivery.getStatus() == DeliveryStatus.DEAD_LETTERED).toList();
	}

	public List<BrokerDelivery> deliveriesFor(String consumerName) {
		return deliveries.stream().filter(delivery -> delivery.getConsumerName().equals(consumerName)).toList();
	}

	public Optional<Instant> oldestPendingRetry() {
		return deliveries.stream()
				.filter(delivery -> delivery.getStatus() == DeliveryStatus.PENDING)
				.filter(delivery -> delivery.getAttemptCount() > 0)
				.map(BrokerDelivery::getNextAttemptAt)
				.min(Comparator.naturalOrder());
	}

	public synchronized void reset() {
		published.clear();
		deliveries.clear();
		failNextPublish.set(0);
	}

	public List<BrokerDelivery> allDeliveries() {
		return new ArrayList<>(deliveries);
	}

	private BrokerDelivery delivery(UUID deliveryId) {
		return deliveries.stream()
				.filter(delivery -> delivery.getDeliveryId().equals(deliveryId))
				.findFirst()
				.orElseThrow();
	}
}
