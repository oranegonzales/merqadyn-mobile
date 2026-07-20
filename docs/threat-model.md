# Threat model

| Risk | Control | Remaining concern |
| --- | --- | --- |
| Administrator secret extracted from APK | No admin credential is built into the app | Enrollment still needs a trusted operator |
| Device token copied from preferences | AES-GCM key remains in Android Keystore; backups are disabled; phone/admin can revoke | A fully compromised unlocked device can act as that device until revocation completes |
| Network interception | Release requires HTTPS; debug HTTP is private-address-only | Local debug traffic is not confidential |
| Token sent to attacker-controlled URL | Endpoint policy rejects embedded credentials, paths, queries, and production HTTP | Users can still enter an HTTPS origin they control during enrollment |
| Duplicate writes after timeouts | Stable mutation IDs and server idempotency ledger | Business conflicts still require review |
| Request or retry storm | Bounded pages/batches, one unique worker, exponential backoff | Production monitoring and server quotas remain necessary |
| Data exposed in screenshots or backups | `FLAG_SECURE`, backup and transfer exclusions | Rooted devices are outside the app's trust boundary |

Lost or reassigned phones should have their device credential rotated or revoked at the API and their managed work profile wiped.
