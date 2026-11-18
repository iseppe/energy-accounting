# Daily Account, Insights and Templates — Implementation Plan

> **For the implementer:** Use the `executing-plans` skill for direct execution, or `subagent-driven-development` only if the user explicitly chooses delegation. Work through the independently testable feature slices below using test-driven development.

**Goal:** Let users reorder daily entries, navigate adjacent Insights periods and manage a large template list without clutter.

**Architecture:** Keep Jetpack Compose and the existing Room/repository/view-model architecture. Store explicit entry positions within each date and Time Slot, migrate old data without changing its prior ordering, and include positions in versioned backups. Insights navigation changes the selected date range; template expansion and search are presentation state only.

**Tech stack:** Kotlin, Jetpack Compose/Material 3, Room, Kotlin coroutines and serialization, JUnit/Robolectric, and Android Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-11-17-daily-insights-templates-design.md`.

## Global constraints and review focus

- Preserve the one-Energy-Dimension model, the app's warm/dark theme, existing horizontal chip rows and the single English/Italian translation catalog.
- Version the Room database from 2 to 3; register both 1→2 and 2→3 migrations. Do not use a destructive upgrade.
- Version JSON backups from 2 to 3 while accepting versions 1 and 2; retain the existing merge deduplication semantics.
- User decision: keep slot-sensitive merge identity even though merging a backup made before a move can re-import the entry, because older backups cannot reliably distinguish that case from two genuinely separate matching entries.
- Existing entries with equal creation times must retain deterministic ID order during migration. Test this in Task 1.
- Editing an already reordered entry must not reset its position; test same-slot and changed-slot edits in Task 1.
- Restore/merge may contain same-slot positions that collide with local ones. Keep local order and append nonduplicate imported entries in backup order; test both modes in Task 2.
- A drag can start on an entry while the list scrolls, end over an empty slot, or cancel. Test gesture continuity, autoscroll, invalid drops and cancellation in Task 3.
- Preset navigation must work across month/year boundaries and future dates without clearing a selected dimension. Test it in Task 4.

## File map

- `app/src/main/kotlin/com/empathie/energyaccounting/data/local/entity/EntryEntity.kt`, `.../local/EnergyAccountingDatabase.kt`, `.../local/DatabaseProvider.kt`, `.../local/EnergyAccountingDao.kt`: persisted order and safe migration.
- `app/src/main/kotlin/com/empathie/energyaccounting/domain/model/Entry.kt`, `.../DailyAccount.kt`, `.../data/repository/EnergyAccountingRepository.kt`: order mapping, ledger sorting, atomic moves and save/edit rules.
- `app/src/main/kotlin/com/empathie/energyaccounting/data/export/BackupSerializer.kt`, `.../CsvExporter.kt`, `.../ui/insights/InsightsViewModel.kt`: backup and consistent display/export order. PDF already consumes `DailyAccount` ledgers.
- `app/src/main/kotlin/com/empathie/energyaccounting/ui/daily/DailyAccountScreen.kt`, `.../DailyAccountViewModel.kt`: drag presentation and move actions.
- `app/src/main/kotlin/com/empathie/energyaccounting/ui/insights/InsightsScreen.kt`, `.../InsightsViewModel.kt`: period navigation.
- `app/src/main/kotlin/com/empathie/energyaccounting/ui/manage/ManageExportScreen.kt`: collapsible searchable template section.
- `app/translations.json`: all new EN/IT user-facing labels and accessibility descriptions; do not edit generated resource files by hand.
- Tests: existing `EnergyAccountingDaoTest.kt`, `EnergyAccountingRepositoryTest.kt`, `ExportAndBackupTest.kt`, `DailyAccountViewModelTest.kt`; add `ui/InsightsViewModelTest.kt` and Compose UI tests under `app/src/androidTest/kotlin/com/empathie/energyaccounting/ui/`.

## Task 1 — Persist entry ordering and atomic moves

**Files:** EntryEntity, Entry, EnergyAccountingDatabase, DatabaseProvider, EnergyAccountingDao, EnergyAccountingRepository, DailyAccount; tests: `data/EnergyAccountingDaoTest.kt`, `data/EnergyAccountingRepositoryTest.kt`, `ui/DailyAccountViewModelTest.kt`.

**Interfaces produced:** `Entry.positionInSlot: Long`; `suspend fun EnergyAccountingRepository.moveEntry(date: LocalDate, entryId: Long, targetSlot: TimeSlot, targetIndex: Int): Boolean`. `targetIndex` is the index in the destination **after removing the dragged entry**; valid values are `0..destinationSize`. Return false without writes for missing/wrong-date IDs or invalid indices; return true for a valid no-op without rewriting rows.

- [ ] Add failing migration tests: create v2 data with same-slot entries `(id,time)=(10,200),(11,100),(12,100)` and a separate date/slot; migrate through Room and assert displayed order `[11,12,10]`, all data retained and independent slot positions begin at zero. Also test the complete 1→2→3 migration path.
- [ ] Add failing repository tests using activities `A,B,C` and distinct creation timestamps. Move C to index 0 and assert `[C,A,B]`; move A to an empty Afternoon slot at index 0 and assert original `[C,B]`, destination `[A]`, timestamp and template usage/defaults unchanged, and net subtotals updated. Test invalid ID/date/index and a no-op drop.
- [ ] Add tests proving a new entry appends, a same-slot edit preserves position, and changing a slot in the editor appends to the destination without modifying creation time. Verify tied positions have deterministic fallback ordering.
- [ ] Run `./gradlew testDebugUnitTest --tests '*EnergyAccountingDaoTest' --tests '*EnergyAccountingRepositoryTest' --tests '*DailyAccountViewModelTest'`; confirm the new assertions fail before implementation.
- [ ] Add a non-null `positionInSlot` integer column with a Room-compatible default and migration `MIGRATION_2_3`. Initialize positions per `(dateIso,timeSlot)` by `(createdAtEpochMillis,id)` using a correlated count compatible with the minimum Android SQLite version, not a window function. Register the migration and map the property through `Entry` and `copy`/equality.
- [ ] Implement a single Room transaction that loads the selected date, removes the entry, validates the post-removal target index, inserts it into the target slot and rewrites contiguous source/destination positions. Do not call template-touching save logic for a move. Keep per-slot positions deterministic for all reads; preserve old timestamps.
- [ ] Update `saveEntry` to append new entries and preserve or reassign position according to the specification; update `DailyAccount.fromEntries` to sort by position, creation time, then ID. Run the same tests and confirm PASS.

## Task 2 — Backups and consistent ordering

**Files:** `data/export/BackupSerializer.kt`, `data/export/CsvExporter.kt`, `ui/insights/InsightsViewModel.kt`; tests: `data/ExportAndBackupTest.kt` and new ordering tests as needed.

**Consumes:** `Entry.positionInSlot` and the save/order rules from Task 1. **Produces:** backup schema version 3 and consistent date → slot → position ordering.

- [ ] Add failing tests: export/replacement-restore entries ordered `[C,A,B]` even though their timestamps sort `[A,B,C]`; verify order and original timestamps after restore. Restore old v1 and v2 samples without position fields and assert historical timestamp/file-order behavior, including timestamp ties.
- [ ] Add a merge test with local `[L1,L2]` and imported `[B,L1,A]` in the same slot, where imported L1 has the same existing `naturalKey`; assert the result is `[L1,L2,B,A]` with unique contiguous positions. Verify a missing or negative version 3 position is rejected before writes.
- [ ] Add CSV and filtered-Insights ordering assertions for `[C,A,B]`; verify PDF receives ledger order through `DailyAccount` without changing its displayed columns.
- [ ] Run `./gradlew testDebugUnitTest --tests '*ExportAndBackupTest'` and relevant new ordering tests; confirm the new tests fail.
- [ ] Add `positionInSlot` to version 3 entry records; export positions, accept and validate version 3, and keep explicit decoding/conversion for v1/v2. Retain the source schema version through validation so legacy conversion does not accidentally require v3 position fields. For old records derive per-slot order by creation time with backup-list order as the tie-breaker. For merge keep existing positions/order and append nonduplicates in imported order, using the existing natural key; replacement restores the saved order.
- [ ] Update CSV and filtered Insights sorting to `(date, timeSlot.ordinal, positionInSlot, createdAtEpochMillis, id)`. Run the targeted tests and confirm PASS.

## Task 3 — Daily Account drag and accessibility

**Files:** `ui/daily/DailyAccountScreen.kt`, `ui/daily/DailyAccountViewModel.kt`, `app/translations.json`; create `app/src/androidTest/kotlin/com/empathie/energyaccounting/ui/DailyAccountDragTest.kt`; update Gradle test dependencies/source configuration as needed.

**Consumes:** repository `moveEntry` from Task 1. **Produces:** `suspend fun DailyAccountViewModel.moveEntry(entryId: Long, targetSlot: TimeSlot, targetIndex: Int): Boolean`, using the currently selected date.

- [ ] Add a view-model test verifying the selected date is passed to the move operation and a failed move leaves entries unchanged.
- [ ] Configure Compose instrumentation dependencies and a test runner, then confirm a minimal smoke test builds before adding feature assertions. Verify the target is an emulator or an isolated application ID, not `com.empathie.energyaccounting`; if an isolated app with that ID is already installed, ask before replacing it.
- [ ] Add Compose UI tests with isolated seeded entries A/B/C: a normal tap opens A's editor; long-press and drag C above A yields `[C,A,B]`; dragging A into an empty Afternoon yields A there; dragging toward the scroll edge scrolls; cancel/outside drop leaves saved order unchanged. Also test the accessibility move actions.
- [ ] Run the targeted view-model and Compose tests before implementation; confirm the new tests fail. Run instrumentation only on an emulator or a separately installed application ID with test data; never target the personal installation.
- [ ] Implement long-press drag with stable item IDs, drop indicators and slot/row hit testing. Coordinate hit tests with the visible LazyColumn, auto-scroll while hovering at an edge, accept empty slots and make cancellation a UI-only reset. Preserve the existing tap-to-edit behavior and theme.
- [ ] Add localized accessible move actions using the same view-model method. Ensure English and Italian labels are in `app/translations.json`. Run the unit and UI tests and confirm PASS.

## Task 4 — Navigate Insights ranges

**Files:** `ui/insights/InsightsViewModel.kt`, `ui/insights/InsightsScreen.kt`, `app/translations.json`; create `app/src/test/kotlin/com/empathie/energyaccounting/ui/InsightsViewModelTest.kt` and `app/src/androidTest/kotlin/com/empathie/energyaccounting/ui/InsightsNavigationTest.kt`.

**Interfaces produced:** `fun InsightsViewModel.goToPreviousPeriod()` and `fun InsightsViewModel.goToNextPeriod()`. For CUSTOM both are no-ops; for the presets both dates shift by the preset's 1/7/30-day width.

- [ ] With clock fixed at 2026-11-17, add failing tests: Today previous/next yields 2026-11-16 and 2026-11-18; Last 7 Days starts 11–17 November and previous gives 4–10 November; Last 30 Days starts 19 October–17 November and next gives 18 November–17 December. Assert repeated moves, year/month crossings, selected dimension retained, preset reselection resets to today and CUSTOM never shifts.
- [ ] Add UI tests that arrows and dates appear for all three presets and are absent for Custom; date label, totals and chart reflect the selected range, including after rapid consecutive arrow taps. Run targeted tests to establish failures.
- [ ] Implement the view-model actions and accessible arrow controls near the visible range, preserving the existing horizontal period chips and haptic conventions. Add EN/IT labels to the catalog. Run targeted tests and confirm PASS.

## Task 5 — Collapse and search Activity Templates

**Files:** `ui/manage/ManageExportScreen.kt`, `app/translations.json`; create `app/src/androidTest/kotlin/com/empathie/energyaccounting/ui/ActivityTemplatesSectionTest.kt`.

**Interfaces:** No repository changes. Use existing `templates`, `toggleTemplatePin`, `saveTemplate` and `deleteTemplate` callbacks; search performs case-insensitive name matching without mutating templates or changing their existing order.

- [ ] Add failing UI tests with many templates: initial section is collapsed and other settings reachable; expand, search mixed-case text, see matching rows only, star/unstar directly, open overflow and edit/delete; create remains available in the header. Check empty search results, collapsing/reopening and many rows do not prevent reaching later settings.
- [ ] Implement the expandable header, search field and row overflow menu. Preserve existing behavior, pinned-first order, theme and haptics; use stable keys/lazy list items for large expanded lists rather than one giant eagerly composed item. Add EN/IT strings and accessibility descriptions to `app/translations.json`.
- [ ] Run targeted Compose tests and translation catalog unit tests; confirm PASS.

## Task 6 — Final verification and safe device review

- [ ] Run `env JAVA_HOME=/var/home/linuxbrew/.linuxbrew/opt/openjdk@21/libexec ANDROID_HOME=/var/home/linuxbrew/.linuxbrew/share/android-commandlinetools ./gradlew testDebugUnitTest assembleDebug`; record actual results. Run Compose UI instrumentation on an emulator or isolated test app ID.
- [ ] Verify on isolated data: reorder within a slot and into an empty/distant slot; cancel; restart and restore a backup; navigate past/future periods and Custom; search/star/edit/delete templates; inspect EN/IT and both themes. Use Mobile MCP for navigation and accessibility inspection; use continuous-touch instrumentation or a manual drag for drag verification.
- [ ] Confirm `git diff` includes only intended source/test/translation changes, no unintended generated artifacts or user data, and no new destructive migration. The tracked Android string and asset mirrors must match `app/translations.json`. Report any test/device limitations accurately. Do not commit unless the user requests it.
