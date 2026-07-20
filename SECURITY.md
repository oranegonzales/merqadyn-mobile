# Security policy

## Reporting a vulnerability

Please report vulnerabilities privately through GitHub's security advisory feature. Do not open a public issue containing credentials, customer data, or working exploit details.

## Local and production configuration

- Keep `local.properties`, API `.env` files, signing keys, and credentials out of Git.
- Use the included helper only on a trusted development computer.
- Use HTTPS for deployed API traffic. Cleartext HTTP is enabled only by the debug manifest for local Android development.
- Replace the seeded device ID with an enrolled device identity before a production rollout.
- Use a dedicated least-privilege mobile authentication flow before exposing the API beyond a controlled demo environment.

The application disables Android backup to reduce accidental copying of its local merchant database. This is not a replacement for encrypted device storage or an application-level authentication screen in a production deployment.
