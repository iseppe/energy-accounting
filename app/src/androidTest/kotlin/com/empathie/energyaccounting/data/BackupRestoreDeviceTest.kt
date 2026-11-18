package com.empathie.energyaccounting.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.empathie.energyaccounting.data.export.BackupSerializer
import com.empathie.energyaccounting.data.export.RestoreMode
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.repository.EnergyAccountingRepository
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.Entry
import com.empathie.energyaccounting.domain.model.TimeSlot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BackupRestoreDeviceTest {
    @Test
    fun reorderedEntriesSurviveReopeningAndBackupRestore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.empathie.energyaccounting.uitest", context.packageName)
        val databaseName = "isolated-backup-${UUID.randomUUID()}.db"
        val date = LocalDate.of(2026, 11, 12)
        val source = Room.databaseBuilder(context, EnergyAccountingDatabase::class.java, databaseName).build()
        val target = Room.inMemoryDatabaseBuilder(context, EnergyAccountingDatabase::class.java).build()
        var reopened: EnergyAccountingDatabase? = null
        var sourceClosed = false
        try {
            source.ensureSeeded()
            target.ensureSeeded()
            val repo = EnergyAccountingRepository(source)
            val dimension = repo.observeActiveDimensions().first().first()
            var lastId = 0L
            listOf("A", "B", "C").forEachIndexed { index, name ->
                lastId = repo.saveEntry(
                    Entry(
                        date = date, timeSlot = TimeSlot.LATE_MORNING, activity = name,
                        situationalNote = null, direction = Direction.SPEND, energyValue = 10,
                        dimension = dimension, createdAtEpochMillis = (index + 1) * 100L,
                    )
                )
            }
            assertTrue(repo.moveEntry(date, lastId, TimeSlot.LATE_MORNING, 0))
            val backup = BackupSerializer(source).exportJson()
            source.close()
            sourceClosed = true

            reopened = Room.databaseBuilder(context, EnergyAccountingDatabase::class.java, databaseName).build()
            val restartedEntries = EnergyAccountingRepository(reopened).observeDailyAccount(date).first()
                .ledgerFor(TimeSlot.LATE_MORNING).entries
            assertEquals(listOf("C", "A", "B"), restartedEntries.map { it.activity })

            BackupSerializer(target).restoreJson(backup, RestoreMode.REPLACE)
            val restoredEntries = EnergyAccountingRepository(target).observeDailyAccount(date).first()
                .ledgerFor(TimeSlot.LATE_MORNING).entries
            assertEquals(listOf("C", "A", "B"), restoredEntries.map { it.activity })
            assertEquals(listOf(300L, 100L, 200L), restoredEntries.map { it.createdAtEpochMillis })
        } finally {
            reopened?.close()
            if (!sourceClosed) source.close()
            target.close()
            context.deleteDatabase(databaseName)
        }
    }
}
