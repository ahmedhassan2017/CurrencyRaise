# Currency Raise — implementation plan and progress tracker

Last updated: 2026-09-11
Project: C:\Users\ahmed\AndroidStudioProjects\CurrencyRaise
Status: Phase 4 implementation and automated checks complete; user visual review pending; Phase 5 is next
Initial audience: personal use
Implementation strategy: one app module, small phases, observable local cache

## How to use this plan

- [x] Review the starter project and prove that it builds.
- [x] Investigate access to at least one real buy/sell source.
- [x] Review this complete plan before feature implementation.
- [ ] Complete the MVP phases below, in order.
- [ ] Complete personal-device acceptance checks.
- [ ] Evaluate optional additions only when requested.

Use `- [ ]` for pending work and `- [x]` only for completed, verified work.
Leave blocked or deferred tasks unchecked and explain the reason beside the task.
Do not mark an entire phase complete while a required acceptance check remains open.

During implementation:
1. Read this file and inspect the current Git changes before starting.
2. Implement only the current agreed phase.
3. Check off individual steps after verifying them.
4. Compile the project, run existing local tests, and add meaningful tests for new behavior.
5. Record the outcome, changed files, commands, limitations, and next step in the progress log.
6. Stop after the phase and wait for the user's instruction before continuing, unless the user explicitly authorizes several phases.

Never overwrite unrelated user changes. Do not commit, publish, or deploy automatically.
A build or unit test cannot substitute for a device check; record those separately.

Review preference, updated 2026-09-11: the user owns visual review from Phase 4 onward.
Continue compilation, local tests, lint, and useful automated device tests, but do not
spend time taking screenshots or manually inspecting layouts unless the user requests it.
Record user visual review separately; it does not block completing the implementation
and handing over a build for review. The user accepted the Phase 3 Home screen.
## Git workflow

Established at the user's request on 2026-09-11:

- master: stable reviewed milestones.
- dev: integration branch created from master.
- codex/phase-4-settings: current feature branch created from dev after Home integration.
- Merge reviewed feature work into dev, then promote verified milestones into master.
- Create each subsequent feature branch from an up-to-date dev branch.
- Commit 0b41ac8 contains Phase 1, the plan, provider notes, and exported wireframes.
  It was pushed to origin/master; dev and the Phase 2 branch were created and pushed
  from that same commit. Phase 2 was committed as 8fb3644, pushed, and fast-forwarded into dev.
  Home was committed as d0ae23d and pushed to codex/phase-3-home and dev.
  The Settings branch was created from that dev commit; master remains at 0b41ac8.
  The user authorized committing and pushing Phase 4 on 2026-09-11; visual review remains separately tracked.
- Future commits/pushes/merges still require the user's instruction; this Git setup
  does not authorize automatically merging future work into master.

## Product scope and working defaults

The first version will:
- Show USD/EGP buy and sell prices from one explicitly named source.
- Express values as EGP per 1 USD.
- Explain that buy/sell are from the bank's perspective.
- Show the last valid saved quote immediately when one exists.
- Offer manual refresh.
- Check approximately every hour by default.
- Offer intervals of 1, 2, 4, 6, 12, and 24 hours.
- Allow automatic background checks to be disabled independently of notifications.
- Notify when a successfully fetched quote changes, as the recommended default.
- Allow notifications to be disabled.
- Show when the app last checked successfully and the source publication time/date when available.
- Keep the last valid quote when offline or when fetching/parsing fails.
- Support light/dark themes, large text, and correct screen insets.

Working provider choice: Banque Misr's public cash/notes USD buy/sell table.
This is a prototype candidate, not a verified public API or a final production commitment.
Do not silently fall back to a different bank or a generic conversion rate.

Out of scope for the MVP:
- Historical charts, multiple banks, additional currency pairs, accounts, cloud sync.
- Threshold alerts, widgets, remote push, ads, analytics, subscriptions.
- Exact-time alarms, foreground services, continuous polling loops.
- Backend hosting or store publication.

Recommended notification policy:
- First successful background quote establishes a baseline without a change alert.
- Subsequent background checks alert only when buy or sell changes.
- Manual refresh does not generate a notification.
- Use a stable notification ID to replace the previous rate notification.
- Do not promise exactly-once delivery: local storage and Android notification posting are not one atomic transaction.
- If the user prefers an alert after every successful check, change this policy explicitly before implementation.

## Verified starting point

- [x] Single `:app` module.
- [x] Starter Compose activity and Material 3 theme inspected.
- [x] Gradle version catalog already exists.
- [x] AGP 9.2.1 and Gradle 9.4.1 inspected.
- [x] Local Gradle report resolves Kotlin and Compose compiler to 2.2.10.
- [x] Compile SDK corrected from 36.1 to 37 to satisfy AndroidX metadata.
- [x] Android SDK Platform 37 installed by Gradle.
- [x] Minimum SDK remains 24; target SDK remains 36.
- [x] Debug APK builds.
- [x] Existing unit test passes: 1 test, 0 failures.
- [x] Direct computer request to Banque Misr returned a page containing separate USD buy/sell values.
- [x] Direct computer request to CBE returned a rejection page despite HTTP 200.

Home now displays saved and fetched USD/EGP cash rates, manual refresh, and separate timestamps.
Existing arithmetic/package-name tests do not establish application behavior.
Phase 3 added and passed device tests on an Android 16 phone; earlier baseline/Phase 1/Phase 2 checks were local only.
The earlier suspected Kotlin/Compose mismatch was not confirmed by the resolved local dependencies.

## Architecture and implementation choices

Keep one Gradle module. Packages separate responsibilities; package boundaries are conventions, not compiler-enforced module boundaries.

Dependency rules:
- presentation depends on domain contracts/models.
- data implements domain repository contracts.
- background coordinates repositories and the notification helper.
- notification owns Android notification APIs.
- di wires implementations together.
- domain contains no Android framework types.
- repositories own refresh/cache coordination; workers and ViewModels do not duplicate it.

Suggested package layout under `com.example.currencyraise`:

```text
CurrencyRaiseApplication.kt
MainActivity.kt
di/
domain/
  model/
  repository/
data/
  remote/
  local/
  mapper/
  repository/
presentation/
  home/
  settings/
  navigation/        when navigation is introduced
background/
notification/
ui/theme/             retain the existing theme
core/                 only add specific shared utilities when needed
```

Do not create empty future feature packages, a generic BaseViewModel, BaseRepository,
a generic result framework, or an interface for every class.
Add use cases only when they own meaningful shared policy or simplify complex coordination.
A domain model inside HomeUiState is acceptable; do not create an identical UI model merely to add another layer.

Stack:
- Kotlin, Compose, Material 3, ViewModel, Coroutines, StateFlow.
- Hilt with KSP; preserve AGP built-in Kotlin.
- Preferences DataStore for settings and the latest small quote snapshot.
- OkHttp for HTTP.
- Jsoup for the provisional HTML provider.
- WorkManager with its Hilt integration.
- NotificationCompat.
- A small Compose navigation setup when Home and Settings need it.
- Existing JUnit plus coroutine test tools and MockWebServer when the relevant behavior is introduced.

Important improvement: Retrofit and JSON serialization are not required for an HTML-only provider.
Add Retrofit plus one JSON serializer if the validated provider later supplies JSON.
Choose compatible stable dependencies when each phase starts; do not blindly upgrade the existing stack.

## Data and refresh contract

Represent rates with BigDecimal parsed directly from decimal text.
Store decimal strings in DataStore and compare values numerically, so 51.2 and 51.20 count as unchanged.
Preserve provider precision; round only for display.

A quote must retain:
- Base currency USD and quote currency EGP.
- Buy rate and sell rate.
- Stable source ID and display name.
- Quote kind: cash/notes for the initial provider.
- Source displayed time/date when supplied; call it a publication instant only when the source contract supports that meaning.
- Fetch time from an injected Clock.

Do not invent a timestamp if the source only supplies a date.
The current provider timestamp has no verified timezone: preserve sourceDisplayedAt as LocalDateTime. Validate the timezone and meaning before any conversion to Instant.
Store absolute instants in UTC; format local display times appropriately.
Enable core-library desugaring for java.time support on API 24/25.

Prefer this repository shape:
```kotlin
interface ExchangeRateRepository {
    fun observeLatestUsdEgpRate(): Flow<ExchangeRate?>
    suspend fun refreshUsdEgpRate(): RefreshOutcome
}
```

RefreshOutcome is a small app-specific success/error type.
A success may include whether the quote changed; a failure identifies a useful error category.

Flow of data:
1. Home observes the persisted quote.
2. Manual refresh and the worker call the same repository refresh function.
3. The repository fetches and validates the response.
4. A valid complete snapshot is persisted atomically.
5. Observers receive the stored snapshot.
6. Background notification policy is evaluated after persistence.

Coordinate concurrent refreshes with a small repository-level mechanism.
Prevent older responses overwriting newer data and avoid unnecessary duplicate requests.
Do not replace valid cached data with partial, empty, or malformed responses.
Do not convert coroutine cancellation into an ordinary network failure.

## Source feasibility and cost

Banque Misr:
- A direct HTTPS page read succeeded without credentials.
- It provides separate notes and transfer columns; do not mix them.
- No documented developer API or explicit ongoing automated-use permission was verified.
- HTML layouts can change, and access may later be restricted.
- Personal use is the initial scope; access alone does not establish redistribution rights.
- A first successful request proves access at that moment, not long-term reliability.

CBE:
- Public rate tables exist, but direct access was rejected in this environment.
- Do not bypass access controls or assume an undocumented endpoint is supported.

Alternative providers:
- AllRatesToday advertises CBE buy/sell data; its free plan is for personal use and excludes commercial use.
- Fexant advertises a free 100-request/day tier and commercial use, but its API and data accuracy have not been independently validated here.
- Neither alternative has been integrated or authenticated.
- Do not select a provider solely on a pricing page or label claiming “official”.

Cost assumptions:
- No paid service is required for the local Android components or local notifications.
- A public webpage prototype has no identified API subscription charge.
- There is no guarantee that provider access or hosting stays free.
- Hourly polling is about 24 scheduled requests per day per device, before retries/manual refresh.
- No backend is planned for the personal prototype.
- If a confidential shared provider key is required, reevaluate a backend or a user-supplied credential approach.
- BuildConfig/local.properties prevent accidental Git exposure only; they do not make APK-embedded keys secret.
- Recheck permission, quotas, and credential handling before public distribution.

## Phase 0 — baseline and plan

Status: complete. User authorized continuing with the reviewed plan.

- [x] Inspect project, build tools, dependencies, manifest, theme, tests, and Git status.
- [x] Run baseline assembly and local tests.
- [x] Fix the compile SDK compatibility failure.
- [x] Re-run assembly and local tests successfully.
- [x] Investigate CBE and one Egyptian bank.
- [x] Write this checklist and progress log.
- [x] Review working defaults with the user before feature work.

Exit: a reproducible starter build and an agreed plan.

## Phase 1 — provider proof and app foundation

Status: complete on 2026-09-11.

- [x] Validate the provisional Banque Misr source scope and document access/usage uncertainties.
- [x] Capture a minimal representative rate-table fixture for deterministic parser tests.
- [x] Confirm cash/notes column mapping; inspect timestamp semantics and preserve the unzoned displayed time without an inferred instant.
- [x] Verify extraction on a fresh response; never rely on a search snippet as live app data.
- [x] Implement strict HTML extraction using Jsoup, not a production regex parser.
- [x] Detect rejection/login/error pages even if their HTTP status is 200.
- [x] Reject missing USD, incomplete pairs, invalid decimals, nonpositive values, and reversed buy/sell values for this provider.
- [x] Keep provider-specific selectors and parsing in data.remote.
- [x] Add only the required Hilt/KSP, network, parser, and testing dependencies.
- [x] Add the application class and minimal dependency injection.
- [x] Add INTERNET permission and HTTPS-only network configuration.
- [x] Configure bounded timeouts and release-safe logging; do not log credentials.
- [x] Introduce the explicit quote model and domain error categories.
- [x] Add java.time desugaring and an injectable Clock.
- [x] Test valid/invalid HTML, column selection, precision, and rejection responses.
- [x] Run assembly and existing/new local tests.
- [x] Update this file and stop.

Exit: real source extraction works in a controlled probe, fixture tests pass, and the app compiles.
Do not claim production provider approval merely because this phase passes.
If access fails, report the failure and evaluate another source rather than silently using fake live data.

Phase 1 evidence:

- Debug assembly passed with Hilt/KSP and API 24-compatible java.time desugaring.
- 28 tests passed: 18 parser/mapper/probe tests, 9 network tests, and 1 existing starter test.
- The production parser accepted the full fresh captured page, not just the reduced fixture.
- Android lint completed with 0 errors and 16 warnings: 7 dependency update suggestions, 7 unused starter colors, 1 redundant activity label, and 1 target-API suggestion. These are recorded for later maintenance; no checks were suppressed.
- Provider timestamp timezone/effective-time semantics remain unverified. The model
  deliberately stores sourceDisplayedAt as LocalDateTime; fetchedAt is an independent UTC Instant.
- Ongoing automated-use permission remains unverified; personal prototype scope is unchanged.
- No emulator/device checks were run, and the starter greeting remains the only screen.

Changed files: version catalog, root/app build scripts, manifest, MainActivity annotation;
new application class, NetworkModule, ExchangeRate/RateFetchResult models, BanqueMisr parser,
remote source and mapper, two test classes, fixture/fixture notes, and PROVIDER_NOTES.md.

Validation commands:

- .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest "-PrateProbeFile=C:\Users\ahmed\Documents\ChatGPT\Currency Raise\banque-misr-probe.html" --console=plain
- .\gradlew.bat :app:lintDebug --console=plain
- git diff --check

## Phase 2 — observable cache and repository

Status: complete on 2026-09-11.

- [x] Add a single DataStore instance per file and inject it.
- [x] Implement settings defaults and atomic quote storage.
- [x] Persist the entire quote metadata, not just two numbers.
- [x] Implement observeLatestUsdEgpRate and refreshUsdEgpRate.
- [x] Coordinate simultaneous refresh calls.
- [x] Distinguish unchanged quotes from failed fetches and changed quotes.
- [x] Keep prior valid data on HTTP, parse, or storage errors.
- [x] Surface a recoverable storage problem instead of crashing or silently erasing valid data.
- [x] Preserve cancellation propagation.
- [x] Add tests for cache persistence, observable updates, failure preservation, numeric comparison, and refresh concurrency.
- [x] Run assembly and local tests.
- [x] Update this file and stop.

Exit: a refresh saves valid data and notifies observers; failure preserves the previous quote.

Phase 2 evidence:

- Debug assembly and all 21 new cache/settings/repository tests passed.
- Full local suite: 49 tests discovered, 48 passed, 0 failures, 1 intentionally skipped
  optional captured-page probe (already exercised in Phase 1).
- Android lint: 0 errors, 17 warnings (8 dependency/tool update suggestions, 7 unused
  starter colors, 1 redundant activity label, 1 target-API suggestion); no suppressions added.
- Real DataStore files were closed and reopened to verify complete quote metadata,
  exact decimal precision, settings persistence, and atomic independent settings changes.
- Tests cover observer updates, malformed/corrupt storage without silent erasure,
  fetch/read/write failures, unchanged decimal values, duplicate refreshes, and cancellation.
- A singleton repository lock spans read/fetch/save, preventing competing requests from
  completing out of order. Fetch timestamps are display metadata, not an ordering lock:
  a regression test verifies that a backward device-clock correction does not block refresh.
- Provider publication ordering remains unverified; no ordering is inferred from its unzoned timestamp.
- Read flows throw StorageReadException instead of emitting empty/default data on failure.
  Home/Settings must catch it, keep the last displayed value, and re-subscribe on retry.
  Persistent file corruption is reported; no automatic destructive reset is implemented.
- Cancellation propagates; failed commits do not report success or erase the prior snapshot.
- The UI remains the starter greeting. No device/emulator checks were run.

Changed files: version catalog and app dependencies; RateRemoteSource boundary and its
Banque Misr implementation; RateCache/SettingsStore; settings, refresh and repository domain
contracts; repository implementations; StorageModule/RepositoryModule; shared test helpers
and three new test classes.

Validation: .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
and git diff --check. The first test run exposed a JUnit return-type signature error; it was
fixed and the full check command then passed.

## Phase 3 — Home screen

Status: complete on 2026-09-11; user accepted the visual result.

- [x] Replace the greeting with HomeScreen and HomeViewModel.
- [x] Expose one immutable HomeUiState via StateFlow.
- [x] Collect state with lifecycle awareness.
- [x] Show cached data immediately.
- [x] Trigger an initial refresh using a clear freshness rule.
- [x] Add manual refresh with duplicate-tap protection.
- [x] Display USD/EGP, bank name, cash quote type, buy/sell, and source attribution link.
- [x] Label source publication time/date separately from last successful check.
- [x] Show configured background interval as an approximation, not an exact next-run promise.
- [x] Handle loading without cache, refreshing with cache, empty state, and recoverable errors.
- [x] Catch storage observation errors, retain displayed data, re-subscribe on retry, and explain persistent corruption recovery.
- [x] Show stale/unverified freshness clearly without calling unchanged weekend prices a connection failure.
- [x] Provide string resources, accessible controls, locale-aware formatting, and scalable text from the start.
- [x] Preserve the existing theme and edge-to-edge padding.
- [x] Test initial cache display, refresh success/failure, and incoming background-style cache updates.
- [x] Run assembly/local tests and inspect Home on an emulator or device.
- [x] Update this file and stop.

Exit: the app can show a real saved quote, refresh it, and remain useful offline.

Phase 3 evidence:

- Added HomeScreen, HomeViewModel, immutable HomeUiState, locale-aware number/time
  formatting, and lifecycle-aware Compose collection using the existing Lifecycle version.
- Show cache first; initial refresh only for missing, expired, or future-dated cache.
  The selected interval defines expiry (one hour if settings cannot be read). Display
  clock ticks only while Home is visible and never trigger network polling.
- Manual retry reconnects failed storage observations, retains displayed data on errors,
  and prevents duplicate taps. Storage help explains explicit recovery without auto-reset.
- Preserved the Material 3 theme and safe screen insets. Cards stack for narrow screens
  or large fonts. Refresh was moved above timestamps following the visual check.
- Debug build passed; 62 local tests passed, 1 optional captured-page probe skipped.
- All 5 device tests passed on Android 16: 4 Home interaction tests plus the starter
  package test. Coverage includes cached-error retry, loading, dark mode with 2x text,
  source-link action, and storage help. Lint: 0 errors, 17 existing maintenance warnings.
- Real app fetch and manual refresh succeeded on the phone; unchanged rates were
  reported correctly. Cold restart retained the quote and last-check time.
- App build installed and launched on the connected phone. User accepted the result.
- Settings, notification delivery, and background scheduling remain future work.

Validation commands:
- .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest --console=plain
- After the final layout-only change: .\gradlew.bat :app:assembleDebug :app:lintDebug :app:connectedDebugAndroidTest --console=plain
- git diff --check
## Phase 4 — Settings and notification permission

Status: implementation and automated checks complete on 2026-09-11; user visual review pending.

- [x] Add Home/Settings navigation without unnecessary destination layers.
- [x] Implement SettingsViewModel and immutable SettingsUiState.
- [x] Add interval choices: 1, 2, 4, 6, 12, and 24 hours.
- [x] Add independent automatic-checks and notifications toggles.
- [x] Persist changes immediately.
- [x] Explain that scheduling is approximate and notification delivery depends on system permission.
- [x] Request Android 13+ notification permission contextually when enabling notifications.
- [x] Display system permission/channel blocking separately from the saved app preference.
- [x] Provide a route to Android notification settings when appropriate.
- [x] Do not repeatedly prompt after denial.
- [x] Clearly label background execution as inactive; allow saving preferences for the later scheduler.
- [x] Test settings persistence, defaults, and permission-related presentation logic.
- [x] Run assembly, local tests, lint, and automated navigation/settings device tests.
- [ ] User visual review of Settings and the Android permission/settings flow (user-owned by request).
- [x] Update this file and stop.

Technical exit: settings persist and notification permission states are honestly represented. Visual acceptance belongs to the user.

Phase 4 evidence:

- Added SettingsScreen/SettingsViewModel and an immutable observable state. Saved values
  remain the source of control state; failed reads/writes preserve prior values and offer retry.
- Added all six intervals and independent automatic-checks/alert preferences. A visible
  inactive-background notice distinguishes saved configuration from actual scheduling.
- Added two-screen navigation using saved destination/scroll state and BackHandler;
  no extra navigation dependency for two destinations. Activity recreation/back is tested.
- Added POST_NOTIFICATIONS and separate runtime, app, and channel/group access checks.
  Access is re-read on resume and after the Android permission result.
- No permission prompt occurs on opening Settings or toggling a preference. The explicit
  Allow notifications action is the contextual opt-in. Before the first prompt, a durable
  permission-request marker is saved. Later attempts direct to Android settings instead
  of prompting repeatedly; saved alert preference is independent of permission.
- Request-marker reads/writes are cancellable and storage failures prevent prompting.
  The marker survives real DataStore recreation. Backup policy remains part of Phase 6:
  restoring this marker conservatively routes the user to Android settings.
- Debug assembly passed; 74 local tests passed, 1 optional captured-page probe skipped.
- All 10 device tests passed in the final full run on Android 16, including navigation
  recreation/back, interval selection, independent toggles, explicit permission action,
  and denial routing. The actual system permission dialog is left for user acceptance;
  automated permission tests exercise app policy without granting permission on the phone.
- Lint: 0 errors and 17 existing maintenance warnings. No new dependencies or suppressions.
- The first navigation run was interrupted by a Home-key event in the device log; the
  isolated test and a subsequent full 10-test run passed without code changes.
- Installed the debug build on the connected phone. No screenshots or manual layout
  inspection were performed after the user's visual-review preference changed.
- Channel creation, notification posting, and WorkManager scheduling remain Phase 5.

Changed files: Home entry/action and MainActivity; new navigation, Settings state/VM/UI,
notification-access helper/model, settings strings and tests; additive DataStore permission
history and repository contract; manifest; persistent storage test and Home test fake.
The Kotlin compiler session directory is ignored; unrelated IDE files are left alone.

Validation:
- .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest --console=plain
- .\gradlew.bat :app:connectedDebugAndroidTest --console=plain (final full device run)
- .\gradlew.bat :app:installDebug --console=plain
- git diff --check

User review checklist:
- [ ] Open Settings, choose an interval, change each toggle independently, and reopen the app.
- [ ] Check large text/theme/layout and return using both Back to Home and Android Back.
- [ ] Use Allow notifications, then verify denial or grant is reflected correctly.
- [ ] If denied, verify the action opens Android settings and does not prompt repeatedly.
## Phase 5 — notification delivery and background synchronization

- [ ] Add the Exchange Rate Updates notification channel.
- [ ] Implement a small notification helper with app/system/channel permission checks.
- [ ] Use an explicit immutable PendingIntent that opens Home.
- [ ] Apply the selected first-fetch, change-only, and manual-refresh notification policies.
- [ ] Persist the last handled notification quote state sufficiently to reduce duplicates after process restart.
- [ ] Implement CoroutineWorker using the existing repository.
- [ ] Configure HiltWorkerFactory correctly and verify worker creation.
- [ ] Enqueue one unique periodic request named exchange_rate_periodic_sync.
- [ ] Use a network-connected constraint and the selected interval.
- [ ] Use ExistingPeriodicWorkPolicy.UPDATE for interval changes.
- [ ] Cancel periodic work when automatic checks are disabled; recreate it when enabled.
- [ ] Reconcile saved settings and scheduler state on startup so interruption cannot leave them permanently inconsistent.
- [ ] Read current notification preferences when the worker runs.
- [ ] Prevent notification failure/denial from causing another rate fetch.
- [ ] Add bounded retry/backoff for temporary network/server failures and respect Retry-After.
- [ ] Avoid repeated retries for invalid data, access denial, or invalid configuration.
- [ ] Remember that Result.failure does not permanently stop periodic work; cancel explicitly if suspension is needed.
- [ ] Avoid unnecessary immediate duplicate fetching when scheduling after a recent manual/initial refresh.
- [ ] Test worker outcomes, unique scheduling, interval changes, disabling, retry policy, and notification decisions.
- [ ] Run assembly/local tests and exercise a worker through testing tools.
- [ ] Update this file and stop.

Exit: one schedule performs refresh -> persistence -> optional notification and respects settings.

Background expectations:
- Normal app closure or process removal does not require an always-running app process.
- WorkManager restores eligible work across reboot under normal Android conditions.
- Network restrictions, Doze, battery policy, and manufacturer behavior may delay execution.
- Force-stop suspends normal background work until user action takes the app out of the stopped state.
- “Every 24 hours” is an interval, not a guaranteed local clock time.
- UPDATE changes the schedule specification; it does not force an immediate fetch or interrupt the active run.

## Phase 6 — personal-use acceptance and release readiness

- [ ] Verify cold launch with no cache, online and offline.
- [ ] Verify launch with cache and failed refresh.
- [ ] Verify process restart retains settings and quote.
- [ ] Verify background updates reach an already-open Home screen.
- [ ] Verify one periodic schedule after repeated launches/interval changes.
- [ ] Verify automatic-checks off cancels polling.
- [ ] Verify notifications off still permits enabled polling.
- [ ] Verify denied app permission and blocked notification channel.
- [ ] Verify changed and unchanged quote behavior and notification tap navigation.
- [ ] Verify normal closure, reboot recovery, battery restrictions, and force-stop expectations on a physical device.
- [ ] Verify supported Android versions, including API 24/25 time handling where test devices/emulators are available.
- [ ] Check large fonts, light/dark mode, screen insets, and RTL layout behavior.
- [ ] Decide whether to retain the placeholder application ID for personal testing or choose a permanent identity.
- [ ] Define backup behavior: restore suitable preferences; exclude transient scheduling/notification state and secrets.
- [ ] Enable release optimization and verify the optimized build, including provider parsing.
- [ ] Run local tests, Android lint, debug assembly, and release assembly.
- [ ] Complete a release-build device smoke test; handle signing material outside source control.
- [ ] Record any unavailable device checks as limitations, not passes.
- [ ] Document install steps, provider limitations, background behavior, and known issues.
- [ ] Update this file and stop.

Exit: a usable personal MVP with reproducible checks and explicit remaining limitations.

## Optional phases — do not start automatically

- [ ] History: agree retention/sampling rules, then evaluate Room.
- [ ] Store meaningful changes or agreed daily samples; do not automatically store every unchanged poll.
- [ ] Explain that device polling history is incomplete market history.
- [ ] Additional banks: validate each source independently and preserve source/quote identity.
- [ ] Threshold notifications or daily summaries: specify crossing/reset rules before coding.
- [ ] Widget: evaluate update cadence and battery impact.
- [ ] Public release: verify provider permission, quota strategy, privacy disclosures, current Play requirements, permanent app ID, and signing.
- [ ] Backend: add only if credentials, scale, or centrally maintained provider parsing justify it.

## Verification commands

From the Android Studio project root:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:lintDebug --console=plain
.\gradlew.bat :app:assembleRelease --console=plain
```

Run connected tests only when a suitable device/emulator is available.
Do not add tests that merely repeat trivial implementation details.
Use deterministic fixtures and injected time; do not make routine unit tests depend on a live banking website.

## Progress log

| Date | Phase | Completed work | Evidence | Remaining |
|---|---|---|---|---|
| 2026-09-10 | Baseline | Inspected starter; changed compile SDK 36.1 -> 37 | Debug assembly succeeded; 1 local test passed; git diff --check passed | Device tests not run |
| 2026-09-10 | Source investigation | CBE and Banque Misr access tested; API alternatives reviewed | CBE returned rejection HTML; Banque Misr returned USD buy/sell in HTML | Ongoing-use permission, parser fixture tests, timezone meaning |
| 2026-09-10 | Planning | Created full phased checklist with personal-use scope | This file | User plan review; no feature implementation |
| 2026-09-11 | Phase 1 | Added strict cash-rate parser, cancellable bounded HTTPS source, decimal model, Hilt/KSP and desugaring; accepted plan defaults | Debug APK built; 28 tests passed including fresh captured-page probe; lint 0 errors/16 warnings; diff check passed | Phase 2 cache/repository; source timezone/permission limitations; device checks |
| 2026-09-11 | Git workflow | Pushed Phase 1 and wireframes to master as 0b41ac8; created and pushed dev, then codex/phase-2-cache-repository from dev | All three branches tracked their matching origin branches at 0b41ac8 | Phase 2 subsequently approved for push and continued development |
| 2026-09-11 | Phase 2 | Added observable atomic quote cache, persistent settings, injected repositories, explicit storage errors and refresh coordination | Debug assembly passed; 48 tests passed/1 optional probe skipped; lint 0 errors/17 warnings; diff check passed | Phase 3 Home; UI storage recovery; device checks; provider limitations unchanged |
| 2026-09-11 | Phase 3 | Added Home, lifecycle state, freshness policy, manual refresh, storage retry and accessible formatting; user accepted visual result | Debug build; 62 local tests passed/1 probe skipped; 5 phone tests passed; lint 0 errors/17 warnings; real refresh and cold restart verified | Phase 4 Settings; future visual reviews belong to the user |

| 2026-09-11 | Phase 3 Git | Committed Home as d0ae23d; pushed its feature branch and fast-forwarded dev; created codex/phase-4-settings | Git pushes succeeded; master remains 0b41ac8 | Phase 4 local changes |
| 2026-09-11 | Phase 4 | Added saved Settings, two-screen navigation, OS notification access state and durable single-prompt policy | Debug build installed; 74 local tests passed/1 probe skipped; final 10 device tests passed; lint 0 errors/17 warnings | User visual/OS permission review; Phase 5 scheduler and notification delivery |

Append a row after every implementation phase. Include a short explanation for any changed requirement.

## Sources checked during planning

References are evidence of the reviewed information, not guarantees of future availability or legal approval.

- Banque Misr rates: https://www.banquemisr.com/en/CAPITAL-MARKETS/Exchange-Rates-and-Currencies?sc_lang=en
- CBE rates: https://www.cbe.org.eg/en/economic-research/statistics/cbe-exchange-rates
- AGP compatibility: https://developer.android.com/build/releases/agp-9-2-0-release-notes
- AGP built-in Kotlin/KSP guidance: https://developer.android.com/build/migrate-to-built-in-kotlin
- java.time desugaring: https://developer.android.com/studio/write/java8-support
- DataStore: https://developer.android.com/topic/libraries/architecture/datastore
- Periodic work: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- Work states: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/states
- Work UPDATE policy: https://developer.android.com/reference/androidx/work/ExistingPeriodicWorkPolicy
- Notification permission: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- AllRatesToday CBE data: https://allratestoday.com/central-bank-rates-api/cbe/
- AllRatesToday pricing/terms: https://allratestoday.com/pricing and https://allratestoday.com/terms/
- Fexant pricing/terms: https://www.fexant.com/pricing and https://www.fexant.com/terms
