# Release optimization review

Reviewed 2026-09-11 for Phase 6.

The project uses AGP 9.2.1. The normal release enables code and resource optimization
with the optimized Android default configuration. It has no app-specific keep rules,
package-wide retention, warning suppression, or full-mode opt-out.

The isolated releaseSmoke target and test APK each contain the same narrow rule for
`androidx.tracing.Trace`. These rules support an AndroidX instrumentation-runner
experiment; application code does not use reflection to load this class. The target
rule allowed the runner to advance, but optimized instrumentation remains unreliable
before test discovery. Remove both rules and their optimized-instrumentation wiring
when that experimental route is retired or replaced. They do not affect the normal
release artifact.

No broader rule subsumes these rules. Hilt, WorkManager, DataStore, Compose, coroutines,
OkHttp, and Jsoup use their library consumer configuration; no extra library-wide rules
should be added.

The optimized, non-debuggable releaseSmoke APK passed standalone device checks through
the parser, live HTTPS source, persistence, Home state, cold restart, and WorkManager
registration. Continue running the UI/device behavior suite against debug, and rerun the
standalone optimized smoke after dependency or shrinker changes.

References:
- https://developer.android.com/reference/tools/gradle-api/9.2/com/android/build/api/dsl/Optimization
- https://developer.android.com/topic/performance/app-optimization/enable-app-optimization
- https://developer.android.com/studio/test/advanced-test-setup
