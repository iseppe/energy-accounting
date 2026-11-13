# Energy Accounting

A personal stress-management and energy-monitoring context based on Maja Toudal and Tony Attwood's Energy Accounting methodology for Autism and AuDHD. It tracks how daily activities drain or recharge energy across distinct areas of functioning.

## Language

### Core Ledger

**Daily Account**:
The zero-based energy ledger for a single calendar date (`dd/MM/yyyy`), aggregating all Entries recorded on that day into Total Gain, Total Spend, and Net Balance.
_Avoid_: Day log, daily sheet, journal, battery

**Time Slot**:
One of four fixed chronological divisions of a Daily Account: `Early Morning`, `Late Morning`, `Afternoon`, or `Evening`.
_Avoid_: Period, shift, timestamp

**Entry**:
A single atomic energy record logged within a Time Slot on a Daily Account, consisting of an Activity, an optional Situational Note, a Direction, an Energy Value, and one Energy Dimension.
_Avoid_: Transaction, event, item, row

**Direction**:
Whether an Entry represents an energy withdrawal (`Spend (-)`) or an energy deposit (`Gain (+)`).
_Avoid_: Polarity, sign, type

**Energy Value**:
The magnitude of an Entry's energy impact, expressed as an integer from `1` to `100` (adjusted in increments of 5 via slider or stepper buttons).
_Avoid_: Score, points, weight, amount

**Net Balance**:
The sum of all `Gain (+)` Energy Values minus all `Spend (-)` Energy Values within a Time Slot or Daily Account, starting from `0` each day.
_Avoid_: Remaining battery, score, total

### Activities & Context

**Activity**:
The concise name of what took place in an Entry (e.g., `"Morning routine"`, `"Quiet time in room"`).
_Avoid_: Title, description, task

**Situational Note**:
An optional short note on a specific Entry capturing one-off context or deviations from routine (e.g., `"Delayed train, loud crowd"`).
_Avoid_: Comment, memo, sub-activity

**Activity Template**:
A reusable blueprint learned from previously logged Activities (or pinned by the user) that pre-fills default Direction, Energy Value, and Energy Dimension without being overwritten by one-off daily adjustments.
_Avoid_: Preset, shortcut, favorite

### Dimensions & Reminders

**Energy Dimension**:
The single domain of human functioning impacted by an Entry—defaulting to `Physical`, `Cognitive`, `Sensory`, `Social`, and `Emotional`, and extensible with user-defined dimensions. Each Entry attributes 100% of its Energy Value to its selected Energy Dimension.
_Avoid_: Category, tag, spend type, primary dimension, secondary dimension

**Check-in Reminder**:
A scheduled, purely local device notification tied to a Time Slot that prompts the user to log Entries and automatically suppresses itself if that Time Slot already has Entries logged for the day.
_Avoid_: Push notification, streak alert
