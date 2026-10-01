package com.app.minicaisse.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PrintStatus { PENDING, PRINTED, FAILED }
enum class SyncStatus { PENDING, SYNCED }

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey val localId: String, // UUID
    val ticketNumber: Long,
    val totalAmount: Double,
    val createdAt: Long,
    val printStatus: PrintStatus,
    val syncStatus: SyncStatus
)

@Entity(tableName = "sale_items")
data class SaleItemEntity(
    @PrimaryKey val id: String,
    val saleId: String,
    val productId: Int,
    val productName: String,
    val unitPrice: Double,
    val quantity: Int
)

@Entity(tableName = "ticket_counter")
data class TicketCounterEntity(
    @PrimaryKey val id: Int = 1, // toujours 1 : une seule ligne
    val nextTicketNumber: Long
)