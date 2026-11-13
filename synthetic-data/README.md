# Synthetic demo data

A ready-to-restore backup for showcasing the app with realistic data.

**File:** `energy-accounting-demo-backup.json` (schemaVersion 2, ~61 KB)

## What it contains

| Item | Value |
|---|---|
| Date range | 15/10/2026 – 14/11/2026 |
| Active days | 23 (plus deliberate empty days for the 30-day chart) |
| Entries | 213 |
| Activity templates | 14 (5 pinned) |
| Energy dimensions | 5 seeded + `Executive Function` and `Interoception` |
| Today (13/11/2026) | 10 entries, Gain +140, Spend −175, Net −35 |
| Future entries | 2 on 14/11/2026 (for the future-date warning) |
| Reminders | All four enabled: 09:30, 12:30, 17:30, 21:00 |

The data mixes recurring routines, work/school, sensory and social load, recovery
activities, situational notes (including Italian accents such as *Caffè* and
*Perché*), every time slot, and both directions so the Daily Account, Insights,
PDF/CSV exports, and the trend chart all have something meaningful to show.

## Import on the phone

1. Connect the phone with USB debugging, then from the repository root:

   ```bash
   adb push synthetic-data/energy-accounting-demo-backup.json /sdcard/Download/
   ```

   Or copy the file to the phone's Downloads folder any other way.

2. In the app: **Manage & Export → Restore JSON Backup → Replace all local data**.
3. Pick `energy-accounting-demo-backup.json` from Downloads.

> **Warning:** *Replace all local data* deletes the current entries, templates, and
> custom dimensions before importing. If you want to keep your existing data, use
> **Export JSON Backup** first. The seeded five dimensions are kept.
>
> *Merge without duplicates* is also supported, but it appends the demo entries to
> whatever you already have, so the totals will mix.

## Things to try

- **Daily Account** → 13/11/2026: all four slots populated, net −35.
- Tap **‹ / ›** to browse to empty days; the date stays centred.
- **Calendar** → days with data have a sage/amber dot; today has the outlined circle.
- **Insights → Today / Last 7 days / Last 30 days**: summary grid, trend chart,
  spend/gain by dimension, and by time slot (averages only for multi-day ranges).
- **Add Entry**: pinned templates (5 + `...`), horizontal Time Slot and Dimension
  rows, full-width slider/stepper.
- **Manage & Export → Export Practitioner PDF Report → Share PDF** to WhatsApp.
- **Developer Mode**: tap the `Build: ...` footer 5 times, enable the fake clock,
  set a date/time just before a reminder slot, and use *Evaluate slot now*.
- **Future date warning**: open 14/11/2026 and tap `+`.

## Regenerate

```bash
python3 synthetic-data/generate_demo_backup.py
```

The generator uses a fixed random seed, so the output is reproducible.
