# Arabic language review

Branch: `codex/arabic-language`

Status: implemented, awaiting the user's own UI review. No commit or push yet.

## Changes

- Add Arabic resources for Home, Settings, history, background status, errors, dialogs, accessibility labels, and notifications.
- Add Follow system, English, and Arabic choices to Settings.
- Use Android's per-app locale APIs with automatic persistence and Android 13+ system-settings synchronization.
- Enable generated locale configuration for English and Arabic.
- Localize displayed bank/source names without changing provider IDs, URLs, cached rates, or parsing logic.
- Resolve notification text from the selected app language, including when a background worker posts it.
- Preserve the approved Home design and rounded button shapes; Arabic uses Android's RTL layout direction.

## User review

Change Settings > Language to Arabic and check Home, both bank tabs, Rate details,
Show rate history, Settings, and a debug test notification. Then switch back to
English and confirm the layout returns to LTR.

## Verification

- Debug app and Android test APK assembled successfully.
- Lint passed with 0 errors.
- 47 focused presentation and notification unit tests passed.
- 20 device tests passed, covering Arabic resources and RTL direction, the language picker, Home, direction indicators, navigation recreation, and notifications.
- Switched from Follow system to Arabic in the app, visually checked Settings and Home in RTL, then switched to English and confirmed LTR was restored.
- Review screenshots are in ignored `build/arabic-settings.png` and `build/arabic-home.png`.
- The debug build is installed on emulator-5554 with the Arabic Home screen open for the user's review.

After approval, commit only this step's files and push this branch before starting
the separate light/dark appearance step. Existing staged release artifacts remain excluded.
