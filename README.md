# Merqadyn Mobile

Merqadyn Mobile is a native Android merchant operations app for the [Merqadyn API](https://github.com/oranegonzales/merqadyn-apido). It keeps products, inventory, and an outgoing mutation queue on the device so day-to-day work can continue through an interrupted connection.

## What it does

- reads the merchant catalog and inventory into a local Room database
- records stock receipts and count corrections while offline
- creates and edits catalog items through the same durable queue
- sends queued work with stable mutation IDs, making retries idempotent
- schedules delivery with WorkManager when network access returns
- shows conflicts and rejected changes without silently discarding them

The app does not let a user type an arbitrary server URL. The endpoint and credentials are build-time settings so normal users cannot redirect merchant data from inside the app.

## Stack

- Kotlin and Jetpack Compose
- Room
- WorkManager
- Retrofit, OkHttp, and Kotlin serialization
- Android API 36, minimum API 23
- Gradle 8.13 and JDK 17

## Windows setup

### 1. Start the API

Clone the API beside this repository so the folders look like this:

```text
C:\Users\Guy\merqadyn-api
C:\Users\Guy\merqadyn-mobile
```

Start Docker Desktop and wait until its engine is ready. Then run:

```powershell
cd C:\Users\Guy\merqadyn-api
docker compose up --build
```

Verify it in a second PowerShell window:

```powershell
curl.exe http://127.0.0.1:8080/actuator/health
```

### 2. Install Android tools

Install the current stable [Android Studio](https://developer.android.com/studio). In **Tools > SDK Manager**, install:

- Android SDK Platform 36
- Android SDK Build-Tools
- Android Emulator
- an API 36 Google APIs x86_64 system image

Create and start a phone in **Tools > Device Manager**.

### 3. Configure the app

The helper reads the generated admin credentials from the API's `.env` file and writes them to this project's ignored `local.properties` file:

```powershell
cd C:\Users\Guy\merqadyn-mobile
powershell -ExecutionPolicy Bypass -File .\scripts\configure-local.ps1
```

It keeps Android Studio's existing `sdk.dir` setting. The default API URL is `http://10.0.2.2:8080/`, which is how an Android emulator reaches port 8080 on the Windows host.

If the two repositories are not siblings, pass the API environment file explicitly:

```powershell
.\scripts\configure-local.ps1 -ApiEnvPath C:\path\to\merqadyn-api\.env
```

### 4. Run

Open `merqadyn-mobile` in Android Studio, wait for Gradle sync, choose the running emulator, and press **Run**.

The first refresh imports the seeded demo merchant. Use these sections:

- **Overview** summarizes the local copy and queue.
- **Inventory** lets you tap an item and record a positive or negative adjustment.
- **Catalog** creates or edits product records.
- **Queue** shows work waiting for the API and any conflicts or rejections.

Turn off the emulator's Wi-Fi to test offline entry. Make an inventory adjustment, confirm it appears in **Queue**, turn Wi-Fi back on, then choose **Send queued changes**. WorkManager will also retry queued work after connectivity returns.

### Physical Android device

A physical device cannot use `10.0.2.2`. Find the Windows computer's LAN IPv4 address with `ipconfig`, allow port 8080 through Windows Firewall only on your private network, then configure the app before building:

```powershell
$env:MERQADYN_API_URL = "http://192.168.1.25:8080/"
.\gradlew.bat installDebug
```

Replace the example address with your computer's actual LAN address. Use HTTPS for any non-local deployment.

## Command-line checks

With JDK 17 and Android SDK 36 configured:

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written under `app\build\outputs\apk\debug\`.

## Configuration keys

| Key | Purpose | Local default |
| --- | --- | --- |
| `MERQADYN_API_URL` | API base URL fixed into the build | `http://10.0.2.2:8080/` |
| `MERQADYN_ADMIN_USER` | Basic-auth username for mutations | read from API `.env` |
| `MERQADYN_ADMIN_PASSWORD` | Basic-auth password for mutations | read from API `.env` |
| `MERQADYN_DEVICE_ID` | Registered device used by sync | seeded HWT device |

Do not commit `local.properties`, `.env` files, keystores, or production credentials.

## Design and behavior

See [docs/architecture.md](docs/architecture.md) for the component map and [docs/offline-sync.md](docs/offline-sync.md) for queue and conflict behavior.

## License

Licensed under the MIT License. See [LICENSE](LICENSE).
