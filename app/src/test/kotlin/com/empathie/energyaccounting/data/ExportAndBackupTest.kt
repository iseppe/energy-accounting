package com.empathie.energyaccounting.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
        val social = EnergyDimension(2L, "Social", SystemDimensionKey.SOCIAL, 1)
        val entry = Entry(
            id = 1L,
            date = LocalDate.of(2026, 11, 12),
            timeSlot = TimeSlot.EARLY_MORNING,
            activity = "School arrival, late",
            situationalNote = "People were looking (\"situational\")",
            direction = Direction.SPEND,
            energyValue = 60,
            primaryDimension = sensory,
            secondaryDimensions = listOf(social),
            createdAtEpochMillis = 1000L
        )
        val csv = CsvExporter.exportEntries(listOf(entry))
        val lines = csv.trim().lines()
        assertEquals("Date,Time Slot,Activity,Situational Note,Direction,Signed Value,Primary Dimension,Secondary Dimensions", lines[0])
        assertEquals("12/11/2026,EARLY_MORNING,\"School arrival, late\",\"People were looking (\"\"situational\"\")\",SPEND,-60,Sensory,Social", lines[1])
    }

    @Test
    fun `BackupSerializer restoreJson in MERGE mode deduplicates seeded dimensions and templates while remapping Entry foreign keys`() = runTest {
        val repo1 = EnergyAccountingRepository(db1)
        val repo2 = EnergyAccountingRepository(db2)
        val date = LocalDate.of(2026, 11, 12)

        val customDim1Id = repo1.createCustomDimension("Executive", 2)
        val customDim1 = repo1.observeAllDimensions().first().first { it.id == customDim1Id }
        val sensory1 = repo1.observeAllDimensions().first().first { it.systemKey == SystemDimensionKey.SENSORY }

        repo1.saveEntry(
            Entry(
                date = date,
                timeSlot = TimeSlot.AFTERNOON,
                activity = "Homework",
                situationalNote = "Maths",
                direction = Direction.SPEND,
                energyValue = 80,
                primaryDimension = customDim1,
                secondaryDimensions = listOf(sensory1),
                createdAtEpochMillis = 5000L
            )
        )

        val json = BackupSerializer(db1).exportJson()

        // db2 already has seeded dimensions AND an existing entry
        val cognitive2 = repo2.observeAllDimensions().first().first { it.systemKey == SystemDimensionKey.COGNITIVE }
        repo2.saveEntry(
            Entry(
                date = date,
                timeSlot = TimeSlot.EARLY_MORNING,
                activity = "Morning routine",
                situationalNote = null,
                direction = Direction.SPEND,
                energyValue = 30,
                primaryDimension = cognitive2,
                createdAtEpochMillis = 1000L
            )
        )

        BackupSerializer(db2).restoreJson(json, RestoreMode.MERGE)

        val allDims2 = repo2.observeAllDimensions().first()
        assertEquals(6, allDims2.size) // 5 seeded + 1 imported "Executive"
        val account2 = repo2.observeDailyAccount(date).first()
        assertEquals(110, account2.totalSpend) // 30 + 80
        val importedEntry = account2.slots.getValue(TimeSlot.AFTERNOON).entries.first()
        assertEquals("Executive", importedEntry.primaryDimension.name)
        assertEquals(listOf("Sensory"), importedEntry.secondaryDimensions.map { it.name })
    }

    @Test
    fun `PractitionerPdfGenerator writes valid non-empty PDF stream`() {
        val date = LocalDate.of(2026, 11, 12)
        val sensory = EnergyDimension(1L, "Sensory", SystemDimensionKey.SENSORY, 0)
        val entry = Entry(1L, date, TimeSlot.AFTERNOON, "Quiet time in room", null, Direction.GAIN, 30, sensory, emptyList(), 100L)
        val account = DailyAccount.fromEntries(date, listOf(entry))
        val insights = InsightsCalculator.calculate(date, date, listOf(entry), listOf(sensory))

        val out = ByteArrayOutputStream()
        PractitionerPdfGenerator().generateReport(out, date, date, listOf(account), insights)
        val bytes = out.toByteArray()
        assertTrue(bytes.size > 100)
        assertEquals("%PDF", String(bytes.copyOfRange(0, 4)))
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
                primaryDimension = cognitive1,
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
                primaryDimension = sensory2,
                createdAtEpochMillis = 2000L,
            )
        )

        BackupSerializer(db2).restoreJson(json, RestoreMode.MERGE)

        val templates = repo2.observeTemplates().first()
        assertEquals(1, templates.size)
        assertEquals(50, templates.first().defaultEnergyValue)
        assertEquals(sensory2.id, templates.first().defaultPrimaryDimension.id)
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
                primaryDimension = cognitive2,
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
                    primaryDimensionId = 1L,
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
    fun `PractitionerPdfGenerator keeps Italian accents and uses the supplied labels`() {
        val date = LocalDate.of(2026, 11, 12)
        val sensory = EnergyDimension(1L, "Sensory", SystemDimensionKey.SENSORY, 0)
        val entry = Entry(
            1L,
            date,
            TimeSlot.AFTERNOON,
            "Caffè con amici",
            "Perché era rumoroso",
            Direction.SPEND,
            30,
            sensory,
            emptyList(),
            100L,
        )
        val account = DailyAccount.fromEntries(date, listOf(entry))
        val insights = InsightsCalculator.calculate(date, date, listOf(entry), listOf(sensory))
        val labels = PdfLabels.english.copy(title = "Bilancio energetico giornaliero")

        val out = ByteArrayOutputStream()
        PractitionerPdfGenerator().generateReport(out, date, date, listOf(account), insights, labels)

        val pdfText = String(out.toByteArray(), Charsets.ISO_8859_1)
        assertTrue(pdfText.contains("Caffè"))
        assertTrue(pdfText.contains("Perché"))
        assertTrue(pdfText.contains("Bilancio energetico giornaliero"))
        assertTrue(pdfText.contains("1°"))
    }
}
