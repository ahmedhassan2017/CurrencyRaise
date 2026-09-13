# CIB, history, and rate direction

## Agreed workflow

- Add CIB alongside Banque Misr for USD/EGP bank buy and sell rates.
- Implement and validate one step at a time on a separate branch.
- Present the completed diff and validation results before every commit.
- Commit only after the user explicitly confirms that particular commit.
- Create each subsequent branch from the approved preceding step.
- Preserve the already staged files under `app/release/`; exclude them from feature commits.

## 1. CIB support and change detection

Branch: `codex/cib-rate-tracking`

- Verify the live source's response contract before implementing its parser.
- Keep Banque Misr available and add a bank selector.
- Isolate each bank's persisted quote, refresh coordination, request deadlines,
  and notification baseline. Refresh and alert for both banks in the background.
- Treat the first quote for each bank as a silent baseline. Compare only quotes
  with the same bank, currency pair, and quote kind, using decimal arithmetic.
- Preserve each bank's last good quote when its source fails. One bank's failure
  must not prevent the other bank from updating.
- Keep manual refresh silent and use distinct notification identities for banks.
- Verify provider parsing, invalid responses, persistence, bank isolation,
  partial refresh failures, background behavior, and bank selection.

Source investigation on 2026-09-13:

- Official page: https://www.cibeg.com/en/currency-converter
- A direct HTTPS request returned an Imperva challenge script instead of rates.
- Normal browser navigation required an hCaptcha security check.
- No CAPTCHA was solved and no access control was bypassed.
- A live response contract and suitability for unattended fetching remain
  unverified. Do not invent an endpoint, parse guessed fixtures, or present
  historical sample values as current rates.
- User approved a third-party CIB rate source. Ta3weem's public CIB table was
  verified and selected; data is labeled CIB via Ta3weem.

## 2. History and charts

Planned branch: `codex/daily-weekly-rate-charts`

- Save real observed quotes per bank with a defined retention bound.
- Provide daily and weekly chart views for both buy and sell rates.
- Use observation times for chart positions unless a provider's publication
  timezone is verified. Label the distinction clearly.
- Begin history with actual stored observations; do not fabricate earlier prices
  or silently fill periods where the app collected no data.
- Show useful empty and single-observation states.
- Validate retention, date boundaries, ordering, bank isolation, and accessible
  chart summaries. Finalize the chart windows and aggregation before this step.

## 3. Rate arrows

Planned branch: `codex/rate-direction-arrows`

- Compare buy and sell independently with the preceding comparable observation.
- Display up/down direction and the numeric change with accessible text.
- First quotes have no direction; unchanged quotes must not indicate movement.
- Keep saved comparison data available across process restarts.
- Validate rises, falls, unchanged prices, mixed directions, and first quotes.

## 4. Notification arrows

Planned branch: `codex/notification-direction-arrows`

- Reuse the same decimal comparison rules for notification buy/sell lines.
- Include the bank name and independently correct direction for each rate.
- Preserve silent first baselines, duplicate suppression, notification
  preferences, permission handling, and silent manual refresh behavior.
- Verify notification content, separate bank identities, duplicate handling,
  and notification taps.

## Status

Step 1 implemented on `codex/cib-rate-tracking`: third-party CIB parser, separate
storage and alert baselines, Home bank selection, both-bank background checks,
and notifications opening the relevant bank. CIB is the first tab and the default
on fresh launches; restored screen selection and notification targets are respected.
The user approved committing and pushing Step 1 after this default-tab adjustment,
then starting Step 2. Later commits still require separate user approval.

Validation on 2026-09-13:

- Unit suite: 139 tests, 138 passed, 1 skipped, zero failures/errors. The skipped
  test is the opt-in Banque Misr captured-page probe; the CIB captured-page probe ran
  successfully against the downloaded real HTML.
- Debug lint: zero errors, 18 warnings (dependency/target suggestions and existing
  unused resources/manifest label).
- Debug app, optimized release, and Android test APK assembled successfully.
- Focused device tests were requested for Home, notification content/taps,
  background workers, and the data pipeline. The phone disconnected before the
  tests started; Gradle reported No connected devices. These checks and live Android
  CIB fetching remain unverified; a compiled test APK is not a device test pass.
- Charts, UI arrows, and notification arrows are intentionally still pending their
  separate branches and user-approved commits.

CIB-first follow-up: the full debug unit suite, lint, debug APK, and Android test
APK checks passed with CIB first and selected on a fresh launch. Saved-state and
notification selections continue to take precedence when present.
