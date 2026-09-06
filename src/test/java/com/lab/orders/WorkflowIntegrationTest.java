package com.lab.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.lab.orders.dlq.DeadLetterRepository;
import com.lab.orders.inbox.ConsumerInboxRepository;
import com.lab.orders.inventory.InventoryConsumer;
import com.lab.orders.inventory.InventoryReservationRepository;
import com.lab.orders.inventory.ReservationStatus;
import com.lab.orders.messaging.EventEnvelope;
import com.lab.orders.messaging.EventTypes;
import com.lab.orders.messaging.LabBroker;
import com.lab.orders.messaging.Topics;
import com.lab.orders.metrics.LabMetrics;
import com.lab.orders.order.Order;
import com.lab.orders.order.OrderRepository;
import com.lab.orders.order.OrderService;
import com.lab.orders.order.OrderStatus;
import com.lab.orders.order.OrderWorkflowConsumer;
import com.lab.orders.outbox.OutboxRepository;
import com.lab.orders.outbox.OutboxStatus;
import com.lab.orders.payment.FakePaymentProvider;
import com.lab.orders.payment.PaymentAttemptRepository;
import com.lab.orders.payment.PaymentAttemptStatus;
import com.lab.orders.payment.PaymentConsumer;
import com.lab.orders.payment.ProviderResult;
import com.lab.orders.runtime.WorkflowRuntime;
import com.lab.orders.shipping.ShipmentRepository;
import com.lab.orders.shipping.ShippingConsumer;

@SpringBootTest
class WorkflowIntegrationTest {

	@Autowired
	OrderService orderService;
	@Autowired
	OrderRepository orderRepository;
	@Autowired
	InventoryReservationRepository reservationRepository;
	@Autowired
	PaymentAttemptRepository paymentAttemptRepository;
	@Autowired
	ShipmentRepository shipmentRepository;
	@Autowired
	OutboxRepository outboxRepository;
	@Autowired
	ConsumerInboxRepository inboxRepository;
	@Autowired
	DeadLetterRepository deadLetterRepository;
	@Autowired
	LabBroker broker;
	@Autowired
	WorkflowRuntime runtime;
	@Autowired
	LabMetrics metrics;
	@Autowired
	FakePaymentProvider provider;
	@Autowired
	InventoryConsumer inventoryConsumer;

	@BeforeEach
	void reset() {
		broker.reset();
		metrics.reset();
		provider.reset();
		inventoryConsumer.failNext(0);
		deadLetterRepository.deleteAll();
		inboxRepository.deleteAll();
		outboxRepository.deleteAll();
		shipmentRepository.deleteAll();
		paymentAttemptRepository.deleteAll();
		reservationRepository.deleteAll();
		orderRepository.deleteAll();
	}

	@Test
	void orderCreationEmitsOrderCreated() {
		Order order = orderService.createOrder("alice", "SKU-1", 2);
		assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_INVENTORY);
		assertThat(outboxRepository.countByEventType(EventTypes.ORDER_CREATED)).isEqualTo(1);
		assertThat(broker.recorded()).isEmpty();
	}

	@Test
	void orderCreatedReservesInventory() {
		Order order = orderService.createOrder("alice", "SKU-1", 1);
		runtime.pump();
		assertThat(reservationRepository.countByOrderId(order.getOrderId())).isEqualTo(1);
		assertThat(reservationRepository.findByOrderId(order.getOrderId()).orElseThrow().getStatus())
				.isEqualTo(ReservationStatus.RESERVED);
		assertThat(outboxRepository.countByEventType(EventTypes.INVENTORY_RESERVED)).isEqualTo(1);
	}

	@Test
	void duplicateOrderCreatedIsAppliedOnce() {
		Order order = orderService.createOrder("alice", "SKU-1", 1);
		runtime.pump();
		EventEnvelope created = broker.recorded(EventTypes.ORDER_CREATED).getFirst();
		broker.duplicate(InventoryConsumer.NAME, created.eventId());
		runtime.pump();
		assertThat(reservationRepository.countByOrderId(order.getOrderId())).isEqualTo(1);
		assertThat(outboxRepository.countByEventType(EventTypes.INVENTORY_RESERVED)).isEqualTo(1);
		assertThat(inboxRepository.countByConsumerName(InventoryConsumer.NAME)).isEqualTo(1);
		assertThat(metrics.consumerDuplicateTotal.get()).isGreaterThanOrEqualTo(1);
	}

	@Test
	void inventoryReservedTriggersPayment() {
		Order order = orderService.createOrder("alice", "SKU-1", 1);
		runtime.pump();
		assertThat(paymentAttemptRepository.findByOrderId(order.getOrderId()).orElseThrow().getStatus())
				.isEqualTo(PaymentAttemptStatus.COMPLETED);
		assertThat(outboxRepository.countByEventType(EventTypes.PAYMENT_COMPLETED)).isEqualTo(1);
	}

	@Test
	void paymentCompletedTriggersShipping() {
		Order order = orderService.createOrder("alice", "SKU-1", 1);
		runtime.pump();
		assertThat(shipmentRepository.countByOrderId(order.getOrderId())).isEqualTo(1);
		assertThat(outboxRepository.countByEventType(EventTypes.SHIPMENT_CREATED)).isEqualTo(1);
	}

	@Test
	void fullHappyPathCompletes() {
		Order order = orderService.createOrder("alice", "SKU-1", 1);
		runtime.pump();
		Order reloaded = orderRepository.findById(order.getOrderId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.SHIPPED);
		assertThat(reloaded.getLastAppliedVersion()).isEqualTo(4);
		assertThat(broker.recorded()).extracting(EventEnvelope::eventType).containsExactly(
				EventTypes.ORDER_CREATED,
				EventTypes.INVENTORY_RESERVED,
				EventTypes.PAYMENT_COMPLETED,
				EventTypes.SHIPMENT_CREATED);
		assertThat(broker.recorded()).allMatch(event -> order.getOrderId().toString().equals(event.correlationId()));
		assertCausationChain();
	}

	@Test
	void duplicateLaterStageEventIsAppliedOnce() {
		Order order = orderService.createOrder("alice", "SKU-1", 1);
		runtime.pump();
		EventEnvelope reserved = broker.recorded(EventTypes.INVENTORY_RESERVED).getFirst();
		broker.duplicate(PaymentConsumer.NAME, reserved.eventId());
		runtime.pump();
		assertThat(paymentAttemptRepository.countByOrderId(order.getOrderId())).isEqualTo(1);
		assertThat(outboxRepository.countByEventType(EventTypes.PAYMENT_COMPLETED)).isEqualTo(1);
		assertThat(provider.charges()).isEqualTo(1);
	}

	@Test
	void consumerCommitThenRedeliveryDoesNotRepeatEffect() {
		Order order = orderService.createOrder("alice", "SKU-1", 1);
		runtime.crashBeforeAck(InventoryConsumer.NAME);
		runtime.pump();
		assertThat(reservationRepository.countByOrderId(order.getOrderId())).isEqualTo(1);
		assertThat(outboxRepository.countByEventType(EventTypes.INVENTORY_RESERVED)).isEqualTo(1);
		assertThat(metrics.brokerRedeliveryTotal.get()).isGreaterThanOrEqualTo(1);
		runtime.pump();
		assertThat(reservationRepository.countByOrderId(order.getOrderId())).isEqualTo(1);
		assertThat(outboxRepository.countByEventType(EventTypes.INVENTORY_RESERVED)).isEqualTo(1);
		assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getStatus())
				.isEqualTo(OrderStatus.SHIPPED);
	}

	@Test
	void retryableFailureIsRetried() {
		inventoryConsumer.failNext(1);
		Order order = orderService.createOrder("alice", "SKU-1", 1);
		runtime.pump();
		assertThat(metrics.consumerRetryTotal.get()).isGreaterThanOrEqualTo(1);
		sleep(15);
		runtime.pump();
		assertThat(reservationRepository.countByOrderId(order.getOrderId())).isEqualTo(1);
		assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getStatus())
				.isEqualTo(OrderStatus.SHIPPED);
	}

	@Test
	void retriesExhaustIntoDeadLetter() {
		inventoryConsumer.failNext(8);
		Order order = orderService.createOrder("alice", "SKU-1", 1);
		for (int i = 0; i < 6; i++) {
			runtime.pump();
			sleep(10);
		}
		assertThat(reservationRepository.countByOrderId(order.getOrderId())).isZero();
		assertThat(deadLetterRepository.count()).isEqualTo(1);
		assertThat(deadLetterRepository.findAll().getFirst().getEventId())
				.isEqualTo(broker.recorded(EventTypes.ORDER_CREATED).getFirst().eventId());
		assertThat(deadLetterRepository.findAll().getFirst().getConsumerName()).isEqualTo(InventoryConsumer.NAME);
		assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getStatus())
				.isEqualTo(OrderStatus.AWAITING_INVENTORY);
	}

	@Test
	void malformedEventDoesNotMutateState() {
		Order order = orderService.createOrder("alice", "SKU-1", 1);
		EventEnvelope bad = new EventEnvelope(
				UUID.randomUUID(),
				EventTypes.ORDER_CREATED,
				99,
				"Order",
				order.getOrderId(),
				1,
				order.getOrderId().toString(),
				null,
				Instant.now(),
				Map.of("order_id", order.getOrderId().toString()));
		broker.publish(Topics.ORDERS, bad);
		runtime.pump();
		assertThat(reservationRepository.count()).isEqualTo(1);
		assertThat(deadLetterRepository.count()).isEqualTo(1);
		assertThat(deadLetterRepository.findByEventId(bad.eventId())).isNotEmpty();
	}

	@Test
	void paymentTimeoutDoesNotBecomeDefiniteFailure() {
		Order order = orderService.createOrder("alice", "PAY-TIMEOUT", 1);
		runtime.pump();
		assertThat(paymentAttemptRepository.findByOrderId(order.getOrderId()).orElseThrow().getStatus())
				.isEqualTo(PaymentAttemptStatus.UNKNOWN);
		assertThat(outboxRepository.countByEventType(EventTypes.PAYMENT_COMPLETED)).isZero();
		assertThat(outboxRepository.countByEventType(EventTypes.PAYMENT_FAILED)).isZero();
		assertThat(shipmentRepository.count()).isZero();
		assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getStatus())
				.isEqualTo(OrderStatus.AWAITING_PAYMENT);
		EventEnvelope reserved = broker.recorded(EventTypes.INVENTORY_RESERVED).getFirst();
		assertThat(provider.hiddenEffect(reserved.eventId())).contains(ProviderResult.SUCCESS);
		assertThat(provider.successfulCharges()).isEqualTo(1);
		broker.duplicate(PaymentConsumer.NAME, reserved.eventId());
		runtime.pump();
		assertThat(paymentAttemptRepository.countByOrderId(order.getOrderId())).isEqualTo(1);
		assertThat(paymentAttemptRepository.findByOrderId(order.getOrderId()).orElseThrow().getStatus())
				.isEqualTo(PaymentAttemptStatus.UNKNOWN);
		assertThat(provider.successfulCharges()).isEqualTo(1);
		assertThat(outboxRepository.countByEventType(EventTypes.PAYMENT_COMPLETED)).isZero();
		assertThat(outboxRepository.countByEventType(EventTypes.PAYMENT_FAILED)).isZero();
	}

	@Test
	void chargeSucceedsResponseLostStaysUnknownAndIsNotBlindlyRetried() {
		Order order = orderService.createOrder("alice", "PAY-TIMEOUT", 1);
		runtime.pump();
		EventEnvelope reserved = broker.recorded(EventTypes.INVENTORY_RESERVED).getFirst();

		assertThat(provider.hiddenEffect(reserved.eventId())).contains(ProviderResult.SUCCESS);
		assertThat(provider.successfulCharges()).isEqualTo(1);
		assertThat(paymentAttemptRepository.findByOrderId(order.getOrderId()).orElseThrow().getStatus())
				.isEqualTo(PaymentAttemptStatus.UNKNOWN);
		assertThat(outboxRepository.countByEventType(EventTypes.PAYMENT_COMPLETED)).isZero();
		assertThat(outboxRepository.countByEventType(EventTypes.PAYMENT_FAILED)).isZero();
		assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getStatus())
				.isEqualTo(OrderStatus.AWAITING_PAYMENT);

		broker.duplicate(PaymentConsumer.NAME, reserved.eventId());
		runtime.pump();

		assertThat(provider.successfulCharges()).isEqualTo(1);
		assertThat(paymentAttemptRepository.countByOrderId(order.getOrderId())).isEqualTo(1);
		assertThat(paymentAttemptRepository.findByOrderId(order.getOrderId()).orElseThrow().getStatus())
				.isEqualTo(PaymentAttemptStatus.UNKNOWN);
		assertThat(outboxRepository.countByEventType(EventTypes.PAYMENT_COMPLETED)).isZero();
		assertThat(outboxRepository.countByEventType(EventTypes.PAYMENT_FAILED)).isZero();
		assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getStatus())
				.isEqualTo(OrderStatus.AWAITING_PAYMENT);
	}

	@Test
	void staleOrOutOfOrderEventDoesNotCorruptState() {
		Order order = orderService.createOrder("alice", "SKU-1", 1);
		EventEnvelope earlyPayment = new EventEnvelope(
				UUID.randomUUID(),
				EventTypes.PAYMENT_COMPLETED,
				EventTypes.CURRENT_VERSION,
				"Order",
				order.getOrderId(),
				3,
				order.getOrderId().toString(),
				UUID.randomUUID(),
				Instant.now(),
				Map.of("order_id", order.getOrderId().toString(), "amount", "10.00", "sku", "SKU-1"));
		broker.deliverTo(Topics.PAYMENTS, OrderWorkflowConsumer.NAME, earlyPayment);
		runtime.dispatchOne(OrderWorkflowConsumer.NAME);
		assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getStatus())
				.isEqualTo(OrderStatus.AWAITING_INVENTORY);
		assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getLastAppliedVersion()).isEqualTo(1);
		assertThat(metrics.orderingGapTotal.get()).isGreaterThanOrEqualTo(1);
		assertThat(shipmentRepository.countByOrderId(order.getOrderId())).isZero();
	}

	@Test
	void onePoisonEventDoesNotBlockAnotherOrder() {
		inventoryConsumer.failSku("SKU-POISON", 8);
		Order poison = orderService.createOrder("bob", "SKU-POISON", 1);
		Order healthy = orderService.createOrder("alice", "SKU-1", 1);
		for (int i = 0; i < 6; i++) {
			runtime.pump();
			sleep(10);
		}
		assertThat(orderRepository.findById(healthy.getOrderId()).orElseThrow().getStatus())
				.isEqualTo(OrderStatus.SHIPPED);
		assertThat(orderRepository.findById(poison.getOrderId()).orElseThrow().getStatus())
				.isEqualTo(OrderStatus.AWAITING_INVENTORY);
		assertThat(deadLetterRepository.count()).isGreaterThanOrEqualTo(1);
	}

	private void assertCausationChain() {
		EventEnvelope created = broker.recorded(EventTypes.ORDER_CREATED).getFirst();
		EventEnvelope reserved = broker.recorded(EventTypes.INVENTORY_RESERVED).getFirst();
		EventEnvelope paid = broker.recorded(EventTypes.PAYMENT_COMPLETED).getFirst();
		EventEnvelope shipped = broker.recorded(EventTypes.SHIPMENT_CREATED).getFirst();
		assertThat(created.causationId()).isNull();
		assertThat(reserved.causationId()).isEqualTo(created.eventId());
		assertThat(paid.causationId()).isEqualTo(reserved.eventId());
		assertThat(shipped.causationId()).isEqualTo(paid.eventId());
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}
}
