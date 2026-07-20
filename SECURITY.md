# Security policy

Report vulnerabilities privately through GitHub Security Advisories. Do not publish credentials, merchant data, or exploit steps in a public issue.

## Security boundary

- A one-time, 10-minute code exchanges for a random device-scoped token; the Android app never receives the API administrator password.
- Android Keystore AES-GCM encrypts the token at rest and binds ciphertext to the device ID as additional authenticated data.
- Android backup and device transfer exclude application files, databases, and preferences.
- Release builds reject cleartext traffic and trust platform certificate authorities. Debug builds permit HTTP only to loopback and private-network addresses.
- Screenshots and non-secure display capture are blocked for the activity.
- API addresses cannot include embedded credentials, paths, query parameters, or fragments.
- Removing phone access revokes the server credential, then deletes the token and local merchant records from the device.

For production, terminate TLS at a maintained ingress, use an organization-controlled domain, distribute signed release builds through managed channels, revoke lost devices server-side, and monitor authentication failures. Never commit `local.properties`, `.env` files, signing keys, or production credentials.
