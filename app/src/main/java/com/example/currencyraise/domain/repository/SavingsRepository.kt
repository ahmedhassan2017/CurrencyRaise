package com.example.currencyraise.domain.repository

import com.example.currencyraise.domain.model.SavingsBalance
import com.example.currencyraise.domain.model.SavingsWriteResult
import kotlinx.coroutines.flow.Flow

interface SavingsRepository {
    /** Read failures throw StorageReadException; saved balances are never silently replaced with zero. */
    fun observeSavings(): Flow<SavingsBalance>
    suspend fun saveSavings(balance: SavingsBalance): SavingsWriteResult
}
