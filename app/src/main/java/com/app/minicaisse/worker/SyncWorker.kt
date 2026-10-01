package com.app.minicaisse.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.app.minicaisse.data.firebase.FirebaseDataSource
import com.app.minicaisse.data.local.AppDatabase
import com.app.minicaisse.data.local.SyncStatus
import kotlinx.coroutines.CancellationException

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val firebase = FirebaseDataSource()
        
        // 1. Récupérer toutes les ventes en attente de synchronisation
        val unsyncedSales = db.saleDao().getUnsyncedSales()
        
        if (unsyncedSales.isEmpty()) {
            return Result.success()
        }
        
        Log.d("FIREBASE_SYNC", "Tentative de synchro de ${unsyncedSales.size} vente(s)...")
        
        var hadError = false

        for (sale in unsyncedSales) {
            try {
                // 2. Préparer l'objet mis à jour
                val syncedSale = sale.copy(syncStatus = SyncStatus.SYNCED)
                
                // 3. Envoyer la version "SYNCED" vers Firebase
                firebase.syncSale(syncedSale)
                
                // 4. Mettre à jour le statut dans Room
                db.saleDao().updateSale(syncedSale)
                
                Log.d("FIREBASE_SYNC", "Vente N°${sale.ticketNumber} synchronisée avec succès ✅")
            } catch (e: Exception) {
                // ATTENTION : On ne stoppe pas la boucle si une vente échoue !
                // On continue d'essayer les ventes suivantes.
                if (e is CancellationException) throw e
                
                Log.e("FIREBASE_SYNC", "Échec synchro Vente N°${sale.ticketNumber}. On continue les autres.", e)
                hadError = true
            }
        }
        
        // 5. Si au moins une vente a échoué, on demande à WorkManager de réessayer plus tard
        return if (hadError) Result.retry() else Result.success()
    }
}