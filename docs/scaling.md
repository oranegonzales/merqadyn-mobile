# Scaling behavior

The mobile client is designed to stay predictable as merchants, devices, and records grow.

## Bounded work

- Catalog and inventory downloads use server pages of 200 rows instead of one unbounded response.
- Sync uploads at most 100 mutations per request, matching the API contract.
- WorkManager uses one unique job, network constraints, and exponential backoff so repeated taps do not create a retry storm.
- Room drives screens locally, so scrolling and data entry do not wait on concurrent API requests.
- Mutation UUIDs make retry safe across timeouts and process restarts.

The refresh guard stops after 50 pages (10,000 rows per resource) to prevent a damaged or hostile API from causing unlimited memory use. Merchants beyond that per-device working set should move from full snapshot refresh to cursor-driven delta materialization while retaining the same Room schema and idempotent outbox.

## Operational targets

Measure sync latency, queued-mutation age, conflict rate, HTTP 429/5xx rates, and refresh row counts in production. Roll out new versions gradually, and load-test the API with realistic device concurrency before increasing page or batch limits.
