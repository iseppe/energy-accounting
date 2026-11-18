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

    private suspend fun addEntry(repo: EnergyAccountingRepository, date: LocalDate, name: String, createdAt: Long): Long {
        val dimension = repo.observeActiveDimensions().first().first()
        return repo.saveEntry(
            Entry(
                date = date,
                timeSlot = TimeSlot.LATE_MORNING,
                activity = name,
                situationalNote = null,
                direction = Direction.SPEND,
                energyValue = 10,
                dimension = dimension,
                createdAtEpochMillis = createdAt,
            )
        )
    }

    @Test
    fun `backup replacement restores manually reordered entries and their creation times`() = runTest {
        val date = LocalDate.of(2026, 11, 12)
        val source = EnergyAccountingRepository(db1)
        val target = EnergyAccountingRepository(db2)
        addEntry(source, date, "A", 100L)
        addEntry(source, date, "B", 200L)
        val c = addEntry(source, date, "C", 300L)
        assertTrue(source.moveEntry(date, c, TimeSlot.LATE_MORNING, 0))

        BackupSerializer(db2).restoreJson(BackupSerializer(db1).exportJson(), RestoreMode.REPLACE)

        val entries = target.observeDailyAccount(date).first().ledgerFor(TimeSlot.LATE_MORNING).entries
        assertEquals(listOf("C", "A", "B"), entries.map { it.activity })
        assertEquals(listOf(300L, 100L, 200L), entries.map { it.createdAtEpochMillis })
    }

    @Test
    fun `backup merge preserves local order and appends nonduplicate entries in backup order`() = runTest {
        val date = LocalDate.of(2026, 11, 12)
        val source = EnergyAccountingRepository(db1)
        val target = EnergyAccountingRepository(db2)
        addEntry(source, date, "B", 100L)
        addEntry(source, date, "L1", 10L)
        addEntry(source, date, "A", 200L)
        addEntry(target, date, "L1", 10L)
        addEntry(target, date, "L2", 20L)

        BackupSerializer(db2).restoreJson(BackupSerializer(db1).exportJson(), RestoreMode.MERGE)

        val entries = target.observeDailyAccount(date).first().ledgerFor(TimeSlot.LATE_MORNING).entries
        assertEquals(listOf("L1", "L2", "B", "A"), entries.map { it.activity })
        assertEquals(listOf(0L, 1L, 2L, 3L), entries.map { it.positionInSlot })
    }

    @Test
    fun `merging an older backup keeps the existing slot-sensitive duplicate rule`() = runTest {
        val date = LocalDate.of(2026, 11, 12)
        val source = EnergyAccountingRepository(db1)
        val target = EnergyAccountingRepository(db2)
        addEntry(source, date, "A", 100L)
        val local = addEntry(target, date, "A", 100L)
        assertTrue(target.moveEntry(date, local, TimeSlot.AFTERNOON, 0))

        BackupSerializer(db2).restoreJson(BackupSerializer(db1).exportJson(), RestoreMode.MERGE)

        val account = target.observeDailyAccount(date).first()
        assertEquals(listOf("A"), account.ledgerFor(TimeSlot.LATE_MORNING).entries.map { it.activity })
        assertEquals(listOf("A"), account.ledgerFor(TimeSlot.AFTERNOON).entries.map { it.activity })
    }

    @Test
    fun `legacy backups retain creation-time order with file order for ties`() = runTest {
        val version2 = """
            {"schemaVersion":2,"dimensions":[{"id":1,"name":"Physical","systemKey":"PHYSICAL","colorIndex":0}],
             "entries":[
              {"dateIso":"2026-11-12","timeSlot":"LATE_MORNING","activity":"A","direction":"SPEND","energyValue":10,"dimensionId":1,"createdAtEpochMillis":200},
              {"dateIso":"2026-11-12","timeSlot":"LATE_MORNING","activity":"C","direction":"SPEND","energyValue":10,"dimensionId":1,"createdAtEpochMillis":100},
              {"dateIso":"2026-11-12","timeSlot":"LATE_MORNING","activity":"B","direction":"SPEND","energyValue":10,"dimensionId":1,"createdAtEpochMillis":100}]}
        """.trimIndent()
        val version1 = version2.replace("\"schemaVersion\":2", "\"schemaVersion\":1")
            .replace("\"dimensionId\"", "\"primaryDimensionId\"")
        val repo = EnergyAccountingRepository(db2)
        for (backup in listOf(version1, version2)) {
            BackupSerializer(db2).restoreJson(backup, RestoreMode.REPLACE)
            val entries = repo.observeDailyAccount(LocalDate.of(2026, 11, 12)).first()
                .ledgerFor(TimeSlot.LATE_MORNING).entries
            assertEquals(listOf("C", "B", "A"), entries.map { it.activity })
        }
    }

    @Test
    fun `version 3 accepts positions and rejects missing or negative positions before replacing data`() = runTest {
        val valid = """
            {"schemaVersion":3,"dimensions":[{"id":1,"name":"Physical","systemKey":"PHYSICAL","colorIndex":0}],
             "entries":[{"dateIso":"2026-11-12","timeSlot":"LATE_MORNING","activity":"Safe",
             "direction":"SPEND","energyValue":10,"dimensionId":1,"createdAtEpochMillis":100,"positionInSlot":0}]}
        """.trimIndent()
        val serializer = BackupSerializer(db2)
        serializer.restoreJson(valid, RestoreMode.REPLACE)
        val repo = EnergyAccountingRepository(db2)
        for (invalid in listOf(valid.replace(",\"positionInSlot\":0", ""), valid.replace("\"positionInSlot\":0", "\"positionInSlot\":-1"))) {
            assertTrue(runCatching { serializer.restoreJson(invalid, RestoreMode.REPLACE) }.isFailure)
            assertEquals(listOf("Safe"), repo.observeDailyAccount(LocalDate.of(2026, 11, 12)).first()
                .ledgerFor(TimeSlot.LATE_MORNING).entries.map { it.activity })
        }
    }

    @Test
    fun `CSV exports entries in their manually selected slot order`() {
        val dimension = EnergyDimension(1L, "Physical", SystemDimensionKey.PHYSICAL, 0)
        val date = LocalDate.of(2026, 11, 12)
        fun entry(name: String, createdAt: Long, position: Long) = Entry(
            date = date, timeSlot = TimeSlot.LATE_MORNING, activity = name, situationalNote = null,
            direction = Direction.SPEND, energyValue = 10, dimension = dimension,
            createdAtEpochMillis = createdAt, positionInSlot = position,
        )
        val csv = CsvExporter.exportEntries(listOf(entry("A", 100L, 1L), entry("B", 200L, 2L), entry("C", 300L, 0L)))
        assertEquals(listOf("C", "A", "B"), csv.trim().lines().drop(1).map { it.split(',')[2] })
    }

    @Test
    fun `PDF lists entries in their manually selected slot order`() {
        val date = LocalDate.of(2026, 11, 12)
        val dimension = EnergyDimension(1L, "Physical", SystemDimensionKey.PHYSICAL, 0)
        fun entry(name: String, createdAt: Long, position: Long) = Entry(
            date = date, timeSlot = TimeSlot.LATE_MORNING, activity = name, situationalNote = null,
            direction = Direction.SPEND, energyValue = 10, dimension = dimension,
            createdAtEpochMillis = createdAt, positionInSlot = position,
        )
        val entries = listOf(entry("Marker-A", 100L, 1L), entry("Marker-B", 200L, 2L), entry("Marker-C", 300L, 0L))
        val account = DailyAccount.fromEntries(date, entries)
        val insights = InsightsCalculator.calculate(date, date, entries, listOf(dimension))
        val output = ByteArrayOutputStream()
        PractitionerPdfGenerator().generateReport(output, date, date, listOf(account), insights, PdfLabels.english)
        val pdf = String(output.toByteArray(), Charsets.ISO_8859_1)

        val c = pdf.indexOf("Marker-C")
        val a = pdf.indexOf("Marker-A")
        val b = pdf.indexOf("Marker-B")
        assertTrue("PDF must list C, A, B", c >= 0 && c < a && a < b)
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
    fun `replacement rejects missing dimension references without deleting local entries`() = runTest {
        val date = LocalDate.of(2026, 11, 12)
        val repo = EnergyAccountingRepository(db2)
        addEntry(repo, date, "Existing", 100L)
        val invalidEntry = """
            {"schemaVersion":3,"dimensions":[],"entries":[{"dateIso":"2026-11-12",
             "timeSlot":"LATE_MORNING","activity":"Imported","direction":"SPEND",
             "energyValue":10,"dimensionId":999,"createdAtEpochMillis":200,"positionInSlot":0}]}
        """.trimIndent()
        val invalidTemplate = """
            {"schemaVersion":3,"dimensions":[],"templates":[{"normalizedName":"missing",
             "displayName":"Missing","defaultDirection":"SPEND","defaultEnergyValue":10,
             "defaultDimensionId":999}],"entries":[]}
        """.trimIndent()

        for (invalid in listOf(invalidEntry, invalidTemplate)) {
            assertTrue(runCatching { BackupSerializer(db2).restoreJson(invalid, RestoreMode.REPLACE) }.isFailure)
            assertEquals(listOf("Existing"), repo.observeDailyAccount(date).first()
                .ledgerFor(TimeSlot.LATE_MORNING).entries.map { it.activity })
        }
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
