package com.app.minicaisse.data.firebase

import com.app.minicaisse.data.local.SaleEntity
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class FirebaseDataSource {
    // Pointe vers le noeud "sales" dans Firebase
    private val db = FirebaseDatabase.getInstance().getReference("sales")

    suspend fun syncSale(sale: SaleEntity) {
        // On utilise le localId (UUID) généré par Room comme clé Firebase.
        // Si on renvoie la même vente 2 fois, Firebase écrasera l'ancienne au lieu d'en créer une nouvelle.
        db.child(sale.localId).setValue(sale).await()
    }
}