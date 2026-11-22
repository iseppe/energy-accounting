<p align="center">
  <img src="docs/images/icon.png" width="88" alt="Empathie Energy Accounting icon">
</p>

# Empathie Energy Accounting

A private, offline-first Android ledger for the Energy Accounting method.

[![Download APK](https://img.shields.io/badge/Download-APK-2ea44f?logo=android&logoColor=white)](https://github.com/iseppe/energy-accounting/releases/latest)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](#install)

<p align="center">
  <img src="docs/screenshots/01-daily-account.png" width="240" alt="Daily Account">
  <img src="docs/screenshots/02-add-entry.png" width="240" alt="Add Entry">
  <img src="docs/screenshots/03-insights.png" width="240" alt="Insights">
</p>

## What it is

Empathie Energy Accounting is a personal stress-management and energy-monitoring
app based on the Energy Accounting method by **Maja Toudal and Tony Attwood**.
It helps you track how daily activities drain or recharge energy across distinct
areas of functioning, using a zero-based daily ledger: every day starts at zero
and each entry is either a **Gain (+)** deposit or a **Spend (−)** withdrawal.

**Not medical advice.** The app is a self-tracking tool, not a medical device or
a substitute for professional care.

## Features

- **Daily Account** — a zero-based energy ledger per day with Total Gain, Total
  Spend, and Net Balance, split into four Time Slots (Early Morning, Late
  Morning, Afternoon, Evening).
- **Entries** — log an Activity with an optional Situational Note, a Direction
  (Spend/Gain), an Energy Value from 1 to 100, and one Energy Dimension
  (Physical, Cognitive, Sensory, Social, Emotional, plus custom dimensions).
- **Activity Templates** — reusable blueprints that pre-fill direction, value,
  and dimension; pin your favourites for one-tap logging.
- **Check-in Reminders** — purely local notifications per Time Slot that
  automatically stay quiet when the slot already has entries.
- **Insights** — summary grid, Gain/Spend/Net trend chart, and breakdowns by
  dimension and time slot for today, the last 7 or 30 days, or a custom range.
- **Calendar history** — browse past days at a glance.
- **Export** — practitioner PDF report, CSV, and JSON backup/restore.
- **Two languages** — English and Italian.

## Install

1. Download the APK from the
   [latest release](https://github.com/iseppe/energy-accounting/releases/latest).
2. On your phone, allow installs from unknown sources (Android asks per app when
   you open the APK).
3. Open the APK and install. Requires **Android 8.0 or newer**.

In-app updates are available from v0.4.0: after installing v0.4.0 once, the app
can update itself from GitHub Releases and you need no file manager.

If you are migrating from the debug build to the release build: export a JSON
backup from the debug build, uninstall the debug build, install the release
build, and restore the JSON backup.

## Privacy

- No account, no ads, no analytics, no trackers.
- All entries stay in a local Room database on your device.
- The only network access is the optional GitHub update check (you can ignore
  update prompts; nothing is uploaded).

## Build from source

Requirements: **JDK 17+** and the **Android SDK 35**.

```bash
./gradlew assembleDebug        # build
./gradlew testDebugUnitTest    # test
```

Translations live in `app/translations.json`; edit that file rather than the
generated XML.

## License

Copyright (C) 2026 iseppe. Licensed under GPL-3.0-only. Full text in [LICENSE](LICENSE).

Credits: the launcher icon uses Lucide brain/zap glyphs (ISC license).
