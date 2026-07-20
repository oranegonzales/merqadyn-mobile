# Merqadyn Mobile

Merqadyn Mobile is the offline-first Android client for [Merqadyn API](https://github.com/oranegonzales/merqadyn-apido). Room remains the UI source of truth, writes enter a durable mutation queue, and WorkManager delivers bounded batches when connectivity returns.

## Capabilities

- browse a paginated merchant catalog and inventory
- record stock changes and catalog work without a connection
- replay writes safely with stable mutation IDs
- retain conflicts and rejected work for review
- pair each phone with a one-time, 10-minute enrollment code
- keep the device token encrypted with Android Keystore
- retry one unique background sync with exponential backoff

## Windows setup

### 1. Start the API

Keep the repositories beside one another:

```text
C:\Users\Guy\merqadyn-api
C:\Users\Guy\merqadyn-mobile
```

Start Docker Desktop, then run:

```powershell
cd C:\Users\Guy\merqadyn-api
powershell -ExecutionPolicy Bypass -File .\scripts\start-local.ps1 -Detach
curl.exe http://127.0.0.1:8080/actuator/health
```

### 2. Install Android tools

Install Android Studio with Android SDK Platform 36, Build-Tools 36, platform-tools, and either an API 36 emulator or a USB-debuggable Android phone. JDK 17 is required for command-line builds.

### 3. Configure and create an enrollment code

For a USB-connected physical phone (recommended for local testing):

```powershell
cd C:\Users\Guy\merqadyn-mobile
powershell -ExecutionPolicy Bypass -File .\scripts\configure-local.ps1 -Target UsbPhone
.\gradlew.bat installDebug
```

Approve USB debugging on the phone. The helper uses `adb reverse`, prints a short enrollment code, and writes only the API address and device ID to ignored `local.properties`. Enter the printed code in the app.

For an emulator:

```powershell
.\scripts\configure-local.ps1 -Target Emulator
.\gradlew.bat installDebug
```

For Wi-Fi/LAN testing:

```powershell
.\scripts\configure-local.ps1 -Target LanPhone
.\gradlew.bat installDebug
```

Keep both devices on the same trusted private network. Permit inbound TCP 8080 only on the Windows Private firewall profile. LAN HTTP and `adb reverse` are debug conveniences; release builds require HTTPS.

If the API repository is elsewhere, pass `-ApiEnvPath C:\path\to\merqadyn-api\.env`.

## Test the workflow

1. Open **Inventory**, select a row, and save an adjustment.
2. Confirm the change appears in **Queue**.
3. Disable the phone network, make another change, and confirm the app remains usable.
4. Restore connectivity and choose **Send queued changes**.
5. Confirm accepted work leaves the queue and the API website reflects the new quantity.
6. Remove phone access from the bottom of **Queue** and confirm the enrollment screen returns.

Run the full local check with:

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk` and is also retained by successful GitHub Actions runs.

## Configuration

| Key | Purpose | Debug default |
| --- | --- | --- |
| `MERQADYN_API_URL` | Initial address on the enrollment screen | `http://10.0.2.2:8080/` |
| `MERQADYN_DEVICE_ID` | Registered phone identity | seeded HWT device |

Administrator credentials and device tokens are never placed in `BuildConfig`. The one-time setup helper uses the local admin credential only to request an enrollment code; the app redeems that code for a device-scoped token.

## Engineering notes

- [Architecture](docs/architecture.md)
- [Offline sync](docs/offline-sync.md)
- [Scaling](docs/scaling.md)
- [Threat model](docs/threat-model.md)
- [Security policy](SECURITY.md)

## License

Licensed under the MIT License. See [LICENSE](LICENSE).
