# Empathie Energy Accounting — Round 2 UX, Domain Simplification & Diagnostics Spec

## 1. Intent and Why

Following real-device testing of the initial v0.1.0 build (`Screenshots 20261113-000012`, `000858`, `000908`, `002406`), several concrete UX friction points, layout issues, and domain complexities surfaced:

1. **Secondary Energy Dimensions (`2°`) created cognitive & layout friction**: Having a two-tier `1° Primary` + `2° Secondary` dimension system on a single chip row made it impossible to switch the primary dimension intuitively, caused chip widths to expand when `"1° "` / `"2° "` prefixes were added (making chips jump across lines), and cluttered both the Daily Account rows and the Insights screen. Simplifying to a **single Energy Dimension per Entry** (`GLOSSARY.md` & `ADR-0003`) makes selection a 1-tap radio choice with fixed-width chips.
2. **Sheet & Control Stability**: The Add/Edit Entry `ModalBottomSheet` drifted downward into a half-expanded state during recompositions; the `-5` / `+5` buttons were out of sync with the step-of-10 slider; the `+100` / `-100` label squeezed the slider width; and pinned/recent templates stacked vertically instead of scrolling horizontally.
3. **Top Bar, Calendar, and Future-Date Safety**: The top bar lacked status-bar inset padding and spacing above the summary card; empty days showed `+0` and `-0`; the Calendar History sheet did not visibly highlight the currently selected date; and logging on a future date gave no warning.
4. **Insights, Localization, PDF Table, and Diagnostics**: Insights defaulted to 7 days instead of `Today`; Manage & Export needed a `"Light"` theme label, an in-app Italian/English language switcher, a **Test Notification** button, a **Debug Log** export button, and a **Build Identifier** footer; and the Practitioner PDF Report needed a true bordered table for the date-range entries with localized dimension names.

---

## 2. Goals, Non-Goals, and Anti-Goals

### Goals
- **Single Energy Dimension per Entry**: Every `Entry` and `ActivityTemplate` references exactly one `EnergyDimension`. Tapping any dimension chip in Add/Edit Entry immediately selects that dimension (1-tap radio selection), without `1°`/`2°` prefixes or chip width jumps.
- **Stable, predictable Add/Edit Entry Bottom Sheet**:
  - Opens fully expanded (`skipPartiallyExpanded = true`) so tapping chips or templates never causes the sheet to drift downward.
  - Sheet title displays the target `TimeSlot`: `"Add entry for <Time Slot>"` (`"Aggiungi voce per <Fascia oraria>"`), or `"Edit entry for <Time Slot>"`.
  - **Horizontal Pinned/Saved Templates bar**: Single-line `LazyRow` showing at most **5 templates** (pinned first, then most-used). When more templates exist (or as the 6th item), a **`"..."` chip** opens an **All Templates Picker Dialog** where the user can pick any saved template.
  - **Consistent 5-unit Energy Value slider & stepper (`5..100`)**: Both the `Slider` (`valueRange = 5f..100f`, `steps = 18`) and the `-5` / `+5` buttons operate in 5-unit increments (`5, 10, 15, ..., 95, 100`) so every button press visibly moves the slider thumb, and the signed readout has a fixed minimum width (`52.dp`) so `±100` never shrinks the slider.
- **Polished Top Bar, Calendar Highlight, and Future-Date Consent**:
  - Proper `WindowInsets.statusBars` padding and `12.dp` spacing between `DateBar` and `DailySummaryCard`.
  - `DailySummaryCard` formats zero Gain/Spend as `0` instead of `+0` / `-0`.
  - `CalendarHistorySheet` highlights `selectedDate` with a **filled primary circle** and `today` (when not selected) with a **1.dp primary outline circle**.
  - Tapping `+` (FAB or Time Slot `+`) when `selectedDate > today` shows a **Future Date Warning Dialog** asking the user to confirm or go back before opening the Add Entry sheet.
- **Insights Starting with Today**:
  - Add `InsightsPeriod.TODAY` (`startDate = today, endDate = today`) as the leftmost chip (`Today`, `Last 7 days`, `Last 30 days`, `Custom range`) and make `TODAY` the default selected period.
  - Remove the `"Secondary dimension occurrences"` card; tapping any dimension row or filter chip filters entries for that dimension.
- **Predictive Back Gesture**:
  - Enable `android:enableOnBackInvokedCallback="true"` in `AndroidManifest.xml` and add `BackHandler(enabled = selectedTab != 0)` in `MainActivity` so back gestures on `Insights` or `Manage & Export` return to `Daily Account`.
- **Manage & Export Upgrades**:
  - Rename theme option `"Warm Light"` / `"Chiaro caldo"` to **`"Light"` / `"Chiaro"`**.
  - Add **Language Switcher** (`System`, `English`, `Italiano`) persisted in `AppSettings` and applied immediately to the Activity context.
  - Add **"Test Notification" button** in the Check-in Reminders card that immediately fires a sample check-in notification (requesting `POST_NOTIFICATIONS` on Android 13+ if needed).
  - Add **"Export Debug Log" button** that writes a diagnostic `.log` file (build identifier, device/SDK info, DB row counts, reminder schedules, internal `AppLogger` ring buffer, and recent process `logcat -d`) via SAF.
  - Display a **Build Identifier** footer (`v0.2.0 (<git-sha> · <build-time>)`) at the bottom of `ManageExportScreen`.
- **Table-Formatted Practitioner PDF Report**:
  - Draw a **bordered 4-column table** (`Date / Time Slot`, `Activity & Situational Note`, `Dimension`, `Value`) with shaded header and daily subtotal rows for the date range entries, followed by aligned summary blocks for **Period Summary**, **Spend / Gain by Dimension**, and **By Time Slot**, using localized dimension names.

### Non-Goals
- Multi-dimensional weighting or secondary dimension tags (explicitly removed by `ADR-0003`).
- Remote crash reporting or network telemetry (debug logs are 100% local and exported only when the user taps "Export Debug Log").

---

## 3. Domain & Schema Changes (`GLOSSARY.md`, `ADR-0003`, Room v2)

### 3.1 Single Dimension Model
- `Entry`:
  - `id: Long = 0L`
  - `date: LocalDate`
  - `timeSlot: TimeSlot`
  - `activity: String` (trimmed, non-blank)
  - `situationalNote: String?` (trimmed, null if blank)
  - `direction: Direction`
  - `energyValue: Int` (`1..100`, default `30`)
  - `dimension: EnergyDimension` (replaces `primaryDimension` + `secondaryDimensions`)
  - `createdAtEpochMillis: Long`
- `ActivityTemplate`:
  - `id: Long = 0L`
  - `activityName: String`
  - `defaultDirection: Direction`
  - `defaultEnergyValue: Int`
  - `defaultDimension: EnergyDimension` (replaces `defaultPrimaryDimension` + `defaultSecondaryDimensions`)
  - `isPinned: Boolean`
  - `usageCount: Int`
  - `lastUsedEpochMillis: Long`
- **Database Schema (`EnergyAccountingDatabase` version 2)**:
  - Drop `EntrySecondaryDimensionCrossRef` (`entry_secondary_dimensions` table) and `ActivityTemplateEntity.defaultSecondaryDimensionIdsCsv`.
  - Rename/alias `primaryDimensionId` → `dimensionId` on `EntryEntity` and `defaultPrimaryDimensionId` → `defaultDimensionId` on `ActivityTemplateEntity` (with a clean Room migration `MIGRATION_1_2` + `.fallbackToDestructiveMigrationOnDowngrade()` so existing local data on the user's phone is preserved seamlessly during upgrade!).
  - Enforce case-insensitive uniqueness when creating custom `EnergyDimension` names in `EnergyAccountingRepository.createCustomDimension`.

### 3.2 Energy Value Step-of-5 Rule
- Both `AddEditEntrySheet` and `TemplateEditDialog` use `valueRange = 5f..100f`, `steps = 18` (20 discrete stops: `5, 10, 15, ..., 100`), with `onValueChange = { ((it / 5f).roundToInt() * 5).coerceIn(5, 100) }`.
- The `-5` button decrements by `5` down to a minimum of `5` (`(value - 5).coerceAtLeast(5)`), and `+5` increments by `5` up to `100` (`(value + 5).coerceAtMost(100)`), keeping the slider thumb and buttons 100% synchronized.
- The signed value readout (`-30`, `+100`) uses `Modifier.widthIn(min = 52.dp)` and `TextAlign.End` so 3-digit values (`+100` / `-100`) never alter the width available to the `Slider`.

---

## 4. UI, Localization, PDF & Diagnostics Specifications

### 4.1 Top Bar, Insets & Future Date Warning
- `MainActivity`: Calls `enableEdgeToEdge()` and updates `WindowCompat.getInsetsController(window, view)` so `isAppearanceLightStatusBars` and `isAppearanceLightNavigationBars` match `!LocalEmpathieColors.current.isDark`.
- `DateBar`, `InsightsScreen`, and `ManageExportScreen` apply `Modifier.windowInsetsPadding(WindowInsets.statusBars)` so top content never collides with the status bar.
- `DailyAccountScreen`'s `LazyColumn` uses `contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp)` so `DailySummaryCard` has clean breathing room below `DateBar`.
- `EnergyFormatter.formatGainTotal(gain: Int)` returns `"0"` when `gain == 0` and `"+$gain"` when `gain > 0`; `formatSpendTotal(spend: Int)` returns `"0"` when `spend == 0` and `"-$spend"` when `spend > 0`.
- **Future Date Warning**:
  - In `DailyAccountScreen`, tapping either the main `+` FAB or a Time Slot `+` button checks `if (selectedDate.isAfter(today))`.
  - If true, it sets `pendingFutureSlot: TimeSlot?` (or a boolean flag) and displays an `AlertDialog`:
    - Title: `"Future date"` / `"Data futura"`
    - Body: `"You are adding an entry for %1$s, which is in the future. Do you want to continue?"` / `"Stai aggiungendo una voce per il %1$s, che è una data futura. Vuoi continuare?"`
    - Confirm: `"Continue"` / `"Continua"` → opens `AddEditEntrySheet` for that slot.
    - Dismiss: `"Go back"` / `"Torna indietro"` → cancels.

### 4.2 Add/Edit Entry Bottom Sheet
- Uses `rememberModalBottomSheetState(skipPartiallyExpanded = true)`.
- **Header Title**:
  - When adding: `stringResource(R.string.add_entry_for_slot, timeSlotLabel(context, draft.timeSlot))` → `"Add entry for Early Morning"` / `"Aggiungi voce per Mattina presto"`.
  - When editing: `stringResource(R.string.edit_entry_for_slot, timeSlotLabel(context, draft.timeSlot))` → `"Edit entry for Early Morning"` / `"Modifica voce per Mattina presto"`.
- **Horizontal Templates Row (`QuickTemplates`)**:
  - Uses a horizontal `LazyRow` (`horizontalArrangement = Arrangement.spacedBy(8.dp)`).
  - Displays up to **5** templates (`templates.take(5)`, which are already sorted by `isPinned DESC, usageCount DESC, lastUsedEpochMillis DESC`).
  - If `templates.size > 5`, a 6th `AssistChip(label = { Text("...") })` is appended at the end of the `LazyRow`. Tapping `"..."` opens an `AllTemplatesDialog` (scrollable list of all saved templates) where tapping any template applies it and closes the dialog.
- **Dimension Chips**:
  - Single `FlowRow` of chips showing each active dimension's localized name (with no `1°` or `2°` prefix!).
  - Tapping any chip calls `viewModel.onSelectDimension(dimension.id)`, immediately making it the selected dimension.

### 4.3 Calendar History Sheet
- Uses `rememberModalBottomSheetState(skipPartiallyExpanded = true)`.
- Receives `selectedDate: LocalDate` in addition to `month: YearMonth` and `netBalances: Map<LocalDate, Int>`.
- Each day cell renders a `36.dp` circular badge around the day number:
  - If `date == selectedDate`: **filled** circle (`MaterialTheme.colorScheme.primary`) with `onPrimary` bold text.
  - Else if `date == today`: **outlined** circle (`BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)`) with `primary` bold text.
  - Below the circle, the `5.dp` net-balance dot (`gain` sage, `spend` amber, or `net` periwinkle) remains visible.

### 4.4 Insights Tab
- `InsightsPeriod` enum order: `TODAY`, `LAST_7_DAYS`, `LAST_30_DAYS`, `CUSTOM`.
- Default initial state in `InsightsViewModel`: `InsightsPeriod.TODAY` (`startDate = today, endDate = today`), refreshing `today = LocalDate.now(clock)` when selecting relative periods.
- Displays:
  1. Period filter chips (`Today`, `Last 7 days`, `Last 30 days`, `Custom range`).
  2. `PeriodSummaryCard`.
  3. `Spend by dimension` (horizontal proportional bars; tapping a dimension bar toggles filtering entries for that dimension).
  4. `Gain by dimension` (horizontal proportional bars; tapping a dimension bar toggles filtering entries for that dimension).
  5. `By time slot` (showing Gain, Spend, Net Balance, and Average per active day).

### 4.5 Manage & Export Tab: Appearance, Language, Test Notification, PDF Table, Debug Log & Build ID
1. **Appearance & Language**:
   - Theme chips: `System`, `Dark`, `Light` (renamed from `"Warm Light"`).
   - Language chips: `System` (`AppLanguage.SYSTEM`), `English` (`AppLanguage.ENGLISH`, tag `"en"`), `Italiano` (`AppLanguage.ITALIAN`, tag `"it"`), persisted in `AppSettings`.
   - Changing `AppLanguage` updates a `LocaleHelper.wrapContext(baseContext, language)` in `MainActivity.attachBaseContext` and calls `activity.recreate()` so all resources (`strings.xml`, weekday names, PDF labels) switch immediately on every Android version from API 26 to 35.
2. **Test Notification Button**:
   - Inside the `Check-in Reminders` section card, a `FilledTonalButton` / `OutlinedButton` labeled `"Send test notification"` (`"Invia notifica di prova"`).
   - Checks `POST_NOTIFICATIONS` permission on Android 13+ (requesting it if needed), posts a live notification via `CheckInReminderReceiver.postTestNotification(context)` (which deep-links into `AddEditEntrySheet` for the current time slot when tapped), and shows a confirmation Toast if system notifications are disabled.
3. **Practitioner PDF Report Table**:
   - `PractitionerPdfGenerator` draws a **bordered A4 table** for the date range's entries:
     - Columns: `Date / Slot` (`115pt`), `Activity & Note` (`230pt`), `Dimension` (`95pt`), `Value` (`75pt`, right-aligned).
     - Shaded table header row, horizontal row dividers, vertical column borders, automatic multi-line text wrapping inside cells, and a bold daily summary row (`Gain +X | Spend -Y | Net Z`) at the end of each day.
     - Below the table (with automatic page breaks): clean boxed/aligned sections for **Period Summary**, **Spend by Dimension**, **Gain by Dimension**, and **By Time Slot**, using localized dimension names via `PdfLabels.dimensionNames`.
4. **Debug Log Export (`AppLogger` + `DebugLogExporter`)**:
   - `AppLogger`: thread-safe ring buffer (up to 500 timestamped events) recording app lifecycle, DB seeding, entry/template saves, reminder scheduling/firing, and export/restore errors.
   - `"Export Debug Log"` (`"Esporta log di debug"`) button in `ManageExportScreen` launches `CreateDocument("text/plain")` (`energy-accounting-debug-<date>.log`) and writes:
     - Build Identifier, package, versionCode/versionName, Android SDK & device model, active locale & theme.
     - Database summary (dimension count, template count, entry count, reminder schedule states).
     - `AppLogger` entries + current process `logcat -d -t 300` output.
5. **Build Identifier Footer**:
   - `app/build.gradle.kts` defines `buildConfigField("String", "BUILD_ID", "\"v0.2.0-${gitShortSha}-${buildTimeUtc}\"")`.
   - Rendered centered at the bottom of `ManageExportScreen` in `MaterialTheme.typography.labelSmall` muted text (`Build: v0.2.0-...`).
