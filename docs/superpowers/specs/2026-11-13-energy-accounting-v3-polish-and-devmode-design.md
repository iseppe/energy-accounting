# Empathie Energy Accounting — Round 3 UI Layout, Single-File i18n, Haptics, Graph, Dev Mode & PDF Share Spec

## 1. Intent and Why

Following real-device testing of v0.2.0 (`Screenshots 20261113-183651`, `183655`, `183711`, `210021`), eight concrete improvements are needed:

1. **Eliminate the top blank space (~100dp) across all tabs**: `MainActivity`'s outer `Scaffold` already applies `ScaffoldDefaults.contentWindowInsets` (which includes `WindowInsets.statusBars`). Adding `.windowInsetsPadding(WindowInsets.statusBars)` inside `DateBar`, `InsightsScreen`, and `ManageExportScreen` (plus the nested `Scaffold` in `DailyAccountScreen`) double-applied the status-bar inset, while `themes.xml` lacked `android:windowLayoutInDisplayCutoutMode="shortEdges"`.
2. **Single-file localization management**: Editing user-facing strings across separate Android XML files (`values/strings.xml` and `values-it/strings.xml`) is tedious. Consolidating all English (`"en"`) and Italian (`"it"`) strings side-by-side in a single `translations.json` source-of-truth file (with an automatic Gradle generator that emits `values/strings.xml` and `values-it/strings.xml` at build time) lets the developer edit every translation in one place while keeping full native `@string/` and `Context.getString(...)` compatibility.
3. **Consistent horizontal swipe (`LazyRow`) for chip selectors**:
   - In `InsightsScreen`, `Custom range` was placed in a second row below `Today`, `Last 7 days`, and `Last 30 days`. All 4 period chips must sit in a single horizontal `LazyRow`.
   - In `AddEditEntrySheet` and `TemplateEditDialog`, **Time Slot** and **Energy Dimension** used wrapping `FlowRow`s (`Evening` and `Emotional` wrapped onto a second line). Both must use single-line horizontal `LazyRow`s matching **Pinned templates**.
4. **Full-Width Slider + Stepper Header (`Design A`) in both Add/Edit Entry and Manage Template Dialog**:
   - Placing `[-5]`, `Slider`, `[+5]`, and `-30` on a single horizontal row squished the slider track to ~130dp. Moving the slider onto its own full-width row below a `[-5]  −30  [+5]` stepper header (and sharing that exact component with `TemplateEditDialog` in `ManageExportScreen`) makes both dialogs spacious and consistent.
5. **Vibration / Haptic Feedback on Button Press**:
   - Users benefit from subtle tactile confirmation when tapping buttons, chips, slider steps, and navigation tabs, with a toggle switch in **Manage & Export** to enable or disable vibration feedback.
6. **Insights Tab Graph (`Graph A`: Diverging Gain/Spend Bars + Net Balance Trend Line)**:
   - Visualizing energy flow over time makes patterns immediately obvious: a zero-baseline chart plotting `Gain (+)` bars above `0` (sage), `Spend (−)` bars below `0` (amber), and a connected `Net Balance` trend line + points (`#94B8FF` periwinkle)—by **Time Slot** when viewing a single day (`Today`), and by **Day (`dd/MM`)** when viewing multi-day ranges.
7. **Hidden Developer Mode (Fake Clock, Slot Notification Simulator, Exact Alarm Helper & Debug Log Export)**:
   - The 09:30 AM reminder did not show during morning testing because:
     (a) `Early Morning` already had an entry logged on `13/11/2026`, causing smart suppression to skip the notification silently, and
     (b) on Android 14+ (`targetSdk = 35`), `SCHEDULE_EXACT_ALARM` is denied by default on install, causing `ReminderScheduler` to fall back to inexact `setAndAllowWhileIdle` alarms unless `USE_EXACT_ALARM` is declared or exact alarm permission is granted.
   - Tapping the `Build: ...` footer 5 times unlocks a hidden **Developer Mode** card housing:
     - **Export Debug Log** (moved here from the main Export card).
     - **Fake Clock (`AdjustableClock`)** to override the app's current `LocalDate` and `LocalTime` (with quick slot presets `09:29`, `12:29`, `17:29`, `20:59`, `+1 min`, and `Reset to System Clock`).
     - **Slot Notification Evaluator** that runs the real `CheckInReminderReceiver` check against the Fake Clock date/time and displays the exact diagnostic reason (`POSTED` vs `SUPPRESSED: N entries already logged in <slot> on <date>`), plus a **Schedule Real OS Alarm in 5s** button.
8. **Quick Share Practitioner PDF to WhatsApp / Other Apps**:
   - In `PdfRangeDialog`, alongside saving the PDF to storage, provide a **Share PDF (`Condividi PDF`)** button that writes the generated PDF into `cacheDir/reports/` and opens the Android Share Sheet (`FileProvider` + `Intent.ACTION_SEND` with `application/pdf`) so it can be sent to WhatsApp in one tap.

---

## 2. Goals, Non-Goals, and Anti-Goals

### Goals
- **Zero top blank space**: `AppShell`'s outer `Scaffold` owns system-bar insets; inner screens and `DailyAccountScreen`'s inner `Scaffold` (`contentWindowInsets = WindowInsets(0, 0, 0, 0)`) never re-apply `WindowInsets.statusBars`.
- **Single-file translation editing**: `app/translations.json` holds every string key with `"en"` and `"it"` values side-by-side; a Gradle task (`syncTranslations`) generates `values/strings.xml` and `values-it/strings.xml` automatically before resource compilation and fails the build if any key is missing a language.
- **Horizontal swipe chip rows everywhere**: Insights period selector, Add/Edit Entry Time Slot, Add/Edit Entry Energy Dimension, and Template Edit Dialog Energy Dimension all use `LazyRow`.
- **Shared `EnergyDirectionAndValueSelector` (`Design A`)**: Used in both `AddEditEntrySheet` and `TemplateEditDialog`.
- **Configurable Haptic Feedback**: Enabled by default in `AppSettings`, toggleable in `ManageExportScreen`, and triggered on interactive taps/steps via `LocalAppHaptics`.
- **Adaptive Insights Graph (`Graph A`)**: Plots 4 Time Slots when `startDate == endDate` (e.g. `Today`), and daily buckets (`startDate..endDate`) when viewing multi-day ranges.
- **Reliable Exact Alarms + Hidden Developer Mode**: Add `USE_EXACT_ALARM` permission in `AndroidManifest.xml` for calendar/reminder exact timing, unlock Developer Mode by tapping the Build ID footer 5 times, move `Export Debug Log` inside it, and provide `AdjustableClock` + slot alarm evaluation diagnostics.
- **One-Tap PDF Share**: Share generated PDF reports via `FileProvider` (`${applicationId}.fileprovider`) and `Intent.ACTION_SEND`.

### Non-Goals
- External network sync or remote translation hosting (`translations.json` is 100% local in the repository).
- Chart zooming/panning libraries (the Insights chart is drawn with Jetpack Compose `Canvas` and zero third-party chart dependencies).

---

## 3. Detailed Design

### 3.1 Top Inset & Cutout Fix
- Create `app/src/main/res/values-v27/themes.xml` with:
  ```xml
  <resources>
      <style name="Theme.EnergyAccounting" parent="android:Theme.Material.NoActionBar">
          <item name="android:windowLayoutInDisplayCutoutMode">shortEdges</item>
      </style>
  </resources>
  ```
- In `DailyAccountScreen.kt`:
  - Pass `contentWindowInsets = WindowInsets(0, 0, 0, 0)` to `Scaffold(...)`.
  - Remove `.windowInsetsPadding(WindowInsets.statusBars)` from `DateBar`.
- In `InsightsScreen.kt` and `ManageExportScreen.kt`:
  - Remove `.windowInsetsPadding(WindowInsets.statusBars)` from the root `LazyColumn` (since `MainActivity.kt`'s `AppShell` `Scaffold` already applies `Modifier.padding(innerPadding)` which includes top status-bar and bottom navigation-bar insets).

### 3.2 Single-File Localization Catalog (`app/translations.json`)
- File location: `app/translations.json`, structured as an ordered JSON object where each key maps to `{ "en": "...", "it": "..." }`:
  ```json
  {
    "app_name": { "en": "Energy Accounting", "it": "Energy Accounting" },
    "tab_daily_account": { "en": "Daily Account", "it": "Bilancio giornaliero" }
  }
  ```
- Gradle task `syncTranslations` in `app/build.gradle.kts` (wired as a dependency of `preBuild`):
  - Parses `app/translations.json`, validates that every key has non-empty `"en"` and `"it"` strings, escapes XML special characters (`&`, `<`, `>`, `'`, `"`), and writes `app/src/main/res/values/strings.xml` and `app/src/main/res/values-it/strings.xml`.
  - Also copies `app/translations.json` into `app/src/main/assets/translations.json` so it can be inspected or unit-tested directly.

### 3.3 Shared `EnergyDirectionAndValueSelector` (`Design A`) & Horizontal `LazyRow` Pickers
- Create `app/src/main/kotlin/com/empathie/energyaccounting/ui/common/EnergyDirectionAndValueSelector.kt`:
  - **Row 1**: Two equal-width `SelectablePill` buttons for `Spend (drain)` and `Gain (recharge)`.
  - **Row 2 (Stepper Header)**:
    - Left: `FilledTonalButton` (`-5`)
    - Center (`Modifier.weight(1f)`): Centered bold `titleLarge` text showing `EnergyFormatter.formatSignedValue(direction, energyValue)` colored by direction accent (`spend` amber or `gain` sage).
    - Right: `FilledTonalButton` (`+5`)
  - **Row 3 (Full-Width Slider)**:
    - `Slider(value = energyValue.coerceIn(5, 100).toFloat(), onValueChange = { ... }, valueRange = 5f..100f, steps = 18, modifier = Modifier.fillMaxWidth())`.
- Reuse `EnergyDirectionAndValueSelector` in both `AddEditEntrySheet.kt` and `TemplateEditDialog` (`ManageExportScreen.kt`).
- Convert **Time Slot** (`AddEditEntrySheet.kt`), **Energy Dimension** (`AddEditEntrySheet.kt` and `TemplateEditDialog`), and **Insights Period Filter** (`InsightsScreen.kt`) to `LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp))`.

### 3.4 Vibration / Haptic Feedback (`AppHaptics`)
- `AppSettings.kt`:
  - `fun vibrationEnabled(): Boolean` (default `true`, key `"vibration_enabled"`).
  - `fun setVibrationEnabled(enabled: Boolean)`.
- `app/src/main/kotlin/com/empathie/energyaccounting/ui/common/AppHaptics.kt`:
  - `class AppHaptics(private val enabled: Boolean, private val perform: () -> Unit) { fun tick() { if (enabled) perform() } }`
  - `val LocalAppHaptics = staticCompositionLocalOf { AppHaptics(enabled = false) {} }`
  - Provided in `MainActivity` using `LocalView.current.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)`.
  - Triggered in `SelectablePill`, `DimensionPill`, `EnergyDirectionAndValueSelector` (`-5`, `+5`, and when slider value crosses a 5-unit step), `NavigationBarItem`, and `DateBar` navigation buttons.
  - Toggle switch added inside the **Appearance** card of `ManageExportScreen` (`Vibration feedback` / `Feedback con vibrazione`).

### 3.5 Insights Tab Graph (`Graph A`: Gain/Spend Bars + Net Trend Line)
- Domain model in `PeriodInsights.kt` & `InsightsCalculator.kt`:
  - `data class EnergyChartPoint(val label: String, val gain: Int, val spend: Int, val netBalance: Int)`
  - `PeriodInsights` adds `val chartPoints: List<EnergyChartPoint>`:
    - When `startDate == endDate` (single day, e.g. `Today`): 4 `EnergyChartPoint`s corresponding to the 4 `TimeSlot`s (`Early`, `Late`, `Aftern.`, `Evening` — localized short labels or slot ordinals), with each slot's `totalGain`, `totalSpend`, and `netBalance`.
    - When `startDate < endDate` (multi-day range): one `EnergyChartPoint` per calendar day from `startDate` to `endDate` (or grouped if `> 31` days), with label `dd/MM` (`DateTimeFormatter.ofPattern("dd/MM")`), day `totalGain`, `totalSpend`, and `netBalance`.
- UI component `EnergyTrendChartCard` in `InsightsScreen.kt`:
  - Placed right below `PeriodSummaryCard`.
  - Legend row showing colored dots for `Gain (+)`, `Spend (−)`, and `Net Balance`.
  - Compose `Canvas` (`height = 170.dp`, `fillMaxWidth()`):
    - Horizontal dashed zero line at vertical midpoint `centerY`.
    - Max scale `maxMagnitude = maxOf(10, chartPoints.maxOfOrNull { maxOf(it.gain, it.spend, kotlin.math.abs(it.netBalance)) } ?: 10)`.
    - For each point $i$:
      - Draws a rounded sage bar from `centerY - (gain / maxMagnitude) * halfHeight` to `centerY`.
      - Draws a rounded amber bar from `centerY` to `centerY + (spend / maxMagnitude) * halfHeight`.
      - Computes net point `(x_i, centerY - (netBalance / maxMagnitude) * halfHeight)`.
    - Connects consecutive net points with a `2.5.dp` periwinkle (`colors.net`) stroke `Path` and draws `3.5.dp` filled circles at each point, with X-axis labels along the bottom.

### 3.6 Hidden Developer Mode, `AdjustableClock` & Exact Alarms
- `AndroidManifest.xml`:
  - Add `<uses-permission android:name="android.permission.USE_EXACT_ALARM" />` alongside `SCHEDULE_EXACT_ALARM` so Android 14+ grants exact alarm capability for user-scheduled daily check-in reminders.
- `app/src/main/kotlin/com/empathie/energyaccounting/data/diagnostics/AdjustableClock.kt`:
  - Implements `java.time.Clock` and wraps either `Clock.systemDefaultZone()` or a user-configured fake `ZonedDateTime` (persisted or held in process memory via `AppSettings` / `AdjustableClockHolder`).
  - Exposes `val state: StateFlow<FakeClockState>` (`isEnabled: Boolean`, `currentDate: LocalDate`, `currentTime: LocalTime`), `setFakeDateTime(date: LocalDate, time: LocalTime)`, `advanceMinutes(minutes: Long)`, and `resetToSystem()`.
  - Injected into `DailyAccountViewModel`, `InsightsViewModel`, and `CheckInReminderReceiver`.
- **Evaluating Slot Notifications in Developer Mode**:
  - `CheckInReminderReceiver.evaluateSlotForDiagnostics(context: Context, dao: EnergyAccountingDao, date: LocalDate, timeSlot: TimeSlot): SlotEvaluationResult`:
    - Returns a structured result:
      - `POSTED(date, timeSlot)` (and posts the actual notification!) when `count == 0` and notifications are enabled.
      - `SUPPRESSED_ENTRIES_EXIST(date, timeSlot, entryCount)` when `count > 0` (without posting, clearly explaining why!).
      - `BLOCKED_PERMISSION_DISABLED` when OS notifications are disabled.
  - `ReminderScheduler.scheduleTestAlarmInSeconds(timeSlot: TimeSlot, seconds: Long = 5)`:
    - Schedules a real `AlarmManager` wakeup `seconds` from now targeting `CheckInReminderReceiver` (with an extra `EXTRA_FORCE_POST = true` option or normal evaluation) so the user can lock the screen and test real OS wakeup delivery.
- **Developer Mode UI in `ManageExportScreen`**:
  - `AppSettings`: `devModeEnabled(): Boolean` / `setDevModeEnabled(Boolean)`.
  - Tapping the `Build: v0.2.0-...` footer **5 times** enables Developer Mode and shows a Toast (`"Developer mode enabled"` / `"Modalità sviluppatore attivata"`).
  - When `devModeEnabled == true`, a **Developer Mode** card appears above the Build ID footer containing:
    1. **Disable Developer Mode** switch/button.
    2. **Fake Clock Controls**: Enable switch, current effective date/time readout (`dd/MM/yyyy HH:mm`), Date & Time pickers, quick preset chips (`09:29`, `12:29`, `17:29`, `20:59`, `+1m`, `Reset`).
    3. **Slot Alarm Evaluator & 5s OS Wakeup Test**:
       - Displays live status of `Notifications` (`OK` / `Blocked`) and `Exact Alarms` (`OK` / `Inexact fallback`).
       - Slot selector (`Early Morning`, `Late Morning`, `Afternoon`, `Evening`) + **"Evaluate slot now (Fake Clock)"** button + **"Fire OS alarm in 5s"** button, displaying the last diagnostic result inline.
    4. **Export Debug Log** button (moved here from the Export card).

### 3.7 Quick Share Practitioner PDF (`FileProvider` + `ACTION_SEND`)
- Add `app/src/main/res/xml/file_paths.xml`:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <paths>
      <cache-path name="shared_reports" path="reports/" />
  </paths>
  ```
- Register `androidx.core.content.FileProvider` in `AndroidManifest.xml` with `android:authorities="${applicationId}.fileprovider"`.
- In `PdfRangeDialog` (`ManageExportScreen.kt`):
  - Provide two buttons:
    - **Share PDF (`Condividi PDF`)**: Calls `viewModel.buildPdf(start, end, PdfLabels.from(context))`, writes the bytes to `File(context.cacheDir, "reports/energy-accounting-${start}_${end}.pdf")`, builds a `content://` URI with `FileProvider.getUriForFile`, and starts `Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, ...)`.
    - **Save PDF (`Salva PDF`)**: Launches the existing `CreateDocument` flow to save to local storage.
