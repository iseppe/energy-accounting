# Native Android Stack Without Google Play Services

We chose a pure native Android stack (Kotlin, Jetpack Compose Material 3, and Room SQLite) over cross-platform frameworks (React Native/Expo, Capacitor, and Kotlin Multiplatform), deferring iOS support until the personal Android workflow is proven. All data persistence and Check-in Reminders run 100% locally on-device using Android's native `AlarmManager` and `NotificationManager` with zero Google Play Services dependencies, ensuring complete privacy and compatibility with de-Googled Android setups.
