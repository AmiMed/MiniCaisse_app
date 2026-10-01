package com.app.minicaisse.printing

import android.util.Log
import com.app.minicaisse.data.local.SaleEntity
import kotlinx.coroutines.delay

class FakePrinter : Printer {
    override suspend fun print(sale: SaleEntity): PrintResult {
        // Simule le temps d'impression de l'imprimante thermique (1 seconde)
        delay(1000)
        
        Log.d("PRINTER", "Impression du Ticket N°${sale.ticketNumber} - Total: ${sale.totalAmount}TND")
        
        // Pour le test, on dit que c'est toujours un succès. 
        // (Tu pourras changer ça en Failure pour tester la logique de retry)
        return PrintResult.Success
    }
}