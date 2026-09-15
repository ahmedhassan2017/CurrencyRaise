package com.example.currencyraise.data.repository

import com.example.currencyraise.data.local.SavingsStore
import com.example.currencyraise.domain.model.SavingsBalance
import com.example.currencyraise.domain.model.SavingsWriteResult
import com.example.currencyraise.domain.model.StorageReadException
import com.example.currencyraise.domain.repository.SavingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.catch

internal class DefaultSavingsRepository(
    private val store: SavingsStore,
) : SavingsRepository {
    override fun observeSavings() = store.observe().catch { error ->
        if (error is IOException) throw StorageReadException(error)
        throw error
    }

    override suspend fun saveSavings(balance: SavingsBalance): SavingsWriteResult = try {
        store.save(balance)
        SavingsWriteResult.SAVED
    } catch (_: IOException) {
        SavingsWriteResult.STORAGE_FAILURE
    }
}
