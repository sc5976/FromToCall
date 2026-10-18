# FromToCall

Android app for tracking your work day. Enter the time you arrived — the app
calculates when you should leave based on the day's duration and fires a
"go home" signal at the right moment. Sound, vibration, and a notification —
three independent ways to remind you.

## Features

### Time tracking

- **Four independent durations by day of the week**: Monday–Thursday, Friday,
  Saturday, Sunday — each with its own full duration. No automatic deductions
  ("1 hour for lunch" and the like): the user sets the value as a whole.
  Saturday and Sunday are independent settings, not a single "weekend"
  value.
- **Arrival time** is set to the current time automatically on launch, if the
  timer has not been started yet. You can edit it and tap "Start".
- **Timer** persists the arrival time in `SharedPreferences`. If the process
  is force-stopped, the next launch restores the same arrival and reschedules
  the alarm.

### Leaving signal

- **Sound + vibration + notification** — three independent reminders. The
  persistent notification shows a countdown ("Leave at 21:50 • 7 h 44 min
  left"), updated every 30 seconds.
- **`STREAM_ALARM` volume** is raised to the maximum for the duration of the
  signal and returned to its original value when the signal is turned off.
  This fixes the "vibration works but no sound" issue caused by Android's
  separate alarm-stream control.
- **Auto-stop after 60 seconds** in case the phone is not with you.
- **"Turn off" button** in the notification and inside the app (a red card
  appears on the main screen while the signal is ringing).
- **Alarm restored after reboot**.

### Settings

- **Durations** for the four day groups — four rows in settings, each tap
  opens a dialog with a TimePicker.
- **Notification sound** — standard Android system ringtone picker.
- **Interface language**: system default, Русский, English. Default is
  Русский. The change applies instantly, without restarting the app.
- **Accent color**: green (default), blue, purple, orange, teal, red.
  Affects the "Departure time" card, the countdown, button accents. The
  overall theme stays dark.

## Build requirements

- **JDK 17** (Temurin / Zulu / Oracle — any will do).
- **Android SDK 34** with build-tools 34.0.0 and platform-tools.
- **Gradle 8.4** — shipped via `gradlew`, no need to install it system-wide.

## How to build

```bash
# Release APK with auto-incremented version (1.0, 1.1, 1.2, …).
# The output lands in dist/FromToCall-release-<version>.apk.
./gradlew distRelease

# Debug variant for development on an emulator or device.
./gradlew distDebug
```

Version lives in `version.properties`: `major` and `minor`. After every
successful `assembleRelease` Gradle increments `minor`. If the build fails,
the version does not move — the next attempt tries the same `1.x` again.
`versionCode` is taken from the epoch (seconds since 1970), so it always
grows monotonically — this is what Android needs to accept an update over
an already-installed APK.

## Release signing

There is no signing key in the repository — it lives locally as
`fromtocall-release.jks`. The password is in `app/build.gradle.kts` in
`signingConfigs.release`. This is fine for a personal project, but before
publishing on Google Play:

1. Generate your own key (`keytool -genkeypair ...`).
2. Place it next to the project and update the path/password in
   `build.gradle.kts`.
3. Rebuild.

Without this step, updating over an installed copy requires uninstalling
the app — the old and new builds will be considered as coming from
different developers.

## Project layout

```
app/
  src/main/java/com/fromtocall/
    data/             # SettingsRepository — settings in DataStore
    service/          # WorkTimerService, AlarmService, receivers
    ui/screens/       # MainScreen, SettingsScreen + ViewModels
    ui/components/    # TimePickerWheel
    ui/theme/         # Theme, AccentPalette
  src/main/res/       # strings, drawables, themes (RU default, values-en for EN)
build.gradle.kts      # SDK, dependencies, signingConfig, dist tasks
settings.gradle.kts
version.properties    # major/minor, auto-incremented
```

## File-by-file overview

- `service/WorkTimerService.kt` — foreground service that keeps the
  persistent notification with the countdown and (re)schedules the
  `AlarmManager` alarm (with reboot support).
- `service/AlarmService.kt` — the actual ringing service: wake lock,
  `MediaPlayer` for sound, vibration, auto-stop after 60 s, temporary
  `STREAM_ALARM` boost.
- `service/AlarmReceiver.kt` — routing `AlarmManager → AlarmService`.
- `service/StopAlarmReceiver.kt` — the "Turn off" button in the
  notification.
- `service/BootReceiver.kt` — reschedules the alarm after a reboot.
- `ui/screens/MainViewModel.kt` — reactive store, recomputes the
  remaining time on every tick and on every change of arrival / settings.
- `ui/screens/SettingsViewModel.kt` + `SettingsScreen.kt` — four duration
  rows (Mon–Thu / Fri / Sat / Sun), sound picker, language switcher,
  accent color palette.
- `ui/theme/AccentPalette.kt` — six accent color presets (green, blue,
  purple, orange, teal, red).

## License

This project is licensed under the **Apache License 2.0** — Copyright ©
2026 Andrey Seis (sc5976). See `LICENSE` for the full text. In short: you
can use, modify, and redistribute, including for commercial purposes, as
long as you preserve the copyright notice and the license text.
Additionally, Apache 2.0 grants an explicit patent license, which
matters for corporate users.
