package com.empathie.energyaccounting.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.local.entity.EntryEntity
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.TimeSlot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EnergyAccountingDaoTest {
    private lateinit var db: EnergyAccountingDatabase

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EnergyAccountingDatabase::class.java
        ).allowMainThreadQueries().build()
        db.ensureSeeded()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `ensureSeeded inserts 5 default dimensions and 4 reminder schedules idempotently`() = runTest {
        db.ensureSeeded()
        val dimensions = db.dao().observeAllDimensions().first()
        assertEquals(5, dimensions.size)
        val reminders = db.dao().observeReminderSchedules().first()
        assertEquals(4, reminders.size)
    }

    @Test
    fun `inserting an entry loads its single dimension and counts slot entries`() = runTest {
        val dims = db.dao().observeAllDimensions().first()
        val sensory = dims.first { it.systemKey == "SENSORY" }

        val entryId = db.dao().insertEntry(
            EntryEntity(
                dateIso = "2026-11-12",
                timeSlot = TimeSlot.LATE_MORNING.name,
                activity = "Team meeting",
                situationalNote = "Loud room",
                direction = Direction.SPEND.name,
                energyValue = 70,
                dimensionId = sensory.id,
                createdAtEpochMillis = 100L
            )
        )

        val loaded = db.dao().observeEntriesForDate("2026-11-12").first()
        assertEquals(1, loaded.size)
        assertEquals(entryId, loaded.first().entry.id)
        assertEquals("SENSORY", loaded.first().dimension.systemKey)
        assertEquals(1, db.dao().countEntriesForSlot("2026-11-12", TimeSlot.LATE_MORNING.name))
        assertEquals(0, db.dao().countEntriesForSlot("2026-11-12", TimeSlot.EVENING.name))
    }

    @Test
    fun `MIGRATION_1_2 preserves v1 entries and templates and drops secondary refs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-test-${System.currentTimeMillis()}.db"

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val supportDb = helper.writableDatabase
        supportDb.execSQL("PRAGMA foreign_keys=OFF")

        supportDb.execSQL(
            "CREATE TABLE `energy_dimensions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `systemKey` TEXT, `colorIndex` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL)"
        )
        supportDb.execSQL("INSERT INTO `energy_dimensions` VALUES (1, 'Sensory', 'SENSORY', 2, 0)")

        supportDb.execSQL(
            "CREATE TABLE `entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`dateIso` TEXT NOT NULL, `timeSlot` TEXT NOT NULL, `activity` TEXT NOT NULL, " +
                "`situationalNote` TEXT, `direction` TEXT NOT NULL, `energyValue` INTEGER NOT NULL, " +
                "`primaryDimensionId` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL)"
        )
        supportDb.execSQL(
            "INSERT INTO `entries` VALUES (10, '2026-11-12', 'EARLY_MORNING', 'sveglia', " +
                "'devo svegliarmi', 'SPEND', 30, 1, 1000)"
        )

        supportDb.execSQL(
            "CREATE TABLE `entry_secondary_dimensions` (`entryId` INTEGER NOT NULL, " +
                "`dimensionId` INTEGER NOT NULL, PRIMARY KEY(`entryId`, `dimensionId`))"
        )

        supportDb.execSQL(
            "CREATE TABLE `activity_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`normalizedName` TEXT NOT NULL, `displayName` TEXT NOT NULL, `defaultDirection` TEXT NOT NULL, " +
                "`defaultEnergyValue` INTEGER NOT NULL, `defaultPrimaryDimensionId` INTEGER NOT NULL, " +
                "`defaultSecondaryDimensionIdsCsv` TEXT NOT NULL, `isPinned` INTEGER NOT NULL, " +
                "`usageCount` INTEGER NOT NULL, `lastUsedEpochMillis` INTEGER NOT NULL)"
        )
        supportDb.execSQL(
            "INSERT INTO `activity_templates` VALUES (5, 'sveglia', 'sveglia', 'SPEND', 30, 1, '2,3', 0, 1, 1000)"
        )

        supportDb.execSQL(
            "CREATE TABLE `reminder_schedules` (`timeSlot` TEXT NOT NULL, `hour` INTEGER NOT NULL, " +
                "`minute` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, PRIMARY KEY(`timeSlot`))"
        )

        EnergyAccountingDatabase.MIGRATION_1_2.migrate(supportDb)

        supportDb.query("SELECT `dimensionId`, `activity` FROM `entries` WHERE `id` = 10").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals("sveglia", cursor.getString(1))
        }
        supportDb.query("SELECT `defaultDimensionId` FROM `activity_templates` WHERE `id` = 5").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
        }
        supportDb.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'entry_secondary_dimensions'"
        ).use { cursor ->
            assertFalse(cursor.moveToFirst())
        }

        helper.close()
        context.deleteDatabase(dbName)
    }
}
