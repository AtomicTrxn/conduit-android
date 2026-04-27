# Conduit — Application Design & Architecture

> A modern, native Android client for self-hosted [Open WebUI](https://github.com/open-webui/open-webui) instances.
> Inspired by [Open WebUI Client for Android](https://github.com/Maticcm/Open-WebUI-Client-for-Android).

---

## Overview

Conduit is a purpose-built Android application that provides a native shell around Open WebUI, extending it with Android OS integrations that a browser-based WebView wrapper cannot deliver — push notifications, background processing, accessibility-first design, and a clean settings experience.

The WebView remains the primary UI for conversations. Native layers handle everything the web UI cannot: background jobs, system notifications, onboarding, settings, and future API-backed features.

---

## Distribution

| Channel | Timeline |
|---------|----------|
| GitHub Releases (APK sideload) | v1 |
| F-Droid | Post-v1 |
| Google Play | Not planned |

---

## Identity

| Property | Value |
|----------|-------|
| App name | Conduit |
| Package name | com.atomictrxn.conduit |
| Repository | github.com/AtomicTrxn/conduit-android |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |
| License | Apache 2.0 |

---

## Architecture

### Pattern: MVVM + Repository

```
UI Layer (Compose)
    │
    ▼
ViewModel (StateFlow)
    │
    ▼
Repository
    │         │
    ▼         ▼
DataStore   Open WebUI API
(local)     (remote)
```

### Layer Responsibilities

| Layer | Responsibility |
|-------|---------------|
| **UI (Compose)** | Render state, dispatch user events, no business logic |
| **ViewModel** | Hold and transform UI state, survive rotation, expose `StateFlow` |
| **Repository** | Single source of truth — coordinates local DataStore and remote API |
| **DataStore** | Persists server URL and API key (replaces SharedPreferences) |
| **API Client** | Retrofit + OkHttp against OpenAI-compatible Open WebUI endpoints |
| **WorkManager** | Background jobs — notification polling, file processing |
| **App** | Application class — initializes DI, accessibility gate for WebView |

---

## Tech Stack

| Concern | Library / Approach |
|---------|-------------------|
| UI | Jetpack Compose + Material 3 |
| State management | `ViewModel` + `StateFlow` |
| Navigation | Jetpack Navigation Compose |
| Persistence | Jetpack DataStore (Preferences) |
| DI | Hilt |
| HTTP client | Retrofit + OkHttp |
| Async | Kotlin Coroutines + Flow |
| Background jobs | WorkManager |
| Notifications | NotificationCompat |
| Build | Gradle with version catalog (`libs.versions.toml`) |
| Min SDK | 26 (Android 8.0) |
| Language | Kotlin |

---

## Module Structure

```
app/
├── data/
│   ├── api/
│   │   ├── OpenWebUIService.kt         # Retrofit interface
│   │   ├── models/                     # API request/response models
│   │   └── ApiClient.kt               # OkHttp + Retrofit setup
│   ├── local/
│   │   └── SettingsDataStore.kt       # DataStore wrapper (URL, API key)
│   └── repository/
│       └── ServerRepository.kt        # Coordinates local + remote
├── domain/
│   └── model/
│       ├── ServerConfig.kt            # URL + API key state
│       └── ConnectionState.kt        # Loading / Connected / Error
├── ui/
│   ├── onboarding/
│   │   ├── OnboardingActivity.kt
│   │   ├── WelcomeScreen.kt           # Compose screen
│   │   ├── ServerSetupScreen.kt       # URL entry
│   │   ├── ApiKeyScreen.kt            # Optional API key entry
│   │   └── OnboardingViewModel.kt
│   ├── webview/
│   │   ├── WebViewActivity.kt         # Thin host, wires Compose toolbar
│   │   ├── WebViewScreen.kt           # Compose wrapper around AndroidView(WebView)
│   │   ├── WebViewToolbar.kt          # Auto-hiding native toolbar
│   │   └── WebViewViewModel.kt        # Connection state, settings dialog
│   └── settings/
│       ├── SettingsScreen.kt          # Full settings page
│       └── SettingsViewModel.kt
├── worker/
│   └── NotificationWorker.kt          # WorkManager job for completion polling
└── App.kt                             # Application class, Hilt entry point
```

---

## Screen Flow

```
App Launch
    │
    ├── First launch ──► WelcomeScreen ──► ServerSetupScreen ──► ApiKeyScreen (skippable)
    │                                                                    │
    │                                                                    ▼
    └── Returning user ──────────────────────────────────────► WebViewActivity
                                                                         │
                                                               Auto-hiding toolbar
                                                                    (three-dot menu)
                                                                         │
                                                                         ▼
                                                                  SettingsScreen
```

---

## Authentication & API Key Flow

### Onboarding
1. User enters server URL (must begin with `http://` or `https://`)
2. User enters API key — clearly explained as optional
3. Skip is prominently available; skipped users land in WebView and log in normally
4. Both values stored in DataStore via `SettingsDataStore`

### API Key Gate
Features that require an API key check `serverConfig.hasApiKey` before activating. If no key is present:
- Feature UI is visible but disabled (not hidden)
- A single contextual prompt explains how to enable it via Settings
- No crashes, no silent failures

### Settings
- Server URL: editable, re-validated on save
- API key: add / edit / clear
- Changing URL reloads the WebView

---

## Navigation to Settings

A **thin auto-hiding native toolbar** sits above the WebView:
- Shows app name and a single three-dot overflow menu
- Auto-hides after 3 seconds of inactivity
- Reappears on tap at the top of the screen
- Three-dot menu contains: Settings, About
- Replaces the original 4-finger long-press gesture entirely

---

## v1 Feature Scope

### In Scope

| Feature | Notes |
|---------|-------|
| Onboarding (URL + optional API key) | Full flow with skip |
| WebView core | Full Open WebUI UI |
| Auto-hiding toolbar + three-dot menu | Settings access |
| Settings screen | URL, API key, clear data |
| Connection error page with Retry | Replaces silent blank screen |
| Push notifications on chat completion | Requires API key — disabled otherwise |
| Back navigation within WebView | Hardware/gesture back navigates WebView history |
| Camera support | File chooser + camera capture |
| Microphone support | Correct `onPermissionRequest` flow |
| File downloads | Via system DownloadManager |
| Share to Conduit | Receive shared text and images |

### Explicitly Out of Scope (v1)

| Feature | Target |
|---------|--------|
| Native model picker | v2 |
| Native conversation list | v2 |
| Streaming chat via API | v2 |
| Background file upload with progress | v2 |
| Offline draft queue | v2 |
| RAG / knowledge base management | v2 |
| Android digital assistant integration | v2 |
| Widgets | v2 |

---

## Accessibility

Follows [Android Accessibility Guidelines](https://developer.android.com/design/ui/mobile/guides/foundations/accessibility).

| Requirement | Implementation |
|-------------|---------------|
| Minimum touch target | 48dp for all interactive elements |
| Text contrast | 4.5:1 minimum (Material 3 tonal palette handles this) |
| Non-text contrast | 3:1 minimum |
| Screen reader support | Compose `Semantics` properties on all UI elements |
| Font scaling | All text in `sp` units |
| No gesture-only actions | Every action reachable via tap (toolbar replaces 4-finger gesture) |
| TalkBack | `WebView.enableSlowWholeDocumentDraw()` gated behind `isTouchExplorationEnabled` in `App.kt` |
| Decorative elements | Content description set to null |

---

## Open WebUI API Integration (v1)

Only the notification polling endpoint is used in v1. Full API client is scaffolded for v2 expansion.

| Endpoint | Used In | v1 / v2 |
|----------|---------|---------|
| `GET /api/chats/{id}` | Notification polling | v1 |
| `GET /api/models` | Native model picker | v2 |
| `GET /api/chats` | Conversation list | v2 |
| `POST /api/chat/completions` | Streaming chat | v2 |
| `POST /api/v1/files/` | Background file upload | v2 |
| `GET /api/v1/files/{id}/process/status` | Upload progress | v2 |

### Authentication
All API calls use `Authorization: Bearer <api_key>` header.
API keys are `sk-` prefixed tokens generated from Open WebUI Settings → Account.

---

## Push Notifications (v1)

Uses `WorkManager` with a periodic polling job:

1. `NotificationWorker` runs every 15 minutes (minimum WorkManager interval)
2. Calls `GET /api/chats` to check for new assistant messages since last check
3. If new messages found, fires an Android notification
4. Tapping notification deep-links into the WebView at that chat
5. Entire feature disabled (worker not scheduled) when no API key is set

---

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Keep WebView as primary UI | Yes | Open WebUI evolves fast; native UI would always lag |
| Compose for native screens | Yes | First-class accessibility, modern, aligns with Google guidance |
| Graceful API key degradation | Features disabled, not hidden | Users understand what they're missing and how to fix it |
| Auto-hiding toolbar over floating button | Toolbar | Less intrusive, standard Android pattern, no WebView overlap |
| WorkManager over foreground service | WorkManager | Battery-friendly, system-managed, appropriate for polling |
| DataStore over SharedPreferences | DataStore | Type-safe, coroutine-native, no main-thread I/O risk |

---

## UI & Design Standards

All native screens follow the Android Mobile Design Guidelines reviewed below.

### Edge-to-Edge

- `enableEdgeToEdge()` called in every Activity
- Status bar and navigation bar are transparent
- `WindowInsets.systemBars` padding applied via Compose to prevent content obscured by system bars
- Toolbar collapses/scrolls per edge-to-edge rules — never opaque over content
- Touch targets never placed under system gesture insets (8dp margin from screen edges)
- Navigation bar: transparent in gesture mode, no additional scrim added

### System Bars

| Bar | Treatment |
|-----|-----------|
| Status bar | Transparent, content draws behind it |
| Navigation bar (gesture) | Always transparent, dynamic color handle |
| Navigation bar (button) | Translucent scrim via system; removed when bottom content present |
| Keyboard | Transitions synchronized with `WindowInsetsAnimationCompat` |

### Color

- **Material 3 HCT color system** — all colors defined as semantic tokens, never hardcoded hex
- Dynamic color (Android 12+ / API 31+) enabled with static fallback scheme for older devices
- Both light and dark themes implemented
- Color tokens defined in `ui/theme/Color.kt` and `ui/theme/Theme.kt`
- No color used as the sole accessibility affordance — icons and labels always supplement color
- Avoid red/green combinations for colorblind users

### Typography

- All font sizes in `sp` units — never `dp` for text
- Material 3 typescale used throughout
- Default system font (Roboto) — no custom font introduced in v1
- User device font size preferences respected automatically via `sp`

### Spacing & Layout

- **8dp baseline grid** for all layout structure, component spacing, and padding
- **4dp grid** for icons, internal component spacing, and small elements
- All dimensions in `dp` — never raw pixels
- Assets exported for all density buckets (mdpi → xxxhdpi); vector drawables preferred

### Predictive Back

- Opt into Android predictive back via `android:enableOnBackInvokedCallback="true"` in manifest
- `OnBackPressedCallback` used for WebView back stack navigation
- Back-within-WebView handled before activity back — preserves in-page navigation
- Exit animation: standard system back-to-home animation (no custom override needed for v1)
- 8dp margin from screen edges respected for all swipe targets

### Theming

- Material 3 theme with both light and dark variants
- Dynamic color enabled on Android 12+ (`DynamicColors.applyToActivitiesIfAvailable`)
- Single consistent icon style throughout — **Outlined** (clean, modern, works on all backgrounds)
- Shape tokens: medium rounding (rounded corners) throughout — approachable feel
- WebView dark theme enabled via `WebSettings` to match system theme

### Navigation Pattern

- **Single Activity** (`WebViewActivity`) hosting Compose UI
- Onboarding is a separate `Activity` (isolated, finishes on completion)
- Jetpack Navigation Compose for all in-app screen transitions
- No bottom navigation bar needed in v1 (single destination app)
- Settings accessed via toolbar overflow menu — not a separate navigation destination
- **No navigation drawer** — overkill for v1 feature set

### Top App Bar (Toolbar)

Follows Material 3 Top App Bar guidance:
- `SmallTopAppBar` variant — single line, collapses cleanly
- Auto-hides after 3 seconds of inactivity; reappears on tap at top of screen
- Contains: app name (left), three-dot overflow menu (right)
- Three-dot menu items: **Settings**, **About**
- Minimum 48dp height, 48dp touch target on overflow icon
- `TopAppBarDefaults.enterAlwaysScrollBehavior()` for scroll-linked show/hide

### Settings Screen

Follows [Android Settings Design Guide](https://developer.android.com/design/ui/mobile/guides/patterns/settings):
- Accessed via toolbar overflow menu (top app bar → standard location for Settings)
- Label is "Settings" — not "Options", "Preferences", or "Configuration"
- Grouped with headings: **Server**, **Account**, **Notifications**
- Uses Material 3 list components with supporting text showing current value
- Controls: `Switch` for toggles, `TextField` for URL/API key
- Dependent settings (notification options) disabled when API key is absent with brief explanation
- No app version info in Settings — goes in About screen

### Notifications

Follows [Android Notification Design Guide](https://developer.android.com/design/ui/mobile/guides/home-screen/notifications):

| Property | Value |
|----------|-------|
| Channel | `conduit_chat_completion` |
| Importance | `HIGH` (sound + heads-up) |
| Template | Standard |
| Header text | "Response ready" (≤ 30 chars) |
| Content text | First ~40 chars of assistant response |
| Large icon | None (no person photo applicable) |
| Category | `CATEGORY_MESSAGE` |
| Tap action | Deep-link to WebView at that chat |
| Lock screen | `VISIBILITY_PRIVATE` (content text hidden, title shown) |

- Notification permission requested during onboarding (Android 13+ / API 33+)
- Permission explained contextually before system dialog shown
- Notification channel created in `App.onCreate()`
- App icon badge enabled automatically (API 26+)

### Immersive Mode

Not used in v1. The WebView is full content — system bars remain visible at all times per the immersive content guidelines (only appropriate for video, games, and reading apps).

---

## Attribution

Conduit is an independent application inspired by the work done in
[Open WebUI Client for Android](https://github.com/Maticcm/Open-WebUI-Client-for-Android)
by [@Maticcm](https://github.com/Maticcm). No source code from that project is used in Conduit.
