package com.app.minicaisse.printing

import com.app.minicaisse.data.local.SaleEntity

// Définit le résultat de l'impression
sealed interface PrintResult {
    data object Success : PrintResult
    data class Failure(val reason: String) : PrintResult
}

// L'interface de l'imprimante
interface Printer {
    suspend fun print(sale: SaleEntity): PrintResult
}