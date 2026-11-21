# MultiStream Chat

Android app (Kotlin + Jetpack Compose) that unifies the live chat of **Twitch, YouTube, Kick and TikTok** into a single view, aimed at streamers and viewers following multiple channels at once.

> 🇪🇸 [Leer en español](README.es.md)

## Features

- Simultaneous connection to Twitch (WebSocket IRC), YouTube (live chat polling), Kick (messages API) and TikTok (protobuf WebSocket), with automatic reconnection and exponential backoff.
- Unified chat with per-platform filtering, 500-message buffer and batch grouping to keep scrolling smooth.
- Inline emote rendering (Twitch/Kick) with remote images via Coil.
- Settings split into tabs: **Appearance** (theme, high contrast, text size), **Chat** (keep screen on, emotes, test bar) and **Platforms** (channel + token per service).
- Material 3 theme with dynamic colors, light/dark/system modes and high contrast.
- Auto-connect to saved channels and preferences persisted in SharedPreferences.
- Test bar to inject sample messages per platform.

## Stack

- Kotlin, Jetpack Compose, Material 3
- OkHttp (WebSockets/HTTP), Coil 3
- Coroutines/Flow, ViewModel, SharedPreferences
- minSdk 24 · targetSdk 37

## Main structure

```
app/src/main/java/com/example/chatmultistream/
├── data/
│   ├── client/          # Per-platform clients + WebcastProto (TikTok)
│   ├── local/           # ChatPreferences (SharedPreferences)
│   └── repositories/    # UnifiedChatRepository
├── model/               # ChatMessage, Platform, ConnectionState, ThemeMode, TextScale
├── ui/
│   ├── screens/         # UnifiedChatScreen
│   ├── theme/           # ChatMultiStreamTheme
│   └── viewmodels/      # ChatViewModel
└── MainActivity.kt
```

## How to run

1. Open the project in Android Studio.
2. Sync Gradle.
3. Run on an emulator or device with Android 7.0+ (API 24+).

## Tests

Unit tests for parsers and client logic:

```bash
./gradlew test
```

## License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

As the author, I allow modification of the app's features and the creation of sub-versions/derivatives, as long as the result **remains free of advertisements** and is distributed under the same GPL-3.0 license. See the [LICENSE](LICENSE) file for the full text.