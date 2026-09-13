package com.example.currencyraise.domain.model

enum class Bank(val displayName: String, val sourceName: String, val sourceUrl: String) {
    CIB(
        "CIB", "CIB via Ta3weem",
        "https://ta3weem.com/en/banks/commercial-international-bank-cib",
    ),
    BANQUE_MISR(
        "Banque Misr", "Banque Misr",
        "https://www.banquemisr.com/en/CAPITAL-MARKETS/Exchange-Rates-and-Currencies?sc_lang=en",
    ),
}
