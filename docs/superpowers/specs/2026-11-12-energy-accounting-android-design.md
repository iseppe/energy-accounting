# Empathie Energy Accounting — Android Application Design Spec

## 1. Intent and Why

**Empathie Energy Accounting** is a personal Android application grounded in Maja Toudal and Tony Attwood's *Energy Accounting: Stress Management and Mental Health Monitoring for Autism and Related Conditions*.

People with Autism and AuDHD experience both energy withdrawals (`Spend (-)`) and energy deposits (`Gain (+)`) differently from neurotypical expectations—routine transitions, sensory environments, social masking, and cognitive load carry tangible costs, while special interests, quiet sensory recovery, and predictability restore energy. Paper *Daily Energy Account Forms* work well initially, but carrying paper forms or re-writing recurring routines every day creates executive-function friction that leads to avoidance or forgotten logs.

This application provides a modern, minimal, low-friction digital **Daily Account** on Android so the user can:
1. Log entries in seconds using auto-learned and pinned **Activity Templates** without repetitive typing.
2. Separate recurring **Activity** names from one-off **Situational Notes**.
3. Track energy across multiple **Energy Dimensions** (`Physical`, `Cognitive`, `Sensory`, `Social`, `Emotional`, plus custom dimensions) without double-counting daily totals.
4. Receive gentle, purely local **Check-in Reminders** per **Time Slot** that prevent executive avoidance without nagging when a slot is already logged.
5. Review patterns across days and export a clean **Practitioner PDF Report** (modeled after the book's *Daily Energy Account Form*, e.g. for sessions with *Gruppo Empathie*) or a full local **JSON/CSV Backup**.

---

## 2. Goals, Non-Goals, and Anti-Goals

### Goals
- **Minimal-friction daily logging**: Adding a recurring entry on *Today* should take as few as 2–3 taps (tap `+`, tap a pinned **Activity Template**, optionally nudge the step-of-10 slider, tap Save).
- **Accurate multi-dimensional accounting**: Every **Entry** captures one mandatory **Primary Dimension** (carrying 100% of the **Energy Value** in proportional breakdowns) and zero or more **Secondary Dimensions** (for contextual filtering and occurrence tracking).
- **Zero-based Daily Account clarity**: Each calendar day (`dd/MM/yyyy`) starts at `0` and displays **Total Gain (`+`)**, **Total Spend (`-`)**, and **Net Balance (`Gain - Spend`)** at both the full-day and **Time Slot** levels.
- **100% offline-first & private**: All data lives in an on-device Room SQLite database with zero accounts, zero network calls, and zero Google Play Services dependencies.
- **Bilingual out of the box**: Full UI localization in **English** (default) and **Italian** (`values-it`), using `dd/MM/yyyy` date formatting everywhere.

### Non-Goals (Deferred / Out of Scope for v1)
- **iOS support**: Explicitly deferred until the personal Android application is validated in daily use (see [ADR-0001](../../adr/0001-native-android-without-play-services.md)).
- **Cloud sync or remote accounts**: Data portability is handled exclusively through local file export/import (JSON, CSV, PDF) via the Android Storage Access Framework (`ActivityResultContracts.CreateDocument` / `OpenDocument`) and Share Sheet.
- **Multi-day carry-over / "Energy Debt"**: Days do not roll over deficits into the next day's starting balance, ensuring that skipping a day of logging never corrupts future days' math.
- **Compound multi-item cluster rows**: Rather than embedding sub-equations inside a single row, compound moments are logged as separate atomic `Spend (-)` and `Gain (+)` Entries within the same **Time Slot**.

### Anti-Goals (Must Never Happen)
- **No alarmist red or deficit shaming**: Having a negative **Net Balance** or logging high `Spend (-)` values is neutral clinical observation, never an error. The UI must never use harsh error-red (`#FF0000` / Material Error token) for `Spend (-)` or negative balances.
- **No streaks, badges, or guilt notifications**: Missing a day is normal. The app must never track logging streaks, show "you broke your streak" messages, or send shaming reminders.
- **No silent template mutation**: Changing an Entry's **Energy Value** on a particularly hard or easy day must never silently overwrite the baseline default of its **Activity Template**.

---

## 3. Constraints & Technical Decisions

- **Platform**: Native Android, `minSdk = 26` (Android 8.0, enabling `java.time.LocalDate` and Notification Channels natively), `targetSdk = 35`, `compileSdk = 35`.
- **Language & UI Toolkit**: Kotlin 2.0+, Jetpack Compose with Material 3, AndroidX Navigation / single-activity architecture.
- **Persistence**: Room SQLite (`androidx.room`) with Kotlin Coroutines & `Flow`.
- **No Google Play Services**: Notifications use Android's native `AlarmManager`, `BroadcastReceiver` (including `BOOT_COMPLETED` / `TIME_SET` / `TIMEZONE_CHANGED` rescheduling), and `NotificationManagerCompat`.
- **Date Format**: Strictly `dd/MM/yyyy` (via `DateTimeFormatter.ofPattern("dd/MM/yyyy")`) across all screens, pickers, PDF reports, and CSV exports. Internal storage uses ISO-8601 `yyyy-MM-dd` strings (`LocalDate.toString()`) for lexicographical sorting and range queries in SQLite.
- **Localization**: All user-facing strings defined in `app/src/main/res/values/strings.xml` (English) and `app/src/main/res/values-it/strings.xml` (Italian).

---

## 4. Domain Model & Core Rules

Canonical terminology follows [`GLOSSARY.md`](../../../GLOSSARY.md).

### 4.1 Time Slots
Every **Daily Account** is divided into four fixed chronological **Time Slots**:
1. `EARLY_MORNING` ("Early Morning" / "Mattina presto")
2. `LATE_MORNING` ("Late Morning" / "Tarda mattina")
3. `AFTERNOON` ("Afternoon" / "Pomeriggio")
4. `EVENING` ("Evening" / "Sera")

**Smart Default Rule**:
When opening the **Add Entry** sheet:
- If the viewed date is *Today* (`LocalDate.now()`), the default selected `TimeSlot` is chosen from the device's current `LocalTime`:
  - `05:00` – `09:59` → `EARLY_MORNING`
  - `10:00` – `12:59` → `LATE_MORNING`
  - `13:00` – `17:59` → `AFTERNOON`
  - `18:00` – `04:59` → `EVENING`
- If the user taps the `+` icon on a specific **Time Slot** card header, or opens the app from a slot's **Check-in Reminder** notification, that specific `TimeSlot` is pre-selected instead.
- If viewing a past or future date (and using the global FAB), it defaults to `EARLY_MORNING` (or the last slot added to on that date).

### 4.2 Entries & Accounting Math
An **Entry** represents a single atomic energy event:
- `id`: `Long` (auto-generated primary key)
- `date`: `LocalDate` (stored as `yyyy-MM-dd`, displayed as `dd/MM/yyyy`)
- `timeSlot`: `TimeSlot`
- `activity`: `String` (trimmed, non-blank, max 120 chars)
- `situationalNote`: `String?` (trimmed, optional, max 240 chars; `null` if blank)
- `direction`: `Direction` (`SPEND` or `GAIN`)
- `energyValue`: `Int` (constrained to `1..100`)
- `primaryDimensionId`: `Long` (foreign key to `EnergyDimension`)
- `secondaryDimensionIds`: `Set<Long>` (0 or more foreign keys to `EnergyDimension`, never containing `primaryDimensionId`)
- `createdAtEpochMillis`: `Long` (used for deterministic chronological ordering within a `TimeSlot`)

**Ledger Aggregations (for any `TimeSlot`, `DailyAccount`, or Date Range)**:
- `totalGain`: $\sum \text{energyValue}$ where $\text{direction} = \text{GAIN}$ (defaults to `0`)
- `totalSpend`: $\sum \text{energyValue}$ where $\text{direction} = \text{SPEND}$ (defaults to `0`, displayed with a leading `−` sign)
- `netBalance`: $\text{totalGain} - \text{totalSpend}$ (displayed as `+N`, `−N`, or `0`)

### 4.3 Energy Dimensions (Primary vs. Secondary)
See [ADR-0002](../../adr/0002-primary-and-secondary-energy-dimensions.md).
- **Seeded Default Dimensions** (created on database initialization with stable string keys for localization):
  1. `PHYSICAL` ("Physical" / "Fisico")
  2. `COGNITIVE` ("Cognitive" / "Cognitivo")
  3. `SENSORY` ("Sensory" / "Sensoriale")
  4. `SOCIAL` ("Social" / "Sociale")
  5. `EMOTIONAL` ("Emotional" / "Emotivo")
- **Custom Dimensions**: Users can create custom dimensions (unique non-blank name, assigned a subtle palette color index).
- **Archiving Custom Dimensions**: Custom dimensions that have been used in historical Entries are soft-archived (`isArchived = true`) rather than hard-deleted so historical Daily Accounts and Insights remain intact. Unused custom dimensions can be deleted outright. Seeded default dimensions cannot be deleted.
- **Selection Mechanics in Add/Edit Entry**:
  - Displayed as a single FlowRow of filter chips.
  - If no Primary Dimension is selected, tapping any chip sets it as `primaryDimensionId` (solid filled chip prefixed with `1°`).
  - When a Primary Dimension is already selected, tapping any unselected chip toggles it in `secondaryDimensionIds` (outlined dashed chip prefixed with `2°`).
  - Tapping an active Secondary chip removes it from `secondaryDimensionIds`.
  - Tapping the active Primary chip: if at least one Secondary chip is active, the first Secondary chip is promoted to Primary; if no Secondary chip is active, the Primary chip stays selected (since every Entry must have 1 Primary Dimension). Long-pressing any Secondary chip immediately swaps it with the current Primary Dimension.

### 4.4 Activity Templates
An **Activity Template** stores reusable defaults for an **Activity**:
- `id`: `Long`
- `activityName`: `String` (case-insensitive unique index on trimmed name)
- `defaultDirection`: `Direction`
- `defaultEnergyValue`: `Int` (`1..100`)
- `defaultPrimaryDimensionId`: `Long`
- `defaultSecondaryDimensionIds`: `Set<Long>`
- `isPinned`: `Boolean`
- `usageCount`: `Int`
- `lastUsedEpochMillis`: `Long`

**Lifecycle & Isolation Rules**:
1. **Auto-Creation on First Use**: When the user saves an **Entry** whose trimmed `activity` does not match any existing `ActivityTemplate` (case-insensitive), a new `ActivityTemplate` is automatically created with that Entry's `direction`, `energyValue`, `primaryDimensionId`, and `secondaryDimensionIds` (`usageCount = 1`, `isPinned = false`).
2. **Isolation on Reuse**: When the user logs an **Entry** whose `activity` matches an existing `ActivityTemplate`—even if the user modifies the `direction`, `energyValue`, or dimensions for *today's* Entry—the existing `ActivityTemplate`'s default fields are **not** overwritten (only `usageCount` and `lastUsedEpochMillis` increment).
3. **Explicit Template Update**: When an existing template is matched in the **Add/Edit Entry** sheet and the user changes its baseline parameters (Direction, Energy Value, or Dimensions), a subtle inline checkbox/chip *"Update default template for '<Activity>'"* appears (unchecked by default). Checking it updates the template's defaults upon saving; templates can also be created, edited, pinned/unpinned, or deleted at any time in the **Manage & Export** tab.

---

## 5. User Interface & Screen Architecture

### 5.1 Sensory-Friendly Visual Language
- **Palette Tokens**:
  - `Gain (+)` accent: Calm Sage/Teal (`#5EC2A6` in Dark mode, `#2B7A65` in Warm-Light mode).
  - `Spend (-)` accent: Warm Amber/Terracotta (`#E29B61` in Dark mode, `#B8652A` in Warm-Light mode).
  - `Net Balance` accent: Soft Periwinkle/Slate (`#94B8FF` in Dark mode, `#3A5A99` in Warm-Light mode).
  - Dark Mode uses deep low-glare slate surfaces (`#131720` / `#181D28`); Light Mode uses warm paper-toned off-white (`#F7F5F0` / `#EFECE6`) to avoid harsh blue-white glare.
- **Theme Preference**: System Default, Dark, or Warm Light (configurable in **Manage & Export**).

### 5.2 Navigation Structure (3 Bottom Tabs)

#### Tab 1: Daily Account (Home)
- **Top Date Bar**:
  - `<` (Previous Day) and `>` (Next Day) icon buttons.
  - Center button showing formatted date `dd/MM/yyyy` + localized weekday name (plus a `"Today"` / `"Oggi"` badge or quick-return button when viewing another date).
  - Tapping the date opens the **Calendar History Bottom Sheet**: a monthly grid (`dd/MM/yyyy` header) where days with logged entries show a small dot colored by net balance (`Gain > Spend` = sage, `Spend > Gain` = amber, `Net == 0` = slate) so the user can jump to any date.
- **Daily Summary Card**:
  - Three side-by-side tabular-numeral pills: **Gain (`+N`)**, **Spend (`−N`)**, and **Net Balance (`+N` / `−N` / `0`)**.
- **Four Time Slot Cards** (`Early Morning`, `Late Morning`, `Afternoon`, `Evening`):
  - Header shows the localized slot name, slot net subtotal (`+N` / `−N`), and a compact `+` icon button to add an entry directly into that slot.
  - Empty slots show a quiet single-line placeholder (*"No entries"* / *"Nessuna voce"*).
  - Populated slots list their **Entries** in chronological creation order:
    - Left indicator bar colored by Direction (amber for Spend, sage for Gain).
    - Activity name, optional Situational Note in muted text, `1° Primary` badge + `2° Secondary` badges, and signed Energy Value (`−30`, `+50`).
    - Tapping an Entry opens the **Add/Edit Entry Bottom Sheet** in edit mode (with a Delete icon button and confirmation/undo snackbar).
- **Floating Action Button (`+`)**: Opens the **Add/Edit Entry Bottom Sheet** with the smart-default `TimeSlot`.

#### Add/Edit Entry Bottom Sheet
1. **Pinned & Recent Templates Row**: Horizontal scrollable chips at the top (`★ Morning routine (−30)`, etc.). Tapping one populates Activity, Direction, Energy Value, and Primary/Secondary Dimensions in one tap.
2. **Activity Field**: Outlined text field with live autocomplete dropdown matching saved `ActivityTemplate` names as the user types.
3. **Situational Note Field**: Optional single-line text field (*"Situational note (optional)"* / *"Nota situazionale (opzionale)"*).
4. **Time Slot Selector**: 4-segment pill selector (`Early Morning`, `Late Morning`, `Afternoon`, `Evening`).
5. **Direction & Energy Value (`1–100`)**:
   - 2-segment toggle: `− Spend` vs. `+ Gain`.
   - Step-of-10 slider (`10, 20, 30, 40, 50, 60, 70, 80, 90, 100`, defaulting to `30`), paired with `−5` / `+5` (or `−1` / `+1` via tap-to-edit number dialog) so both fast multiples of 10 and exact integers in `1..100` are effortless.
6. **Energy Dimensions Selector**: Single FlowRow of dimension chips implementing the `1° Primary` (first tap) and `2° Secondary` (subsequent taps / long-press to promote) mechanics.
7. **Save Bar**: Optional *"Update default template"* checkbox (only shown when modifying an existing template's baseline) + primary **Save** button.

#### Tab 2: Insights
- **Period Selector**: Segmented selector for **Last 7 Days**, **Last 30 Days**, or **Custom Range** (`dd/MM/yyyy – dd/MM/yyyy` date range picker).
- **Period Summary Totals**: Total Gain, Total Spend, Net Balance, and Average Daily Net Balance across days with entries.
- **Breakdown by Primary Energy Dimension**:
  - Horizontal proportional bars for **Spend by Primary Dimension** and **Gain by Primary Dimension** (guaranteed to sum to 100% of period Spend and Gain respectively).
  - **Secondary Dimension Occurrence Section**: Shows how many entries included each dimension as a `2° Secondary Dimension` (and allows tapping any dimension chip to filter and view all matching entries where that dimension was either Primary or Secondary).
- **Breakdown by Time Slot**:
  - Shows average and total Gain/Spend across `Early Morning`, `Late Morning`, `Afternoon`, and `Evening` so the user can spot which part of the day drains or restores the most energy.

#### Tab 3: Manage & Export
1. **Activity Templates Management**: List of saved templates with search, Pin/Unpin (`★`) toggle, Edit dialog (name, default Direction, Value, Primary/Secondary Dimensions), Create button, and Delete button.
2. **Energy Dimensions Management**: List of the 5 default dimensions + user-created custom dimensions, with an **Add Custom Dimension** dialog and Archive/Unarchive toggle for custom dimensions.
3. **Check-in Reminders (Local `AlarmManager`)**:
   - 4 independent rows—one for each **Time Slot** (`Early Morning` default `09:30`, `Late Morning` default `12:30`, `Afternoon` default `17:30`, `Evening` default `21:00`).
   - Each row has an Enable switch and a clickable `HH:mm` time picker.
   - Requests `POST_NOTIFICATIONS` permission on Android 13+ when the user enables their first reminder.
   - **Smart Suppression Rule**: When a slot's alarm fires, the `BroadcastReceiver` queries Room for the count of Entries on `LocalDate.now()` in that `TimeSlot`. If `count > 0`, no notification is posted and the alarm simply reschedules for tomorrow. If `count == 0`, it posts a calm notification whose tap intent opens `MainActivity` directly into the **Add Entry** sheet pre-set to that `TimeSlot`.
4. **Practitioner PDF Report & Data Backup**:
   - **Export Practitioner PDF Report**: User selects a date range (`dd/MM/yyyy – dd/MM/yyyy`, defaulting to the last 7 days). Generates a multi-page A4 `PdfDocument` modeled after Maja Toudal's *Daily Energy Account Form* (table columns: `Date / Time Slot`, `Activity & Situational Note`, `Dimensions (1° / 2°)`, `Value (+/-)`, with daily and period summary footers) and opens the Android Share / Save dialog.
   - **Export CSV**: Exports flat tabular rows for spreadsheet analysis (`Date (dd/MM/yyyy)`, `Time Slot`, `Activity`, `Situational Note`, `Direction`, `Signed Value`, `Primary Dimension`, `Secondary Dimensions`).
   - **Full JSON Backup & Restore**:
     - Exports a versioned JSON snapshot (`schemaVersion = 1`) containing all `EnergyDimensions`, `ActivityTemplates`, `Entries` (with secondary dimension links), and `ReminderSchedules`.
     - **Restore Policy**: Importing a JSON backup validates the schema and merges/restores inside a single database transaction (supports user choice between **Merge without duplicates** and **Replace all local data**).
5. **Appearance & Language Hint**: Theme selector (`System`, `Dark`, `Warm Light`).

---

## 6. What Is Left to the Builder

The following minor implementation details are left to the builder's engineering judgment:
- Exact Jetpack Compose animation curves and spacing constants (`dp` / `sp`), provided the screen stays compact, readable, and avoids flashy motion.
- Internal Room DAO query decomposition and Flow combinators, provided reactive UI updates are instantaneous and all domain calculations (Daily Account totals, Dimension Breakdowns, Template isolation, Reminder suppression, and Date formatting) are covered by unit tests.
- Whether to use inexact repeating alarms (`setAndAllowWhileIdle` / `setWindow`) vs. `canScheduleExactAlarms()` fallback on Android 12+, provided reminders work reliably without requiring special restricted permissions if exact alarm permission is not granted.
