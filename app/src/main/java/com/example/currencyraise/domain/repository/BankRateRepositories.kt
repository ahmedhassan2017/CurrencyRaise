package com.example.currencyraise.domain.repository

import com.example.currencyraise.domain.model.Bank

/** Each entry owns a separate cache, refresh lock, and provider retry deadline. */
class BankRateRepositories(private val repositories: Map<Bank, ExchangeRateRepository>) {
    val banks: List<Bank> = Bank.entries.filter { it in repositories }

    init { require(Bank.BANQUE_MISR in repositories) }

    operator fun get(bank: Bank): ExchangeRateRepository = repositories.getValue(bank)
}
