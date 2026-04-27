# Conduit for Android — AI Agent Guidance

## Project overview

Conduit is a native Android WebView shell for self-hosted [Open WebUI](https://github.com/open-webui/open-webui) instances. The app wraps the Open WebUI web interface in a `WebView` and adds native Android features: push notifications, file/camera uploads, microphone access, and deep-link navigation from notifications.

## Architecture

**Language:** Kotlin  
**UI:** Jetpack Compose (screens/components) + a single `ComponentActivity` that hosts the WebView outside the Compose hierarchy  
**DI:** Hilt  
**Async:** Kotlin coroutines + Flow  
**Storage:** DataStore (non-sensitive preferences) + EncryptedSharedPreferences (API key, AES-256-GCM)  
**Background work:** WorkManager (`NotificationWorker`)  
**Network:** Retrofit + OkHttp (`ApiClient`, `OpenWebUIService`)

### Package structure

```
com.atomictrxn.conduit
├── data/
│   ├── api/          # Retrofit service, OkHttp client, API models
│   ├── local/        # SettingsDataStore (server URL, prefs); API key in EncryptedSharedPreferences
│   └── repository/   # ServerRepository — coordinates data sources
├── di/               # Hilt AppModule
├── domain/model/     # ConnectionState, ServerConfig
├── ui/
│   ├── about/        # AboutScreen
│   ├── onboarding/   # Welcome → ServerSetup → ApiKey flow
│   ├── settings/     # SettingsScreen + SettingsViewModel
│   ├── theme/        # Material3 color, type, theme
│   └── webview/      # WebViewActivity, WebViewToolbar, WebViewViewModel
└── worker/           # NotificationWorker
```

### Key design decisions

- The `WebView` is created imperatively in `WebViewActivity` and injected into the Compose tree via `AndroidView`. This avoids recomposition destroying and recreating the WebView, which would lose page state.
- Back-press handling in `WebViewActivity` checks: About open → Settings open → WebView can go back → system back, in that order. Currently only the WebView back-stack step is implemented; overlay dismissal on back is a known gap.
- Cleartext HTTP is permitted broadly via `network_security_config.xml` because Android's domain-config does not support IP-range wildcards. The file documents the compensating controls and should be tightened if Android ever adds RFC-1918 range support.
- The API key is stored in `EncryptedSharedPreferences` (AES-256-GCM) and auto-synced from the active WebView session via a `JavascriptInterface` (`TokenBridge`) that reads `localStorage.token`. If the server supports persistent API keys (`POST /api/v1/auths/api_key`) the JWT is upgraded; otherwise the JWT itself is stored as the bearer credential. Re-sync is only triggered when the stored token is within 24 hours of expiry.
- Notifications use `GET /api/v1/chats/` and fire for any chat whose `updated_at` exceeds the last-check timestamp — not exclusively on assistant completion. This may catch renames and manual edits; no finer-grained completion event is available on the polling API.

## Build

```bash
# Debug (for emulator/sideload)
./gradlew assembleDebug

# Release (requires keystore.properties — see README)
./gradlew assembleRelease

# Install to connected device/emulator
./gradlew installDebug
```

**Current version:** 1.0.8 (versionCode 8)  
**Min SDK:** 26 (Android 8.0)  
**Target/Compile SDK:** 35

## Code style

Kotlin style is enforced with ktlint v12. **Always run `ktlintFormat` before committing** — CI runs `ktlintCheck` and will fail on any violation.

```bash
./gradlew ktlintFormat   # auto-fix
./gradlew ktlintCheck    # lint only (runs in CI)
```

The `.editorconfig` at the root configures ktlint with Compose PascalCase conventions.

## CI

GitHub Actions runs on every push to `main`:
1. `ktlintCheck` — style gate
2. `assembleDebug` — build gate
3. Debug APK uploaded as a workflow artifact

## Sensitive files — never commit

| File | Purpose | Location |
|------|---------|----------|
| `keystore.properties` | Release signing config | Project root (gitignored) |
| `*.keystore` / `*.jks` | Signing keystore | Outside repo (`~/.conduit-signing/`) |
| `local.properties` | SDK path | Project root (gitignored) |

These are all covered by `.gitignore`. Do not add them to version control.

## Release process

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`
2. Run `./gradlew ktlintFormat assembleRelease` and verify the build is clean
3. Commit to `main` and push
4. Create a GitHub release: `gh release create vX.Y.Z app/build/outputs/apk/release/conduit-X.Y.Z.apk --title "vX.Y.Z" --notes "..."`

The release APK is automatically named `conduit-{versionName}.apk` by the `applicationVariants` output filename config in `app/build.gradle.kts`.

## String resources

All user-visible strings live in `app/src/main/res/values/strings.xml`. Never hardcode strings in Kotlin or XML layouts — always add a resource entry and reference it via `R.string.*` / `stringResource()`.
