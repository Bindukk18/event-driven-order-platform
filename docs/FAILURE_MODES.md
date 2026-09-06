# Failure modes

Broker delivery is **at-least-once**. Duplicates, redeliveries after a
missed ACK, and retries are normal.

## Classification

| Class | Examples | Action |
|---|---|---|
| Retryable | temporary broker failure, provider 5xx, dependency unavailable, ordering gap | bounded retry (`attempt_count`, `next_attempt_at`, `last_error`) |
| Permanent | malformed event, unsupported `event_version`, invalid invariant | dead-letter immediately |
| Unknown | provider-side effect may have happened even though the caller timed out | persist `PAYMENT_UNKNOWN`; **UNKNOWN is not blindly retryable** |

TEST / DESIGN CONFIGURATION: consumer `max-attempts` is 3.

## Dead letter

After permanent failure or exhausted retries the event is stored in
`dead_letter`:

- event_id
- consumer_name
- original event JSON
- attempt_count
- sanitized reason
- timestamp

It is **not** discarded. Operator replay must republish the **same**
`event_id` so inbox idempotency still holds.

## Crash after commit, before ACK

```
consume
  local TX: inbox + state + outbox
  COMMIT
  crash before broker ACK
  broker redelivers
  inbox unique hit
  no second business effect
```

## Payment timeout

Timeout is not `PaymentFailed`. Timeout is also not "nothing happened".

The FAKE / TEST fixture `CHARGE_SUCCEEDS_RESPONSE_LOST` (`PAY-TIMEOUT`)
records a successful charge internally, then withholds the response.
The caller therefore cannot know whether the financial effect occurred.

PaymentConsumer:

- persists `PaymentAttempt` as `UNKNOWN`
- does not emit `PaymentCompleted` or `PaymentFailed`
- leaves the order at `AWAITING_PAYMENT`
- does not invent a new provider idempotency key

UNKNOWN is **not** blindly retryable. Ordinary redelivery of the same
`InventoryReserved` event reuses `event_id`; the fake provider applies
at most one charge per key.

Recovery is **not** implemented here. A later status lookup against that
same key would resolve `PaymentCompleted` or `PaymentFailed` once.
Do not treat this lab as a payment-system design.

## Inventory / payment terminals

`InventoryRejected` and `PaymentFailed` stop the workflow. Compensation
is intentionally omitted.

## Isolation

Retries and DLQ are per delivery. One poison event does not block a
different order's deliveries.
