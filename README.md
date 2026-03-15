# ParentalORMental

Free, open-source Android cyberbullying monitor for at-risk children.

**No cloud. No subscriptions. No data harvesting. Everything stays on-device.**

## What It Does

ParentalORMental runs silently on a child's Android phone and monitors incoming SMS messages for bullying, harassment, and threatening language. When something is flagged, the parent gets an immediate alert — either as an on-device notification, an SMS to their phone, or both.

### Core Features

- **On-device bullying detection** — Regex-based NLP engine that scores messages across 4 severity levels (LOW / MEDIUM / HIGH / CRITICAL) covering threats, harassment, insults, exclusion language, and teasing patterns
- **Parent SMS alerts** — Instant text message to a parent's phone when bullying is detected, with severity and context
- **Incident logging** — SQLite database of every flagged message with timestamps, matched patterns, and severity scores
- **Parent dashboard** — Review incidents, add notes, mark false positives, track trends
- **Stealth mode** — Optional hidden operation so bullies can't see the app is installed
- **Boot persistence** — Monitoring automatically restarts after device reboot
- **Configurable thresholds** — Parents choose the minimum severity level that triggers alerts

## Why This Exists

Commercial parental monitoring suites charge monthly premiums that families of at-risk children often can't afford. This project exists to provide the same protection for free.

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material3)
- **Room** — Local SQLite database for incident storage
- **DataStore** — Preferences and settings
- **WorkManager** — Background monitoring
- **BroadcastReceiver** — SMS interception
- **Foreground Service** — Persistent monitoring that survives OS process cleanup

## Requirements

- Android 8.0+ (API 26)
- Android Studio for building
- No external services or API keys required

## Getting Started

1. Clone the repo
2. Open in Android Studio
3. Let Gradle sync
4. Run on a device or emulator

The app will walk you through a setup wizard on first launch — enter a parent phone number and grant the required permissions.

## Permissions

| Permission | Why |
|---|---|
| `RECEIVE_SMS` / `READ_SMS` | Core monitoring — intercept and analyze incoming messages |
| `SEND_SMS` | Send alert texts to the parent's phone |
| `READ_CALL_LOG` / `READ_PHONE_STATE` | Harassment call tracking (future) |
| `POST_NOTIFICATIONS` | On-device alerts |
| `FOREGROUND_SERVICE` | Keep monitoring alive in the background |
| `RECEIVE_BOOT_COMPLETED` | Restart monitoring after reboot |
| `ACCESS_FINE_LOCATION` | Optional safety feature (future) |

## Project Structure

```
app/src/main/java/com/parentalormente/
├── detection/BullyingDetector.kt    # The engine — regex severity scoring
├── monitor/SmsReceiver.kt           # SMS interception + analysis
├── monitor/MonitorService.kt        # Foreground service
├── monitor/BootReceiver.kt          # Reboot persistence
├── alerts/AlertManager.kt           # Notifications + SMS to parent
├── data/db/                         # Room database (incidents)
├── data/prefs/AppPreferences.kt     # DataStore settings
└── ui/screens/                      # Dashboard, Setup, Settings, Detail
```

## Roadmap

- [ ] Social media notification monitoring (accessibility service)
- [ ] Call log harassment pattern detection
- [ ] Panic button for kids ("I need help now")
- [ ] Mood tracker / wellbeing check-ins
- [ ] Export incident reports (PDF for schools/authorities)
- [ ] Companion parent app
- [ ] ML-based detection to supplement regex patterns

## License

MIT — use it, fork it, help kids.

## Contributing

PRs welcome. If you're a parent, teacher, counselor, or developer who gives a damn — jump in.
