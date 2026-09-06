# LOCAL LAB RESULT — 2026-09-06

Captured from a local `make test` / `make demo` run on this machine.
Not a production benchmark. Numbers below are this run only.

Java 21.0.12.1, Spring Boot 3.4.5, Zonky embedded PostgreSQL 14.15.

## Tests

```
make test
exit 0

Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
  -- in com.lab.orders.WorkflowIntegrationTest

Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
  -- in com.lab.orders.LabWalkthroughTest
```

Total: **16** tests, all passed.

The extra integration test is `chargeSucceedsResponseLostStaysUnknownAndIsNotBlindlyRetried`.

## Happy-path events (`make demo`)

```
HAPPY PATH
OrderCreated  bdda48fd-d2d9-4d8e-ae9e-00229212bc36  caused-by -
InventoryReserved  823587d7-6af1-43b4-aa5e-551e2c912cf4  caused-by bdda48fd-d2d9-4d8e-ae9e-00229212bc36
PaymentCompleted  8e931207-5261-4bc8-9834-d6d30ea34c34  caused-by 823587d7-6af1-43b4-aa5e-551e2c912cf4
ShipmentCreated  b7ddd4c0-0d16-44fa-9e05-54783eb80e88  caused-by 8e931207-5261-4bc8-9834-d6d30ea34c34
Correlation: 0e554193-bba7-4bda-ab1d-249417f42be7
Final status: SHIPPED
```

Event IDs are generated per run. The causation chain is the durable part.

## Duplicate-event demo

```
DUPLICATE OrderCreated
inventory reservations=1
InventoryReserved outbox=1
consumer_duplicate_total=1
```

## Timeout after provider effect

SKU `PAY-TIMEOUT` is FAKE / TEST fixture `CHARGE_SUCCEEDS_RESPONSE_LOST`.

The provider records one successful charge internally, then withholds
the response. PaymentConsumer sees timeout / UNKNOWN. The charge count
below is **hidden provider truth** exposed only because this is a
LAB TEST DOUBLE. A production application would not have this visibility.

```
TIMEOUT AFTER PROVIDER EFFECT
provider successful charges=1
local payment status=UNKNOWN
order status=AWAITING_PAYMENT
blind retry issued=false
LAB TEST DOUBLE: charge count is hidden provider truth, not app-visible state
```

Timeout is not `PaymentFailed`. The provider-side effect may already
exist. UNKNOWN is not blindly retryable. Recovery (status lookup →
`PaymentCompleted` or `PaymentFailed`) is documented, not implemented.
