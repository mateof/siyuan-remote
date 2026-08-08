# Features

Every item here exists because a browser tab cannot do it. Anything the SiYuan web UI already does well is left alone: this app does not reimplement the editor, and it injects no code into the SiYuan frontend beyond one download helper that touches no SiYuan global.

## Session that does not expire

**Always on.** The kernel caps its session cookie at 30 days. The app keeps your access authorization code encrypted with an AES-256/GCM key in the Android Keystore, and when the cookie is gone it replays the login in the background and reloads the page you were going to. You only re-enter the code if you sign out or if the code changes on the server.

`SecretStore` creates its key with `setUserAuthenticationRequired(false)`. That is intentional: the share target, widget and notification reply must work while the app is locked. The gate that protects you is the app lock, not the key.

## Biometric app lock

**On by default.** Because the credential is stored forever, anyone holding an unlocked phone would otherwise reach every note. The prompt accepts biometrics or the device PIN, whichever the device supports, and re-arms after 60 seconds in the background so that picking a file or granting a permission does not force a second unlock.

Turn it off in **Settings → Security** if the phone never leaves your hand.

## Quick capture

One text box that appends a markdown block to today's daily note through `/api/block/appendDailyNoteBlock`, creating the note if it does not exist. Five ways in:

| Entry point | Where |
|---|---|
| Share sheet | Any app: text, links, images, PDFs. Multiple images at once |
| Launcher shortcut | Long press the app icon |
| Home screen widget | 2x1, one tap to capture, one to open the editor |
| Floating in-app button | Bottom right of the editor |
| Notification reply | Inline field in the notification shade, no app switch |

Options in **Settings → Capture**:

- **Target notebook**: which notebook's daily note receives the block. Defaults to the first open notebook.
- **Capture as task**: prefixes `- [ ] `.
- **Add a timestamp**: appends `(HH:mm)`.
- **Capture notification**: keeps a silent, minimum priority, ongoing notification with the reply field. Restored after reboot.

Attached images are uploaded through `/api/asset/upload` and referenced as `![name](assets/...)`, exactly as the editor would.

### Voice dictation

The microphone button in the capture box uses the system speech recogniser and appends the result to whatever is already typed, so you can dictate and then correct with the keyboard.

## Camera in the file picker

When the SiYuan editor opens a file picker for an image, the app adds a camera entry next to the document picker. Photos go from lens to note without a stop in the gallery. No `CAMERA` permission is declared: the capture runs in the system camera app and returns through a `FileProvider` URI.

## Download rescue

SiYuan generates some files client side and hands them to the browser as `blob:` URLs. Android's `DownloadManager` cannot resolve those, which is why exports often appear to do nothing in a mobile browser.

The app detects `blob:` downloads, reads the blob back through a small injected script, and writes it into the shared Downloads folder through MediaStore. Ordinary `http(s)` downloads go through `DownloadManager` with the session cookie attached.

The script is namespaced under `SiyuanRemoteBridge` and touches no SiYuan global, so it cannot interfere with the frontend or be mistaken for the official app's `JSAndroid` bridge.

## Dual address with automatic fallback

Set a primary address and a fallback. On each cold start the app probes them in order with a 3 second timeout using the unauthenticated `/api/system/version`, uses the first that answers, and remembers it so the next start goes straight there. Typical pairing: LAN address at home, Tailscale or reverse proxy address outside.

## Back button that behaves

The SiYuan mobile UI keeps its own stack of panels, dialogs, viewers and documents. Before treating a back press as "leave the app", the app asks the frontend whether it has anything to close and calls `window.goBack()`. Only when nothing is open does it fall back to WebView history, and then to press-back-again-to-exit.

This matters on a remote kernel: `goBack()` normally ends by calling the official app's native bridge, which does not exist here, so without this the back button would appear dead at the top level.

## Force desktop interface

The kernel picks the mobile or desktop UI from the User-Agent, with a `siyuan-desktop-mode` cookie taking precedence. The setting flips that cookie, which is useful on tablets and open foldables where the mobile layout wastes the screen.

## Other switches

| Setting | Default | What it does |
|---|---|---|
| Floating app button | On | Access to settings, reload, capture and sync from inside the editor. It fades to 25% opacity after 3 seconds |
| Keep the screen on | Off | Holds `FLAG_KEEP_SCREEN_ON` while the app is in the foreground |
| Text size | 100% | WebView text zoom, 70% to 150% |
| Open external links in the browser | On | Links to other hosts open in a Custom Tab instead of hijacking the editor |
| Sync when the app opens | Off | Calls `/api/sync/performSync` on resume. Only useful if your kernel has sync configured |
| Allow mixed content | Off | Last resort for an HTTPS server that loads assets over plain HTTP |

## WebView compatibility check

SiYuan supports Chromium engines only. Android System WebView *is* Chromium, so the app is compatible by construction, but an old or disabled WebView package produces a subtly broken editor rather than an obvious failure. The app reads the installed version through `WebViewCompat`, warns below Chromium 108, and shows the version in **Settings → About**.

## Deep links

`siyuan://blocks/<id>` opens that block in the editor, whether it arrives from another app or from the "Open in the editor" action after a capture. Links that reach the WebView before the page is ready are parked and replayed once the frontend has defined `openFileByURL`.

## Deliberately not implemented

- **Offline access and local editing.** That would mean an on-device kernel, which is the official app's job.
- **A native editor on top of the documented API.** The documented API covers about 60 endpoints; the kernel exposes over 500, and the editor drives `/api/transactions` plus a WebSocket that carry no stability guarantee. Reimplementing it would be a permanent chase.
- **Ignoring TLS errors.** Install your CA instead, see [SETUP.md](SETUP.md).
- **Pull to refresh.** The WebView does not expose nested scrolling, so the gesture would fire inside the editor. Reload lives in the floating button instead.
