#!/usr/bin/env python3
"""Generate a rich synthetic Energy Accounting backup for demos and manual testing.

Run from the repository root:

    python3 synthetic-data/generate_demo_backup.py

The output is a schemaVersion=2 JSON backup that the app's Manage & Export →
Restore JSON Backup screen accepts. It covers the last 30 days, all four time
slots, both directions, seeded and custom dimensions, pinned templates, italian
accents, unlogged days, and two future entries for the future-date warning.
"""

from __future__ import annotations

import datetime as dt
import json
import random
from pathlib import Path

random.seed(20261113)

ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT / "energy-accounting-demo-backup.json"

# ---- Dimensions -----------------------------------------------------------

DIMENSIONS = [
    {"id": 1, "systemKey": "PHYSICAL", "name": "Physical", "colorIndex": 0, "isArchived": False},
    {"id": 2, "systemKey": "COGNITIVE", "name": "Cognitive", "colorIndex": 1, "isArchived": False},
    {"id": 3, "systemKey": "SENSORY", "name": "Sensory", "colorIndex": 2, "isArchived": False},
    {"id": 4, "systemKey": "SOCIAL", "name": "Social", "colorIndex": 3, "isArchived": False},
    {"id": 5, "systemKey": "EMOTIONAL", "name": "Emotional", "colorIndex": 4, "isArchived": False},
    {"id": 6, "systemKey": None, "name": "Executive Function", "colorIndex": 5, "isArchived": False},
    {"id": 7, "systemKey": None, "name": "Interoception", "colorIndex": 6, "isArchived": False},
]

# ---- Activities -----------------------------------------------------------
# (activity, direction, base value, dimension id, notes)

WAKE = ("Wake up", "SPEND", 15, 2, ["Slept badly", "Woke up before the alarm"])
ROUTINE = ("Morning routine", "SPEND", 30, 2, ["Rushed morning", "Change of plan"])
BREAKFAST = ("Favourite breakfast", "GAIN", 20, 3, ["Caffè e marmellata", "Warm tea and toast"])
COMMUTE = ("Commute to work", "SPEND", 40, 3, ["Treno in ritardo", "Crowded bus"])
INTERO = ("Interoception check-in", "GAIN", 15, 7, ["Noticed tension early", "Body scan before work"])

FOCUS = ("Focus block", "SPEND", 50, 2, ["Deep work", "No interruptions"])
MEETING = ("Team meeting", "SPEND", 60, 4, ["Unexpected agenda change", "Loud room"])
GROCERY = ("Grocery store", "SPEND", 60, 3, ["Crowded aisles", "Fluorescent lights"])
PAPERWORK = ("Paperwork", "SPEND", 45, 6, ["Deadline", "Too many tabs open"])
CALL = ("Call a friend", "GAIN", 35, 4, ["Long chat", "Laughed a lot"])
SUN = ("Sunlight break", "GAIN", 20, 3, ["Sun on my face", "Five minutes outside"])

LUNCH = ("Lunch break", "GAIN", 30, 3, ["Quiet lunch", "Ate outside"])
QUIET = ("Quiet time with headphones", "GAIN", 50, 3, ["Noise-cancelling", "Dark room", "Favourite playlist"])
DOCTOR = ("Doctor appointment", "SPEND", 50, 4, ["Waiting room", "New doctor"])
HOMEWORK = ("Homework support", "SPEND", 40, 2, ["Maths", "Needed a break"])
WALK = ("Evening walk", "GAIN", 30, 1, ["Fresh air", "Sunset", "Cool air"])
OVERLOAD = ("Sensory overload", "SPEND", 70, 3, ["Too much noise", "Bright lights"])
PRESSURE = ("Deep pressure break", "GAIN", 40, 1, ["Weighted blanket", "Firm hug"])

DINNER = ("Dinner with family", "SPEND", 20, 4, ["Loud table", "Everyone talking"])
BEDTIME = ("Bedtime routine", "SPEND", 15, 2, ["Late shower", "Forgot meds"])
WRITING = ("Creative writing", "GAIN", 45, 5, ["Wrote a chapter", "Flow state"])
PHONE = ("Unexpected phone call", "SPEND", 25, 4, ["Bad news", "Long call"])
MEDITATION = ("Meditation", "GAIN", 25, 5, ["Ten minutes", "Breathing"])
CHORES = ("Household chores", "SPEND", 35, 1, ["Laundry", "Dishes"])
SCHOOL = ("School drop-off", "SPEND", 30, 3, ["Loud playground", "Rushed out the door"])
STORY = ("Bedtime story", "GAIN", 20, 5, ["Read together", "Calm ending"])

PROFILES = [
    [
        ("EARLY_MORNING", WAKE), ("EARLY_MORNING", ROUTINE), ("EARLY_MORNING", BREAKFAST),
        ("EARLY_MORNING", COMMUTE), ("LATE_MORNING", FOCUS), ("LATE_MORNING", MEETING),
        ("AFTERNOON", LUNCH), ("AFTERNOON", QUIET), ("EVENING", DINNER),
        ("EVENING", BEDTIME), ("EVENING", QUIET),
    ],
    [
        ("EARLY_MORNING", WAKE), ("EARLY_MORNING", BREAKFAST), ("EARLY_MORNING", INTERO),
        ("LATE_MORNING", FOCUS), ("LATE_MORNING", PAPERWORK), ("AFTERNOON", LUNCH),
        ("AFTERNOON", WALK), ("EVENING", WRITING), ("EVENING", DINNER), ("EVENING", MEDITATION),
    ],
    [
        ("EARLY_MORNING", WAKE), ("EARLY_MORNING", ROUTINE), ("LATE_MORNING", CALL),
        ("LATE_MORNING", GROCERY), ("AFTERNOON", LUNCH), ("AFTERNOON", DOCTOR),
        ("EVENING", DINNER), ("EVENING", PHONE), ("EVENING", QUIET),
    ],
    [
        ("EARLY_MORNING", WAKE), ("EARLY_MORNING", BREAKFAST), ("LATE_MORNING", SUN),
        ("AFTERNOON", OVERLOAD), ("AFTERNOON", PRESSURE), ("AFTERNOON", QUIET),
        ("EVENING", DINNER), ("EVENING", BEDTIME),
    ],
    [
        ("EARLY_MORNING", WAKE), ("EARLY_MORNING", SCHOOL), ("EARLY_MORNING", COMMUTE),
        ("LATE_MORNING", FOCUS), ("AFTERNOON", HOMEWORK), ("AFTERNOON", LUNCH),
        ("AFTERNOON", OVERLOAD), ("AFTERNOON", PRESSURE), ("EVENING", DINNER),
        ("EVENING", STORY),
    ],
    [
        ("EARLY_MORNING", WAKE), ("EARLY_MORNING", BREAKFAST), ("LATE_MORNING", GROCERY),
        ("AFTERNOON", LUNCH), ("AFTERNOON", WALK), ("EVENING", WRITING),
        ("EVENING", DINNER), ("EVENING", QUIET), ("EVENING", BEDTIME),
    ],
]

SLOT_ORDER = ["EARLY_MORNING", "LATE_MORNING", "AFTERNOON", "EVENING"]
SLOT_HOURS = {"EARLY_MORNING": 7, "LATE_MORNING": 11, "AFTERNOON": 15, "EVENING": 20}

# ---- Templates ------------------------------------------------------------

TEMPLATE_SPECS = [
    ("Morning routine", "SPEND", 30, 2, True),
    ("Favourite breakfast", "GAIN", 20, 3, True),
    ("Commute to work", "SPEND", 40, 3, True),
    ("Team meeting", "SPEND", 60, 4, False),
    ("Focus block", "SPEND", 50, 2, False),
    ("Lunch break", "GAIN", 30, 3, False),
    ("Grocery store", "SPEND", 60, 3, False),
    ("Quiet time with headphones", "GAIN", 50, 3, True),
    ("Evening walk", "GAIN", 30, 1, False),
    ("Dinner with family", "SPEND", 20, 4, False),
    ("Bedtime routine", "SPEND", 15, 2, False),
    ("Call a friend", "GAIN", 35, 4, False),
    ("Paperwork", "SPEND", 45, 6, False),
    ("Interoception check-in", "GAIN", 15, 7, True),
]

START = dt.date(2026, 10, 15)
END = dt.date(2026, 11, 13)
SKIP_DAYS = {dt.date(2026, 10, 18), dt.date(2026, 10, 25), dt.date(2026, 11, 2), dt.date(2026, 11, 8)}


def epoch(y, m, d, hour, minute=0):
    return int(dt.datetime(y, m, d, hour, minute, tzinfo=dt.timezone.utc).timestamp() * 1000)


def add_entry(entries, day, slot, activity, note_index=None):
    name, direction, base, dimension, notes = activity
    value = max(5, min(100, base + random.choice([-10, -5, 0, 0, 0, 5, 10])))
    value = int(round(value / 5.0) * 5)
    note = None
    if note_index is not None and notes:
        note = notes[note_index % len(notes)]
    elif notes and random.random() < 0.22:
        note = random.choice(notes)
    index = sum(1 for e in entries if e["dateIso"] == day.isoformat() and e["timeSlot"] == slot)
    entries.append({
        "dateIso": day.isoformat(),
        "timeSlot": slot,
        "activity": name,
        "situationalNote": note,
        "direction": direction,
        "energyValue": value,
        "dimensionId": dimension,
        "createdAtEpochMillis": epoch(day.year, day.month, day.day, SLOT_HOURS[slot], index * 7),
    })


def main():
    entries = []
    today = dt.date(2026, 11, 13)
    day = START
    profile_index = 0
    while day <= END:
        if day == today or day in SKIP_DAYS or random.random() < 0.12:
            day += dt.timedelta(days=1)
            continue
        profile = PROFILES[profile_index % len(PROFILES)]
        profile_index += 1
        for slot, activity in profile:
            add_entry(entries, day, slot, activity)
        day += dt.timedelta(days=1)

    # Curated "today" so the Today view, chart and slots are all rich at once.
    for slot, activity, note_index in [
        ("EARLY_MORNING", WAKE, 0),
        ("EARLY_MORNING", ROUTINE, 0),
        ("EARLY_MORNING", BREAKFAST, 0),
        ("LATE_MORNING", FOCUS, 0),
        ("LATE_MORNING", MEETING, 0),
        ("AFTERNOON", LUNCH, 0),
        ("AFTERNOON", QUIET, 1),
        ("EVENING", DINNER, 0),
        ("EVENING", WALK, 1),
        ("EVENING", BEDTIME, 0),
    ]:
        add_entry(entries, today, slot, activity, note_index)

    # A future day so the future-date warning can be exercised by tapping +.
    future = today + dt.timedelta(days=1)
    add_entry(entries, future, "EARLY_MORNING", PAPERWORK, 0)
    add_entry(entries, future, "AFTERNOON", CALL, 0)

    by_activity = {}
    for entry in entries:
        by_activity.setdefault(entry["activity"].strip().lower(), []).append(entry)

    templates = []
    for display_name, direction, value, dimension_id, pinned in TEMPLATE_SPECS:
        normalized = display_name.strip().lower()
        used = by_activity.get(normalized, [])
        templates.append({
            "normalizedName": normalized,
            "displayName": display_name,
            "defaultDirection": direction,
            "defaultEnergyValue": value,
            "defaultDimensionId": dimension_id,
            "isPinned": pinned,
            "usageCount": max(1, len(used)),
            "lastUsedEpochMillis": max((e["createdAtEpochMillis"] for e in used), default=epoch(2026, 10, 20, 9)),
        })

    reminders = [
        {"timeSlot": "EARLY_MORNING", "hour": 9, "minute": 30, "isEnabled": True},
        {"timeSlot": "LATE_MORNING", "hour": 12, "minute": 30, "isEnabled": True},
        {"timeSlot": "AFTERNOON", "hour": 17, "minute": 30, "isEnabled": True},
        {"timeSlot": "EVENING", "hour": 21, "minute": 0, "isEnabled": True},
    ]

    payload = {
        "schemaVersion": 2,
        "dimensions": DIMENSIONS,
        "templates": templates,
        "entries": sorted(entries, key=lambda e: (e["dateIso"], e["createdAtEpochMillis"])),
        "reminders": reminders,
    }

    OUTPUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {OUTPUT}")
    print(f"entries: {len(entries)}  templates: {len(templates)}  dimensions: {len(DIMENSIONS)}")


if __name__ == "__main__":
    main()
