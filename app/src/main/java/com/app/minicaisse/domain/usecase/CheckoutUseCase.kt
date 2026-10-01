package com.app.minicaisse.domain.usecase

import androidx.room.withTransaction
import com.app.minicaisse.data.local.AppDatabase
import com.app.minicaisse.data.local.PrintStatus
import com.app.minicaisse.data.local.SaleDao
import com.app.minicaisse.data.local.SaleEntity
import com.app.minicaisse.data.local.SyncStatus
import com.app.minicaisse.data.local.TicketCounterDao
import com.app.minicaisse.data.local.TicketCounterEntity
import com.app.minicaisse.data.local.SaleItemEntity
import java.util.UUID

class CheckoutUseCase(
    private val db: AppDatabase,
    private val saleDao: SaleDao,
    private val ticketCounterDao: TicketCounterDao
) {
    data class CartItem(val productId: Int, val productName: String, val unitPrice: Double, val quantity: Int)

    suspend fun execute(cartItems: List<CartItem>): SaleEntity {
        // 1. Initialiser le compteur s'il n'existe pas
        if (ticketCounterDao.getNextTicketNumber() == null) {
            ticketCounterDao.insertCounter(TicketCounterEntity(id = 1, nextTicketNumber = 1))
        }

        // 2. Lire le numéro de ticket
        val ticketNumber = ticketCounterDao.getNextTicketNumber()!!

        // 3. Calculer le total
        val totalAmount = cartItems.sumOf { it.unitPrice * it.quantity }

        // 4. Créer l'entité Vente
        val saleId = UUID.randomUUID().toString()
        val newSale = SaleEntity(
            localId = saleId,
            ticketNumber = ticketNumber,
            totalAmount = totalAmount,
            createdAt = System.currentTimeMillis(),
            printStatus = PrintStatus.PENDING,
            syncStatus = SyncStatus.PENDING
        )

        // 5. Créer les lignes de vente
        val saleItems = cartItems.map { item ->
            SaleItemEntity(
                id = UUID.randomUUID().toString(),
                saleId = saleId,
                productId = item.productId,
                productName = item.productName,
                unitPrice = item.unitPrice,
                quantity = item.quantity
            )
        }

        // 6. Exécuter la transaction atomique de manière propre
        db.withTransaction {
            saleDao.insertSale(newSale)
            saleDao.insertSaleItems(saleItems)
            ticketCounterDao.incrementCounter()
        }

        return newSale
    }
}