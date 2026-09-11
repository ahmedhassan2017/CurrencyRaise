# Currency Raise

A personal Android app for Banque Misr USD/EGP cash buy and sell prices.

The Home screen shows the last successfully saved quote, a manual refresh action,
the last successful check time, and the bank's displayed timestamp. Buy/sell labels
are from the bank's perspective. Settings controls approximate background intervals
of 1, 2, 4, 6, 12, or 24 hours and a separate rate-alert preference.

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
- The first successful background check establishes a silent baseline. Later
  background checks alert only when a buy or sell price changes. Manual refresh is silent.
- An alert tap opens Home. App permission, channel settings, and Android policy all
  affect delivery. Notification delivery is not exactly once: a crash in the small
  gap between saving the handled event and posting can miss an alert.

## Storage and backup

Valid quotes survive network/provider failures; errors never erase the saved quote.
DataStore persists the complete quote and settings atomically within each file.
Corruption is reported; the app does not silently clear data.

Android backup and device transfer are restricted to:
- files/datastore/settings.preferences_pb
- files/datastore/latest_rate.preferences_pb

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

Optimized device checks use a separate, non-debuggable application ID
(com.example.currencyraise.smoke), displayed as Currency Raise Release Check:

```powershell
.\gradlew.bat -PtestBuildType=releaseSmoke :app:connectedReleaseSmokeAndroidTest --console=plain
```

This variant inherits release optimization and uses the local debug certificate.
It is for local verification only. It can coexist with the usual app. Connected
instrumentation runs can reinstall/remove their target app; use the isolated
variant for acceptance runs when you want to preserve your personal installation.

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
default rules. No broad app-specific keep rules are added.

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

The app reads the bank's public HTML page over HTTPS:
[Banque Misr exchange rates](https://www.banquemisr.com/en/CAPITAL-MARKETS/Exchange-Rates-and-Currencies?sc_lang=en).

Cash notes prices are used, not transfer prices. HTML can change or be blocked;
an unusable response retains the saved quote. The bank's timestamp has no verified
timezone, so it is shown as published. Device check time is stored separately.
These prices can stay unchanged for long periods and are not a live trading feed.

There is no paid API subscription, backend, account, or API key in this app.
Network access uses your normal connection. Public page access does not establish
permission for automated use or public redistribution; those remain unresolved
before a public release.

The saved [implementation plan](IMPLEMENTATION_PLAN.md) records actual test results,
pending visual review, older-Android coverage, real notification delivery,
reboot/battery behavior, backup restore, and remaining release acceptance.
Visual review belongs to the user; automated checks do not claim to cover it.
