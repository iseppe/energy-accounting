# Empathie Energy Accounting Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a 100% offline-first, bilingual (English/Italian) native Android application for Autism and AuDHD Energy Accounting based on Maja Toudal and Tony Attwood's methodology.

**Architecture:** Single-activity Kotlin + Jetpack Compose (Material 3) app backed by a reactive Room SQLite database. Domain models enforce atomic `1..100` entries, strict `PrimaryDimension` (`1°`) vs. `SecondaryDimension` (`2°`) attribution, isolated `ActivityTemplate` baselines, native `AlarmManager` check-in reminders with smart slot suppression, and local PDF/CSV/JSON export.

**Tech Stack:** Kotlin 2.0, Jetpack Compose (Material 3), Room 2.6 (`androidx.room`), Kotlin Coroutines & Flow, Kotlinx Serialization JSON, Android `PdfDocument` & `AlarmManager`, JUnit 4 + Robolectric + Turbine for unit/database/UI testing.

**Spec:** `docs/superpowers/specs/2026-11-12-energy-accounting-android-design.md`

## Global Constraints

- **Platform floors:** `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`, package `com.empathie.energyaccounting`.
- **Zero Google Play Services:** Never add `com.google.android.gms` or Firebase dependencies; notifications and alarms use only Android framework `AlarmManager`, `BroadcastReceiver`, and `NotificationManagerCompat`.
- **Date formatting:** Display all dates strictly as `dd/MM/yyyy` (`DateTimeFormatter.ofPattern("dd/MM/yyyy")`); store dates in SQLite as ISO-8601 `yyyy-MM-dd` (`LocalDate.toString()`).
- **Non-alarmist sensory palette:** Never use Material `error` red for `Spend (-)` or negative `Net Balance`. Use Warm Amber (`#E29B61` dark / `#B8652A` light) for `Spend (-)`, Calm Sage (`#5EC2A6` dark / `#2B7A65` light) for `Gain (+)`, and Soft Periwinkle (`#94B8FF` dark / `#3A5A99` light) for `Net Balance`.
- **Bilingual copy:** Every user-visible string lives in `app/src/main/res/values/strings.xml` (English) and `app/src/main/res/values-it/strings.xml` (Italian).
- **Canonical domain vocabulary:** Code types and properties must use the terms in `GLOSSARY.md` (`DailyAccount`, `TimeSlot`, `Entry`, `Direction`, `energyValue`, `EnergyDimension`, `primaryDimension`, `secondaryDimensions`, `ActivityTemplate`, `situationalNote`, `CheckInReminder`).

## Review Focus

1. **Case-insensitive and whitespace-padded Activity names when matching `ActivityTemplate`:** Logging `"  morning routine "` when `"Morning routine"` already exists must match the existing template, increment its `usageCount`, and preserve its baseline defaults (unless `updateTemplateDefaults = true`) rather than creating a duplicate template or throwing a SQLite unique constraint exception (pinned in Task 3).
2. **Primary Dimension overlap inside `secondaryDimensionIds`:** Constructing or saving an `Entry` or `ActivityTemplate` where `secondaryDimensionIds` includes `primaryDimensionId` must automatically strip `primaryDimensionId` from `secondaryDimensionIds` so the same dimension never renders as both `1°` and `2°` (pinned in Task 1).
3. **Archiving a custom `EnergyDimension` referenced by historical Entries or Activity Templates:** Deleting/archiving a custom dimension that is referenced by any `Entry` or `ActivityTemplate` must soft-archive it (`isArchived = true`) instead of hard-deleting it, preserving historical `DailyAccount` and `Insights` breakdowns while hiding it from new unselected dimension pickers (pinned in Task 3).
4. **CSV export escaping for commas, double quotes, and newlines in `activity` or `situationalNote`:** An entry with activity `"School arrival"` and situational note `"Late for school, people were looking (\"situational\")"` must be RFC-4180 escaped so spreadsheet imports never shift columns (pinned in Task 4).
5. **JSON Backup restore in `MERGE` mode when seeded dimensions or identically named templates already exist:** Importing a backup in `MERGE` mode into a database that already has the 5 seeded default dimensions (`PHYSICAL`, `COGNITIVE`, `SENSORY`, `SOCIAL`, `EMOTIONAL`) and overlapping `ActivityTemplate` names must map existing IDs by `systemKey` / normalized `activityName` and rewrite imported `Entry` foreign keys rather than duplicating seeded dimensions or failing unique constraints (pinned in Task 4).

## File Structure

```text
/
├── GLOSSARY.md
├── docs/
│   ├── adr/
│   │   ├── 0001-native-android-without-play-services.md
│   │   └── 0002-primary-and-secondary-energy-dimensions.md
│   └── superpowers/
│       ├── specs/2026-11-12-energy-accounting-android-design.md
│       └── plans/2026-11-12-energy-accounting-android.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/
        │   │   ├── values/strings.xml
        │   │   └── values-it/strings.xml
        │   └── kotlin/com/empathie/energyaccounting/
        │       ├── MainActivity.kt
        │       ├── EnergyAccountingApp.kt
        │       ├── domain/
        │       │   ├── model/
        │       │   │   ├── TimeSlot.kt
        │       │   │   ├── Direction.kt
        │       │   │   ├── EnergyDimension.kt
        │       │   │   ├── Entry.kt
        │       │   │   ├── ActivityTemplate.kt
        │       │   │   ├── DailyAccount.kt
        │       │   │   ├── DimensionChoiceState.kt
        │       │   │   ├── PeriodInsights.kt
        │       │   │   └── CheckInReminder.kt
        │       │   └── service/
        │       │       ├── EnergyFormatter.kt
        │       │       └── InsightsCalculator.kt
        │       ├── data/
        │       │   ├── local/
        │       │   │   ├── entity/
        │       │   │   │   ├── EnergyDimensionEntity.kt
        │       │   │   │   ├── EntryEntity.kt
        │       │   │   │   ├── EntrySecondaryDimensionCrossRef.kt
        │       │   │   │   ├── EntryWithDimensions.kt
        │       │   │   │   ├── ActivityTemplateEntity.kt
        │       │   │   │   └── ReminderScheduleEntity.kt
        │       │   │   ├── EnergyAccountingDao.kt
        │       │   │   └── EnergyAccountingDatabase.kt
        │       │   ├── repository/
        │       │   │   └── EnergyAccountingRepository.kt
        │       │   └── export/
        │       │       ├── CsvExporter.kt
        │       │       ├── BackupSerializer.kt
        │       │       └── PractitionerPdfGenerator.kt
        │       ├── reminder/
        │       │   ├── ReminderScheduler.kt
        │       │   ├── CheckInReminderReceiver.kt
        │       │   └── BootCompletedReceiver.kt
        │       └── ui/
        │           ├── theme/
        │           │   ├── Color.kt
        │           │   └── Theme.kt
        │           ├── daily/
        │           │   ├── DailyAccountViewModel.kt
        │           │   ├── DailyAccountScreen.kt
        │           │   ├── AddEditEntrySheet.kt
        │           │   └── CalendarHistorySheet.kt
        │           ├── insights/
        │           │   ├── InsightsViewModel.kt
        │           │   └── InsightsScreen.kt
        │           └── manage/
        │               ├── ManageExportViewModel.kt
        │               └── ManageExportScreen.kt
        └── test/kotlin/com/empathie/energyaccounting/
            ├── domain/
            │   ├── DomainModelTest.kt
            │   └── InsightsCalculatorTest.kt
            ├── data/
            │   ├── EnergyAccountingDaoTest.kt
            │   ├── EnergyAccountingRepositoryTest.kt
            │   └── ExportAndBackupTest.kt
            ├── reminder/
            │   └── CheckInReminderReceiverTest.kt
            └── ui/
                └── DailyAccountViewModelTest.kt
```

---

### Task 1: Project Scaffolding, Bilingual Resources, and Core Domain Models

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values-it/strings.xml`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/domain/model/TimeSlot.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/domain/model/Direction.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/domain/model/EnergyDimension.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/domain/model/Entry.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/domain/model/ActivityTemplate.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/domain/model/DailyAccount.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/domain/model/DimensionChoiceState.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/domain/service/EnergyFormatter.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/domain/DomainModelTest.kt`

**Interfaces:**
- Consumes: none (root foundation task).
- Produces:
  - `enum class TimeSlot { EARLY_MORNING, LATE_MORNING, AFTERNOON, EVENING }` with `companion object { fun defaultForTime(time: LocalTime): TimeSlot }`
  - `enum class Direction { SPEND, GAIN }`
  - `enum class SystemDimensionKey { PHYSICAL, COGNITIVE, SENSORY, SOCIAL, EMOTIONAL }`
  - `data class EnergyDimension(val id: Long, val name: String, val systemKey: SystemDimensionKey?, val colorIndex: Int, val isArchived: Boolean = false)`
  - `data class Entry(val id: Long = 0L, val date: LocalDate, val timeSlot: TimeSlot, val activity: String, val situationalNote: String?, val direction: Direction, val energyValue: Int, val primaryDimension: EnergyDimension, val secondaryDimensions: List<EnergyDimension> = emptyList(), val createdAtEpochMillis: Long)`
  - `data class TimeSlotLedger(val timeSlot: TimeSlot, val entries: List<Entry>, val totalGain: Int, val totalSpend: Int, val netBalance: Int)`
  - `data class DailyAccount(val date: LocalDate, val slots: Map<TimeSlot, TimeSlotLedger>, val totalGain: Int, val totalSpend: Int, val netBalance: Int)` with `companion object { fun fromEntries(date: LocalDate, entries: List<Entry>): DailyAccount }`
  - `data class DimensionChoiceState(val primaryId: Long?, val secondaryIds: Set<Long> = emptySet())` with `fun onTap(dimensionId: Long): DimensionChoiceState` and `fun onLongPress(dimensionId: Long): DimensionChoiceState`
  - `object EnergyFormatter` with `fun formatDate(date: LocalDate): String`, `fun parseDate(ddMmYyyy: String): LocalDate`, `fun formatSignedValue(direction: Direction, value: Int): String`, and `fun formatNetBalance(net: Int): String`

- [ ] **Step 1: Initialize git repository, verify/configure Android SDK + JDK 21 toolchain, and create Gradle scaffolding + bilingual `strings.xml`**

Initialize git (`git init`) and commit `GLOSSARY.md`, `docs/adr/`, and `docs/superpowers/specs/`. Ensure `ANDROID_HOME` (with `platforms;android-35` and `build-tools;35.0.0`) and JDK 21 are configured for Gradle. Create `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `AndroidManifest.xml`, `res/values/strings.xml`, and `res/values-it/strings.xml`.

- [ ] **Step 2: Write the failing unit tests in `app/src/test/kotlin/com/empathie/energyaccounting/domain/DomainModelTest.kt`**

```kotlin
package com.empathie.energyaccounting.domain

import com.empathie.energyaccounting.domain.model.*
import com.empathie.energyaccounting.domain.service.EnergyFormatter
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DomainModelTest {
    private val sensory = EnergyDimension(1L, "Sensory", SystemDimensionKey.SENSORY, 0)
    private val social = EnergyDimension(2L, "Social", SystemDimensionKey.SOCIAL, 1)
    private val cognitive = EnergyDimension(3L, "Cognitive", SystemDimensionKey.COGNITIVE, 2)

    @Test
    fun `formatDate and parseDate strictly use dd_MM_yyyy`() {
        val date = LocalDate.of(2026, 11, 12)
        assertEquals("12/11/2026", EnergyFormatter.formatDate(date))
        assertEquals(date, EnergyFormatter.parseDate("12/11/2026"))
    }

    @Test
    fun `TimeSlot defaultForTime maps clock boundaries accurately`() {
        assertEquals(TimeSlot.EARLY_MORNING, TimeSlot.defaultForTime(LocalTime.of(5, 0)))
        assertEquals(TimeSlot.EARLY_MORNING, TimeSlot.defaultForTime(LocalTime.of(9, 59)))
        assertEquals(TimeSlot.LATE_MORNING, TimeSlot.defaultForTime(LocalTime.of(10, 0)))
        assertEquals(TimeSlot.LATE_MORNING, TimeSlot.defaultForTime(LocalTime.of(12, 59)))
        assertEquals(TimeSlot.AFTERNOON, TimeSlot.defaultForTime(LocalTime.of(13, 0)))
        assertEquals(TimeSlot.AFTERNOON, TimeSlot.defaultForTime(LocalTime.of(17, 59)))
        assertEquals(TimeSlot.EVENING, TimeSlot.defaultForTime(LocalTime.of(18, 0)))
        assertEquals(TimeSlot.EVENING, TimeSlot.defaultForTime(LocalTime.of(2, 30)))
    }

    @Test
    fun `Entry strips primaryDimension from secondaryDimensions and normalizes strings`() {
        val entry = Entry(
            date = LocalDate.of(2026, 11, 12),
            timeSlot = TimeSlot.LATE_MORNING,
            activity = "  Lunch in cafeteria  ",
            situationalNote = "   ",
            direction = Direction.SPEND,
            energyValue = 50,
            primaryDimension = sensory,
            secondaryDimensions = listOf(sensory, social, social),
            createdAtEpochMillis = 1000L
        )
        assertEquals("Lunch in cafeteria", entry.activity)
        assertNull(entry.situationalNote)
        assertEquals(listOf(social), entry.secondaryDimensions)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Entry rejects energyValue outside 1 to 100`() {
        Entry(
            date = LocalDate.of(2026, 11, 12),
            timeSlot = TimeSlot.EARLY_MORNING,
            activity = "Invalid value",
            situationalNote = null,
            direction = Direction.SPEND,
            energyValue = 0,
            primaryDimension = sensory,
            createdAtEpochMillis = 1000L
        )
    }

    @Test
    fun `DailyAccount aggregates zero-based gain, spend, and net balance per slot and day`() {
        val date = LocalDate.of(2026, 11, 12)
        val entries = listOf(
            Entry(1L, date, TimeSlot.EARLY_MORNING, "Morning routine", null, Direction.SPEND, 30, cognitive, emptyList(), 10L),
            Entry(2L, date, TimeSlot.EARLY_MORNING, "Favourite breakfast", null, Direction.GAIN, 10, sensory, emptyList(), 20L),
            Entry(3L, date, TimeSlot.AFTERNOON, "Quiet room", null, Direction.GAIN, 50, sensory, listOf(social), 30L)
        )
        val account = DailyAccount.fromEntries(date, entries)
        assertEquals(60, account.totalGain)
        assertEquals(30, account.totalSpend)
        assertEquals(30, account.netBalance)
        assertEquals(-20, account.slots.getValue(TimeSlot.EARLY_MORNING).netBalance)
        assertEquals(0, account.slots.getValue(TimeSlot.LATE_MORNING).netBalance)
        assertEquals(50, account.slots.getValue(TimeSlot.AFTERNOON).netBalance)
    }

    @Test
    fun `DimensionChoiceState promotes first tap to primary and subsequent taps to secondary`() {
        val s0 = DimensionChoiceState(primaryId = null)
        val s1 = s0.onTap(1L)
        assertEquals(1L, s1.primaryId)
        assertTrue(s1.secondaryIds.isEmpty())

        val s2 = s1.onTap(2L).onTap(3L)
        assertEquals(1L, s2.primaryId)
        assertEquals(setOf(2L, 3L), s2.secondaryIds)

        // Tapping primary when secondaries exist promotes first secondary to primary
        val s3 = s2.onTap(1L)
        assertEquals(2L, s3.primaryId)
        assertEquals(setOf(3L), s3.secondaryIds)

        // Long-pressing a secondary swaps it into primary
        val s4 = s3.onLongPress(3L)
        assertEquals(3L, s4.primaryId)
        assertEquals(setOf(2L), s4.secondaryIds)
    }
}
```

- [ ] **Step 3: Run unit test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.domain.DomainModelTest"`
Expected: FAIL with unresolved symbols (`TimeSlot`, `Entry`, `DailyAccount`, `DimensionChoiceState`, `EnergyFormatter`).

- [ ] **Step 4: Implement the domain models and `EnergyFormatter`**

Implement `TimeSlot`, `Direction`, `EnergyDimension`, `Entry` (using `init` block to require `energyValue in 1..100` and trimmed `activity.isNotBlank()`, and normalizing `secondaryDimensions` via `.filter { it.id != primaryDimension.id }.distinctBy { it.id }`), `ActivityTemplate` (with the same secondary dimension deduplication), `DailyAccount`, `DimensionChoiceState`, and `EnergyFormatter` (using Unicode minus `−` `U+2212` or ASCII `-` consistently: use standard `-` in data/math and `EnergyFormatter.formatSignedValue(SPEND, 30) -> "-30"`, `formatSignedValue(GAIN, 10) -> "+10"`, `formatNetBalance(0) -> "0"`).

- [ ] **Step 5: Run unit tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.domain.DomainModelTest"`
Expected: PASS (6 tests passing).

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "feat(domain): scaffold Android project, bilingual strings, and core domain models"
```

---

### Task 2: Room SQLite Persistence, Seeded Energy Dimensions, and Reactive DAOs

**Files:**
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/data/local/entity/EnergyDimensionEntity.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/data/local/entity/EntryEntity.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/data/local/entity/EntrySecondaryDimensionCrossRef.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/data/local/entity/EntryWithDimensions.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/data/local/entity/ActivityTemplateEntity.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/data/local/entity/ReminderScheduleEntity.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/data/local/EnergyAccountingDao.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/data/local/EnergyAccountingDatabase.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/data/EnergyAccountingDaoTest.kt`

**Interfaces:**
- Consumes: `TimeSlot`, `Direction`, `SystemDimensionKey` from Task 1.
- Produces:
  - `EnergyDimensionEntity(id: Long = 0, name: String, systemKey: String?, colorIndex: Int, isArchived: Boolean = false)`
  - `EntryEntity(id: Long = 0, dateIso: String, timeSlot: String, activity: String, situationalNote: String?, direction: String, energyValue: Int, primaryDimensionId: Long, createdAtEpochMillis: Long)`
  - `EntrySecondaryDimensionCrossRef(entryId: Long, dimensionId: Long)`
  - `EntryWithDimensions(@Embedded val entry: EntryEntity, @Relation(...) val primaryDimension: EnergyDimensionEntity, @Relation(..., associateBy = Junction(EntrySecondaryDimensionCrossRef::class)) val secondaryDimensions: List<EnergyDimensionEntity>)`
  - `ActivityTemplateEntity(id: Long = 0, normalizedName: String, displayName: String, defaultDirection: String, defaultEnergyValue: Int, defaultPrimaryDimensionId: Long, defaultSecondaryDimensionIdsCsv: String, isPinned: Boolean, usageCount: Int, lastUsedEpochMillis: Long)`
  - `ReminderScheduleEntity(timeSlot: String, hour: Int, minute: Int, isEnabled: Boolean)`
  - `interface EnergyAccountingDao` with reactive `Flow` queries for dimensions, entries by `dateIso`, entries in `startDateIso..endDateIso`, active dates summary, templates ordered by `(isPinned DESC, usageCount DESC, lastUsedEpochMillis DESC)`, reminder schedules, and `suspend fun countEntriesForSlot(dateIso: String, timeSlot: String): Int`.
  - `abstract class EnergyAccountingDatabase : RoomDatabase()` with `suspend fun ensureSeeded()` seeding the 5 default `SystemDimensionKey` dimensions and 4 default `ReminderScheduleEntity` rows (`09:30`, `12:30`, `17:30`, `21:00`, all `isEnabled = false`) idempotently.

- [ ] **Step 1: Write the failing Room DAO test in `app/src/test/kotlin/com/empathie/energyaccounting/data/EnergyAccountingDaoTest.kt`**

```kotlin
package com.empathie.energyaccounting.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.local.entity.EntryEntity
import com.empathie.energyaccounting.data.local.entity.EntrySecondaryDimensionCrossRef
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
    fun `inserting entry with secondary dimensions loads EntryWithDimensions and counts slot entries`() = runTest {
        val dims = db.dao().observeAllDimensions().first()
        val sensory = dims.first { it.systemKey == "SENSORY" }
        val social = dims.first { it.systemKey == "SOCIAL" }

        val entryId = db.dao().insertEntryWithSecondaries(
            entry = EntryEntity(
                dateIso = "2026-11-12",
                timeSlot = TimeSlot.LATE_MORNING.name,
                activity = "Team meeting",
                situationalNote = "Loud room",
                direction = Direction.SPEND.name,
                energyValue = 70,
                primaryDimensionId = sensory.id,
                createdAtEpochMillis = 100L
            ),
            secondaryDimensionIds = listOf(social.id)
        )

        val loaded = db.dao().observeEntriesForDate("2026-11-12").first()
        assertEquals(1, loaded.size)
        assertEquals(entryId, loaded.first().entry.id)
        assertEquals("SENSORY", loaded.first().primaryDimension.systemKey)
        assertEquals(listOf("SOCIAL"), loaded.first().secondaryDimensions.map { it.systemKey })
        assertEquals(1, db.dao().countEntriesForSlot("2026-11-12", TimeSlot.LATE_MORNING.name))
        assertEquals(0, db.dao().countEntriesForSlot("2026-11-12", TimeSlot.EVENING.name))
    }
}
```

- [ ] **Step 2: Run the Room DAO test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.data.EnergyAccountingDaoTest"`
Expected: FAIL with unresolved entity/DAO/database classes.

- [ ] **Step 3: Implement Room entities, `EnergyAccountingDao`, and `EnergyAccountingDatabase`**

Create the 6 entity/relation files, `EnergyAccountingDao` (using `@Transaction` on `observeEntriesForDate`, `observeEntriesInRange`, and `insertEntryWithSecondaries`/`updateEntryWithSecondaries`), and `EnergyAccountingDatabase` with `ensureSeeded()`. Put a unique index on `ActivityTemplateEntity.normalizedName` and `EnergyDimensionEntity.systemKey`.

- [ ] **Step 4: Run the Room DAO test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.data.EnergyAccountingDaoTest"`
Expected: PASS (2 tests passing).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/empathie/energyaccounting/data/local app/src/test/kotlin/com/empathie/energyaccounting/data/EnergyAccountingDaoTest.kt
git commit -m "feat(data): add Room entities, DAO queries, and default dimension seeding"
```

---

### Task 3: Repository, Activity Template Isolation, and Insights Breakdown Calculator

**Files:**
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/domain/model/PeriodInsights.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/domain/model/CheckInReminder.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/domain/service/InsightsCalculator.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/data/repository/EnergyAccountingRepository.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/domain/InsightsCalculatorTest.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/data/EnergyAccountingRepositoryTest.kt`

**Interfaces:**
- Consumes: Domain models from Task 1; `EnergyAccountingDatabase` & `EnergyAccountingDao` from Task 2.
- Produces:
  - `data class DimensionImpactSummary(val dimension: EnergyDimension, val primarySpend: Int, val primarySpendSharePercent: Int, val primaryGain: Int, val primaryGainSharePercent: Int, val secondaryOccurrenceCount: Int)`
  - `data class TimeSlotImpactSummary(val timeSlot: TimeSlot, val totalGain: Int, val totalSpend: Int, val netBalance: Int)`
  - `data class PeriodInsights(val startDate: LocalDate, val endDate: LocalDate, val activeDaysCount: Int, val totalGain: Int, val totalSpend: Int, val netBalance: Int, val averageDailyNet: Int, val byDimension: List<DimensionImpactSummary>, val byTimeSlot: List<TimeSlotImpactSummary>)`
  - `object InsightsCalculator { fun calculate(startDate: LocalDate, endDate: LocalDate, entries: List<Entry>, allDimensions: List<EnergyDimension>): PeriodInsights }`
  - `class EnergyAccountingRepository(private val db: EnergyAccountingDatabase)` with:
    - `fun observeDailyAccount(date: LocalDate): Flow<DailyAccount>`
    - `fun observeActiveDimensions(): Flow<List<EnergyDimension>>`
    - `fun observeAllDimensions(): Flow<List<EnergyDimension>>`
    - `fun observeTemplates(): Flow<List<ActivityTemplate>>`
    - `fun observePeriodInsights(startDate: LocalDate, endDate: LocalDate): Flow<PeriodInsights>`
    - `fun observeDateNetBalances(startDate: LocalDate, endDate: LocalDate): Flow<Map<LocalDate, Int>>`
    - `suspend fun saveEntry(entry: Entry, updateTemplateDefaults: Boolean = false): Long`
    - `suspend fun deleteEntry(entryId: Long)`
    - `suspend fun createCustomDimension(name: String, colorIndex: Int): Long`
    - `suspend fun removeOrArchiveCustomDimension(dimensionId: Long)`
    - `suspend fun saveTemplate(template: ActivityTemplate): Long`
    - `suspend fun toggleTemplatePin(templateId: Long, isPinned: Boolean)`
    - `suspend fun deleteTemplate(templateId: Long)`

- [ ] **Step 1: Write failing tests for `InsightsCalculator` and `EnergyAccountingRepository` (including Review Focus #1 and #3)**

Create `InsightsCalculatorTest.kt` testing that `PrimaryDimension` carries 100% of `energyValue` in `primarySpend`/`primaryGain` while `SecondaryDimension` increments `secondaryOccurrenceCount` without inflating `totalSpend` or `totalGain`.
Create `EnergyAccountingRepositoryTest.kt`:

```kotlin
package com.empathie.energyaccounting.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.repository.EnergyAccountingRepository
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.Entry
import com.empathie.energyaccounting.domain.model.TimeSlot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class EnergyAccountingRepositoryTest {
    private lateinit var db: EnergyAccountingDatabase
    private lateinit var repo: EnergyAccountingRepository

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EnergyAccountingDatabase::class.java
        ).allowMainThreadQueries().build()
        db.ensureSeeded()
        repo = EnergyAccountingRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `saveEntry auto-creates template and preserves baseline on case-insensitive reuse unless updateTemplateDefaults is true`() = runTest {
        val dims = repo.observeActiveDimensions().first()
        val cognitive = dims.first { it.name == "Cognitive" }
        val sensory = dims.first { it.name == "Sensory" }
        val today = LocalDate.of(2026, 11, 12)

        // First log creates template with baseline -30 Cognitive
        repo.saveEntry(
            Entry(
                date = today,
                timeSlot = TimeSlot.EARLY_MORNING,
                activity = "Morning routine",
                situationalNote = null,
                direction = Direction.SPEND,
                energyValue = 30,
                primaryDimension = cognitive,
                createdAtEpochMillis = 1000L
            ),
            updateTemplateDefaults = false
        )

        // Second log with different casing/spaces and tweaked value -60 does NOT overwrite template default
        repo.saveEntry(
            Entry(
                date = today.plusDays(1),
                timeSlot = TimeSlot.EARLY_MORNING,
                activity = "  MORNING ROUTINE ",
                situationalNote = "Poor sleep",
                direction = Direction.SPEND,
                energyValue = 60,
                primaryDimension = sensory,
                createdAtEpochMillis = 2000L
            ),
            updateTemplateDefaults = false
        )

        val templatesAfterIsolatedTweaks = repo.observeTemplates().first()
        assertEquals(1, templatesAfterIsolatedTweaks.size)
        val tpl = templatesAfterIsolatedTweaks.first()
        assertEquals("Morning routine", tpl.activityName)
        assertEquals(30, tpl.defaultEnergyValue)
        assertEquals(cognitive.id, tpl.defaultPrimaryDimension.id)
        assertEquals(2, tpl.usageCount)

        // Third log with updateTemplateDefaults = true explicitly updates baseline to -50 Sensory
        repo.saveEntry(
            Entry(
                date = today.plusDays(2),
                timeSlot = TimeSlot.EARLY_MORNING,
                activity = "morning routine",
                situationalNote = null,
                direction = Direction.SPEND,
                energyValue = 50,
                primaryDimension = sensory,
                createdAtEpochMillis = 3000L
            ),
            updateTemplateDefaults = true
        )

        val updatedTpl = repo.observeTemplates().first().first()
        assertEquals(50, updatedTpl.defaultEnergyValue)
        assertEquals(sensory.id, updatedTpl.defaultPrimaryDimension.id)
        assertEquals(3, updatedTpl.usageCount)
    }

    @Test
    fun `removeOrArchiveCustomDimension soft-archives used custom dimension and hard-deletes unused custom dimension`() = runTest {
        val usedId = repo.createCustomDimension("Executive Function", 3)
        val unusedId = repo.createCustomDimension("Temporary Tag", 4)
        val usedDim = repo.observeAllDimensions().first().first { it.id == usedId }

        repo.saveEntry(
            Entry(
                date = LocalDate.of(2026, 11, 12),
                timeSlot = TimeSlot.AFTERNOON,
                activity = "Tax paperwork",
                situationalNote = null,
                direction = Direction.SPEND,
                energyValue = 80,
                primaryDimension = usedDim,
                createdAtEpochMillis = 1000L
            )
        )

        repo.removeOrArchiveCustomDimension(usedId)
        repo.removeOrArchiveCustomDimension(unusedId)

        val activeDims = repo.observeActiveDimensions().first()
        val allDims = repo.observeAllDimensions().first()
        assertFalse(activeDims.any { it.id == usedId })
        assertTrue(allDims.any { it.id == usedId && it.isArchived })
        assertFalse(allDims.any { it.id == unusedId })

        // Historical DailyAccount still loads the entry with its archived dimension intact
        val account = repo.observeDailyAccount(LocalDate.of(2026, 11, 12)).first()
        assertEquals(80, account.totalSpend)
        assertEquals("Executive Function", account.slots.getValue(TimeSlot.AFTERNOON).entries.first().primaryDimension.name)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.domain.InsightsCalculatorTest" --tests "com.empathie.energyaccounting.data.EnergyAccountingRepositoryTest"`
Expected: FAIL with missing `InsightsCalculator` and `EnergyAccountingRepository`.

- [ ] **Step 3: Implement `PeriodInsights`, `CheckInReminder`, `InsightsCalculator`, and `EnergyAccountingRepository`**

Implement `InsightsCalculator.calculate(...)` and `EnergyAccountingRepository`. Normalize template names via `activity.trim().lowercase(Locale.ROOT)` for `normalizedName`. In `removeOrArchiveCustomDimension`, check if any `EntryEntity`, `EntrySecondaryDimensionCrossRef`, or `ActivityTemplateEntity` references `dimensionId`; if so, mark `isArchived = true`, otherwise delete the row (ignoring deletion attempts on seeded dimensions where `systemKey != null`).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.domain.InsightsCalculatorTest" --tests "com.empathie.energyaccounting.data.EnergyAccountingRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/empathie/energyaccounting/domain app/src/main/kotlin/com/empathie/energyaccounting/data/repository app/src/test/kotlin/com/empathie/energyaccounting
git commit -m "feat(repository): implement template baseline isolation, custom dimension archiving, and insights math"
```

---

### Task 4: Practitioner PDF Report, CSV Exporter, and JSON Backup & Restore Engine

**Files:**
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/data/export/CsvExporter.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/data/export/BackupSerializer.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/data/export/PractitionerPdfGenerator.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/data/ExportAndBackupTest.kt`

**Interfaces:**
- Consumes: Domain models and `EnergyAccountingDatabase` / `EnergyAccountingRepository` from Tasks 1–3.
- Produces:
  - `object CsvExporter { fun exportEntries(entries: List<Entry>): String }`
  - `enum class RestoreMode { MERGE, REPLACE }`
  - `class BackupSerializer(private val db: EnergyAccountingDatabase)` with `suspend fun exportJson(): String` and `suspend fun restoreJson(jsonString: String, mode: RestoreMode)`
  - `class PractitionerPdfGenerator` with `fun generateReport(outputStream: OutputStream, startDate: LocalDate, endDate: LocalDate, accounts: List<DailyAccount>, insights: PeriodInsights)`

- [ ] **Step 1: Write failing tests for CSV RFC-4180 escaping, JSON Backup `MERGE`/`REPLACE` deduplication, and PDF generation (Review Focus #4 and #5)**

```kotlin
package com.empathie.energyaccounting.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.empathie.energyaccounting.data.export.BackupSerializer
import com.empathie.energyaccounting.data.export.CsvExporter
import com.empathie.energyaccounting.data.export.PractitionerPdfGenerator
import com.empathie.energyaccounting.data.export.RestoreMode
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.repository.EnergyAccountingRepository
import com.empathie.energyaccounting.domain.model.*
import com.empathie.energyaccounting.domain.service.InsightsCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
}
```

- [ ] **Step 2: Run the export & backup tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.data.ExportAndBackupTest"`
Expected: FAIL with missing `CsvExporter`, `BackupSerializer`, and `PractitionerPdfGenerator`.

- [ ] **Step 3: Implement `CsvExporter`, `BackupSerializer`, and `PractitionerPdfGenerator`**

Implement `CsvExporter` with RFC-4180 field escaping, `BackupSerializer` with `@Serializable data class BackupPayload(val schemaVersion: Int = 1, ...)` and transactional `MERGE`/`REPLACE` logic (matching dimensions by `systemKey` first, then case-insensitive `name`, and remapping IDs on templates and entries), and `PractitionerPdfGenerator` using `android.graphics.pdf.PdfDocument` laid out after Toudal's *Daily Energy Account Form*.

- [ ] **Step 4: Run the export & backup tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.data.ExportAndBackupTest"`
Expected: PASS (3 tests passing).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/empathie/energyaccounting/data/export app/src/test/kotlin/com/empathie/energyaccounting/data/ExportAndBackupTest.kt
git commit -m "feat(export): add Practitioner PDF generator, RFC-4180 CSV exporter, and JSON backup/restore"
```

---

### Task 5: Local Check-in Reminders (`AlarmManager` + Smart Suppression)

**Files:**
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/reminder/ReminderScheduler.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/reminder/CheckInReminderReceiver.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/reminder/BootCompletedReceiver.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/reminder/CheckInReminderReceiverTest.kt`

**Interfaces:**
- Consumes: `TimeSlot`, `CheckInReminder`, `EnergyAccountingDatabase` from Tasks 1–3.
- Produces:
  - `class ReminderScheduler(private val context: Context)` with `fun scheduleOrCancel(reminder: CheckInReminder, now: ZonedDateTime = ZonedDateTime.now())` and `fun computeNextTriggerMillis(hour: Int, minute: Int, now: ZonedDateTime): Long`
  - `class CheckInReminderReceiver : BroadcastReceiver()` with `companion object { const val EXTRA_TIME_SLOT = "extra_time_slot"; const val EXTRA_OPEN_ADD_SHEET = "extra_open_add_sheet"; suspend fun shouldPostNotification(dao: EnergyAccountingDao, today: LocalDate, timeSlot: TimeSlot): Boolean }`
  - `class BootCompletedReceiver : BroadcastReceiver()` rescheduling all enabled `ReminderScheduleEntity` rows on `ACTION_BOOT_COMPLETED`, `ACTION_MY_PACKAGE_REPLACED`, `ACTION_TIME_CHANGED`, and `ACTION_TIMEZONE_CHANGED`.

- [ ] **Step 1: Write failing tests in `app/src/test/kotlin/com/empathie/energyaccounting/reminder/CheckInReminderReceiverTest.kt`**

```kotlin
package com.empathie.energyaccounting.reminder

import androidx.room.Room
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class CheckInReminderReceiverTest {
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
    fun `shouldPostNotification returns true when slot is empty and false when slot already has an entry today`() = runTest {
        val today = LocalDate.of(2026, 11, 12)
        assertTrue(CheckInReminderReceiver.shouldPostNotification(db.dao(), today, TimeSlot.EVENING))

        val dim = db.dao().observeAllDimensions().first().first()
        db.dao().insertEntryWithSecondaries(
            EntryEntity(
                dateIso = today.toString(),
                timeSlot = TimeSlot.EVENING.name,
                activity = "Dinner with family",
                situationalNote = null,
                direction = Direction.SPEND.name,
                energyValue = 10,
                primaryDimensionId = dim.id,
                createdAtEpochMillis = 100L
            ),
            emptyList()
        )

        assertFalse(CheckInReminderReceiver.shouldPostNotification(db.dao(), today, TimeSlot.EVENING))
        assertTrue(CheckInReminderReceiver.shouldPostNotification(db.dao(), today, TimeSlot.AFTERNOON))
    }

    @Test
    fun `computeNextTriggerMillis schedules today if time is in future or tomorrow if time already passed`() {
        val scheduler = ReminderScheduler(ApplicationProvider.getApplicationContext())
        val now = ZonedDateTime.of(2026, 11, 12, 14, 0, 0, 0, ZoneId.of("Europe/Rome"))
        val todayAt1730 = scheduler.computeNextTriggerMillis(17, 30, now)
        val tomorrowAt0930 = scheduler.computeNextTriggerMillis(9, 30, now)

        assertEquals(now.withHour(17).withMinute(30).toInstant().toEpochMilli(), todayAt1730)
        assertEquals(now.plusDays(1).withHour(9).withMinute(30).toInstant().toEpochMilli(), tomorrowAt0930)
    }
}
```

- [ ] **Step 2: Run the reminder tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.reminder.CheckInReminderReceiverTest"`
Expected: FAIL with missing `ReminderScheduler` and `CheckInReminderReceiver`.

- [ ] **Step 3: Implement `ReminderScheduler`, `CheckInReminderReceiver`, and `BootCompletedReceiver`, and register receivers in `AndroidManifest.xml`**

Use `AlarmManager.setAndAllowWhileIdle` (or `setExactAndAllowWhileIdle` when `canScheduleExactAlarms()` is true) with zero Google Play Services dependencies. Post notifications to channel `"energy_checkin_reminders"` with a `PendingIntent` launching `MainActivity` with `EXTRA_OPEN_ADD_SHEET = true` and `EXTRA_TIME_SLOT = timeSlot.name`.

- [ ] **Step 4: Run the reminder tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.reminder.CheckInReminderReceiverTest"`
Expected: PASS (2 tests passing).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/kotlin/com/empathie/energyaccounting/reminder app/src/test/kotlin/com/empathie/energyaccounting/reminder
git commit -m "feat(reminder): implement local AlarmManager scheduling and smart slot notification suppression"
```

---

### Task 6: Sensory-Friendly Theme & Daily Account UI (Home Screen, Add/Edit Sheet, Calendar Sheet)

**Files:**
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/ui/theme/Color.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/ui/theme/Theme.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/ui/daily/DailyAccountViewModel.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/ui/daily/DailyAccountScreen.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/ui/daily/AddEditEntrySheet.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/ui/daily/CalendarHistorySheet.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/ui/DailyAccountViewModelTest.kt`

**Interfaces:**
- Consumes: Domain models, `DimensionChoiceState`, `EnergyFormatter`, and `EnergyAccountingRepository` from Tasks 1–3.
- Produces:
  - `enum class ThemeMode { SYSTEM, DARK, WARM_LIGHT }`
  - `@Composable fun EmpathieTheme(themeMode: ThemeMode, content: @Composable () -> Unit)`
  - `data class EntryDraftState(val editingEntryId: Long? = null, val timeSlot: TimeSlot, val activity: String = "", val situationalNote: String = "", val direction: Direction = Direction.SPEND, val energyValue: Int = 30, val dimensionChoice: DimensionChoiceState = DimensionChoiceState(null), val matchedTemplate: ActivityTemplate? = null, val hasModifiedTemplateDefaults: Boolean = false, val updateTemplateDefaults: Boolean = false)`
  - `class DailyAccountViewModel(private val repo: EnergyAccountingRepository, private val clock: Clock = Clock.systemDefaultZone()) : ViewModel()` managing selected `LocalDate`, `DailyAccount` state, `YearMonth` calendar net-balance map, and `EntryDraftState` actions (`openNewEntrySheet(preferredSlot: TimeSlot?)`, `openEditEntrySheet(entry: Entry)`, `applyTemplate(template: ActivityTemplate)`, `onActivityChange(text: String)`, `onTapDimension(id: Long)`, `onLongPressDimension(id: Long)`, `saveDraft()`).
  - `@Composable fun DailyAccountScreen(viewModel: DailyAccountViewModel)`

- [ ] **Step 1: Write failing tests in `app/src/test/kotlin/com/empathie/energyaccounting/ui/DailyAccountViewModelTest.kt`**

```kotlin
package com.empathie.energyaccounting.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.repository.EnergyAccountingRepository
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.TimeSlot
import com.empathie.energyaccounting.ui.daily.DailyAccountViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class DailyAccountViewModelTest {
    private lateinit var db: EnergyAccountingDatabase
    private lateinit var repo: EnergyAccountingRepository
    private val fixedClock = Clock.fixed(Instant.parse("2026-11-12T14:15:00Z"), ZoneId.of("UTC"))

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EnergyAccountingDatabase::class.java
        ).allowMainThreadQueries().build()
        db.ensureSeeded()
        repo = EnergyAccountingRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `openNewEntrySheet on Today preselects TimeSlot from clock and saving updates DailyAccount`() = runTest {
        val vm = DailyAccountViewModel(repo, fixedClock)
        val dims = repo.observeActiveDimensions().first()
        vm.openNewEntrySheet(preferredSlot = null)

        val draft = vm.draftState.value!!
        assertEquals(TimeSlot.AFTERNOON, draft.timeSlot) // 14:15 UTC -> AFTERNOON

        vm.onActivityChange("Quiet walk")
        vm.onDirectionChange(Direction.GAIN)
        vm.onEnergyValueChange(40)
        vm.onTapDimension(dims.first().id)
        vm.saveDraft()

        assertNull(vm.draftState.value)
        val account = repo.observeDailyAccount(LocalDate.of(2026, 11, 12)).first()
        assertEquals(40, account.totalGain)
        assertEquals(40, account.netBalance)
    }
}
```

- [ ] **Step 2: Run the ViewModel test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.ui.DailyAccountViewModelTest"`
Expected: FAIL with missing `DailyAccountViewModel`.

- [ ] **Step 3: Implement `Color.kt`, `Theme.kt`, `DailyAccountViewModel.kt`, `DailyAccountScreen.kt`, `AddEditEntrySheet.kt`, and `CalendarHistorySheet.kt`**

Implement the sensory-friendly palette tokens (`GainSageDark = Color(0xFF5EC2A6)`, `SpendAmberDark = Color(0xFFE29B61)`, `NetPeriwinkleDark = Color(0xFF94B8FF)`, plus Warm Light counterparts), `DailyAccountViewModel`, and the three Compose files (`DailyAccountScreen`, `AddEditEntrySheet` with pinned template chips, autocomplete dropdown, step-of-10 slider + `-5/+5` buttons, and `1°/2°` dimension chip FlowRow, and `CalendarHistorySheet` with net-balance colored dots).

- [ ] **Step 4: Run the ViewModel test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.ui.DailyAccountViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/empathie/energyaccounting/ui/theme app/src/main/kotlin/com/empathie/energyaccounting/ui/daily app/src/test/kotlin/com/empathie/energyaccounting/ui/DailyAccountViewModelTest.kt
git commit -m "feat(ui-daily): add sensory-friendly Material 3 theme, DailyAccountScreen, AddEditEntrySheet, and CalendarHistorySheet"
```

---

### Task 7: Insights Screen, Manage & Export Screen, and Single-Activity Navigation Shell

**Files:**
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/ui/insights/InsightsViewModel.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/ui/insights/InsightsScreen.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/ui/manage/ManageExportViewModel.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/ui/manage/ManageExportScreen.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/EnergyAccountingApp.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/MainActivity.kt`

**Interfaces:**
- Consumes: All domain, repository, export, reminder, and UI components from Tasks 1–6.
- Produces:
  - `class InsightsViewModel(private val repo: EnergyAccountingRepository, private val clock: Clock = Clock.systemDefaultZone()) : ViewModel()` supporting `7 Days`, `30 Days`, and custom `dd/MM/yyyy` date ranges plus optional dimension filtering (showing entries where a selected dimension is either `1° Primary` or `2° Secondary`).
  - `class ManageExportViewModel(private val repo: EnergyAccountingRepository, private val backupSerializer: BackupSerializer, private val pdfGenerator: PractitionerPdfGenerator, private val reminderScheduler: ReminderScheduler) : ViewModel()` managing templates, custom dimensions, reminder schedules, PDF/CSV/JSON exports, and `ThemeMode`.
  - `class MainActivity : ComponentActivity()` hosting the 3-tab `NavigationBar` (`Daily Account`, `Insights`, `Manage & Export`) and handling `CheckInReminderReceiver.EXTRA_OPEN_ADD_SHEET` + `EXTRA_TIME_SLOT` intents.

- [ ] **Step 1: Implement `InsightsViewModel.kt` and `InsightsScreen.kt`**

Build the period selector (`7d`, `30d`, `Custom`), Period Summary cards, proportional `PrimaryDimension` bars for `Spend (-)` and `Gain (+)`, `SecondaryDimension` occurrence chips with filtered entry preview, and `TimeSlot` breakdown cards.

- [ ] **Step 2: Implement `ManageExportViewModel.kt` and `ManageExportScreen.kt`**

Build the 5 management sections: Activity Templates editor (pin/unpin, create/edit/delete), Energy Dimensions manager (add custom dimension, archive/delete unused), Check-in Reminders schedule (`POST_NOTIFICATIONS` permission check on Android 13+ + time pickers per slot), Practitioner PDF Report & CSV/JSON Backup/Restore (`ActivityResultContracts.CreateDocument` & `OpenDocument`), and Theme Mode selector (`System`, `Dark`, `Warm Light`).

- [ ] **Step 3: Implement `EnergyAccountingApp.kt` and `MainActivity.kt`**

Wire database initialization (`ensureSeeded()`), dependency container on `EnergyAccountingApp`, 3-tab bottom navigation in `MainActivity`, and deep-link handling from `CheckInReminderReceiver` intents into `DailyAccountViewModel.openNewEntrySheet(timeSlot)`.

- [ ] **Step 4: Run full unit test suite and assemble debug APK**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: `BUILD SUCCESSFUL`, all unit and Robolectric tests passing, and `app/build/outputs/apk/debug/app-debug.apk` generated with zero Google Play Services dependencies (`./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep -i "com.google.android.gms"` returns empty).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/empathie/energyaccounting
git commit -m "feat(app): complete Insights screen, Manage & Export screen, and MainActivity navigation shell"
```
