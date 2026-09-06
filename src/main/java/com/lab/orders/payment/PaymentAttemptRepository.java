package com.lab.orders.payment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

	Optional<PaymentAttempt> findByOrderId(UUID orderId);

	long countByOrderId(UUID orderId);

	long countByStatus(PaymentAttemptStatus status);
}
