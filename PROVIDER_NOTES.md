# Banque Misr prototype provider

Validated scope: personal-use USD/EGP cash/notes quotes only.
Source: https://www.banquemisr.com/en/CAPITAL-MARKETS/Exchange-Rates-and-Currencies?sc_lang=en
Inspection date: 2026-09-10.

## Observed contract

The public HTML contains section#generic-table.exchange-rates, an EGP denomination
label, and a two-row table header. Notes and Transfer each have Buy/Sell columns.
Cash prices come from the first pair; the parser rejects unexpected header layouts
rather than silently interpreting the transfer pair as cash.

The displayed page timestamp was 10-09-2026 14:28:09 and quote identifier 2026091015.
Neither the timestamp label nor the page supplies a timezone or explicitly guarantees
a quote-effective instant. Preserve this as sourceDisplayedAt: LocalDateTime, with
no conversion to UTC, no inferred offset, and no fabricated publication instant.
fetchedAt is independently recorded by Clock.systemUTC().
Future UI must label this as the source's displayed time (timezone unspecified).
Source-time-based freshness comparisons must wait for a verified timezone contract;
last successful fetch age can be evaluated independently.

## Failure and security policy

Reject missing/duplicate USD rows, incomplete cells, ambiguous header layouts,
invalid/nonpositive decimals, reversed cash spreads, and rejection/login pages.
HTTP 200 is insufficient to establish success.
Accept only HTML, cap decompressed response content at 512 KiB, and close every body.
Use HTTPS, normal certificate validation, bounded timeouts, no redirects, no implicit
connection retries, and no request/body logging. No API key or cookie is needed.
Cancellation cancels the underlying OkHttp request.
A later repository owns cache preservation and refresh coordination.

## Limits

A successful probe is not a supported API contract, uptime guarantee, or permission
for repeated automated use or redistribution. Explicit automated-use permission
has not been verified. Do not bypass access controls if access becomes restricted.
No backend, account, paid API, or alternate provider is introduced in Phase 1.
Do not use captured fixtures as a live fallback.

## Verification

Routine tests use a reduced historical fixture and local MockWebServer only.
An explicit captured-page probe may be run with:
```powershell
.\gradlew.bat :app:testDebugUnitTest "-PrateProbeFile=C:\absolute\path\to\fresh-page.html" --console=plain
```
Capture the public page separately; the test exercises the production parser/mapper
without introducing a network dependency into ordinary tests. The full captured page
is not committed because it contains unrelated scripts and tracking content.

Dependencies introduced: Hilt 2.60.1, KSP 2.3.12, OkHttp/MockWebServer 5.3.0,
Jsoup 1.22.2, Coroutines 1.10.2, desugar_jdk_libs 2.1.5.
Versions are pinned in the existing version catalog and validated by the phase build.

## Phase 1 verification — 2026-09-11

Debug assembly and all 28 tests passed, including the opt-in full captured-page probe.
The probe parsed cash buy 51.27 and sell 51.37 from the 2026-09-10 capture and preserved
the page-displayed 2026-09-10T14:28:09 without assigning a timezone.
These historical values are test evidence, not current-price claims.

Android lint passed with 0 errors and 16 warnings (dependency update suggestions,
existing starter resources/label, and target SDK). Versions are compatible tested
pins, not a claim that every dependency is the newest available.
Device execution and real Android network access remain untested.