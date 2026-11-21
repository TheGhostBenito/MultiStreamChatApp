# MultiStream Chat

App Android (Kotlin + Jetpack Compose) que unifica en una sola vista el chat en vivo de **Twitch, YouTube, Kick y TikTok**, pensada para streamers y viewers que siguen varios canales a la vez.

> 🇬🇧 [Read in English](README.md)

## Características

- Conexión simultánea a Twitch (WebSocket IRC), YouTube (live chat polling), Kick (API de mensajes) y TikTok (WebSocket protobuf), con reconexión automática y backoff exponencial.
- Chat unificado con filtro por plataforma, buffer de 500 mensajes y agrupación por batches para mantener el scroll fluido.
- Renderizado de emotes inline (Twitch/Kick) con imágenes remotas vía Coil.
- Ajustes segmentados en pestañas: **Apariencia** (tema, alto contraste, tamaño de texto), **Chat** (pantalla despierta, emotes, barra de simulación) y **Plataformas** (canal + token por servicio).
- Tema Material 3 con colores dinámicos, modo claro/oscuro/sistema y alto contraste.
- Auto-conexión a canales guardados y persistencia de preferencias en SharedPreferences.
- Barra de simulación para inyectar mensajes de prueba por plataforma.

## Stack

- Kotlin, Jetpack Compose, Material 3
- OkHttp (WebSockets/HTTP), Coil 3
- Coroutines/Flow, ViewModel, SharedPreferences
- minSdk 24 · targetSdk 37

## Estructura principal

```
app/src/main/java/com/example/chatmultistream/
├── data/
│   ├── client/          # Clientes por plataforma + WebcastProto (TikTok)
│   ├── local/           # ChatPreferences (SharedPreferences)
│   └── repositories/    # UnifiedChatRepository
├── model/               # ChatMessage, Platform, ConnectionState, ThemeMode, TextScale
├── ui/
│   ├── screens/         # UnifiedChatScreen
│   ├── theme/           # ChatMultiStreamTheme
│   └── viewmodels/      # ChatViewModel
└── MainActivity.kt
```

## Cómo ejecutar

1. Abre el proyecto en Android Studio.
2. Sincroniza Gradle.
3. Ejecuta en un emulador o dispositivo con Android 7.0+ (API 24+).

## Tests

Tests unitarios de parsers y lógica de clientes:

```bash
./gradlew test
```

## Licencia

Este proyecto se distribuye bajo la licencia MIT.