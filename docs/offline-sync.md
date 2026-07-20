# Offline sync

Each user write becomes a Room row before the app asks the network for anything. The row contains a generated UUID, mutation type, target entity, base version where required, JSON payload, creation time, and human-readable summary.

## Delivery sequence

1. The app writes the local optimistic record and mutation in one transaction where both are required.
2. WorkManager schedules unique network-constrained work.
3. The worker loads no more than 100 queued rows and marks them as uploading.
4. The app posts the rows with their original mutation IDs and the last durable server cursor.
5. Applied rows are removed. Conflicts and rejections are retained for the Queue screen.
6. Products and inventory are refreshed from authoritative API snapshots.
7. The returned cursor and completion time are stored in Room.

If transport fails, uploading rows return to queued state. Retrying does not create a new mutation ID. The API's processed-mutation ledger therefore returns the original result instead of applying the same operation twice.

## Conflict policy

Product updates carry the version that was present when editing began. If the server version has changed, the API keeps the server record and returns a conflict. The mobile app retains that result in the queue so the merchant can review it. Stock adjustments are commutative deltas and use the server's idempotent mutation ledger rather than product version matching.

Removing a conflict or rejection deletes only that queued attempt. It does not reverse an operation already accepted by the API.
