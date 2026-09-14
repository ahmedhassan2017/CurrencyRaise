# Currency Raise

A personal Android app for Banque Misr and CIB USD/EGP buy and sell prices.
Banque Misr cash quotes come from the bank; CIB quotes come from Ta3weem and are
labeled **CIB via Ta3weem**. The CIB table does not distinguish cash and transfer
prices, and third-party quotes may lag the bank.

The Home screen focuses on the bank selector, saved buy/sell prices and directions,
manual refresh, and a compact check-time or stale-rate status. **Rate details** opens
source attribution, publication and comparison timestamps, and the source link.
Background controls and diagnostics are available in Settings. Buy/sell labels
are from the bank's perspective. Settings controls approximate background intervals
of 1, 2, 4, 6, 12, or 24 hours and a separate rate-alert preference. These preferences
apply to both banks. Bank selection survives Android screen/process restoration;
a fresh launch defaults to CIB, which is the first tab. Notification taps open the relevant bank.

## Language

The app supports English and Arabic, including right-to-left layouts and localized
rate notifications. Choose **Follow system**, **English**, or **العربية** from
Settings. On Android 13 and newer, the same choice is synchronized with the app's
language entry in Android Settings.

## Appearance

Choose **System**, **Light**, or **Dark** in Settings. The selected appearance is
saved and applied across the whole app, while System continues to follow the device.

## Rate history

**Show rate history** on Home expands a **Daily** chart covering the last 24 hours and a **Weekly** chart
covering the last 7 days for the selected bank. Buy and sell have separate line
styles and markers. Tap a point, or use Previous point / Next point, to inspect its
exact prices and local device check time. The change summary compares the first
and last observations actually shown, not an assumed midnight or market close.

Every successful fetch saves an observation, including unchanged prices. Cached
background checks and failed requests do not create observations. Each bank keeps
at most 2,048 observations within eight days of its newest stored observation.
The latest quote and history are saved atomically in the same DataStore file and
follow the existing quote backup rules. No new runtime dependency was added.

Existing installations begin with the actual saved quote, then collect new history.
No earlier prices are backfilled. Lines stop across gaps longer than twice the
configured checking interval, and nothing is extrapolated beyond the observations.
Chart windows use elapsed time (24 hours / 168 hours); labels use the device timezone.
Provider publication timestamps are not used as chart positions.

## Rate direction

Each buy/sell card compares its price with the preceding saved observation for
that bank. Up/down arrows include the exact EGP change and a spoken direction;
unchanged prices show "No change" without an arrow. The comparison timestamp is
available in **Rate details**. A later unchanged check clears the previous arrow.

Comparisons use persisted history, so they survive restarts and reflect both
manual and background checks. First quotes, expired comparison history (eight-day
retention), and unavailable history have no direction. The cards wait for matching
quote/history data before displaying a change. Failed refreshes retain the comparison
for the last saved quote.

Rate-change notifications show independent up/down arrows and decimal changes for
buy and sell, with "No change" for an unchanged side. Expanded notifications put
the prices on separate lines. Direction comes from the previous comparable quote
captured during that refresh, including a preceding silent manual check; it is not
compared with an older notification or re-read from a potentially newer cache.
Bank-specific titles, tap targets, silent first baselines, preferences, and duplicate
suppression are preserved. The labeled debug sample has no comparison or arrows.

## Build and install

Open this folder in Android Studio. Let Gradle sync with the committed wrapper and
version catalog. The project uses AGP 9.2.1, Gradle 9.4.1, compile SDK 37, target
SDK 36, and minimum Android 7 / API 24. Configure your local SDK through Android
Studio; local.properties is not committed.

From the project root, with USB debugging enabled and the phone authorized:

```powershell
.\gradlew.bat :app:installDebug --console=plain
```

Open Currency Raise on the phone after installation. In Settings, select an
interval and use Allow notifications if you want alerts. Enabling the app's alert
toggle does not grant Android notification permission.

The debug APK is app/build/outputs/apk/debug/app-debug.apk. It uses the local
Android debug signing key. Keep the same signing key when installing updates;
do not uninstall just to fix a signature mismatch without considering saved data.

## How background checks behave

- A newly enabled schedule starts after approximately the selected interval.
- Android may delay a check for connectivity, Doze, battery policy, or manufacturer
  restrictions. This app does not promise an exact next-check time.
- Normal closure does not require keeping the app open. Force-stop suspends
  background work until the app is opened again.
- A successful check less than five minutes old lets a background run reuse the
  cache. Manual refresh can check sooner, except during a bank-supplied Retry-After
  waiting period. That waiting period applies across app restarts.
- Transient failures have at most two retries after the first attempt, with
  exponential backoff. Other failures wait for a later check or explicit manual retry.
- Turning automatic checks off cancels scheduled checks. Turning notifications off
  leaves enabled checking active.
- Both banks are checked independently. Each has its own cache, request deadline,
  refresh lock, and notification baseline. A provider failure preserves its saved
  quote and does not prevent the other provider from updating.
- The first successful background check for each bank establishes a silent baseline. Later
  background checks alert only when a buy or sell price changes. Manual refresh is silent.
- An alert tap opens Home with the corresponding bank selected. Each bank has a
  separate notification ID so one bank's alert does not overwrite the other's.
  App permission, channel settings, and Android policy all
  affect delivery. Notification delivery is not exactly once: a crash in the small
  gap between saving the handled event and posting can miss an alert.

## Testing notifications in a debug build

A debuggable installation shows **Send test notification** in Settings. First allow
Android notifications, then press the test button. It posts a clearly labeled sample
through the real Exchange Rate Updates channel with a separate notification ID. Tapping
it must open Home. The test does not fetch the bank, change saved rates, alter the first-run
baseline, or replace a real rate-change notification. This control is absent from release
and releaseSmoke builds.

Manual refresh remains silent. A real alert still requires a later successful background
check whose buy or sell rate differs from the established baseline.

## Build identities

The production application uses `com.example.currencyraise` and the navy launcher icon.
Debug uses `com.example.currencyraise.debug`, the name **Currency Raise Debug**, and the
orange launcher icon with a `D` badge. They can be installed together and keep independent
rates, preferences, notification permission, channels, and scheduled work. The notification
itself uses a dedicated monochrome rising-rate icon because Android status bars mask and tint
small notification icons.

## Storage and backup

Valid quotes survive network/provider failures; errors never erase the saved quote.
DataStore persists the complete quote and settings atomically within each file.
Corruption is reported; the app does not silently clear data.

Android backup and device transfer are restricted to:
- files/datastore/settings.preferences_pb
- files/datastore/latest_rate.preferences_pb
- files/datastore/cib_latest_rate.preferences_pb

Permission prompt history, handled notification events, provider waiting periods,
and WorkManager state are device-only. Permission history from the previous app
version migrates into noBackupFilesDir before its legacy key is removed.
The migration preserves a prior request, retries after write failures, and never
undoes a newer request. Restoring a pre-migration backup can preserve that older
prompt history; use Android notification settings if necessary.

An actual cloud/device-transfer restore remains an acceptance check. Availability
of backup is controlled by Android and the device's backup settings.

## Automated checks

Normal checks:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

The optimized device check uses a separate, non-debuggable application ID
(`com.example.currencyraise.smoke`), displayed as Currency Raise Release Check:

```powershell
.\gradlew.bat :app:assembleReleaseSmoke --console=plain
android run --device=<serial> --apks=app\build\outputs\apk\releaseSmoke\app-releaseSmoke.apk --activity=com.example.currencyraise.MainActivity
```

This variant inherits release optimization and uses the local debug certificate.
It can coexist with the usual app. Verify a fresh online launch, a cold restart,
the persisted quote, and its WorkManager job on the disposable installation.

Instrumentation behavior is exercised against debug. The two tests that change
notification permission/channel state stay isolated-only and are currently pending:
the optimized AndroidJUnitRunner test APK is not reliable with this AGP/R8 setup.
This does not affect the normal release APK or the standalone optimized app smoke test.

Tests use historical fixtures as test inputs, never as fallback production rates.
The parser/cache/repository/Home pipeline is exercised with deterministic online
and offline outcomes. Worker tests use isolated work names and fake notification
sinks. Notification tap testing does not post historical prices as real alerts.

The optional local parser probe accepts a separately saved provider page:

```powershell
.\gradlew.bat :app:testDebugUnitTest -PrateProbeFile=C:/path/to/captured-page.html --console=plain
```

## Release artifacts and signing

Release builds enable code and resource shrinking with the optimized Android
default rules. The normal release uses no app-specific keep rules. The isolated
releaseSmoke setup has narrow AndroidX test-runner rules under review; they do not
apply to the normal release artifact.

The normal artifact is app/build/outputs/apk/release/app-release-unsigned.apk.
It requires a separate signing step before installation or distribution.
Create/select a private signing key using Android Studio's Generate Signed
App Bundle / APK flow when you are ready. Keep signing keys and passwords outside
the repository, and back up the private key securely. No distribution key is
generated or committed by this project.

The optimized local test APK is
app/build/outputs/apk/releaseSmoke/app-releaseSmoke.apk.
Its debug certificate is not a production signing identity.

For now the main application ID remains com.example.currencyraise for personal
testing. Select a permanent ID before public distribution; changing it creates a
separate Android installation.

## Source and limitations

The app reads public HTML pages over HTTPS:
[Banque Misr exchange rates](https://www.banquemisr.com/en/CAPITAL-MARKETS/Exchange-Rates-and-Currencies?sc_lang=en).
[CIB rates via Ta3weem](https://ta3weem.com/en/banks/commercial-international-bank-cib).

Banque Misr uses cash notes prices. Ta3weem's CIB table supplies bank buy/sell prices
without a cash/transfer distinction. HTML can change or be blocked;
an unusable response retains that bank's saved quote. Source timestamps have no verified
timezone, so they are shown as published. Device check time is stored separately.
These prices can stay unchanged for long periods and are not a live trading feed.

CIB's official page presented a security challenge during integration, so the user
approved a third-party source. See [provider notes](PROVIDER_NOTES.md) for the
verified table contract and limitations. No fallback substitutes another bank's prices.

There is no paid API subscription, backend, account, or API key in this app.
Network access uses your normal connection. Public page access does not establish
permission for automated use or public redistribution; those remain unresolved
before a public release.

The saved [implementation plan](IMPLEMENTATION_PLAN.md) records actual test results,
pending visual review, older-Android coverage, real notification delivery,
reboot/battery behavior, backup restore, and remaining release acceptance.
Visual review belongs to the user; automated checks do not claim to cover it.
