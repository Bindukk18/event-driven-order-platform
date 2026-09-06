package com.lab.orders.payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * FAKE / TEST payment provider (LAB TEST DOUBLE).
 *
 * Charges are keyed by the caller's idempotency key ({@code event_id}) so a
 * retry of the same work does not invent a second financial effect.
 *
 * {@link Mode#CHARGE_SUCCEEDS_RESPONSE_LOST}: the provider accepts the key,
 * records exactly one successful charge in a hidden ledger, then withholds
 * the response (returns {@link ProviderResult#UNKNOWN}). PaymentConsumer
 * must not treat that as definite success or failure.
 *
 * Hidden ledger / charge counts are for tests and the lab demo only.
 * A production application would not have this internal visibility.
 */
@Component
public class FakePaymentProvider {

	public enum Mode {
		SUCCESS,
		CHARGE_SUCCEEDS_RESPONSE_LOST,
		FAILED,
		TRANSIENT
	}

	/** What authorize() last returned to the caller. */
	private final Map<UUID, ProviderResult> prior = new ConcurrentHashMap<>();
	/** Hidden financial truth. Not read by PaymentConsumer. */
	private final Map<UUID, ProviderResult> hiddenEffects = new ConcurrentHashMap<>();
	private final AtomicInteger authorizeCalls = new AtomicInteger();
	private final AtomicInteger charges = new AtomicInteger();
	private volatile Mode forcedMode;
	private final AtomicInteger transientRemaining = new AtomicInteger(0);

	public ProviderResult authorize(UUID idempotencyKey, UUID orderId, BigDecimal amount, String sku) {
		authorizeCalls.incrementAndGet();
		Mode mode = resolveMode(sku);
		if (mode == Mode.TRANSIENT && transientRemaining.getAndDecrement() > 0) {
			return ProviderResult.TRANSIENT;
		}
		if (mode == Mode.CHARGE_SUCCEEDS_RESPONSE_LOST) {
			recordSuccessfulChargeOnce(idempotencyKey);
			prior.putIfAbsent(idempotencyKey, ProviderResult.UNKNOWN);
			return ProviderResult.UNKNOWN;
		}
		ProviderResult remembered = prior.get(idempotencyKey);
		if (remembered != null) {
			return remembered;
		}
		ProviderResult result = switch (mode) {
			case FAILED -> ProviderResult.FAILED;
			case TRANSIENT, SUCCESS -> {
				recordSuccessfulChargeOnce(idempotencyKey);
				yield ProviderResult.SUCCESS;
			}
			case CHARGE_SUCCEEDS_RESPONSE_LOST -> throw new IllegalStateException("handled above");
		};
		prior.put(idempotencyKey, result);
		return result;
	}

	private void recordSuccessfulChargeOnce(UUID idempotencyKey) {
		if (hiddenEffects.putIfAbsent(idempotencyKey, ProviderResult.SUCCESS) == null) {
			charges.incrementAndGet();
		}
	}

	private Mode resolveMode(String sku) {
		if (forcedMode != null) {
			return forcedMode;
		}
		if ("PAY-TIMEOUT".equals(sku)) {
			return Mode.CHARGE_SUCCEEDS_RESPONSE_LOST;
		}
		if ("PAY-FAIL".equals(sku)) {
			return Mode.FAILED;
		}
		if ("PAY-TRANSIENT".equals(sku)) {
			return Mode.TRANSIENT;
		}
		return Mode.SUCCESS;
	}

	public void force(Mode mode) {
		this.forcedMode = mode;
	}

	public void failTransientTimes(int n) {
		this.forcedMode = Mode.TRANSIENT;
		this.transientRemaining.set(n);
	}

	public int authorizeCalls() {
		return authorizeCalls.get();
	}

	/** Hidden provider truth. LAB TEST DOUBLE only. */
	public int successfulCharges() {
		return charges.get();
	}

	public int charges() {
		return successfulCharges();
	}

	/** Hidden ledger for a key. LAB TEST DOUBLE / tests only. */
	public Optional<ProviderResult> hiddenEffect(UUID idempotencyKey) {
		return Optional.ofNullable(hiddenEffects.get(idempotencyKey));
	}

	public void reset() {
		prior.clear();
		hiddenEffects.clear();
		authorizeCalls.set(0);
		charges.set(0);
		forcedMode = null;
		transientRemaining.set(0);
	}
}
