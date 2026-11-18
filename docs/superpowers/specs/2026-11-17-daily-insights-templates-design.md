# Daily Account, Insights and Templates — Design

## Intent

Make daily Entries easier to reorganize, let people browse adjacent Insights ranges, and keep Manage & Export usable when many Activity Templates accumulate. The existing Android app, its single-Energy-Dimension model, and its visual and localization conventions remain the foundation.

## Daily Account

- Long-press an Entry and drag it to another position in the same Time Slot or any position in another Time Slot on the **same date**. Other entries shift around the insertion point; a normal tap continues to open the existing editor.
- Display a clear insertion target; support empty slots and automatic scrolling during a long drag. Cancelling a drag, releasing outside a valid target or encountering an invalid target leaves the ledger unchanged.
- Persist the moved entry's slot and order. Moving must not change its creation timestamp, activity, note, direction, value, dimension, or template defaults/usage; slot subtotals and Insights update from the new slot.
- Add accessible move actions so users who cannot perform a drag can move entries within and across slots. Use the same move operation as drag and localized labels.
- New entries append to their selected slot. Editing an entry without changing its slot retains its position; changing the slot in the existing editor appends it to the destination.
- Preserve existing entry order on upgrade, including timestamp ties. Save order in backups, accept old backups, and show the same ordering in the daily account, filtered Insights entries, CSV and PDF.
- Keep the existing slot-sensitive backup-merge identity. An older backup can re-import an entry that was moved to a different slot; this avoids silently discarding genuinely distinct entries that happen to have identical timestamps and details.

## Insights

- Add previous/next arrows for Today, Last 7 Days and Last 30 Days, shifting the whole inclusive date range by exactly 1, 7 or 30 calendar days. Permit past and future ranges.
- Display the actual resulting dates. Keep the chosen dimension filter when moving between ranges. Selecting a preset returns that preset to its current range ending today.
- Custom range remains unchanged, with no navigation arrows.

## Manage & Export

- Activity Templates is collapsed by default and expands in place. Keep the existing create action in the header.
- The expanded section has name search and a directly accessible star/unstar control per template. Move the less-used edit and delete controls into a per-template overflow menu. Preserve existing pin/usage ordering and existing create, edit, delete and pin behavior.
- The expanded list must remain usable with many templates, and the rest of Manage & Export remains reachable. Use English and Italian translations from `app/translations.json`.

## Safety and non-goals

- Do not move entries between dates; do not alter the energy model, template defaults, reminder settings or export content except ordering and backup position metadata.
- Test using isolated data. Do **not** overwrite the installed personal app, modify its database, uninstall it, or run instrumentation against it without the user's explicit permission. An isolated app ID or emulator is required for mutable device testing.
- Mobile MCP was available and detected a connected Pixel 7. Its exposed long-press and swipe are separate actions and do not establish one continuous drag; use Compose touch-injection tests or a manual drag for that interaction rather than claiming MCP alone verified it.
