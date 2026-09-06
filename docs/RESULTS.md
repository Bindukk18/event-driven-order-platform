# Results

See [results/2026-09-06-local-demo.md](results/2026-09-06-local-demo.md)
for the LOCAL LAB RESULT generated from `make test` / `make demo`.

The timeout fixture is **CHARGE_SUCCEEDS_RESPONSE_LOST**: the fake
provider may have charged even though the caller timed out. The printed
charge count is hidden LAB TEST DOUBLE truth, not application-visible
state. Recovery (status lookup → PaymentCompleted) is documented, not
implemented.

Do not treat those numbers as a production benchmark.
