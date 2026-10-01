package com.app.minicaisse

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class CaisseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Active la persistance hors-ligne de Firebase
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
    }
}