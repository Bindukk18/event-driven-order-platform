package com.lab.orders;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.lab.orders.dlq.DeadLetterRepository;
import com.lab.orders.inbox.ConsumerInboxRepository;
import com.lab.orders.inventory.InventoryConsumer;
import com.lab.orders.inventory.InventoryReservationRepository;
import com.lab.orders.messaging.EventEnvelope;
import com.lab.orders.messaging.EventTypes;
import com.lab.orders.messaging.LabBroker;
import com.lab.orders.metrics.LabMetrics;
import com.lab.orders.order.Order;
import com.lab.orders.order.OrderRepository;
import com.lab.orders.order.OrderService;
import com.lab.orders.order.OrderStatus;
import com.lab.orders.outbox.OutboxRepository;
import com.lab.orders.payment.FakePaymentProvider;
import com.lab.orders.payment.PaymentAttemptRepository;
import com.lab.orders.payment.PaymentAttemptStatus;
import com.lab.orders.payment.PaymentConsumer;
import com.lab.orders.payment.ProviderResult;
import com.lab.orders.runtime.WorkflowRuntime;
import com.lab.orders.shipping.ShipmentRepository;

@SpringBootTest
class LabWalkthroughTest {

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
	void printsHappyPathDuplicateAndTimeout() {
		Order order = orderService.createOrder("alice", "SKU-1", 1);
		runtime.pump();
		System.out.println("HAPPY PATH");
		for (EventEnvelope event : broker.recorded()) {
			String caused = event.causationId() == null ? "-" : event.causationId().toString();
			System.out.println(event.eventType() + "  " + event.eventId() + "  caused-by " + caused);
		}
		System.out.println("Correlation: " + order.getOrderId());
		System.out.println("Final status: " + orderRepository.findById(order.getOrderId()).orElseThrow().getStatus());

		EventEnvelope created = broker.recorded(EventTypes.ORDER_CREATED).getFirst();
		broker.duplicate(InventoryConsumer.NAME, created.eventId());
		runtime.pump();
		System.out.println("DUPLICATE OrderCreated");
		System.out.println("inventory reservations=" + reservationRepository.count());
		System.out.println("InventoryReserved outbox=" + outboxRepository.countByEventType(EventTypes.INVENTORY_RESERVED));
		System.out.println("consumer_duplicate_total=" + metrics.consumerDuplicateTotal.get());
		assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.SHIPPED);
		assertThat(reservationRepository.count()).isEqualTo(1);

		reset();
		Order timedOut = orderService.createOrder("alice", "PAY-TIMEOUT", 1);
		runtime.pump();
		EventEnvelope reserved = broker.recorded(EventTypes.INVENTORY_RESERVED).getFirst();
		int chargesBeforeRetry = provider.successfulCharges();
		broker.duplicate(PaymentConsumer.NAME, reserved.eventId());
		runtime.pump();
		boolean blindRetryIssued = provider.successfulCharges() > chargesBeforeRetry;
		System.out.println("TIMEOUT AFTER PROVIDER EFFECT");
		System.out.println("provider successful charges=" + provider.successfulCharges());
		System.out.println("local payment status="
				+ paymentAttemptRepository.findByOrderId(timedOut.getOrderId()).orElseThrow().getStatus());
		System.out.println("order status=" + orderRepository.findById(timedOut.getOrderId()).orElseThrow().getStatus());
		System.out.println("blind retry issued=" + blindRetryIssued);
		System.out.println("LAB TEST DOUBLE: charge count is hidden provider truth, not app-visible state");
		assertThat(provider.hiddenEffect(reserved.eventId())).contains(ProviderResult.SUCCESS);
		assertThat(provider.successfulCharges()).isEqualTo(1);
		assertThat(blindRetryIssued).isFalse();
		assertThat(paymentAttemptRepository.findByOrderId(timedOut.getOrderId()).orElseThrow().getStatus())
				.isEqualTo(PaymentAttemptStatus.UNKNOWN);
		assertThat(orderRepository.findById(timedOut.getOrderId()).orElseThrow().getStatus())
				.isEqualTo(OrderStatus.AWAITING_PAYMENT);
	}
}
