# Conduit for Android — AI Agent Guidance

## Project overview

Conduit is a native Android WebView shell for self-hosted [Open WebUI](https://github.com/open-webui/open-webui) instances. The app wraps the Open WebUI web interface in a `WebView` and adds native Android features: push notifications, file/camera uploads, microphone access, and deep-link navigation from notifications.

## Architecture

**Language:** Kotlin  
**UI:** Jetpack Compose (screens/components) + a single `ComponentActivity` that hosts the WebView outside the Compose hierarchy  
**DI:** Hilt  
**Async:** Kotlin coroutines + Flow  
**Storage:** DataStore (user preferences)  
**Background work:** WorkManager (`NotificationWorker`)  
**Network:** Retrofit + OkHttp (`ApiClient`, `OpenWebUIService`)

### Package structure

```
com.atomictrxn.conduit
├── data/
│   ├── api/          # Retrofit service, OkHttp client, API models
│   ├── local/        # SettingsDataStore (server URL, API key)
│   └── repository/   # ServerRepository — coordinates data sources
├── di/               # Hilt AppModule
├── domain/model/     # ConnectionState, ServerConfig
├── ui/
│   ├── about/        # AboutScreen
│   ├── onboarding/   # Welcome → ServerSetup → ApiKey flow
│   ├── settings/     # SettingsScreen + SettingsViewModel
│   ├── theme/        # Material3 color, type, theme
│   └── webview/      # WebViewActivity, WebViewScreen, WebViewToolbar, WebViewViewModel
└── worker/           # NotificationWorker
```

### Key design decisions

- The `WebView` is created imperatively in `WebViewActivity` and injected into the Compose tree via `AndroidView`. This avoids recomposition destroying and recreating the WebView, which would lose page state.
- Back-press handling in `WebViewActivity` checks: About open → Settings open → WebView can go back → system back, in that order.
- Cleartext HTTP is allowed only for private IP ranges via `network_security_config.xml` (not `usesCleartextTraffic="true"`), supporting self-hosted LAN servers while keeping public traffic HTTPS-only.
- The API key is stored in DataStore and used only for the notifications polling endpoint — the WebView session uses its own cookie-based auth.

## Build

```bash
# Debug (for emulator/sideload)
./gradlew assembleDebug

# Release (requires keystore.properties — see README)
./gradlew assembleRelease

# Install to connected device/emulator
./gradlew installDebug
```

**Current version:** 1.0.5 (versionCode 5)  
**Min SDK:** 26 (Android 8.0)  
**Target/Compile SDK:** 35

## Code style

Kotlin style is enforced with ktlint v12. Run before committing:

```bash
./gradlew ktlintFormat   # auto-fix
./gradlew ktlintCheck    # lint only (runs in CI)
```

The `.editorconfig` at the root configures ktlint with Compose PascalCase conventions. Always run `ktlintFormat` before opening a PR — CI will fail on style violations.

## CI

GitHub Actions runs on every push and PR to `main`:
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
2. Commit to a feature branch, open a PR, squash merge to `main`
3. Build the release APK: `./gradlew assembleRelease`
4. Create a GitHub release targeting `main`: `gh release create vX.Y.Z --title "Conduit vX.Y.Z" --notes "..." --target main`
5. Attach the APK: `gh release upload vX.Y.Z app/build/outputs/apk/release/app-release.apk`

## String resources

All user-visible strings live in `app/src/main/res/values/strings.xml`. Never hardcode strings in Kotlin or XML layouts — always add a resource entry and reference it via `R.string.*` / `stringResource()`.
