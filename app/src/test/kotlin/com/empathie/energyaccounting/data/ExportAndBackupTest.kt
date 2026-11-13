package com.empathie.energyaccounting.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.empathie.energyaccounting.data.diagnostics.DebugLogExporter
import com.empathie.energyaccounting.data.export.BackupPayload
import com.empathie.energyaccounting.data.export.BackupSerializer
import com.empathie.energyaccounting.data.export.CsvExporter
import com.empathie.energyaccounting.data.export.EntryRecord
import com.empathie.energyaccounting.data.export.PdfLabels
import com.empathie.energyaccounting.data.export.PractitionerPdfGenerator
import com.empathie.energyaccounting.data.export.RestoreMode
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.repository.EnergyAccountingRepository
import com.empathie.energyaccounting.domain.model.*
import com.empathie.energyaccounting.domain.service.InsightsCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ExportAndBackupTest {
    private lateinit var db1: EnergyAccountingDatabase
    private lateinit var db2: EnergyAccountingDatabase

    @Before
    fun setUp() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db1 = Room.inMemoryDatabaseBuilder(ctx, EnergyAccountingDatabase::class.java).allowMainThreadQueries().build()
        db2 = Room.inMemoryDatabaseBuilder(ctx, EnergyAccountingDatabase::class.java).allowMainThreadQueries().build()
        db1.ensureSeeded()
        db2.ensureSeeded()
    }

    @After
    fun tearDown() {
        db1.close()
        db2.close()
    }

    @Test
    fun `CsvExporter escapes commas, quotes, and newlines according to RFC 4180`() {
        val sensory = EnergyDimension(1L, "Sensory", SystemDimensionKey.SENSORY, 0)
        val entry = Entry(
            id = 1L,
            date = LocalDate.of(2026, 11, 12),
            timeSlot = TimeSlot.EARLY_MORNING,
            activity = "School arrival, late",
            situationalNote = "People were looking (\"situational\")",
            direction = Direction.SPEND,
            energyValue = 60,
            dimension = sensory,
            createdAtEpochMillis = 1000L
        )
        val csv = CsvExporter.exportEntries(listOf(entry))
        val lines = csv.trim().lines()
        assertEquals("Date,Time Slot,Activity,Situational Note,Direction,Signed Value,Dimension", lines[0])
        assertEquals("12/11/2026,EARLY_MORNING,\"School arrival, late\",\"People were looking (\"\"situational\"\")\",SPEND,-60,Sensory", lines[1])
    }

    @Test
    fun `BackupSerializer restoreJson in MERGE mode deduplicates seeded dimensions and remaps Entry foreign keys`() = runTest {
        val repo1 = EnergyAccountingRepository(db1)
        val repo2 = EnergyAccountingRepository(db2)
        val date = LocalDate.of(2026, 11, 12)

        val customDim1Id = repo1.createCustomDimension("Executive", 2)
        val customDim1 = repo1.observeAllDimensions().first().first { it.id == customDim1Id }

        repo1.saveEntry(
            Entry(
                date = date,
                timeSlot = TimeSlot.AFTERNOON,
                activity = "Homework",
                situationalNote = "Maths",
                direction = Direction.SPEND,
                energyValue = 80,
                dimension = customDim1,
                createdAtEpochMillis = 5000L
            )
        )

        val json = BackupSerializer(db1).exportJson()

        val cognitive2 = repo2.observeAllDimensions().first().first { it.systemKey == SystemDimensionKey.COGNITIVE }
        repo2.saveEntry(
            Entry(
                date = date,
                timeSlot = TimeSlot.EARLY_MORNING,
                activity = "Morning routine",
                situationalNote = null,
                direction = Direction.SPEND,
                energyValue = 30,
                dimension = cognitive2,
                createdAtEpochMillis = 1000L
            )
        )

        BackupSerializer(db2).restoreJson(json, RestoreMode.MERGE)

        val allDims2 = repo2.observeAllDimensions().first()
        assertEquals(6, allDims2.size)
        val account2 = repo2.observeDailyAccount(date).first()
        assertEquals(110, account2.totalSpend)
        val importedEntry = account2.slots.getValue(TimeSlot.AFTERNOON).entries.first()
        assertEquals("Executive", importedEntry.dimension.name)
    }

    @Test
    fun `PractitionerPdfGenerator writes a valid PDF with a bordered table and localized accents`() {
        val date = LocalDate.of(2026, 11, 12)
        val physical = EnergyDimension(1L, "Physical", SystemDimensionKey.PHYSICAL, 0)
        val entry = Entry(
            1L,
            date,
            TimeSlot.EARLY_MORNING,
            "caffè + tempo mio con una nota molto lunga per verificare il wrapping nella tabella",
            "Perché il treno era in ritardo",
            Direction.GAIN,
            20,
            physical,
            100L
        )
        val account = DailyAccount.fromEntries(date, listOf(entry))
        val insights = InsightsCalculator.calculate(date, date, listOf(entry), listOf(physical))
        val labels = PdfLabels.english.copy(
            title = "Bilancio energetico giornaliero",
            dimensionNames = mapOf(SystemDimensionKey.PHYSICAL to "Fisico"),
        )

        val out = ByteArrayOutputStream()
        PractitionerPdfGenerator().generateReport(out, date, date, listOf(account), insights, labels)
        val bytes = out.toByteArray()

        assertTrue(bytes.size > 100)
        assertEquals("%PDF", String(bytes.copyOfRange(0, 4)))

        val pdfText = String(bytes, Charsets.ISO_8859_1)
        assertTrue(pdfText.contains("re\n"))
        assertTrue(pdfText.contains("S\n"))
        assertTrue(pdfText.contains("Fisico"))
        assertTrue(pdfText.contains("caffè"))
        assertTrue(pdfText.contains("Perché"))
        assertTrue(pdfText.contains("Bilancio energetico giornaliero"))
    }

    @Test
    fun `MERGE keeps the local template baseline when the backup has the same normalized name`() = runTest {
        val repo1 = EnergyAccountingRepository(db1)
        val repo2 = EnergyAccountingRepository(db2)
        val date = LocalDate.of(2026, 11, 12)

        val cognitive1 = repo1.observeActiveDimensions().first()
            .first { it.systemKey == SystemDimensionKey.COGNITIVE }
        repo1.saveEntry(
            Entry(
                date = date,
                timeSlot = TimeSlot.EARLY_MORNING,
                activity = "Morning routine",
                situationalNote = null,
                direction = Direction.SPEND,
                energyValue = 30,
                dimension = cognitive1,
                createdAtEpochMillis = 1000L,
            )
        )
        val json = BackupSerializer(db1).exportJson()

        val sensory2 = repo2.observeActiveDimensions().first()
            .first { it.systemKey == SystemDimensionKey.SENSORY }
        repo2.saveEntry(
            Entry(
                date = date.plusDays(1),
                timeSlot = TimeSlot.EARLY_MORNING,
                activity = "morning routine",
                situationalNote = null,
                direction = Direction.GAIN,
                energyValue = 50,
                dimension = sensory2,
                createdAtEpochMillis = 2000L,
            )
        )

        BackupSerializer(db2).restoreJson(json, RestoreMode.MERGE)

        val templates = repo2.observeTemplates().first()
        assertEquals(1, templates.size)
        assertEquals(50, templates.first().defaultEnergyValue)
        assertEquals(sensory2.id, templates.first().defaultDimension.id)
    }

    @Test
    fun `restoreJson accepts a schemaVersion 1 backup and maps primaryDimensionId to dimensionId`() = runTest {
        val v1Json = """
            {
              "schemaVersion": 1,
              "dimensions": [
                {"id": 10, "systemKey": "SENSORY", "name": "Sensory", "colorIndex": 2, "isArchived": false}
              ],
              "templates": [
                {"normalizedName": "sveglia", "displayName": "sveglia", "defaultDirection": "SPEND",
                 "defaultEnergyValue": 30, "defaultPrimaryDimensionId": 10,
                 "defaultSecondaryDimensionIds": [11], "isPinned": false, "usageCount": 1,
                 "lastUsedEpochMillis": 1000}
              ],
              "entries": [
                {"dateIso": "2026-11-12", "timeSlot": "EARLY_MORNING", "activity": "sveglia",
                 "situationalNote": "test", "direction": "SPEND", "energyValue": 30,
                 "primaryDimensionId": 10, "secondaryDimensionIds": [], "createdAtEpochMillis": 1000}
              ],
              "reminders": []
            }
        """.trimIndent()

        BackupSerializer(db2).restoreJson(v1Json, RestoreMode.REPLACE)

        val repo2 = EnergyAccountingRepository(db2)
        val account = repo2.observeDailyAccount(LocalDate.of(2026, 11, 12)).first()
        assertEquals(30, account.totalSpend)
        assertEquals(
            SystemDimensionKey.SENSORY,
            account.slots.getValue(TimeSlot.EARLY_MORNING).entries.first().dimension.systemKey
        )
        val template = repo2.observeTemplates().first().first { it.activityName == "sveglia" }
        assertEquals(SystemDimensionKey.SENSORY, template.defaultDimension.systemKey)
    }

    @Test
    fun `restoreJson rejects an invalid record before touching local data`() = runTest {
        val date = LocalDate.of(2026, 11, 12)
        val repo2 = EnergyAccountingRepository(db2)
        val cognitive2 = repo2.observeActiveDimensions().first()
            .first { it.systemKey == SystemDimensionKey.COGNITIVE }
        repo2.saveEntry(
            Entry(
                date = date,
                timeSlot = TimeSlot.EARLY_MORNING,
                activity = "Existing entry",
                situationalNote = null,
                direction = Direction.SPEND,
                energyValue = 30,
                dimension = cognitive2,
                createdAtEpochMillis = 1000L,
            )
        )

        val invalidPayload = BackupPayload(
            entries = listOf(
                EntryRecord(
                    dateIso = "not-a-date",
                    timeSlot = "AFTERNOON",
                    activity = "Broken",
                    direction = "SPEND",
                    energyValue = 10,
                    dimensionId = 1L,
                    createdAtEpochMillis = 1L,
                )
            )
        )
        val invalidJson = Json.encodeToString(BackupPayload.serializer(), invalidPayload)

        val result = runCatching {
            BackupSerializer(db2).restoreJson(invalidJson, RestoreMode.REPLACE)
        }

        assertTrue(result.isFailure)
        val account = repo2.observeDailyAccount(date).first()
        assertEquals(1, account.slots.getValue(TimeSlot.EARLY_MORNING).entries.size)
    }

    @Test
    fun `restoreJson returns the reminders it applied so alarms can be re-armed`() = runTest {
        val repo1 = EnergyAccountingRepository(db1)
        repo1.updateReminder(CheckInReminder(TimeSlot.EVENING, 21, 30, true))
        val json = BackupSerializer(db1).exportJson()

        val restored = BackupSerializer(db2).restoreJson(json, RestoreMode.MERGE)

        val evening = restored.first { it.timeSlot == TimeSlot.EVENING }
        assertTrue(evening.isEnabled)
        assertEquals(21, evening.hour)
        assertEquals(30, evening.minute)
    }

    @Test
    fun `DebugLogExporter includes device, build and database metadata`() = runTest {
        val report = DebugLogExporter(db1).buildLogReport(
            buildId = "v0.2.0-abc1234-20261113-0100",
            packageName = "com.empathie.energyaccounting",
            versionName = "0.2.0",
            versionCode = 2,
            sdkInt = 35,
            deviceModel = "Google Pixel 7",
            localeTag = "it-IT",
            themeName = "LIGHT",
        )

        assertTrue(report.contains("v0.2.0-abc1234-20261113-0100"))
        assertTrue(report.contains("Package: com.empathie.energyaccounting"))
        assertTrue(report.contains("Version: 0.2.0 (2)"))
        assertTrue(report.contains("Android SDK: 35"))
        assertTrue(report.contains("Device: Google Pixel 7"))
        assertTrue(report.contains("Locale: it-IT"))
        assertTrue(report.contains("dimensions: 5"))
    }
}
