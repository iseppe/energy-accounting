# Empathie Energy Accounting — Round 3 UI Layout, Single-File i18n, Haptics, Graph, Dev Mode & PDF Share Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the top blank space across all screens, centralize English/Italian strings into a single `app/translations.json` catalog, convert all chip selectors to horizontal `LazyRow`s, apply the full-width slider + stepper header (`Design A`) in both Add Entry and Manage Template dialogs, add toggleable haptic feedback, add the Insights Gain/Spend + Net trend graph (`Graph A`), build the hidden Developer Mode (with Fake Clock, slot notification diagnostics, and Debug Log export), and add 1-tap PDF sharing to WhatsApp/other apps.

**Architecture:** A single `app/translations.json` file serves as the source of truth for all English and Italian strings, with a Gradle `syncTranslations` task generating `values/strings.xml` and `values-it/strings.xml` before every build and copying the catalog into assets. `AdjustableClock` (a mutable `java.time.Clock` implementation) is shared across ViewModels and `CheckInReminderReceiver` so Developer Mode can simulate any date/time and inspect slot alarm suppression deterministically. `FileProvider` enables direct `ACTION_SEND` PDF sharing from `cacheDir/reports/`.

**Tech Stack:** Kotlin 2.1, Jetpack Compose (Material 3, `Canvas`), Room 2.7, Android `FileProvider` & `AlarmManager`, Gradle custom task for JSON-to-XML resource generation, JUnit 4 + Robolectric.

**Spec:** `docs/superpowers/specs/2026-11-13-energy-accounting-v3-polish-and-devmode-design.md`

## Global Constraints

- **Platform floors:** `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`, package `com.empathie.energyaccounting`.
- **Zero Google Play Services:** Never add `com.google.android.gms` or Firebase dependencies.
- **Single-file translation source of truth:** Every user-facing string must live in `app/translations.json` with both `"en"` and `"it"` keys; `syncTranslations` generates `res/values/strings.xml` and `res/values-it/strings.xml` automatically on every build.
- **Single system-bar inset application:** Only `MainActivity`'s outer `AppShell` `Scaffold` applies system-bar insets; inner screens and `DailyAccountScreen`'s `Scaffold` (`contentWindowInsets = WindowInsets(0, 0, 0, 0)`) must never re-apply `WindowInsets.statusBars`.
- **Consistent `Design A` Value Selector & Horizontal `LazyRow`s:** Both `AddEditEntrySheet` and `TemplateEditDialog` must use the shared `EnergyDirectionAndValueSelector` (full-width slider below `[-5] value [+5]`) and horizontal `LazyRow` chip selectors.

## Review Focus

1. **Android XML escaping when generating `strings.xml` from `app/translations.json`:** Strings containing `&` (`"Manage & Export"`), apostrophes (`"all'app"`), or quotes (`"\"%1$s\""`) must be properly escaped (`&amp;`, `\'`, `\"`) by the translation generator so `aapt2` resource compilation succeeds and formatted strings render without backslashes (pinned in Task 1).
2. **Chart point generation for single-day (`Today`) vs. multi-day ranges with unlogged intermediate days:** `InsightsCalculator.calculate` must emit 4 `EnergyChartPoint`s (one per `TimeSlot`) when `startDate == endDate`, and one `EnergyChartPoint` for every calendar day in `startDate..endDate` (including 0-value days) when `startDate < endDate` so the X-axis timeline never skips unlogged dates (pinned in Task 2).
3. **`AdjustableClock` propagation across ViewModels and reminder diagnostics:** Setting a fake date/time on `AdjustableClock` must immediately change `LocalDate.now(clock)` / `LocalTime.now(clock)` seen by `DailyAccountViewModel`, `InsightsViewModel`, and `CheckInReminderReceiver.evaluateSlotForDiagnostics`, and `resetToSystem()` must restore the system clock (pinned in Task 3).
4. **Slot notification diagnostic evaluation distinguishing `Posted` vs. `SuppressedEntriesExist`:** Calling `CheckInReminderReceiver.evaluateSlotForDiagnostics` for a slot with 0 entries on the evaluated date must post the notification and return `SlotEvaluationResult.Posted`, while calling it when that slot already has entries on that date must return `SlotEvaluationResult.SuppressedEntriesExist(count)` without posting a notification (pinned in Task 3).
5. **5-tap Build ID unlock gesture and Fake Clock reset on disabling Developer Mode:** Tapping the Build ID footer 4 times keeps `devModeEnabled == false`; the 5th tap enables and persists `devModeEnabled = true`; and disabling Developer Mode resets `AdjustableClock` back to the real system clock so a user cannot remain stuck on a fake date after closing Developer Mode (pinned in Task 4).

## File Structure

```text
/
├── docs/superpowers/
│   ├── specs/2026-11-13-energy-accounting-v3-polish-and-devmode-design.md
│   └── plans/2026-11-13-energy-accounting-v3-polish-and-devmode.md
└── app/
    ├── translations.json                                              (Create: single EN + IT translation catalog)
    ├── build.gradle.kts                                               (Modify: add syncTranslations task wired to preBuild)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml                                    (Modify: add USE_EXACT_ALARM + FileProvider)
        │   ├── res/
        │   │   ├── values-v27/themes.xml                              (Create: shortEdges display cutout mode)
        │   │   ├── xml/file_paths.xml                                 (Create: FileProvider cache-path for shared PDFs)
        │   │   ├── values/strings.xml                                 (Generated by syncTranslations)
        │   │   └── values-it/strings.xml                              (Generated by syncTranslations)
        │   └── kotlin/com/empathie/energyaccounting/
        │       ├── MainActivity.kt                                    (Modify: provide LocalAppHaptics, wire AdjustableClock)
        │       ├── EnergyAccountingApp.kt                             (Modify: expose AdjustableClock singleton)
        │       ├── domain/
        │       │   ├── model/
        │       │   │   └── PeriodInsights.kt                          (Modify: add EnergyChartPoint + chartPoints)
        │       │   └── service/
        │       │       └── InsightsCalculator.kt                      (Modify: compute single-day slot points & multi-day daily points)
        │       ├── data/
        │       │   ├── settings/
        │       │   │   └── AppSettings.kt                             (Modify: add vibrationEnabled + devModeEnabled)
        │       │   └── diagnostics/
        │       │       └── AdjustableClock.kt                         (Create: mutable Clock with fake date/time override)
        │       ├── reminder/
        │       │   ├── ReminderScheduler.kt                           (Modify: add scheduleTestAlarmInSeconds + canScheduleExactAlarms status)
        │       │   └── CheckInReminderReceiver.kt                     (Modify: add evaluateSlotForDiagnostics + SlotEvaluationResult + EXTRA_FORCE_POST)
        │       └── ui/
        │           ├── common/
        │           │   ├── AppHaptics.kt                              (Create: LocalAppHaptics wrapper)
        │           │   └── EnergyDirectionAndValueSelector.kt         (Create: shared Design A full-width slider + stepper header)
        │           ├── daily/
        │           │   ├── DailyAccountViewModel.kt                   (Modify: read today from clock dynamically)
        │           │   ├── DailyAccountScreen.kt                      (Modify: remove duplicate statusBars inset, zero inner Scaffold insets, use clock-based today)
        │           │   └── AddEditEntrySheet.kt                       (Modify: LazyRow for TimeSlot & Dimension, use shared EnergyDirectionAndValueSelector)
        │           ├── insights/
        │           │   ├── InsightsScreen.kt                          (Modify: remove duplicate statusBars inset, LazyRow for period chips, add EnergyTrendChartCard)
        │           │   └── EnergyTrendChart.kt                        (Create: Canvas diverging Gain/Spend bars + Net line chart)
        │           └── manage/
        │               ├── ManageExportViewModel.kt                   (Modify: vibration toggle, dev mode 5-tap unlock, fake clock controls, slot evaluation, PDF file share helper)
        │               └── ManageExportScreen.kt                      (Modify: remove duplicate statusBars inset, shared EnergyDirectionAndValueSelector + LazyRow in TemplateEditDialog, vibration switch, Share PDF button, 5-tap Build ID footer + Developer Mode card)
        └── test/kotlin/com/empathie/energyaccounting/
            ├── domain/
            │   └── InsightsCalculatorTest.kt                          (Modify: test single-day & multi-day chartPoints)
            ├── data/
            │   ├── TranslationCatalogTest.kt                          (Create: verify translations.json completeness & XML escaping)
            │   └── AppSettingsTest.kt                                 (Modify: test vibrationEnabled & devModeEnabled)
            ├── reminder/
            │   └── CheckInReminderReceiverTest.kt                     (Modify: test evaluateSlotForDiagnostics Posted vs Suppressed + AdjustableClock)
            └── ui/
                └── ManageExportViewModelTest.kt                       (Modify: test 5-tap Dev Mode unlock, fake clock reset on disable, and slot evaluation)
```

---

### Task 1: Single-File Localization (`app/translations.json` + Gradle `syncTranslations`) and Top Blank-Space / Cutout Fix

**Files:**
- Create: `app/translations.json`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/res/values-v27/themes.xml`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/ui/daily/DailyAccountScreen.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/ui/insights/InsightsScreen.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/ui/manage/ManageExportScreen.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/data/TranslationCatalogTest.kt`

**Interfaces:**
- Consumes: Existing string keys in `res/values/strings.xml` and `res/values-it/strings.xml`.
- Produces:
  - `app/translations.json` containing every existing and new v3 string key with `"en"` and `"it"` values side-by-side (including new keys: `vibration_feedback`, `energy_trend_chart`, `share_pdf`, `save_pdf`, `dev_mode_title`, `dev_mode_enabled_toast`, `dev_mode_disable`, `fake_clock_enable`, `fake_clock_reset`, `dev_evaluate_slot`, `dev_fire_alarm_5s`, `dev_alarm_scheduled_5s`, `dev_status_notifications`, `dev_status_exact_alarms`, `dev_grant_exact_alarm`, `dev_result_posted`, `dev_result_suppressed`, `dev_result_blocked`, `slot_short_early`, `slot_short_late`, `slot_short_afternoon`, `slot_short_evening`).
  - Gradle task `syncTranslations` in `app/build.gradle.kts` (bound to `preBuild`) that reads `app/translations.json`, validates every entry has `"en"` and `"it"`, writes `app/src/main/res/values/strings.xml` and `app/src/main/res/values-it/strings.xml` with proper Android XML escaping, and copies `app/translations.json` to `app/src/main/assets/translations.json`.
  - Single-inset layout across `DailyAccountScreen` (`Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0))`, no `statusBars` padding in `DateBar`), `InsightsScreen` (no `statusBars` padding), and `ManageExportScreen` (no `statusBars` padding).

- [ ] **Step 1: Write the failing test in `app/src/test/kotlin/com/empathie/energyaccounting/data/TranslationCatalogTest.kt` (Review Focus #1)**

```kotlin
package com.empathie.energyaccounting.data

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.empathie.energyaccounting.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class TranslationCatalogTest {

    @Test
    fun `translations_json asset contains non-blank en and it strings for every key and generates valid Android resources`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rawJson = context.assets.open("translations.json").bufferedReader().use { it.readText() }
        val root = Json.parseToJsonElement(rawJson).jsonObject
        assertTrue("Expected at least 85 translation keys", root.size >= 85)

        root.forEach { (key, element) ->
            val obj = element.jsonObject
            val en = obj["en"]?.jsonPrimitive?.content
            val it = obj["it"]?.jsonPrimitive?.content
            assertFalse("Missing 'en' for key '$key'", en.isNullOrBlank())
            assertFalse("Missing 'it' for key '$key'", it.isNullOrBlank())
        }

        // Verify special characters (&, ', quotes) round-trip through Android resources in both locales
        val enConfig = Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        val itConfig = Configuration(context.resources.configuration).apply { setLocale(Locale.ITALIAN) }
        val enCtx = context.createConfigurationContext(enConfig)
        val itCtx = context.createConfigurationContext(itConfig)

        assertEquals("Manage & Export", enCtx.getString(R.string.tab_manage))
        assertEquals("Update default template for \"sveglia\"", enCtx.getString(R.string.update_default_template, "sveglia"))
        assertTrue(itCtx.getString(R.string.reminder_permission_rationale).contains("all'app"))
        assertEquals("Condividi PDF", itCtx.getString(R.string.share_pdf))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.data.TranslationCatalogTest"`
Expected: FAIL (`translations.json` asset and `R.string.share_pdf` do not exist).

- [ ] **Step 3: Create `app/translations.json`, add `syncTranslations` task in `app/build.gradle.kts`, create `values-v27/themes.xml`, and remove duplicate `WindowInsets.statusBars` from `DailyAccountScreen.kt`, `InsightsScreen.kt`, and `ManageExportScreen.kt`**

In `app/build.gradle.kts`, register `tasks.register("syncTranslations")` using `groovy.json.JsonSlurper` to parse `app/translations.json`, escape XML (`&` → `&amp;`, `<` → `&lt;`, `>` → `&gt;`, `'` → `\'`, `"` → `\"`), write `src/main/res/values/strings.xml` and `src/main/res/values-it/strings.xml`, copy `app/translations.json` to `src/main/assets/translations.json`, and wire `tasks.named("preBuild") { dependsOn("syncTranslations") }`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.data.TranslationCatalogTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/translations.json app/build.gradle.kts app/src/main/assets/translations.json app/src/main/res app/src/main/kotlin/com/empathie/energyaccounting/ui app/src/test/kotlin/com/empathie/energyaccounting/data/TranslationCatalogTest.kt
git commit -m "feat(i18n,ui): add single-file translations.json generator and remove duplicate top status-bar insets"
```

---

### Task 2: Insights Tab Graph (`Graph A`: Gain/Spend Bars + Net Trend Line) and Horizontal Period Chips

**Files:**
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/domain/model/PeriodInsights.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/domain/service/InsightsCalculator.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/ui/insights/EnergyTrendChart.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/ui/insights/InsightsScreen.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/domain/InsightsCalculatorTest.kt`

**Interfaces:**
- Consumes: `Entry`, `TimeSlot`, `Direction`, `EnergyDimension` from `domain/model`.
- Produces:
  - `data class EnergyChartPoint(val label: String, val timeSlot: TimeSlot? = null, val date: LocalDate? = null, val gain: Int, val spend: Int, val netBalance: Int)`
  - `PeriodInsights(..., val chartPoints: List<EnergyChartPoint>)`:
    - When `startDate == endDate`: 4 points (one per `TimeSlot` in order `EARLY_MORNING..EVENING`, with `timeSlot` set and `label = timeSlot.name`).
    - When `startDate < endDate`: one point for every calendar day `d` from `startDate` through `endDate` inclusive (`date = d`, `label = d.format(DateTimeFormatter.ofPattern("dd/MM"))`), including days with `0` gain and `0` spend.
  - `@Composable fun EnergyTrendChartCard(insights: PeriodInsights)` in `EnergyTrendChart.kt`.

- [ ] **Step 1: Write failing tests in `InsightsCalculatorTest.kt` (Review Focus #2)**

```kotlin
@Test
fun `single-day period produces 4 chronological TimeSlot chartPoints`() {
    val day = LocalDate.of(2026, 11, 13)
    val entries = listOf(
        Entry(1L, day, TimeSlot.EARLY_MORNING, "Wake up", null, Direction.SPEND, 15, cognitive, 1L),
        Entry(2L, day, TimeSlot.LATE_MORNING, "Coffee", null, Direction.GAIN, 20, sensory, 2L)
    )
    val insights = InsightsCalculator.calculate(day, day, entries, dimensions)
    assertEquals(4, insights.chartPoints.size)
    assertEquals(TimeSlot.EARLY_MORNING, insights.chartPoints[0].timeSlot)
    assertEquals(0, insights.chartPoints[0].gain)
    assertEquals(15, insights.chartPoints[0].spend)
    assertEquals(-15, insights.chartPoints[0].netBalance)
    assertEquals(TimeSlot.LATE_MORNING, insights.chartPoints[1].timeSlot)
    assertEquals(20, insights.chartPoints[1].gain)
    assertEquals(0, insights.chartPoints[1].spend)
    assertEquals(20, insights.chartPoints[1].netBalance)
}

@Test
fun `multi-day period produces one chartPoint per calendar day including unlogged days`() {
    val d1 = LocalDate.of(2026, 11, 11)
    val d3 = LocalDate.of(2026, 11, 13)
    val insights = InsightsCalculator.calculate(
        d1, d3,
        listOf(
            Entry(1L, d1, TimeSlot.EARLY_MORNING, "A", null, Direction.SPEND, 30, cognitive, 1L),
            Entry(2L, d3, TimeSlot.EVENING, "B", null, Direction.GAIN, 40, sensory, 2L)
        ),
        dimensions
    )
    assertEquals(3, insights.chartPoints.size)
    assertEquals("11/11", insights.chartPoints[0].label)
    assertEquals(-30, insights.chartPoints[0].netBalance)
    assertEquals("12/11", insights.chartPoints[1].label)
    assertEquals(0, insights.chartPoints[1].gain)
    assertEquals(0, insights.chartPoints[1].spend)
    assertEquals(0, insights.chartPoints[1].netBalance)
    assertEquals("13/11", insights.chartPoints[2].label)
    assertEquals(40, insights.chartPoints[2].netBalance)
}
```

- [ ] **Step 2: Run `InsightsCalculatorTest` to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.domain.InsightsCalculatorTest"`
Expected: FAIL (`chartPoints` unresolved).

- [ ] **Step 3: Implement `EnergyChartPoint` in `PeriodInsights.kt`, `InsightsCalculator.kt`, `EnergyTrendChart.kt`, and update `InsightsScreen.kt`**

In `InsightsScreen.kt`, put all 4 `InsightsPeriod` filter chips (`TODAY`, `LAST_7_DAYS`, `LAST_30_DAYS`, `CUSTOM`) in a single `LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp))`, and render `EnergyTrendChartCard(insights)` right below `PeriodSummaryCard(insights)`.

- [ ] **Step 4: Run `InsightsCalculatorTest` to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.domain.InsightsCalculatorTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/empathie/energyaccounting/domain app/src/main/kotlin/com/empathie/energyaccounting/ui/insights app/src/test/kotlin/com/empathie/energyaccounting/domain/InsightsCalculatorTest.kt
git commit -m "feat(insights): add Gain/Spend + Net trend chart and horizontal period chip row"
```

---

### Task 3: `AdjustableClock`, Exact Alarm Permission, and Slot Notification Diagnostic Evaluator

**Files:**
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/data/diagnostics/AdjustableClock.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/reminder/ReminderScheduler.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/reminder/CheckInReminderReceiver.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/EnergyAccountingApp.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/reminder/CheckInReminderReceiverTest.kt`

**Interfaces:**
- Consumes: `EnergyAccountingDao`, `TimeSlot`, `CheckInReminder`.
- Produces:
  - `data class FakeClockState(val isEnabled: Boolean, val currentDate: LocalDate, val currentTime: LocalTime)`
  - `class AdjustableClock(private val systemClock: Clock = Clock.systemDefaultZone()) : Clock()` with `val state: StateFlow<FakeClockState>`, `fun setFakeDateTime(date: LocalDate, time: LocalTime)`, `fun advanceMinutes(minutes: Long)`, and `fun resetToSystem()`
  - `sealed interface SlotEvaluationResult { data class Posted(val date: LocalDate, val timeSlot: TimeSlot) : SlotEvaluationResult; data class SuppressedEntriesExist(val date: LocalDate, val timeSlot: TimeSlot, val entryCount: Int) : SlotEvaluationResult; data object BlockedNotificationsDisabled : SlotEvaluationResult }`
  - `CheckInReminderReceiver.evaluateSlotForDiagnostics(context: Context, dao: EnergyAccountingDao, date: LocalDate, timeSlot: TimeSlot): SlotEvaluationResult`
  - `ReminderScheduler.scheduleTestAlarmInSeconds(timeSlot: TimeSlot, seconds: Long = 5, forcePost: Boolean = true)` and `ReminderScheduler.canScheduleExactAlarms(): Boolean`

- [ ] **Step 1: Write failing tests in `CheckInReminderReceiverTest.kt` (Review Focus #3 and #4)**

```kotlin
@Test
fun `AdjustableClock overrides date and time when set and restores system clock on reset`() {
    val baseClock = Clock.fixed(Instant.parse("2026-11-13T08:00:00Z"), ZoneId.of("UTC"))
    val adjustable = com.empathie.energyaccounting.data.diagnostics.AdjustableClock(baseClock)
    assertFalse(adjustable.state.value.isEnabled)
    assertEquals(LocalDate.of(2026, 11, 13), LocalDate.now(adjustable))

    adjustable.setFakeDateTime(LocalDate.of(2026, 11, 20), java.time.LocalTime.of(9, 29))
    assertTrue(adjustable.state.value.isEnabled)
    assertEquals(LocalDate.of(2026, 11, 20), LocalDate.now(adjustable))
    assertEquals(java.time.LocalTime.of(9, 29), java.time.LocalTime.now(adjustable))

    adjustable.advanceMinutes(1)
    assertEquals(java.time.LocalTime.of(9, 30), java.time.LocalTime.now(adjustable))

    adjustable.resetToSystem()
    assertFalse(adjustable.state.value.isEnabled)
    assertEquals(LocalDate.of(2026, 11, 13), LocalDate.now(adjustable))
}

@Test
fun `evaluateSlotForDiagnostics returns Posted when slot is empty and SuppressedEntriesExist when slot has entries`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val date = LocalDate.of(2026, 11, 13)

    val firstEval = CheckInReminderReceiver.evaluateSlotForDiagnostics(
        context, db.dao(), date, TimeSlot.EARLY_MORNING
    )
    assertEquals(
        SlotEvaluationResult.Posted(date, TimeSlot.EARLY_MORNING),
        firstEval
    )

    val dim = db.dao().observeAllDimensions().first().first()
    db.dao().insertEntry(
        EntryEntity(
            dateIso = date.toString(),
            timeSlot = TimeSlot.EARLY_MORNING.name,
            activity = "ansia per multa treno",
            situationalNote = null,
            direction = Direction.SPEND.name,
            energyValue = 15,
            dimensionId = dim.id,
            createdAtEpochMillis = 100L
        )
    )

    val secondEval = CheckInReminderReceiver.evaluateSlotForDiagnostics(
        context, db.dao(), date, TimeSlot.EARLY_MORNING
    )
    assertEquals(
        SlotEvaluationResult.SuppressedEntriesExist(date, TimeSlot.EARLY_MORNING, entryCount = 1),
        secondEval
    )
}
```

- [ ] **Step 2: Run `CheckInReminderReceiverTest` to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.reminder.CheckInReminderReceiverTest"`
Expected: FAIL (`AdjustableClock`, `evaluateSlotForDiagnostics`, and `SlotEvaluationResult` unresolved).

- [ ] **Step 3: Implement `AdjustableClock`, update `ReminderScheduler` & `CheckInReminderReceiver`, add `USE_EXACT_ALARM` in `AndroidManifest.xml`, and expose `clock: AdjustableClock` on `EnergyAccountingApp`**

In `CheckInReminderReceiver.onReceive`, read `today = LocalDate.now((context.applicationContext as? EnergyAccountingApp)?.clock ?: Clock.systemDefaultZone())` and support `EXTRA_FORCE_POST` for the 5-second OS alarm test.

- [ ] **Step 4: Run `CheckInReminderReceiverTest` to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.reminder.CheckInReminderReceiverTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/kotlin/com/empathie/energyaccounting/EnergyAccountingApp.kt app/src/main/kotlin/com/empathie/energyaccounting/data/diagnostics/AdjustableClock.kt app/src/main/kotlin/com/empathie/energyaccounting/reminder app/src/test/kotlin/com/empathie/energyaccounting/reminder/CheckInReminderReceiverTest.kt
git commit -m "feat(reminder,diagnostics): add AdjustableClock, USE_EXACT_ALARM, and slot notification diagnostic evaluator"
```

---

### Task 4: Shared `EnergyDirectionAndValueSelector` (`Design A`), Horizontal `LazyRow` Pickers, Vibration Feedback, Developer Mode UI, and One-Tap PDF Share

**Files:**
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/ui/common/AppHaptics.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/ui/common/EnergyDirectionAndValueSelector.kt`
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/data/settings/AppSettings.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/ui/daily/DailyAccountViewModel.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/ui/daily/DailyAccountScreen.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/ui/daily/AddEditEntrySheet.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/ui/manage/ManageExportViewModel.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/ui/manage/ManageExportScreen.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/MainActivity.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/data/AppSettingsTest.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/ui/ManageExportViewModelTest.kt`

**Interfaces:**
- Consumes: `AdjustableClock`, `SlotEvaluationResult`, `EnergyTrendChartCard`, `AppSettings`.
- Produces:
  - `AppSettings.vibrationEnabled(): Boolean`, `setVibrationEnabled(Boolean)`, `devModeEnabled(): Boolean`, `setDevModeEnabled(Boolean)`
  - `@Composable fun EnergyDirectionAndValueSelector(direction: Direction, energyValue: Int, onDirectionChange: (Direction) -> Unit, onEnergyValueChange: (Int) -> Unit)` used in both `AddEditEntrySheet` and `TemplateEditDialog`.
  - `ManageExportViewModel`:
    - `val vibrationEnabled: StateFlow<Boolean>`, `fun setVibrationEnabled(Boolean)`
    - `val devModeEnabled: StateFlow<Boolean>`, `fun onBuildFooterTapped(): Boolean`, `fun disableDevMode()`
    - `val fakeClockState: StateFlow<FakeClockState>`, `fun setFakeDateTime(LocalDate, LocalTime)`, `fun advanceFakeClockMinutes(Long)`, `fun resetFakeClock()`
    - `val lastSlotEvaluation: StateFlow<SlotEvaluationResult?>`, `suspend fun evaluateSlotAtCurrentClock(context: Context, timeSlot: TimeSlot): SlotEvaluationResult`, `fun scheduleTestAlarmIn5Seconds(timeSlot: TimeSlot)`

- [ ] **Step 1: Write failing tests in `AppSettingsTest.kt` and `ManageExportViewModelTest.kt` (Review Focus #5)**

```kotlin
// In AppSettingsTest.kt:
@Test
fun `vibrationEnabled defaults to true and devModeEnabled defaults to false`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val settings = AppSettings(context)
    assertTrue(settings.vibrationEnabled())
    assertFalse(settings.devModeEnabled())

    settings.setVibrationEnabled(false)
    settings.setDevModeEnabled(true)
    assertFalse(settings.vibrationEnabled())
    assertTrue(settings.devModeEnabled())
}
```

```kotlin
// In ManageExportViewModelTest.kt:
@Test
fun `tapping build footer 5 times unlocks devMode and disabling devMode resets fake clock`() = runTest {
    val clock = com.empathie.energyaccounting.data.diagnostics.AdjustableClock()
    val vm = viewModel(EnergyAccountingRepository(db1), clock)
    assertFalse(vm.devModeEnabled.value)

    repeat(4) {
        assertFalse(vm.onBuildFooterTapped())
        assertFalse(vm.devModeEnabled.value)
    }
    assertTrue(vm.onBuildFooterTapped())
    assertTrue(vm.devModeEnabled.value)

    vm.setFakeDateTime(LocalDate.of(2026, 11, 25), java.time.LocalTime.of(9, 29))
    assertTrue(vm.fakeClockState.value.isEnabled)

    vm.disableDevMode()
    assertFalse(vm.devModeEnabled.value)
    assertFalse(vm.fakeClockState.value.isEnabled)
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.empathie.energyaccounting.data.settings.AppSettingsTest" --tests "com.empathie.energyaccounting.ui.ManageExportViewModelTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `AppHaptics`, `EnergyDirectionAndValueSelector`, `LazyRow` pickers, `FileProvider` PDF sharing, and Developer Mode in `ManageExportViewModel` & `ManageExportScreen`**

- Wire `EnergyDirectionAndValueSelector` and `LazyRow` dimension/time-slot selectors into both `AddEditEntrySheet.kt` and `TemplateEditDialog` (`ManageExportScreen.kt`).
- Wire `LocalAppHaptics` in `MainActivity.kt` and trigger `haptics.tick()` on buttons, chips, slider steps, and tabs.
- Register `FileProvider` in `AndroidManifest.xml` with `res/xml/file_paths.xml`, and add the **Share PDF** button in `PdfRangeDialog`.
- Move **Export Debug Log** into the hidden **Developer Mode** card in `ManageExportScreen.kt` alongside the Fake Clock controls and Slot Alarm Evaluator.

- [ ] **Step 4: Run full unit test suite and assemble debug APK**

Run: `./gradlew clean testDebugUnitTest assembleDebug`
Expected: `BUILD SUCCESSFUL`, all tests passing, and `app/build/outputs/apk/debug/app-debug.apk` generated.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat(ui,devmode): add Design A value selector, horizontal LazyRows, haptics, PDF share, and hidden Developer Mode"
```
