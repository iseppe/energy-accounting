package com.empathie.energyaccounting.data.local

import android.content.Context
import androidx.room.Room

/**
 * Process-wide Room instance, because two entry points need the same database before any
 * Application subclass could coordinate them: alarm receivers fire without an Activity,
 * and tests inject an in-memory instance.
 */
object DatabaseProvider {

    private const val DATABASE_NAME = "energy_accounting.db"

    @Volatile
    private var instance: EnergyAccountingDatabase? = null

    fun get(context: Context): EnergyAccountingDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                EnergyAccountingDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(EnergyAccountingDatabase.MIGRATION_1_2)
                .build()
                .also { instance = it }
        }

    /**
     * Allows Robolectric or instrumented tests to share a single in-memory database with
     * receivers and the app shell.
     */
    fun setForTesting(database: EnergyAccountingDatabase?) {
        instance = database
    }
}
