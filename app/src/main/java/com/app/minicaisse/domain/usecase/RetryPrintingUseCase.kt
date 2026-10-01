package com.app.minicaisse.domain.usecase

import com.app.minicaisse.data.local.PrintStatus
import com.app.minicaisse.data.local.SaleDao
import com.app.minicaisse.data.local.SaleEntity
import com.app.minicaisse.data.local.SyncStatus
import com.app.minicaisse.printing.PrintResult
import com.app.minicaisse.printing.Printer

class RetryPrintingUseCase(
    private val saleDao: SaleDao,
    private val printer: Printer
) {
    // 1. Méthode pour le démarrage de l'app (Retry global)
    suspend fun retryAllPending() {
        val pendingSales = saleDao.getPendingOrFailedPrints()
        for (sale in pendingSales) {
            printSingleSale(sale)
        }
    }

    // 2. Méthode pour un ticket spécifique (Nouvelle vente)
    suspend fun printSingleSale(sale: SaleEntity) {
        val result = printer.print(sale)
        
        when (result) {
            is PrintResult.Success -> {
                saleDao.updateSale(sale.copy(
                    printStatus = PrintStatus.PRINTED,
                    syncStatus = SyncStatus.PENDING
                ))
            }
            is PrintResult.Failure -> {
                saleDao.updateSale(sale.copy(
                    printStatus = PrintStatus.FAILED,
                    syncStatus = SyncStatus.PENDING
                ))
            }
        }
    }
}