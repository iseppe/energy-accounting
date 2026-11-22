# Empathie Energy Accounting — Round 2 UX, Domain Simplification & Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the app to a single Energy Dimension per Entry, fix all 16 real-device UX/layout findings and 6 branch-audit issues, upgrade the PDF export to a bordered table, and add in-app language switching, future-date warnings, test notifications, build ID, and debug log export.

**Architecture:** Simplifies the domain and Room schema (v1 → v2 with non-destructive `MIGRATION_1_2`) so each `Entry` and `ActivityTemplate` holds a single `EnergyDimension`. Updates the Jetpack Compose UI layer for proper window insets, non-drifting expanded bottom sheets, horizontal template chips, step-of-5 energy sliders, Material-style calendar date highlights, `Today`-first Insights, predictive back navigation, dynamic English/Italian locale wrapping, vector-drawn PDF tables, and on-device diagnostic log export.

**Tech Stack:** Kotlin 2.1, Jetpack Compose (Material 3), Room 2.7 (`MIGRATION_1_2`), Kotlin Coroutines & Flow, Kotlinx Serialization JSON (supporting both v1 and v2 backup payloads), custom PDF 1.4 table/vector writer, JUnit 4 + Robolectric.

**Spec:** `docs/superpowers/specs/2026-11-13-energy-accounting-v2-refinements-design.md`.

## Global Constraints

- **Platform floors:** `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`, package `com.empathie.energyaccounting`.
- **Zero Google Play Services:** Never add `com.google.android.gms` or Firebase dependencies.
- **Single Energy Dimension per Entry:** `Entry` has `val dimension: EnergyDimension`; `ActivityTemplate` has `val defaultDimension: EnergyDimension`. No `1°` or `2°` prefixes anywhere in the UI, CSV, or PDF.
- **Step-of-5 Energy Value UI (`5..100`):** Both the `Slider` (`valueRange = 5f..100f`, `steps = 18`) and `-5` / `+5` buttons step by 5 (`5..100`), with a fixed-width (`52.dp`) value label so `±100` never shrinks the slider.
- **Bilingual & In-App Switchable:** All user-facing strings live in `values/strings.xml` (English) and `values-it/strings.xml` (Italian), switchable in-app (`System`, `English`, `Italiano`) without losing state.
- **Non-destructive upgrade:** Room `MIGRATION_1_2` and `BackupSerializer` must preserve existing v1 on-device entries/templates and accept both `schemaVersion = 1` and `schemaVersion = 2` JSON backups.

## Review Focus

1. **Backward-compatible JSON Backup Restore (`schemaVersion = 1` and `schemaVersion = 2`):** Restoring a v1 JSON backup (which used `primaryDimensionId` and `defaultPrimaryDimensionId`) into the v2 database must succeed and map `primaryDimensionId` → `dimensionId` and `defaultPrimaryDimensionId` → `defaultDimensionId` (pinned in Task 3).
2. **Room SQLite `MIGRATION_1_2` data preservation:** Running `MIGRATION_1_2` on a v1 SQLite database containing entries, secondary dimension cross-refs, and templates must preserve all entries and templates with their primary dimension intact while dropping `entry_secondary_dimensions` (pinned in Task 2).
3. **Case-insensitive duplicate custom `EnergyDimension` names:** Calling `createCustomDimension("  physical ")` or duplicating an existing custom dimension name must throw `IllegalArgumentException` and report `ManageMessage.DIMENSION_NAME_IN_USE` in the UI instead of creating duplicate dimension chips (pinned in Task 2).
4. **Future Date Warning gating on Add Entry vs. Edit Entry:** Tapping `+` on a future date (`selectedDate > today`) must set `pendingFutureSlotWarning` instead of opening `AddEditEntrySheet` immediately; confirming the warning opens `AddEditEntrySheet` for that exact `TimeSlot`, whereas `openEditEntrySheet` on an existing future entry opens directly without warning (pinned in Task 4).
5. **Multi-line cell wrapping and localized dimension names in the bordered PDF table:** Generating a PDF report with long activities/notes (`> 45` chars), Italian accents (`Caffè`, `Perché`), and seeded dimensions (`SystemDimensionKey.PHYSICAL`) must draw PDF table grid borders (`re`, `m`, `l`, `S`), wrap long cell text across multiple lines within the column without overflowing into adjacent columns, and print localized dimension names (`Fisico` when Italian `PdfLabels` are supplied) (pinned in Task 3).

## File Structure

```text
/
├── GLOSSARY.md                                                        (Modify: single EnergyDimension per Entry)
├── docs/
│   ├── adr/
│   │   ├── 0002-primary-and-secondary-energy-dimensions.md            (Modify: mark superseded by ADR-0003)
│   │   └── 0003-single-energy-dimension-per-entry.md                  (Create)
│   └── superpowers/
│       ├── specs/2026-11-13-energy-accounting-v2-refinements-design.md (Create)
│       └── plans/2026-11-13-energy-accounting-v2-refinements.md        (Create)
└── app/
    ├── build.gradle.kts                                               (Modify: enable buildConfig + BUILD_ID field)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml                                    (Modify: enableOnBackInvokedCallback="true")
        │   ├── res/
        │   │   ├── values/strings.xml                                 (Modify: add v2 strings, Light label, language, future warning, debug log)
        │   │   └── values-it/strings.xml                              (Modify: Italian counterparts)
        │   └── kotlin/com/empathie/energyaccounting/
        │       ├── MainActivity.kt                                    (Modify: edge-to-edge status bar contrast, LocaleHelper attachBaseContext, BackHandler)
        │       ├── EnergyAccountingApp.kt                             (Modify: AppLogger hooks)
        │       ├── domain/
        │       │   ├── model/
        │       │   │   ├── Entry.kt                                   (Modify: single dimension)
        │       │   │   ├── ActivityTemplate.kt                        (Modify: single defaultDimension)
        │       │   │   ├── DimensionChoiceState.kt                    (Delete: replaced by direct dimensionId)
        │       │   │   └── PeriodInsights.kt                          (Modify: remove secondaryOccurrenceCount)
        │       │   └── service/
        │       │       ├── EnergyFormatter.kt                         (Modify: formatGainTotal / formatSpendTotal without +0/-0, localizeDimensionName)
        │       │       └── InsightsCalculator.kt                      (Modify: single dimension breakdown + slot daily average)
        │       ├── data/
        │       │   ├── local/
        │       │   │   ├── entity/
        │       │   │   │   ├── EntryEntity.kt                         (Modify: dimensionId)
        │       │   │   │   ├── EntrySecondaryDimensionCrossRef.kt     (Delete)
        │       │   │   │   ├── EntryWithDimensions.kt                 (Modify: single @Relation dimension)
        │       │   │   │   └── ActivityTemplateEntity.kt              (Modify: defaultDimensionId)
        │       │   │   ├── EnergyAccountingDao.kt                     (Modify: v2 queries)
        │       │   │   └── EnergyAccountingDatabase.kt                (Modify: version = 2, MIGRATION_1_2)
        │       │   ├── repository/
        │       │   │   └── EnergyAccountingRepository.kt              (Modify: single dimension, duplicate custom dimension check)
        │       │   ├── settings/
        │       │   │   ├── AppSettings.kt                             (Modify: add AppLanguage preference)
        │       │   │   └── LocaleHelper.kt                            (Create: Context locale wrapper)
        │       │   ├── diagnostics/
        │       │   │   ├── AppLogger.kt                               (Create: ring-buffer logger)
        │       │   │   └── DebugLogExporter.kt                        (Create: diagnostic + logcat exporter)
        │       │   └── export/
        │       │       ├── CsvExporter.kt                             (Modify: single localized dimension column)
        │       │       ├── BackupSerializer.kt                        (Modify: schemaVersion = 2 + v1 backward compatibility)
        │       │       ├── PdfLabels.kt                               (Modify: table headers + localized SystemDimensionKey map)
        │       │       └── PractitionerPdfGenerator.kt                (Modify: bordered 4-column table + formatted summary sections)
        │       ├── reminder/
        │       │   └── CheckInReminderReceiver.kt                     (Modify: postTestNotification + finally re-arm)
        │       └── ui/
        │           ├── theme/
        │           │   └── Theme.kt                                   (Modify: rename WARM_LIGHT -> LIGHT with backward compat, status bar appearance sync)
        │           ├── daily/
        │           │   ├── DailyAccountViewModel.kt                   (Modify: single dimensionId, future-date warning state, midnight-safe today)
        │           │   ├── DailyAccountScreen.kt                      (Modify: status bar insets, contentPadding, +0/-0 fix, future warning dialog)
        │           │   ├── AddEditEntrySheet.kt                       (Modify: skipPartiallyExpanded, slot title, horizontal LazyRow max 5 + "..." dialog, step-of-5 slider, fixed readout width, 1-tap dimension chips)
        │           │   └── CalendarHistorySheet.kt                    (Modify: skipPartiallyExpanded, filled circle for selectedDate, outlined circle for today)
        │           ├── insights/
        │           │   ├── InsightsViewModel.kt                       (Modify: InsightsPeriod.TODAY default, dynamic today refresh)
        │           │   └── InsightsScreen.kt                          (Modify: status bar insets, Today chip first, tappable dimension bars, remove secondary card)
        │           └── manage/
        │               ├── ManageExportViewModel.kt                   (Modify: language state, test notification, debug log builder, duplicate dimension message)
        │               └── ManageExportScreen.kt                      (Modify: status bar insets, Light label, language chips, Test Notification button, Export Debug Log button, PdfRange normalization, Build ID footer)
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
                ├── DailyAccountViewModelTest.kt
                ├── MainActivityDeepLinkTest.kt
                └── ManageExportViewModelTest.kt
```

---

### Task 1: Docs, BuildConfig `BUILD_ID`, Bilingual Resources, and Single-Dimension Domain Model

**Files:**
- Modify: `GLOSSARY.md`
- Modify: `docs/adr/0002-primary-and-secondary-energy-dimensions.md`
- Create: `docs/adr/0003-single-energy-dimension-per-entry.md`
- Create: `docs/superpowers/specs/2026-11-13-energy-accounting-v2-refinements-design.md`
- Create: `docs/superpowers/plans/2026-11-13-energy-accounting-v2-refinements.md`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-it/strings.xml`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/domain/model/Entry.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/domain/model/ActivityTemplate.kt`
- Delete: `app/src/main/kotlin/com/empathie/energyaccounting/domain/model/DimensionChoiceState.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/domain/model/PeriodInsights.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/domain/service/EnergyFormatter.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/domain/service/InsightsCalculator.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/domain/DomainModelTest.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/domain/InsightsCalculatorTest.kt`

**Interfaces:**
- Consumes: Existing `TimeSlot`, `Direction`, `EnergyDimension`, `SystemDimensionKey`, `DailyAccount`.
- Produces:
  - `BuildConfig.BUILD_ID: String`
  - `class Entry(val id: Long = 0L, val date: LocalDate, val timeSlot: TimeSlot, activity: String, situationalNote: String?, val direction: Direction, val energyValue: Int, val dimension: EnergyDimension, val createdAtEpochMillis: Long)`
  - `class ActivityTemplate(val id: Long = 0L, activityName: String, val defaultDirection: Direction, val defaultEnergyValue: Int, val defaultDimension: EnergyDimension, val isPinned: Boolean = false, val usageCount: Int = 0, val lastUsedEpochMillis: Long = 0L)`
  - `data class DimensionImpactSummary(val dimension: EnergyDimension, val spend: Int, val spendSharePercent: Int, val gain: Int, val gainSharePercent: Int)`
  - `data class TimeSlotImpactSummary(val timeSlot: TimeSlot, val totalGain: Int, val totalSpend: Int, val netBalance: Int, val averageNetPerActiveDay: Int)`
  - `EnergyFormatter.formatGainTotal(gain: Int): String` (`"0"` when `0`, `"+$gain"` when `> 0`)
  - `EnergyFormatter.formatSpendTotal(spend: Int): String` (`"0"` when `0`, `"-$spend"` when `> 0`)

- [ ] **Step 1: Update `GLOSSARY.md`, `ADR-0002`, `ADR-0003`, copy spec & plan into `docs/superpowers/`, update `app/build.gradle.kts` (`buildConfig = true` + `BUILD_ID`), and add v2 strings to `values/strings.xml` and `values-it/strings.xml`**

Add strings for: `add_entry_for_slot` (`"Add entry for %1$s"` / `"Aggiungi voce per %1$s"`), `edit_entry_for_slot` (`"Edit entry for %1$s"` / `"Modifica voce per %1$s"`), `all_templates` (`"All saved templates"` / `"Tutti i modelli salvati"`), `more_templates` (`"..."`), `today_period` (`"Today"` / `"Oggi"`), `theme_light` (`"Light"` / `"Chiaro"`), `language` (`"Language"` / `"Lingua"`), `language_system` (`"System"` / `"Sistema"`), `language_english` (`"English"` / `"Inglese"`), `language_italian` (`"Italiano"` / `"Italiano"`), `send_test_notification` (`"Send test notification"` / `"Invia notifica di prova"`), `test_notification_sent` (`"Test notification sent"` / `"Notifica di prova inviata"`), `export_debug_log` (`"Export debug log"` / `"Esporta log di debug"`), `future_date_warning_title` (`"Future date"` / `"Data futura"`), `future_date_warning_body` (`"You are adding an entry for %1$s, which is in the future. Do you want to continue?"` / `"Stai aggiungendo una voce per il %1$s, che è una data futura. Vuoi continuare?"`), `continue_action` (`"Continue"` / `"Continua"`), `go_back` (`"Go back"` / `"Torna indietro"`), `dimension_name_in_use` (`"A dimension with this name already exists"` / `"Esiste già una dimensione con questo nome"`), `spend_by_dimension` (`"Spend by dimension"` / `"Consumo per dimensione"`), `gain_by_dimension` (`"Gain by dimension"` / `"Guadagno per dimensione"`), and `build_id_label` (`"Build: %1$s"` / `"Build: %1$s"`).

- [ ] **Step 2: Write failing tests in `DomainModelTest.kt` and `InsightsCalculatorTest.kt`**

```kotlin
// In DomainModelTest.kt:
@Test
fun `Entry holds a single dimension and normalizes activity and situationalNote`() {
    val entry = Entry(
        date = LocalDate.of(2026, 11, 13),
        timeSlot = TimeSlot.LATE_MORNING,
        activity = "  Lunch in cafeteria  ",
        situationalNote = "   ",
        direction = Direction.SPEND,
        energyValue = 50,
        dimension = sensory,
        createdAtEpochMillis = 1000L
    )
    assertEquals("Lunch in cafeteria", entry.activity)
    assertNull(entry.situationalNote)
    assertEquals(sensory, entry.dimension)
}

@Test
fun `EnergyFormatter formats zero gain and spend without plus or minus signs`() {
    assertEquals("0", EnergyFormatter.formatGainTotal(0))
    assertEquals("+20", EnergyFormatter.formatGainTotal(20))
    assertEquals("0", EnergyFormatter.formatSpendTotal(0))
    assertEquals("-35", EnergyFormatter.formatSpendTotal(35))
}
```

```kotlin
// In InsightsCalculatorTest.kt:
@Test
fun `dimension breakdown attributes full energyValue to each entry dimension and computes slot averages`() {
    val entries = listOf(
        Entry(1L, LocalDate.of(2026, 11, 5), TimeSlot.AFTERNOON, "Team meeting", "Loud room", Direction.SPEND, 60, sensory, 1L),
        Entry(2L, LocalDate.of(2026, 11, 5), TimeSlot.EVENING, "Quiet time", null, Direction.GAIN, 20, sensory, 2L),
        Entry(3L, LocalDate.of(2026, 11, 6), TimeSlot.AFTERNOON, "Homework", null, Direction.SPEND, 40, cognitive, 3L)
    )
    val insights = InsightsCalculator.calculate(start, end, entries, dimensions)
    val sensorySummary = insights.byDimension.first { it.dimension.id == sensory.id }
    assertEquals(60, sensorySummary.spend)
    assertEquals(60, sensorySummary.spendSharePercent)
    assertEquals(20, sensorySummary.gain)
    assertEquals(100, sensorySummary.gainSharePercent)

    val afternoon = insights.byTimeSlot.first { it.timeSlot == TimeSlot.AFTERNOON }
    assertEquals(-100, afternoon.netBalance)
    assertEquals(-50, afternoon.averageNetPerActiveDay) // -100 across 2 active days
}
```

- [ ] **Step 3: Run domain tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.domain.*"`
Expected: FAIL with constructor/property mismatch (`dimension`, `formatGainTotal`, `spend`, `averageNetPerActiveDay`).

- [ ] **Step 4: Implement single-dimension `Entry`, `ActivityTemplate`, `PeriodInsights`, `EnergyFormatter`, and `InsightsCalculator`, and delete `DimensionChoiceState.kt`**

Update `Entry`, `ActivityTemplate`, `PeriodInsights`, `EnergyFormatter`, and `InsightsCalculator`.

- [ ] **Step 5: Run domain tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.domain.*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add GLOSSARY.md docs app/build.gradle.kts app/src/main/res app/src/main/kotlin/com/empathie/energyaccounting/domain app/src/test/kotlin/com/empathie/energyaccounting/domain
git commit -m "refactor(domain): simplify Entry and ActivityTemplate to a single EnergyDimension"
```

---

### Task 2: Room Schema v2 (`MIGRATION_1_2`) and Repository Single-Dimension & Uniqueness Rules

**Files:**
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/data/local/entity/EntryEntity.kt`
- Delete: `app/src/main/kotlin/com/empathie/energyaccounting/data/local/entity/EntrySecondaryDimensionCrossRef.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/data/local/entity/EntryWithDimensions.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/data/local/entity/ActivityTemplateEntity.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/data/local/EnergyAccountingDao.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/data/local/EnergyAccountingDatabase.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/data/local/DatabaseProvider.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/data/repository/EnergyAccountingRepository.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/data/EnergyAccountingDaoTest.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/data/EnergyAccountingRepositoryTest.kt`

**Interfaces:**
- Consumes: Single-dimension domain models from Task 1.
- Produces:
  - `EntryEntity(id: Long = 0, dateIso: String, timeSlot: String, activity: String, situationalNote: String?, direction: String, energyValue: Int, dimensionId: Long, createdAtEpochMillis: Long)`
  - `EntryWithDimension(@Embedded val entry: EntryEntity, @Relation(parentColumn = "dimensionId", entityColumn = "id") val dimension: EnergyDimensionEntity)`
  - `ActivityTemplateEntity(id: Long = 0, normalizedName: String, displayName: String, defaultDirection: String, defaultEnergyValue: Int, defaultDimensionId: Long, isPinned: Boolean, usageCount: Int, lastUsedEpochMillis: Long)`
  - `EnergyAccountingDatabase.MIGRATION_1_2: Migration` converting v1 tables (`primaryDimensionId`, `defaultPrimaryDimensionId`, `entry_secondary_dimensions`) to v2 (`dimensionId`, `defaultDimensionId`) without data loss.
  - `EnergyAccountingRepository.createCustomDimension(name: String, colorIndex: Int): Long` throwing `IllegalArgumentException` if a dimension with the same case-insensitive trimmed name already exists.

- [ ] **Step 1: Write failing tests in `EnergyAccountingDaoTest.kt` and `EnergyAccountingRepositoryTest.kt` (including Review Focus #2 and #3)**

```kotlin
// In EnergyAccountingDaoTest.kt:
@Test
fun `MIGRATION_1_2 preserves existing v1 entries and templates while dropping secondary cross-refs`() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val dbName = "migration-test-${System.currentTimeMillis()}.db"
    val sqliteDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
        context.getDatabasePath(dbName), null
    )
    sqliteDb.execSQL("CREATE TABLE `energy_dimensions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `systemKey` TEXT, `colorIndex` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL)")
    sqliteDb.execSQL("INSERT INTO `energy_dimensions` VALUES (1, 'Sensory', 'SENSORY', 2, 0)")
    sqliteDb.execSQL("CREATE TABLE `entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dateIso` TEXT NOT NULL, `timeSlot` TEXT NOT NULL, `activity` TEXT NOT NULL, `situationalNote` TEXT, `direction` TEXT NOT NULL, `energyValue` INTEGER NOT NULL, `primaryDimensionId` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL)")
    sqliteDb.execSQL("INSERT INTO `entries` VALUES (10, '2026-11-12', 'EARLY_MORNING', 'sveglia', 'devo svegliarmi', 'SPEND', 30, 1, 1000)")
    sqliteDb.execSQL("CREATE TABLE `entry_secondary_dimensions` (`entryId` INTEGER NOT NULL, `dimensionId` INTEGER NOT NULL, PRIMARY KEY(`entryId`, `dimensionId`))")
    sqliteDb.execSQL("CREATE TABLE `activity_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `normalizedName` TEXT NOT NULL, `displayName` TEXT NOT NULL, `defaultDirection` TEXT NOT NULL, `defaultEnergyValue` INTEGER NOT NULL, `defaultPrimaryDimensionId` INTEGER NOT NULL, `defaultSecondaryDimensionIdsCsv` TEXT NOT NULL, `isPinned` INTEGER NOT NULL, `usageCount` INTEGER NOT NULL, `lastUsedEpochMillis` INTEGER NOT NULL)")
    sqliteDb.execSQL("INSERT INTO `activity_templates` VALUES (5, 'sveglia', 'sveglia', 'SPEND', 30, 1, '2,3', 0, 1, 1000)")
    sqliteDb.execSQL("CREATE TABLE `reminder_schedules` (`timeSlot` TEXT NOT NULL, `hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, PRIMARY KEY(`timeSlot`))")
    sqliteDb.version = 1
    sqliteDb.close()

    val migratedDb = Room.databaseBuilder(context, EnergyAccountingDatabase::class.java, dbName)
        .addMigrations(EnergyAccountingDatabase.MIGRATION_1_2)
        .allowMainThreadQueries()
        .build()

    kotlinx.coroutines.test.runTest {
        val entries = migratedDb.dao().observeEntriesForDate("2026-11-12").first()
        assertEquals(1, entries.size)
        assertEquals("sveglia", entries.first().entry.activity)
        assertEquals("SENSORY", entries.first().dimension.systemKey)
        val templates = migratedDb.dao().getTemplates()
        assertEquals(1, templates.size)
        assertEquals(1L, templates.first().defaultDimensionId)
    }
    migratedDb.close()
    context.deleteDatabase(dbName)
}
```

```kotlin
// In EnergyAccountingRepositoryTest.kt:
@Test
fun `createCustomDimension rejects duplicate names case-insensitively`() = runTest {
    val resultSeeded = runCatching { repo.createCustomDimension("  physical ", 1) }
    assertTrue(resultSeeded.exceptionOrNull() is IllegalArgumentException)

    repo.createCustomDimension("Focus", 2)
    val resultCustom = runCatching { repo.createCustomDimension("FOCUS", 3) }
    assertTrue(resultCustom.exceptionOrNull() is IllegalArgumentException)
}
```

- [ ] **Step 2: Run the DAO and Repository tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.data.EnergyAccountingDaoTest" --tests "com.empathie.energyaccounting.data.EnergyAccountingRepositoryTest"`
Expected: FAIL.

- [ ] **Step 3: Implement Room v2 entities, `MIGRATION_1_2`, `DatabaseProvider` migration registration, and `EnergyAccountingRepository`**

Update `EntryEntity`, `EntryWithDimensions` (renamed class or kept in file as `EntryWithDimension`), `ActivityTemplateEntity`, `EnergyAccountingDao`, `EnergyAccountingDatabase` (`version = 2` + `MIGRATION_1_2`), `DatabaseProvider` (`.addMigrations(EnergyAccountingDatabase.MIGRATION_1_2)`), and `EnergyAccountingRepository`.

- [ ] **Step 4: Run the DAO and Repository tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.data.EnergyAccountingDaoTest" --tests "com.empathie.energyaccounting.data.EnergyAccountingRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/empathie/energyaccounting/data app/src/test/kotlin/com/empathie/energyaccounting/data
git commit -m "feat(data): migrate Room schema to v2 single dimension and enforce custom dimension uniqueness"
```

---

### Task 3: Table-Formatted Practitioner PDF Report, Localized CSV, Backward-Compatible Backup (v1/v2), and Debug Log Exporter

**Files:**
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/data/export/CsvExporter.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/data/export/BackupSerializer.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/data/export/PdfLabels.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/data/export/PractitionerPdfGenerator.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/data/diagnostics/AppLogger.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/data/diagnostics/DebugLogExporter.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/data/ExportAndBackupTest.kt`

**Interfaces:**
- Consumes: Single-dimension domain & Room v2 models from Tasks 1–2.
- Produces:
  - `CsvExporter.exportEntries(entries: List<Entry>, dimensionLabel: (EnergyDimension) -> String = { it.name }): String` with columns `Date,Time Slot,Activity,Situational Note,Direction,Signed Value,Dimension`
  - `BackupSerializer` writing `schemaVersion = 2` and accepting both `schemaVersion = 1` (reading `primaryDimensionId` / `defaultPrimaryDimensionId`) and `schemaVersion = 2` (`dimensionId` / `defaultDimensionId`)
  - `PdfLabels` including `dimensionNames: Map<SystemDimensionKey, String>`, `columnDateSlot`, `columnActivity`, `columnDimension`, `columnValue`, and `fun resolveDimensionName(dimension: EnergyDimension): String`
  - `PractitionerPdfGenerator` rendering a **bordered 4-column table** (`re` header shading, `m`/`l`/`S` cell borders, wrapped multi-line rows, daily subtotal footer rows) for the date-range entries, plus structured summary sections below the table
  - `object AppLogger { fun info(tag: String, message: String); fun error(tag: String, message: String, throwable: Throwable? = null); fun dumpLines(): List<String> }`
  - `class DebugLogExporter(private val db: EnergyAccountingDatabase)` with `suspend fun buildLogReport(buildId: String, localeTag: String, themeName: String): String`

- [ ] **Step 1: Write failing tests in `ExportAndBackupTest.kt` (including Review Focus #1 and #5)**

```kotlin
@Test
fun `BackupSerializer restores both schemaVersion 1 and schemaVersion 2 payloads`() = runTest {
    val v1Json = """
        {
          "schemaVersion": 1,
          "dimensions": [{"id": 10, "systemKey": "SENSORY", "name": "Sensory", "colorIndex": 2, "isArchived": false}],
          "templates": [{"normalizedName": "sveglia", "displayName": "sveglia", "defaultDirection": "SPEND", "defaultEnergyValue": 30, "defaultPrimaryDimensionId": 10}],
          "entries": [{"dateIso": "2026-11-12", "timeSlot": "EARLY_MORNING", "activity": "sveglia", "situationalNote": "test", "direction": "SPEND", "energyValue": 30, "primaryDimensionId": 10, "createdAtEpochMillis": 1000}],
          "reminders": []
        }
    """.trimIndent()

    BackupSerializer(db2).restoreJson(v1Json, RestoreMode.REPLACE)
    val repo2 = EnergyAccountingRepository(db2)
    val account = repo2.observeDailyAccount(LocalDate.of(2026, 11, 12)).first()
    assertEquals(30, account.totalSpend)
    assertEquals(SystemDimensionKey.SENSORY, account.slots.getValue(TimeSlot.EARLY_MORNING).entries.first().dimension.systemKey)
}

@Test
fun `PractitionerPdfGenerator draws bordered table vector commands and localizes dimension names`() {
    val date = LocalDate.of(2026, 11, 12)
    val physical = EnergyDimension(1L, "Physical", SystemDimensionKey.PHYSICAL, 0)
    val entry = Entry(
        1L, date, TimeSlot.EARLY_MORNING,
        "caffè + tempo mio con una nota molto lunga per verificare il wrapping nella tabella",
        "Perché il treno era in ritardo",
        Direction.GAIN, 20, physical, 100L
    )
    val account = DailyAccount.fromEntries(date, listOf(entry))
    val insights = InsightsCalculator.calculate(date, date, listOf(entry), listOf(physical))
    val labels = PdfLabels.english.copy(
        title = "Bilancio energetico giornaliero",
        dimensionNames = mapOf(SystemDimensionKey.PHYSICAL to "Fisico")
    )

    val out = ByteArrayOutputStream()
    PractitionerPdfGenerator().generateReport(out, date, date, listOf(account), insights, labels)
    val pdfContent = String(out.toByteArray(), Charsets.ISO_8859_1)

    assertTrue(pdfContent.contains("re\n")) // Table rectangle drawing commands
    assertTrue(pdfContent.contains("S\n"))  // Table border stroke commands
    assertTrue(pdfContent.contains("Fisico"))
    assertTrue(pdfContent.contains("caffè"))
}
```

- [ ] **Step 2: Run `ExportAndBackupTest` to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.data.ExportAndBackupTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `CsvExporter`, `BackupSerializer` (v1 & v2 support), `PdfLabels`, table-drawing `PractitionerPdfGenerator`, `AppLogger`, and `DebugLogExporter`**

In `PractitionerPdfGenerator`, draw table header and row boxes using PDF rectangle (`x y w h re`) and line (`x1 y1 m x2 y2 l S`) operators at columns `x = 40` (`Date / Slot`, width `110`), `x = 150` (`Activity & Note`, width `235`), `x = 385` (`Dimension`, width `100`), `x = 485..555` (`Value`, width `70`), followed by cleanly formatted summary cards below the table.

- [ ] **Step 4: Run `ExportAndBackupTest` to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.data.ExportAndBackupTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/empathie/energyaccounting/data/export app/src/main/kotlin/com/empathie/energyaccounting/data/diagnostics app/src/test/kotlin/com/empathie/energyaccounting/data/ExportAndBackupTest.kt
git commit -m "feat(export): draw bordered PDF table, support v1/v2 backup restore, and add DebugLogExporter"
```

---

### Task 4: Daily Account & Add/Edit Sheet Refinements (Insets, Future-Date Warning, Horizontal Templates, Step-of-5 Slider, Calendar Highlight)

**Files:**
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/ui/daily/DailyAccountViewModel.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/ui/daily/DailyAccountScreen.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/ui/daily/AddEditEntrySheet.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/ui/daily/CalendarHistorySheet.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/ui/DailyAccountViewModelTest.kt`

**Interfaces:**
- Consumes: Single-dimension `Entry`, `ActivityTemplate`, `EnergyFormatter`, `EnergyAccountingRepository`.
- Produces:
  - `data class EntryDraftState(val editingEntryId: Long? = null, val timeSlot: TimeSlot, val activity: String = "", val situationalNote: String = "", val direction: Direction = Direction.SPEND, val energyValue: Int = 30, val selectedDimensionId: Long? = null, val matchedTemplate: ActivityTemplate? = null, val hasModifiedTemplateDefaults: Boolean = false, val updateTemplateDefaults: Boolean = false)`
  - `DailyAccountViewModel`:
    - `val pendingFutureSlotWarning: StateFlow<TimeSlot?>`
    - `fun requestNewEntrySheet(preferredSlot: TimeSlot? = null)` (checks `if (selectedDate.value.isAfter(today))`; if so, sets `pendingFutureSlotWarning` to the resolved `TimeSlot`; otherwise opens `draftState` immediately)
    - `fun confirmFutureEntryWarning()` (opens `draftState` with the pending `TimeSlot` and clears `pendingFutureSlotWarning`)
    - `fun dismissFutureEntryWarning()` (clears `pendingFutureSlotWarning`)
    - `fun onSelectDimension(dimensionId: Long)` (1-tap selection; switches immediately)

- [ ] **Step 1: Write failing tests in `DailyAccountViewModelTest.kt` (including Review Focus #4)**

```kotlin
@Test
fun `onSelectDimension switches selectedDimensionId in one tap`() = runTest {
    val vm = DailyAccountViewModel(repo, fixedClock)
    val dims = repo.observeActiveDimensions().first()
    vm.openNewEntrySheet(TimeSlot.EARLY_MORNING)

    vm.onSelectDimension(dims[0].id)
    assertEquals(dims[0].id, vm.draftState.value!!.selectedDimensionId)

    vm.onSelectDimension(dims[1].id)
    assertEquals(dims[1].id, vm.draftState.value!!.selectedDimensionId)
}

@Test
fun `requestNewEntrySheet on a future date triggers pendingFutureSlotWarning until confirmed`() = runTest {
    val vm = DailyAccountViewModel(repo, fixedClock)
    vm.selectDate(LocalDate.of(2026, 11, 14)) // 2 days in the future

    vm.requestNewEntrySheet(TimeSlot.AFTERNOON)
    assertNull(vm.draftState.value)
    assertEquals(TimeSlot.AFTERNOON, vm.pendingFutureSlotWarning.value)

    vm.confirmFutureEntryWarning()
    assertNull(vm.pendingFutureSlotWarning.value)
    assertEquals(TimeSlot.AFTERNOON, vm.draftState.value!!.timeSlot)
}
```

- [ ] **Step 2: Run `DailyAccountViewModelTest` to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.ui.DailyAccountViewModelTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `DailyAccountViewModel`, `DailyAccountScreen`, `AddEditEntrySheet`, and `CalendarHistorySheet`**

- `DailyAccountScreen`: Add `Modifier.windowInsetsPadding(WindowInsets.statusBars)` on `DateBar`, add `contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp)` on `LazyColumn`, use `EnergyFormatter.formatGainTotal` / `formatSpendTotal` (no `+0` / `-0`), wire `+` buttons to `viewModel.requestNewEntrySheet(...)`, and render the **Future Date Warning `AlertDialog`** when `pendingFutureSlotWarning != null`.
- `AddEditEntrySheet`:
  - Pass `sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)`.
  - Title shows `stringResource(if (draft.editingEntryId == null) R.string.add_entry_for_slot else R.string.edit_entry_for_slot, timeSlotLabel(context, draft.timeSlot))`.
  - `QuickTemplates`: horizontal `LazyRow` showing `templates.take(5)` plus a 6th `AssistChip(label = { Text("...") })` when `templates.size > 5` (opening `AllTemplatesDialog`).
  - Slider & `±5` buttons: `valueRange = 5f..100f`, `steps = 18`, `(value / 5f).roundToInt() * 5`, and readout `Text` with `Modifier.widthIn(min = 52.dp)`.
  - Dimension chips: 1-tap `SelectablePill` / `DimensionChip` without `1°`/`2°` prefixes so chip widths never change.
- `CalendarHistorySheet`:
  - Pass `sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)` and `selectedDate: LocalDate`.
  - Highlight `date == selectedDate` with a filled primary circle and `date == today` with a `1.5.dp` primary border circle.

- [ ] **Step 4: Run `DailyAccountViewModelTest` to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.ui.DailyAccountViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/empathie/energyaccounting/ui/daily app/src/test/kotlin/com/empathie/energyaccounting/ui/DailyAccountViewModelTest.kt
git commit -m "feat(ui-daily): refine AddEditEntrySheet, horizontal templates, step-of-5 slider, calendar highlight, and future-date warning"
```

---

### Task 5: Insights (`Today` Default), Manage & Export (Language, Light Label, Test Notification, Debug Log, Build ID), and Predictive Back

**Files:**
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/data/settings/LocaleHelper.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/data/settings/AppSettings.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/ui/theme/Theme.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/reminder/CheckInReminderReceiver.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/ui/insights/InsightsViewModel.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/ui/insights/InsightsScreen.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/ui/manage/ManageExportViewModel.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/ui/manage/ManageExportScreen.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/EnergyAccountingApp.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/ui/ManageExportViewModelTest.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/ui/MainActivityDeepLinkTest.kt`

**Interfaces:**
- Consumes: All v2 domain, repository, export, diagnostics, and UI components from Tasks 1–4.
- Produces:
  - `enum class AppLanguage(val tag: String?) { SYSTEM(null), ENGLISH("en"), ITALIAN("it") }`
  - `object LocaleHelper { fun wrap(context: Context, language: AppLanguage): Context }`
  - `enum class InsightsPeriod { TODAY, LAST_7_DAYS, LAST_30_DAYS, CUSTOM }` (defaulting to `TODAY` in `InsightsViewModel`)
  - `CheckInReminderReceiver.postTestNotification(context: Context, timeSlot: TimeSlot): Boolean`
  - `ManageExportViewModel`:
    - `val appLanguage: StateFlow<AppLanguage>`
    - `fun setAppLanguage(language: AppLanguage)`
    - `suspend fun buildDebugLog(buildId: String, localeTag: String): ByteArray`
  - `MainActivity` with `attachBaseContext` locale wrapping, `enableEdgeToEdge()`, system bar icon contrast sync, and `BackHandler(enabled = selectedTab != 0) { onSelectTab(0) }`.

- [ ] **Step 1: Write failing tests in `ManageExportViewModelTest.kt` for duplicate dimension name handling, language persistence, and `InsightsViewModel` `TODAY` default**

```kotlin
@Test
fun `InsightsViewModel defaults to TODAY period`() = runTest {
    val fixedClock = java.time.Clock.fixed(java.time.Instant.parse("2026-11-13T10:00:00Z"), java.time.ZoneId.of("UTC"))
    val vm = com.empathie.energyaccounting.ui.insights.InsightsViewModel(EnergyAccountingRepository(db1), fixedClock)
    assertEquals(com.empathie.energyaccounting.ui.insights.InsightsPeriod.TODAY, vm.state.value.period)
    assertEquals(LocalDate.of(2026, 11, 13), vm.state.value.startDate)
    assertEquals(LocalDate.of(2026, 11, 13), vm.state.value.endDate)
}

@Test
fun `adding a duplicate custom dimension reports DIMENSION_NAME_IN_USE instead of crashing`() = runTest {
    val viewModel = viewModel(EnergyAccountingRepository(db1))
    viewModel.addCustomDimension("Physical")
    assertEquals(ManageMessage.DIMENSION_NAME_IN_USE, viewModel.message.value)
}

@Test
fun `setAppLanguage persists language preference in AppSettings`() = runTest {
    val viewModel = viewModel(EnergyAccountingRepository(db1))
    viewModel.setAppLanguage(com.empathie.energyaccounting.data.settings.AppLanguage.ITALIAN)
    assertEquals(com.empathie.energyaccounting.data.settings.AppLanguage.ITALIAN, viewModel.appLanguage.value)
    assertEquals(com.empathie.energyaccounting.data.settings.AppLanguage.ITALIAN, AppSettings(context).appLanguage())
}
```

- [ ] **Step 2: Run `ManageExportViewModelTest` to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.ui.ManageExportViewModelTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `LocaleHelper`, `AppSettings`, `CheckInReminderReceiver.postTestNotification`, `InsightsViewModel`/`InsightsScreen`, `ManageExportViewModel`/`ManageExportScreen`, and `MainActivity` + `AndroidManifest.xml`**

- `CheckInReminderReceiver`: Move tomorrow's alarm re-arm into a `finally` block wrapped in `runCatching`, and expose `fun postTestNotification(context: Context, timeSlot: TimeSlot): Boolean`.
- `InsightsScreen`: Add `Modifier.windowInsetsPadding(WindowInsets.statusBars)`, add the `Today` chip to the left of `Last 7 days`, remove the Secondary Dimension occurrences card, and make each dimension bar clickable to filter entries.
- `ManageExportScreen`: Add `Modifier.windowInsetsPadding(WindowInsets.statusBars)`, show `"Light"` (`R.string.theme_light`), add the **Language** chip row (`System`, `English`, `Italiano`) calling `(context as? Activity)?.recreate()`, localize seeded dimension names in the dimensions list (`dimensionLabel(context, dimension)`), use `R.string.dimension_archived` for archived status, add the **Send test notification** button, add the **Export debug log** button, normalize `startDate <= endDate` in `PdfRangeDialog`, and display `BuildConfig.BUILD_ID` at the bottom of the screen.
- `AndroidManifest.xml` & `MainActivity.kt`: Enable `android:enableOnBackInvokedCallback="true"`, wrap base context with `LocaleHelper`, sync status/navigation bar light/dark icon appearance, and add `BackHandler(enabled = selectedTab != 0) { onSelectTab(0) }`.

- [ ] **Step 4: Run full unit test suite and assemble debug APK**

Run: `./gradlew clean testDebugUnitTest assembleDebug`
Expected: `BUILD SUCCESSFUL`, all unit and Robolectric tests passing, and `app/build/outputs/apk/debug/app-debug.apk` built cleanly.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat(ui,diagnostics): add Today insights default, language switcher, test notification, debug log export, and predictive back"
```
