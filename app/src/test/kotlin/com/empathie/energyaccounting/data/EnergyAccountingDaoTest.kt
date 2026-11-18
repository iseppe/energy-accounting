package com.empathie.energyaccounting.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.empathie.energyaccounting.data.local.DatabaseProvider
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
        supportDb.execSQL("PRAGMA foreign_keys=ON")

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
                "`dimensionId` INTEGER NOT NULL, PRIMARY KEY(`entryId`, `dimensionId`), " +
                "FOREIGN KEY(`entryId`) REFERENCES `entries`(`id`) ON DELETE CASCADE, " +
                "FOREIGN KEY(`dimensionId`) REFERENCES `energy_dimensions`(`id`) ON DELETE CASCADE)"
        )
        supportDb.execSQL("INSERT INTO `entry_secondary_dimensions` VALUES (10, 1)")

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

        EnergyAccountingDatabase.MIGRATION_2_3.migrate(supportDb)
        supportDb.query("SELECT `activity`, `dimensionId`, `positionInSlot` FROM `entries` WHERE `id` = 10").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("sveglia", cursor.getString(0))
            assertEquals(1L, cursor.getLong(1))
            assertEquals(0L, cursor.getLong(2))
        }

        helper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `opening a v2 database preserves entries and their original order`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "energy_accounting.db"
        DatabaseProvider.setForTesting(null)
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE energy_dimensions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, systemKey TEXT, colorIndex INTEGER NOT NULL, isArchived INTEGER NOT NULL)")
                        db.execSQL("CREATE UNIQUE INDEX index_energy_dimensions_systemKey ON energy_dimensions (systemKey)")
                        db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, dateIso TEXT NOT NULL, timeSlot TEXT NOT NULL, activity TEXT NOT NULL, situationalNote TEXT, direction TEXT NOT NULL, energyValue INTEGER NOT NULL, dimensionId INTEGER NOT NULL, createdAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(dimensionId) REFERENCES energy_dimensions(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                        db.execSQL("CREATE INDEX index_entries_dateIso ON entries (dateIso)")
                        db.execSQL("CREATE INDEX index_entries_dimensionId ON entries (dimensionId)")
                        db.execSQL("CREATE INDEX index_entries_dateIso_timeSlot ON entries (dateIso, timeSlot)")
                        db.execSQL("CREATE TABLE activity_templates (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, normalizedName TEXT NOT NULL, displayName TEXT NOT NULL, defaultDirection TEXT NOT NULL, defaultEnergyValue INTEGER NOT NULL, defaultDimensionId INTEGER NOT NULL, isPinned INTEGER NOT NULL, usageCount INTEGER NOT NULL, lastUsedEpochMillis INTEGER NOT NULL)")
                        db.execSQL("CREATE UNIQUE INDEX index_activity_templates_normalizedName ON activity_templates (normalizedName)")
                        db.execSQL("CREATE TABLE reminder_schedules (timeSlot TEXT NOT NULL PRIMARY KEY, hour INTEGER NOT NULL, minute INTEGER NOT NULL, isEnabled INTEGER NOT NULL)")
                        db.execSQL("INSERT INTO energy_dimensions VALUES (1, 'Sensory', 'SENSORY', 2, 0)")
                        db.execSQL("INSERT INTO entries VALUES (10, '2026-11-12', 'LATE_MORNING', 'A', NULL, 'SPEND', 10, 1, 200)")
                        db.execSQL("INSERT INTO entries VALUES (11, '2026-11-12', 'LATE_MORNING', 'B', NULL, 'SPEND', 10, 1, 100)")
                        db.execSQL("INSERT INTO entries VALUES (12, '2026-11-12', 'LATE_MORNING', 'C', NULL, 'SPEND', 10, 1, 100)")
                        db.execSQL("INSERT INTO entries VALUES (13, '2026-11-12', 'AFTERNOON', 'D', NULL, 'GAIN', 20, 1, 50)")
                        db.execSQL("INSERT INTO entries VALUES (14, '2026-11-13', 'LATE_MORNING', 'E', NULL, 'GAIN', 20, 1, 50)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        helper.writableDatabase
        helper.close()

        val upgraded = DatabaseProvider.get(context)
        try {
            val sql = upgraded.openHelper.readableDatabase
            val columns = sql.query("PRAGMA table_info(entries)").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(1))
                }
            }
            assertTrue("The upgraded ledger must persist entry positions", "positionInSlot" in columns)
            sql.query("SELECT id, positionInSlot FROM entries WHERE dateIso='2026-11-12' AND timeSlot='LATE_MORNING' ORDER BY positionInSlot, id").use { cursor ->
                val rows = buildList {
                    while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getLong(1))
                }
                assertEquals(listOf(11L to 0L, 12L to 1L, 10L to 2L), rows)
            }
            sql.query("SELECT positionInSlot FROM entries WHERE id IN (13, 14) ORDER BY id").use { cursor ->
                val positions = buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
                assertEquals(listOf(0L, 0L), positions)
            }
        } finally {
            upgraded.close()
            DatabaseProvider.setForTesting(null)
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `opening a v1 database runs the full Room migration chain without losing entries`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "energy_accounting.db"
        DatabaseProvider.setForTesting(null)
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE energy_dimensions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, systemKey TEXT, colorIndex INTEGER NOT NULL, isArchived INTEGER NOT NULL)")
                        db.execSQL("CREATE UNIQUE INDEX index_energy_dimensions_systemKey ON energy_dimensions (systemKey)")
                        db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, dateIso TEXT NOT NULL, timeSlot TEXT NOT NULL, activity TEXT NOT NULL, situationalNote TEXT, direction TEXT NOT NULL, energyValue INTEGER NOT NULL, primaryDimensionId INTEGER NOT NULL, createdAtEpochMillis INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE entry_secondary_dimensions (entryId INTEGER NOT NULL, dimensionId INTEGER NOT NULL, PRIMARY KEY(entryId, dimensionId), FOREIGN KEY(entryId) REFERENCES entries(id) ON DELETE CASCADE)")
                        db.execSQL("CREATE TABLE activity_templates (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, normalizedName TEXT NOT NULL, displayName TEXT NOT NULL, defaultDirection TEXT NOT NULL, defaultEnergyValue INTEGER NOT NULL, defaultPrimaryDimensionId INTEGER NOT NULL, defaultSecondaryDimensionIdsCsv TEXT NOT NULL, isPinned INTEGER NOT NULL, usageCount INTEGER NOT NULL, lastUsedEpochMillis INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE reminder_schedules (timeSlot TEXT NOT NULL, hour INTEGER NOT NULL, minute INTEGER NOT NULL, isEnabled INTEGER NOT NULL, PRIMARY KEY(timeSlot))")
                        db.execSQL("INSERT INTO energy_dimensions VALUES (1, 'Sensory', 'SENSORY', 2, 0)")
                        db.execSQL("INSERT INTO entries VALUES (10, '2026-11-12', 'LATE_MORNING', 'A', NULL, 'SPEND', 10, 1, 200)")
                        db.execSQL("INSERT INTO entries VALUES (11, '2026-11-12', 'LATE_MORNING', 'B', NULL, 'GAIN', 20, 1, 100)")
                        db.execSQL("INSERT INTO activity_templates VALUES (5, 'a', 'A', 'SPEND', 10, 1, '', 1, 1, 200)")
                        db.execSQL("INSERT INTO reminder_schedules VALUES ('EVENING', 21, 0, 1)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        helper.writableDatabase
        helper.close()

        val upgraded = DatabaseProvider.get(context)
        try {
            val sqlite = upgraded.openHelper.readableDatabase
            val rows = mutableListOf<Pair<Long, Long>>()
            sqlite.query("SELECT id, positionInSlot FROM entries ORDER BY positionInSlot").use { cursor ->
                while (cursor.moveToNext()) rows += cursor.getLong(0) to cursor.getLong(1)
            }
            assertEquals(listOf(11L to 0L, 10L to 1L), rows)
            sqlite.query("SELECT displayName, defaultDimensionId, isPinned FROM activity_templates WHERE id = 5").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("A", cursor.getString(0))
                assertEquals(1L, cursor.getLong(1))
                assertEquals(1, cursor.getInt(2))
            }
            sqlite.query("SELECT isEnabled FROM reminder_schedules WHERE timeSlot = 'EVENING'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        } finally {
            upgraded.close()
            DatabaseProvider.setForTesting(null)
            context.deleteDatabase(dbName)
        }
    }
}
