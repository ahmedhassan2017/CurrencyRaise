# Release optimization review

Reviewed 2026-09-11 for Phase 6.

The project uses AGP 9.2.1 and its standard minify/shrink settings. The release build enables
code and resource optimization with the optimized Android default configuration.
No package restrictions or full-mode opt-out are configured.

There are no app-specific keep-rule files to remove or narrow. Hilt, WorkManager,
DataStore, Compose, coroutines, OkHttp, and Jsoup retain their consumer rules.
Do not add broad package-wide keep rules or blanket warning suppression as a
substitute for understanding an actual optimized-build failure.

The releaseSmoke variant inherits release optimization, uses a separate application
ID, and is signed with the local debug certificate only for device checks.
The normal release artifact remains unsigned until explicitly signed for distribution.

Verification results are recorded in IMPLEMENTATION_PLAN.md. Automated optimized
device checks must cover Hilt worker creation, provider parsing, persistence,
Home/Settings behavior, and notification construction. Visual and real long-running
device acceptance remain separately tracked.

References:
- https://developer.android.com/reference/tools/gradle-api/9.2/com/android/build/api/dsl/Optimization
- https://developer.android.com/topic/performance/app-optimization/enable-app-optimization
- https://developer.android.com/studio/test/advanced-test-setup
