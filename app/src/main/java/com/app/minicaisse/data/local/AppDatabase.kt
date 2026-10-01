package com.app.minicaisse.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// Convertisseur pour les Enum (Room ne sait pas stocker d'Enum directement)
class Converters {
    @androidx.room.TypeConverter
    fun toPrintStatus(value: String) = enumValueOf<PrintStatus>(value)
    @androidx.room.TypeConverter
    fun fromPrintStatus(status: PrintStatus) = status.name

    @androidx.room.TypeConverter
    fun toSyncStatus(value: String) = enumValueOf<SyncStatus>(value)
    @androidx.room.TypeConverter
    fun fromSyncStatus(status: SyncStatus) = status.name
}

@Database(entities = [SaleEntity::class, SaleItemEntity::class, TicketCounterEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun saleDao(): SaleDao
    abstract fun ticketCounterDao(): TicketCounterDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "caisse_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}