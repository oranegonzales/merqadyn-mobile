# Contributing

1. Use JDK 17 and Android SDK 36.
2. Create a branch from `main`.
3. Keep credentials in environment variables or `local.properties`.
4. Run `./gradlew clean testDebugUnitTest lintDebug assembleDebug`.
5. Describe offline behavior and any API contract change in the pull request.

Changes to mutation serialization must remain compatible with the Merqadyn API. Preserve a mutation's ID across every retry and retain explicit handling for applied, conflicted, and rejected results.
