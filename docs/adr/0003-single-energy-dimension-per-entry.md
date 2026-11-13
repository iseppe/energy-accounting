---
status: accepted
---

# Single Energy Dimension per Entry

Real-device testing of Primary (`1°`) and Secondary (`2°`) Energy Dimensions (ADR-0002) showed that multi-tier dimension selection introduced unnecessary cognitive and UI friction: tapping chips to switch the primary dimension conflicted with toggling secondary dimensions, dynamic `1°`/`2°` prefixes caused chip widths to reflow across lines, and the separate "Secondary dimension occurrences" breakdown in Insights created confusion rather than clarity. We therefore superseded ADR-0002 and simplified the domain model so every Entry and Activity Template has a single Energy Dimension selected with one tap. Nuances or secondary factors of an event are captured in the Entry's optional Situational Note.
