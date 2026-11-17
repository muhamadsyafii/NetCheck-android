# NetCheck Android

NetCheck is a single-screen Android dashboard for continuously monitoring internet availability and connection quality while the app is open. It can inspect Wi-Fi, Ethernet/LAN, and cellular/SIM routes independently, run a manual speed test, display detailed public IP information, and keep the screen awake for dedicated monitoring use.

The project is built with Kotlin, Jetpack Compose, and Material 3.

## Features

- Overall internet state: `ONLINE`, `OFFLINE`, or `CHECKING`.
- Automatic connectivity checks every 5, 10, or 30 seconds.
- Independent route checks for:
  - Wi-Fi.
  - Ethernet/LAN, including Android-compatible USB-C Ethernet adapters.
  - Cellular data from a SIM.
- Real HTTPS validation instead of relying only on network attachment.
- Android `NET_CAPABILITY_VALIDATED` fallback when probe services are blocked.
- Manual speed test with:
  - Download speed.
  - Upload speed.
  - Latency.
- Active route and local IPv4 address.
- Detailed public IP information:
  - Public IPv4 or IPv6 address.
  - IP version and network/CIDR when available.
  - ASN and ISP/organization.
  - Approximate city, region, country, postal code, and continent.
  - Approximate latitude and longitude.
  - Time zone and UTC offset.
  - Country calling code, currency, and languages.
- History of the last 12 checks.
- Keep-screen-on option for monitoring displays.
- Collapsible network, public IP, and monitor settings sections.
- Dark dashboard theme.
- Custom NetCheck adaptive launcher icon and branded splash screen.
- Edge-to-edge layout with status bar and navigation bar safe areas.

## Using the app

The automatic monitor uses a 30-second default interval when the app opens for the first time. A selected 5, 10, or 30-second interval is saved locally and restored when the app is opened again.

1. The status panel shows the overall internet state.
2. The metric cards show the latest download, upload, and latency results.
3. Tap **Network details** to inspect Wi-Fi, Ethernet/LAN, cellular/SIM, the active route, the local IP, and recent check history.
4. Tap **Public IP details** to inspect the public address, provider, ASN, and approximate geolocation of the active route.
5. Use the **Automatic monitor** checkbox to start or stop periodic checks.
6. Tap the automatic monitor card to reveal the interval settings.
7. Enable **Keep screen on** to prevent the display from sleeping while NetCheck is visible.
8. Tap **Run Speed Test** to perform a manual measurement.

## Status meanings

| Status | Meaning |
|---|---|
| `ONLINE` | The route reached a public HTTPS service or was validated by Android. |
| `NO INTERNET` | The device is attached to the route, but public internet access could not be validated. |
| `NOT CONNECTED` | Android does not currently expose that network transport. |
| `CHECKING…` | Connectivity validation is in progress. |

The overall state is `ONLINE` when at least one available route can access the internet. Wi-Fi, Ethernet, and cellular states remain visible independently under **Network details**.

## How connectivity checks work

`NetworkChecker` reads all networks exposed by Android's `ConnectivityManager` and groups them by transport:

- `TRANSPORT_WIFI` for Wi-Fi.
- `TRANSPORT_ETHERNET` for Ethernet/LAN.
- `TRANSPORT_CELLULAR` for cellular/SIM data.

Available routes are checked concurrently. Each HTTPS request is bound to its corresponding Android `Network` with `Network.openConnection()`. This prevents a Wi-Fi check from silently using cellular data, or a cellular check from silently using Wi-Fi.

The connectivity probes use:

- `https://connectivitycheck.gstatic.com/generate_204`
- `https://clients3.google.com/generate_204`
- `https://www.cloudflare.com/cdn-cgi/trace`

If all probe endpoints are blocked by DNS, a firewall, or a managed network, NetCheck uses Android's `NET_CAPABILITY_VALIDATED` result as a fallback. An HTTP response from a public TLS endpoint proves that DNS/routing and TLS connectivity succeeded, even if the endpoint returns an application-level error response.

### Latency

Latency is measured from connection start until the public endpoint returns an HTTP response. The app prefers the active route's latency and falls back to another online route when necessary.

## Public IP details

NetCheck requests public IP metadata from:

```text
https://ipapi.co/json/
```

The request is bound to the active online Android route. Results are cached in memory for five minutes to avoid making a geolocation request during every automatic connectivity scan.

Public IP geolocation is approximate. A city or coordinate may represent an ISP gateway, mobile carrier gateway, VPN exit node, or regional network hub rather than the physical device. Public IP details are informational and must not be treated as precise device location.

If the public IP metadata service fails or is rate-limited:

- The connection can still be reported as online.
- The public IP panel shows that details are unavailable.
- A later automatic scan can try again.

## Speed test

The manual speed test uses Cloudflare's speed test service:

- Approximately 10 MB downloaded through `__down`.
- Approximately 2 MB uploaded through `__up`.
- Approximately 12 MB total per run, excluding protocol overhead.

The speed test uses Android's active/default route. To test a specific route:

- For cellular: temporarily disable Wi-Fi and disconnect Ethernet.
- For Wi-Fi: disconnect Ethernet and ensure Wi-Fi is the active route.
- For Ethernet: connect Ethernet and verify Android selects it as the active route.

Speed tests are never started automatically because they consume substantially more data than connectivity probes.

## Cellular/SIM behavior

NetCheck does not read a phone number, SIM identity, carrier account, IMEI, contacts, or other private telephony data. It only checks whether Android exposes a cellular network transport and whether that route has internet access.

Some devices suspend the cellular network while Wi-Fi is active. In that situation, cellular may appear as `NOT CONNECTED` even when a SIM and mobile data plan are available. To verify the SIM route directly:

1. Enable mobile data.
2. Temporarily disable Wi-Fi.
3. Disconnect Ethernet if present.
4. Wait 5–10 seconds for Android to establish the cellular route.
5. Open **Network details** and inspect **Cellular data / SIM**.

## Keep screen on

The feature uses:

```kotlin
WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
```

It applies only while the NetCheck activity is visible.

It does:

- Prevent normal screen timeout while the app is open.

It does not:

- Bypass a PIN, pattern, fingerprint, or lock-screen security.
- Turn the app into a foreground service.
- Keep monitoring after the app is closed.
- Prevent Android from stopping the process under exceptional resource pressure.

A dedicated kiosk deployment could later add Lock Task Mode, a foreground service, boot startup, and battery optimization guidance.

## Branding assets

The NetCheck mark combines a Wi-Fi signal with a network-health pulse. It uses the dashboard's mint green and cool blue colors on a dark navy background.

Source and preview assets are stored under:

```text
assets/branding/
├── netcheck-logo-source.png
├── netcheck-icon-square.png
└── netcheck-icon-round.png
```

Android resources include:

- Adaptive icon foreground in `drawable-nodpi/ic_netcheck_foreground.png`.
- Adaptive icon definitions in `mipmap-anydpi-v26`.
- Legacy launcher icons for mdpi through xxxhdpi.
- A pre-Android 12 splash drawable in `drawable/splash_screen.xml`.
- Android 12+ splash attributes in `values-v31/themes.xml`.

## Project requirements

| Component | Version |
|---|---|
| Android Studio | A version compatible with AGP 9.2.1 and Android API 37 |
| JDK | Java 11 or a newer version supported by the configured AGP |
| Compile SDK | 37 |
| Target SDK | 36 |
| Minimum SDK | 24 (Android 7.0) |
| Kotlin | 2.2.10 |
| Android Gradle Plugin | 9.2.1 |
| Jetpack Compose BOM | 2026.02.01 |

Install **Android SDK Platform 37** before syncing:

1. Open **Tools > SDK Manager** in Android Studio.
2. Select **SDK Platforms**.
3. Enable Android API 37.
4. Apply the installation and sync the project again.

## Running the project

### Android Studio

1. Clone or open the project directory.
2. Wait for Gradle Sync to complete.
3. Select a physical Android device or emulator.
4. Press **Run**.
5. Provide a Wi-Fi, Ethernet, or cellular connection for validation.

Testing physical network transports on a real device is recommended because emulator network behavior may differ.

### Command line

Build a debug APK:

```bash
./gradlew assembleDebug
```

The output is created at:

```text
app/build/outputs/apk/debug/NetCheck-v-1.0.apk
```

APK output names are generated automatically from `versionName` using the format `NetCheck-v-<versionName>.apk`. For example, changing `versionName` to `1.1` produces `NetCheck-v-1.1.apk` without any additional build configuration changes.

Install the debug app on a connected device:

```bash
./gradlew installDebug
```

Run local unit tests:

```bash
./gradlew test
```

Run instrumented tests:

```bash
./gradlew connectedAndroidTest
```

## Permissions

The manifest declares:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

| Permission | Purpose |
|---|---|
| `INTERNET` | Access connectivity probes, public IP metadata, and speed test services. |
| `ACCESS_NETWORK_STATE` | Inspect active networks, transport types, link properties, and Android validation state. |

Both are normal Android permissions and do not require a runtime permission dialog.

## Project structure

```text
NetCheck-android/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/pilkupil/netcheck/
│       │   │   ├── MainActivity.kt
│       │   │   ├── network/
│       │   │   │   └── NetworkChecker.kt
│       │   │   └── ui/
│       │   │       ├── NetCheckDashboard.kt
│       │   │       └── theme/
│       │   │           ├── Color.kt
│       │   │           ├── Theme.kt
│       │   │           └── Type.kt
│       │   └── res/
│       ├── test/
│       └── androidTest/
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Main components

### `MainActivity.kt`

- Configures Jetpack Compose.
- Displays `NetCheckDashboard`.
- Adds or removes `FLAG_KEEP_SCREEN_ON` based on the user's setting.

### `NetworkChecker.kt`

- Reads Android networks through `ConnectivityManager`.
- Detects Wi-Fi, Ethernet, and cellular transports.
- Binds validation requests to specific Android routes.
- Uses multiple probe endpoints plus Android validation fallback.
- Fetches and caches public IP metadata for the active online route.
- Calculates latency, download Mbps, and upload Mbps.
- Performs blocking network work on `Dispatchers.IO`.

### `NetCheckDashboard.kt`

- Owns the Compose UI state.
- Runs the automatic monitor with `LaunchedEffect`.
- Displays connectivity, speed, public IP, route, and history information.
- Uses collapsible sections to keep the single-screen dashboard compact.
- Applies navigation bar padding for edge-to-edge layouts.

## UI state

Important dashboard state includes:

- `automaticCheck`: whether periodic checks are enabled.
- `intervalSeconds`: persistent 5, 10, or 30-second scan interval, with 30 seconds as the default.
- `status`: `CHECKING`, `ONLINE`, or `OFFLINE`.
- `networkInfo`: active route and per-transport availability.
- `publicIpInfo`: cached public IP and approximate network metadata.
- `latencyMs`: latest latency.
- `downloadMbps`: latest download result.
- `uploadMbps`: latest upload result.
- `history`: up to 12 recent check results.
- `keepScreenOn`: screen timeout override.

Basic preferences use `rememberSaveable`, so they survive configuration changes such as rotation. The history and public IP data are in-memory only and are not persisted after the app process stops.

## Troubleshooting

### A dependency requires compileSdk 37

Example:

```text
Dependency requires libraries and applications that depend on it to compile against version 37 or later
```

Check that:

- `compileSdk` is set to API 37.
- Android SDK Platform 37 is installed.
- Gradle Sync completes after SDK installation.

`compileSdk` can be upgraded independently from `targetSdk` and `minSdk`.

### Connected but reported as no internet

- Verify that a public HTTPS site opens in the browser.
- Check the device date and time; incorrect time can break TLS.
- Complete any captive portal or Wi-Fi login.
- Review Private DNS, VPN, firewall, or ad-blocking settings.
- Wait for the next automatic scan.
- Toggle the affected network route off and on.

NetCheck tries three public endpoints and Android validation to reduce false offline results.

### Cellular is not visible while Wi-Fi is active

Android may suspend cellular networking while Wi-Fi is preferred. Disable Wi-Fi temporarily and wait for the mobile route to appear.

### Ethernet is not detected

- Confirm that Android supports the adapter.
- Inspect the cable, adapter, and link indicators.
- Check whether Android itself displays an Ethernet connection.
- Some USB-C adapters require external power.

### Public IP details are unavailable

- Confirm that the route is online.
- Check whether `ipapi.co` is blocked by DNS, VPN, or firewall policy.
- Wait for a later automatic scan.
- Remember that public IP metadata failure does not mean the internet route is offline.

### The bottom of the page is hidden

The dashboard is scrollable and uses `navigationBarsPadding()`. Install the latest build if the action button is still covered by Android's navigation bar.

### Speed test fails

- Confirm that the overall state is online.
- Check whether `speed.cloudflare.com` is blocked.
- Try another network route.
- Remember that each test consumes approximately 12 MB.

## Privacy and network services

NetCheck does not collect phone numbers, SIM identities, contacts, precise GPS location, or device identifiers. It has no project-owned backend.

Requests are sent directly to:

- Google connectivity check endpoints.
- Cloudflare connectivity and speed test endpoints.
- ipapi.co for public IP metadata and approximate geolocation.

These providers can receive standard request information such as the public IP address and user-agent. IP-based location can reveal an approximate region but is not precise GPS location.

Connectivity history and public IP metadata remain in app memory and are not uploaded to a NetCheck-owned server. Review the third-party services' terms and privacy policies before public distribution.

## Current limitations

- Monitoring runs while the Compose screen is active; there is no foreground service.
- There are no outage or recovery notifications.
- History is not stored permanently.
- There are no long-term latency or speed charts.
- The speed test uses Android's default route and cannot yet be assigned to a route from the UI.
- There is no boot auto-start.
- There is no kiosk or Lock Task Mode.
- Cellular status represents the cellular data route, not physical SIM identity or carrier account state.
- Public IP geolocation is approximate and depends on a third-party service.

## Roadmap ideas

- Route-selectable speed tests for Wi-Fi, Ethernet, and cellular.
- Foreground service monitoring.
- Connection loss and recovery notifications.
- Room-based history storage.
- Uptime, downtime, latency, and speed charts.
- CSV or JSON report export.
- Configurable probe endpoints.
- Kiosk mode and boot startup.
- Quality thresholds such as good, slow, or unstable.
- Light and dark themes.
- Move UI strings into localized Android resources.

## Testing

The repository contains test source sets for:

- Local unit tests under `app/src/test`.
- Instrumented/UI tests under `app/src/androidTest`.

Recommended coverage:

- Mbps calculation.
- Transport selection.
- Online/offline transitions.
- Public IP parsing and cache behavior.
- The 12-entry history limit.
- Monitor interval changes.
- Expand/collapse state.
- Keep-screen-on behavior.
- Different screen sizes and Android navigation modes.
- Wi-Fi, Ethernet, and cellular behavior on physical devices.

## Contribution guidelines

When extending the project:

1. Keep blocking network operations on `Dispatchers.IO`.
2. Keep automatic probes small.
3. Start high-data speed tests only after explicit user action.
4. Give every request a finite timeout.
5. Preserve per-transport route binding.
6. Cache third-party metadata appropriately.
7. Test on physical devices from multiple manufacturers when possible.

## License

No license has been selected yet. Add a `LICENSE` file before public distribution or accepting external contributions.
