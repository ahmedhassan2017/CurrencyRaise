# Home page cleanup review

Branch: `codex/home-page-cleanup`

Status: approved by the user after UI review, including the rounded buttons and restored bank/rate section.

## Changes

- Focus Home on bank selection, USD/EGP buy and sell prices, independent direction indicators, and refresh.
- Keep source attribution and a compact check-time or stale-rate message with the quote.
- Show history on demand through Show rate history / Hide rate history.
- Move detailed source notes, timestamps, comparison time, and the source link into Rate details.
- Remove the duplicated background-status section from Home; it remains in Settings.
- Preserve the reference screenshot's bank/rate section: Select bank label, source badge above the large currency heading, currency-unit subtitle, and vertically stacked buy/sell cards with the original typography, padding, and colors.
- Save the requested branch, review, approve, commit, and push workflow in AGENTS.md.
- Review adjustment: use the shared 12 dp rounded rectangle shape for buttons and selection chips across Home, history, Settings, and dialogs.

## Verification

- Debug app and Android test APKs assembled successfully.
- 33 home unit tests passed.
- 14 emulator tests passed: 6 HomeScreenTest, 3 RateDirectionTest, 4 RateHistoryChartTest, 1 NavigationTest.
- Lint completed with 0 errors and 21 warnings.
- Full unit suite: 165 tests, 7 failures, 2 skipped. Six failures report Windows DataStore file-rename errors; one reports a settings-persistence assertion failure. Failing classes: PersistentStoresTest (6) and PermissionHistoryMigrationTest (1). These storage files were not changed in this step; a baseline reproduction was not performed.
- Full-suite XML results preserved in ignored `build/home-review/full-unit-results/` before running the focused tests.
- Visually checked the actual app's Home and Rate details dialog on the connected emulator. Screenshots: ignored `build/home-before.png`, `build/home-after.png`, and `build/home-details.png`.
- Debug build installed and Home left open for review. APK: `app/build/outputs/apk/debug/app-debug.apk`.
- After the rounded-button adjustment, debug assembly and lint passed again. Visually verified Home and Settings; screenshots are in ignored `build/home-rounded-buttons.png` and `build/settings-rounded-buttons.png`. The behavior tests above were run before this shape-only adjustment.
- After restoring the bank/rate section to the supplied screenshot, debug and Android test assembly plus lint passed. All 9 HomeScreenTest and RateDirectionTest checks passed on the connected RMX5106 phone, including large-text scrolling. Visually checked dark mode; screenshot: ignored `build/home-reference-restored.png`. The updated debug app is installed and Home is open on that phone for review.

## User review

Check both banks, refresh, Show/Hide rate history, Rate details, and Settings. Approve this step or request adjustments before committing or pushing.

After approval, commit only this step's files and push this branch. Existing staged release APK/profile/metadata files belong to earlier work and must be preserved without including them in this commit.

Next steps, each with the same review gate and its own branch:

1. Arabic language support.
2. A user-selectable light/dark appearance setting.
