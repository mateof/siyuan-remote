# Architecture

## The shape of the thing

SiYuan Remote is a **thin native shell around the SiYuan frontend**, plus a small HTTP client for the things the frontend cannot do from a browser.

```
┌──────────────────────── Android app ─────────────────────────┐
│                                                              │
│  MainActivity ── WebView ──────────────┐                     │
│      │                                 │  cookies            │
│      │ SessionManager                  │  (shared)           │
│      │      │                          │                     │
│      │      ├── SecretStore  (Keystore AES/GCM)              │
│      │      ├── SettingsRepository (DataStore)               │
│      │      └── SiyuanApi ── OkHttp ───┘                     │
│      │                                                       │
│  QuickCaptureActivity / widget / notification reply          │
│      └── CaptureRepository ── SiyuanApi                      │
└──────────────────────────────────────────────────────────────┘
                              │ HTTP
                              ▼
                    your SiYuan kernel (:6806)
```

Single Gradle module. Kotlin, Jetpack Compose, Hilt, OkHttp, DataStore. Three activities rather than one activity with navigation, so that leaving the editor for Settings or Quick Capture never destroys the WebView and never reloads the page.

## Package map

| Package | What lives there |
|---|---|
| `data` | `AppSettings`, `SettingsRepository` (DataStore), `SecretStore` (Keystore) |
| `network` | `SiyuanApi`, `SessionManager`, `WebViewCookieJar` |
| `ui.web` | WebView configuration, `WebViewClient`, `WebChromeClient`, `Downloader`, floating button |
| `ui.setup` / `ui.settings` / `ui.lock` / `ui.theme` | Compose screens |
| `capture` | `CaptureRepository`, `QuickCaptureActivity` and its view model |
| `notification` | Ongoing capture notification, reply receiver, boot receiver |
| `widget` | Home screen widget |
| `update` | GitHub release check, download, install, daily worker |
| `util` | URL helpers, WebView version check |

## Authentication

This is the part worth reading carefully, because it is the reason the app exists.

### What the kernel does

From `kernel/model/session.go`:

- `POST /api/system/loginAuth` with `{authCode, rememberMe: true}` sets a session cookie with `maxAge = 60 * 60 * 24 * 30`, so **30 days is the hard ceiling**. Without `rememberMe` it is a browser session cookie.
- `CheckAuth` accepts the cookie, or HTTP Basic with the workspace name as user and the lock screen password as password, or an API token.
- With no lock screen password configured at all, the kernel rejects every non-localhost request with a message naming it. The app detects that string and shows an actionable error instead of a generic auth failure.
- An unauthenticated `GET` from a `Mozilla/`-prefixed User-Agent gets a `302` to `/check-auth?to=<original path>`. Anything else gets a `401` JSON body.
- Repeated wrong passwords flip a global counter and the kernel starts demanding a captcha, served as a PNG at `GET /api/system/getCaptcha`.

### What the app does

1. `WebViewCookieJar` bridges OkHttp's `CookieJar` to `android.webkit.CookieManager`, so the HTTP client and the WebView share one session. This is the keystone: a background login instantly authenticates the WebView.
2. On start, `SessionManager.prepare()` probes the candidate URLs, then calls `POST /api/system/getConf`. A `200` means the cookie is alive.
3. A `401` triggers a silent `loginAuth` with the stored password. Only if that fails does the user see anything.
4. If the WebView still reaches `/check-auth` (cookie expired mid-session), `SiyuanWebViewClient` intercepts the navigation, `SessionManager.renewSession()` logs in again, and the app reloads the `to` target. Debounced at 1.5 s and capped at 3 consecutive attempts so a rejected password surfaces as an error instead of a redirect loop.
5. `signOut()` is the only path that deletes the stored password: it calls `logoutAuth`, clears the cookie store and wipes `SecretStore`.

### Where the code is kept

`SecretStore` encrypts it with AES-256/GCM using a key generated in the `AndroidKeyStore`, and stores `ivLength | iv | ciphertext` base64 encoded in a private `SharedPreferences`. The key is excluded from cloud backup and device transfer (`res/xml/data_extraction_rules.xml`), since ciphertext without the device-bound key is useless anyway.

`setUserAuthenticationRequired(false)` is deliberate. Binding the key to biometrics would break background capture from the share sheet, the widget and the notification while the phone is locked. The user-facing gate is the app lock.

## WebView configuration

`applySiyuanConfiguration` in `ui/web/WebViewSetup.kt`. The parts that matter:

- **User-Agent**: the default WebView string plus a ` SiYuanRemote/<version>` suffix. `kernel/server/serve.go` picks the UI from this string, so the `Mozilla/` prefix and the `Mobile` token must survive. Appending after them keeps mobile detection working and makes app requests identifiable in the kernel log.
- **Algorithmic darkening off**: SiYuan ships its own themes, and letting the WebView darken on top of them washes out the colours.
- **Mixed content blocked** by default, `MIXED_CONTENT_NEVER_ALLOW`.
- **No file access**, multiple windows disabled so `target=_blank` loads in place.
- **`siyuan-desktop-mode` cookie** for the desktop UI switch, which `serve.go` honours ahead of User-Agent sniffing.

The app never sets `window.JSAndroid`. `isInAndroid()` in the SiYuan frontend requires both `container === "android"` (which only an on-device kernel reports) and that global. A Docker kernel reports `container === "docker"`, so the frontend runs in plain browser mode and never tries to call a native bridge that does not exist.

## Kernel endpoints used

| Endpoint | Used for |
|---|---|
| `POST /api/system/version` | Unauthenticated liveness probe, 3 s timeout, for address fallback |
| `POST /api/system/getConf` | Cheap "is the cookie still valid" check |
| `POST /api/system/loginAuth` | Initial and silent re-login |
| `POST /api/system/logoutAuth` | Sign out |
| `GET /api/system/getCaptcha` | Captcha PNG after repeated failures |
| `POST /api/notebook/lsNotebooks` | Notebook picker for capture |
| `POST /api/block/appendDailyNoteBlock` | Quick capture |
| `POST /api/filetree/createDailyNote` | "Daily note" launcher shortcut |
| `POST /api/asset/upload` | Shared images and files |
| `POST /api/sync/performSync` | Optional sync on resume |

All of them are documented in SiYuan's `docs/API.md` except `getConf`, `loginAuth`, `logoutAuth`, `getCaptcha` and `appendDailyNoteBlock`, which are stable kernel routes the official clients use. The editor's own protocol (`/api/transactions` and the WebSocket) is left entirely to the frontend.

## Updates

`update/` talks to `GET https://api.github.com/repos/mateof/siyuan-remote/releases`, which is the only host besides your kernel that the app ever contacts.

It uses its own `OkHttpClient`, marked with the `@UpdateHttpClient` qualifier. This is not tidiness: the kernel client carries `WebViewCookieJar`, cookie jars are not scoped by host, and reusing it would ship your SiYuan session cookie to GitHub on every check.

`UpdateCheckWorker` runs daily through WorkManager and reaches its dependencies with a Hilt `@EntryPoint` rather than `@HiltWorker`, which would need `androidx.hilt:hilt-work`, a `Configuration.Provider` on the Application and a manifest override of the WorkManager initialiser. R8 must not rename the worker: WorkManager stores its class name in its own database, hence the keep rule in `proguard-rules.pro`.

## Threading and lifecycle

- All HTTP work is `suspend` on `Dispatchers.IO`; `SessionManager` serialises with a `Mutex` so concurrent entry points cannot stampede logins.
- Background receivers use `goAsync()` with a `SupervisorJob` scope and always call `finish()`.
- `MainViewModel` exposes `StateFlow<AppSettings?>` with `null` meaning "DataStore has not answered yet", so the lock screen never flashes for users who disabled it.
- One-shot navigation and WebView commands travel through a buffered `Channel`, so a deep link that arrives before the WebView exists is replayed once it does.

## Testing

Unit tests cover the pure logic: URL normalisation, candidate ordering, the tolerant parsing of kernel responses, and version comparison. The WebView integration is not unit tested; it is verified by running the app against a real kernel.
