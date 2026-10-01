package com.app.minicaisse.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface SaleDao {
    @Insert
    suspend fun insertSale(sale: SaleEntity)

    @Insert
    suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Update
    suspend fun updateSale(sale: SaleEntity)

    @Query("SELECT * FROM sales WHERE printStatus IN ('PENDING', 'FAILED')")
    suspend fun getPendingOrFailedPrints(): List<SaleEntity>

    @Query("SELECT * FROM sales WHERE syncStatus = 'PENDING'")
    suspend fun getUnsyncedSales(): List<SaleEntity>
       
    @Query("SELECT * FROM sales ORDER BY ticketNumber DESC")
    fun getAllSales(): kotlinx.coroutines.flow.Flow<List<SaleEntity>>
}

@Dao
interface TicketCounterDao {
    @Query("SELECT nextTicketNumber FROM ticket_counter WHERE id = 1")
    suspend fun getNextTicketNumber(): Long?

    @Insert
    suspend fun insertCounter(counter: TicketCounterEntity)

    @Query("UPDATE ticket_counter SET nextTicketNumber = nextTicketNumber + 1 WHERE id = 1")
    suspend fun incrementCounter()
}