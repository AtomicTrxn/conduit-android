# Conduit for Android

A native Android client for self-hosted [Open WebUI](https://github.com/open-webui/open-webui) instances. Access your AI models and chat history with a fast, persistent connection — no browser tab required.

## Requirements

- Android 8.0 (API 26) or higher
- A running [Open WebUI](https://github.com/open-webui/open-webui) instance reachable from your device

## Setup

1. Install the APK from the [latest release](https://github.com/AtomicTrxn/conduit-android/releases/latest)
2. On first launch, enter the URL of your Open WebUI instance (e.g. `https://ai.yourdomain.com` or `http://192.168.1.50:3000`)
3. Optionally add an API key to enable chat completion notifications — generate one in Open WebUI under **Settings → Account**

## Features

- Full Open WebUI interface in a native shell
- File and camera upload support
- Microphone access for voice input
- Chat completion push notifications (requires API key)
- Deep-link navigation from notifications directly to the relevant chat

## Building from Source

```bash
git clone https://github.com/AtomicTrxn/conduit-android.git
cd conduit-android
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Release builds

Create a `keystore.properties` file in the project root:

```properties
storeFile=/path/to/your.keystore
storePassword=yourStorePassword
keyAlias=yourKeyAlias
keyPassword=yourKeyPassword
```

Then run:

```bash
./gradlew assembleRelease
```

## Code Quality

Kotlin style is enforced with [ktlint](https://github.com/pinterest/ktlint). To check:

```bash
./gradlew ktlintCheck
```

To auto-format:

```bash
./gradlew ktlintFormat
```

## Acknowledgements

- [Open WebUI](https://github.com/open-webui/open-webui) — the self-hosted AI chat platform this app connects to
- [Conduit for Open WebUI](https://github.com/tnware/conduit-for-open-webui) — the original project this Android client is based on
