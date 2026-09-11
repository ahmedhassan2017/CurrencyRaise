# Banque Misr fixture

Reduced from a fresh public HTML response fetched on 2026-09-10.
Only the rate section, its displayed timestamp/quote identifier, and two currency rows remain.
Navigation, third-party scripts, tracking, and unrelated page content are excluded.

Source: https://www.banquemisr.com/en/CAPITAL-MARKETS/Exchange-Rates-and-Currencies?sc_lang=en

The fixture is historical test input, not a live rate or production fallback.
Parser tests deliberately mutate cash/transfer prices to verify column selection.
The timestamp has no supplied timezone and is preserved as LocalDateTime.
