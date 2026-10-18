# Background notification investigation

Date: 18 October 2026 (Africa/Cairo)

Investigated checkout: `68dc8d1`, on `codex/background-notification-investigation`.

## Conclusion and confidence

The October 9 dollar-only change did not directly change background scheduling or dollar change detection. The scheduler and worker files have identical Git blob hashes before that change and in the investigated checkout. Dollar alert handling and the dollar rate repository are also unchanged across that range.

There are reproducible reliability defects in the existing scheduling architecture. An earlier September 25 change, `077f5c5`, replaced native recurring work with a succession of one-time workers to accommodate a five-minute interval. An unexpected exception can now terminate recurrence until startup configuration is applied again. Startup can also replace a valid multi-entry chain even when the requested interval has not changed.

The reported phone is Realme, corrected from the initial Samsung description. Notifications stop after swiping away the app and leaving it inactive, and resume after reopening. This is compatible with both a broken work chain and manufacturer background restrictions. No logs or scheduling state from that phone were available, so neither is established as the specific trigger on the phone. The reproduction tests establish code defects, not a reproduction of an overnight Realme failure.

## Change comparison

| Change | Finding |
| --- | --- |
| `077f5c5`, September 25: minute-based intervals | Replaced `PeriodicWorkRequest` with `OneTimeWorkRequest`; added manual successor scheduling. This changed failure recovery. |
| `02b8c56`, October 9: dollar-only notifications | Removed the gold alert pipeline. Gold prices still refresh. Dollar notification IDs, channel ID, scheduler, worker, and dollar change detection remain intact. |
| Tabs/history/CI changes since then | No modifications to the worker or scheduling implementation. |

The startup cleanup introduced by the dollar-only change cancels only the old gold notification IDs, `1011..1013`. Dollar notifications use `1001` and `1003`; the channel remains `exchange_rate_updates`. That cleanup does not cancel background jobs.

There is a possible indirect relationship: fewer visible notifications can change an app's activity/standby classification. This is a hypothesis; it was not measured on this Realme phone. Android documents visible notifications among the conditions relevant to App Standby. [Android Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby).

## Confirmed defects

### 1. An unexpected provider exception can terminate future checks

`RateSyncWorker.kt:18` invokes `runner.run()` outside the exception handler that protects rescheduling. `BankBackgroundRefreshRunner.kt:10` runs all providers in the same coroutine scope and awaits them together. An unexpected exception from one provider cancels the other providers and escapes the worker before `scheduleNext()` is reached.

This includes the gold price refresh task, which remains part of the background group even though gold notifications were removed. A gold task exception can therefore affect dollar checks. Normal HTTP/network errors represented as `BackgroundResult` already have bounded retries and successor scheduling; the reproduction specifically concerns an unexpected exception.

WorkManager then marks the one-time work as failed. With no durable recurring request or independent recovery task, there is no automatic next check. Startup calls `applyConfiguration()` again and can recreate the work, explaining how reopening could restore checks.

### 2. Startup reconciliation can discard a valid chain

`WorkSyncScheduler.kt:68` recognizes existing work only when exactly one unfinished entry exists. A one-time chain can have a current entry and a blocked successor during handoff, or after an interrupted handoff. Reapplying the same settings to a two-entry chain cancels/replaces that chain and starts a new initial delay.

The diagnostic reproduced this with an enqueued parent and blocked successor. It did not measure how often startup overlaps a running-worker handoff on a real phone. Cancellation and replacement are also separate awaited operations, leaving an additional interruption window between them.

### 3. The UI does not report actual worker health

The scheduler publishes `BackgroundStatus.Active` after applying configuration. It does not continuously observe worker failure, OS stop reasons, or overdue work. Consequently, the status can describe a successfully configured interval while subsequent work has failed or is being deferred.

## Runtime evidence

Added `BackgroundReliabilityInvestigationTest.kt`, containing diagnostic assertions of the current behavior:

| Diagnostic | Observed result |
| --- | --- |
| Unexpected provider exception | The other provider was cancelled; successor scheduling count was zero. |
| Failed scheduled production-worker delegate | WorkManager recorded `FAILED`; no unfinished work remained. Applying configuration created a replacement. |
| Unchanged settings with a two-entry chain | Both entries were replaced by a new request. |

All three diagnostics passed in a direct instrumentation run on the Android 17 Pixel AVD. These are reproductions of undesirable behavior, not a reliability acceptance suite. The future fix should replace these assertions with regression expectations for recovery and preserved scheduling.

The first Gradle-driven instrumentation attempt ended before running any tests. The emulator showed memory pressure during startup. After it was fully booted, the same compiled APKs ran successfully using:

```powershell
adb -s emulator-5554 shell am instrument -w -e class com.example.currencyraise.background.BackgroundReliabilityInvestigationTest com.example.currencyraise.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Result: `OK (3 tests)`. App-specific evidence is saved locally in `app/build/background-reliability-evidence.log`. The full unit suite was not run; the unrelated CI test edits in the checkout were preserved.

## Realme and Android restrictions

Realme's own notification troubleshooting instructions identify background activity and auto-launch permissions as relevant controls. Check Currency Raise under App info / Battery usage, or Battery / App battery management, and inspect **Allow background activity** and **Allow auto launch**. Menu names differ by Realme UI version. [Realme notification troubleshooting](https://www.realme.com/global/support/faq).

Android Doze also defers WorkManager jobs and their network access. Changing the app scheduler does not remove those OS restrictions. [Android Doze documentation](https://developer.android.com/training/monitoring-device-state/doze-standby).

The user subsequently confirmed that Currency Raise's background activity setting was **Smart** and changed it to allow background activity regardless of battery condition. This strengthens the manufacturer battery-management explanation, but no observation after the change has yet established whether it resolves the reported interruptions. It does not resolve the independently reproduced scheduler defects.

Keep the app version and check interval unchanged while evaluating this setting. Verify whether genuine changed dollar prices produce notifications after Recents dismissal and prolonged inactivity, before reopening the app. A lack of notifications while dollar prices stay unchanged is expected and does not demonstrate a failure.

The selected update interval, Realme model/UI version, auto-launch setting, and duration before the original failure remain unconfirmed. No job state or logs have been collected from the physical phone.

## Recommended fixes

1. **Restore a durable recurring background schedule.** Prefer unique `PeriodicWorkRequest` for background intervals of at least 15 minutes, with an update policy that preserves existing work. Migrate the legacy one-time chain once. Recurrence should not depend on a worker successfully scheduling its successor. Android documents that periodic work recurs until cancelled and has a minimum interval of 15 minutes. A five-minute background cadence cannot be promised; make the distinction visible if five-minute foreground checks remain available. [PeriodicWorkRequest contract](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest).
2. **Isolate provider failures and add a worker exception boundary.** Catch unexpected recoverable exceptions per provider, record the failure, and retain bounded retries. Preserve cancellation propagation. One provider must not cancel otherwise healthy dollar checks. Do not add unconditional rescheduling in `finally`: deliberate cancellation must remain effective, and a failed parent can still fail its dependents.
3. **Replace destructive startup reconciliation.** Preserve valid matching work instead of cancelling it based on the count of unfinished chain entries. Ensure interval changes and worker completion cannot recreate outdated work. Avoid repeatedly resetting the initial delay.
4. **Persist minimal diagnostics.** Record worker start/completion, result/stop reason, next pending work, fetch outcome, and notification decision (changed/unchanged/permission denied/error). Surface actual worker health and a link to the phone's relevant battery settings. Do not rely on a scheduling-success label as a health signal.
5. **Validate on the actual Realme phone.** Capture scheduling state before reopening, then test Recents dismissal, screen-off idle, network loss/recovery, process death, and reboot. A pending overdue job suggests OS deferral; no pending work after a failure suggests the broken-chain path. Verify alerts only using genuine changed dollar prices or clearly identified test data.

For time-sensitive alerts during prolonged idle, a backend that checks rates and sends user-visible Firebase Cloud Messaging notifications is a stronger architecture than device-only polling. It requires a server and still cannot guarantee delivery while the phone is offline or the app is explicitly force-stopped. Android recommends FCM for user-visible messages during Doze. Do not use a permanent foreground service solely to evade idle restrictions. [Android's guidance](https://developer.android.com/training/monitoring-device-state/doze-standby).

## Scope of this investigation

Production application behavior was not changed. This investigation consists of this report and diagnostic tests; the recommended runtime fixes have not been implemented. The next implementation should address recurring scheduling and exception isolation first, retain dollar-only notifications, then be verified on the Realme before making any reliability claim.
