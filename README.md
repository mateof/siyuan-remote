# SiYuan Remote

An unofficial Android client for a **self-hosted [SiYuan](https://github.com/siyuan-note/siyuan) server**.

The official SiYuan Android app runs its own kernel on the phone and syncs through cloud storage. If you already run SiYuan in Docker on a NAS, a VPS or a home server, that model duplicates your workspace and gives you two sources of truth. SiYuan Remote takes the other route: it talks to *your* kernel, over your network, and adds the things a browser tab cannot do.

[![CI](https://github.com/mateof/siyuan-remote/actions/workflows/ci.yml/badge.svg)](https://github.com/mateof/siyuan-remote/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/mateof/siyuan-remote?include_prereleases&sort=semver)](https://github.com/mateof/siyuan-remote/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![minSdk](https://img.shields.io/badge/minSdk-26-brightgreen)](app/build.gradle.kts)

> **Not affiliated with the SiYuan project.** This is an independent client. Please do not open SiYuan issues about it.

---

## Why not just open the server in Chrome

You can, and it works. SiYuan serves a full mobile UI to any Chromium browser. What a browser tab cannot do:

| | Browser tab | SiYuan Remote |
|---|---|---|
| Stays signed in | 30 days maximum, then a login form | Indefinitely, until you sign out |
| Share sheet target | No | Yes: text, links, images, PDFs |
| Home screen widget and launcher shortcuts | No | Yes |
| Capture from the notification shade | No | Yes, with inline reply |
| Voice dictation into a note | Only inside the keyboard | Dedicated button in quick capture |
| Camera straight into the file picker | Inconsistent | Always offered |
| `blob:` downloads (exports) | Usually fails silently | Saved to Downloads |
| Biometric lock in front of your notes | No | Optional, on by default |
| Automatic LAN then VPN fallback | No | Yes |

Everything else, the whole editor, is the real SiYuan frontend served by your own kernel. This app deliberately does **not** reimplement the editor.

## Requirements

- A SiYuan kernel you can reach from the phone (Docker, bare metal, VPS, Tailscale, reverse proxy).
- An **access authorization code** set on that server. The kernel refuses non-local clients without one.
- Android 8.0 (API 26) or newer, with a reasonably current Android System WebView (Chromium 108+).

Full server-side instructions, including a `docker-compose.yml` and HTTPS notes, are in **[docs/SETUP.md](docs/SETUP.md)**.

## Install

Grab the APK from the [latest release](https://github.com/mateof/siyuan-remote/releases/latest) and install it.

Releases are signed with a developer key, so an APK from GitHub will not upgrade over a locally built debug build (different signature). Uninstall first if you switch.

## First run

1. Enter your server address, for example `http://192.168.1.50:6806`. The scheme is optional; plain HTTP is accepted on a local network.
2. Optionally add a fallback address, for example your Tailscale hostname. The app probes the primary first and falls back automatically, then remembers whichever answered.
3. Enter the access authorization code from SiYuan under **Settings → About → Access authorization code**.
4. Done. The editor loads and the app stays signed in from then on.

## How the indefinite session works

The kernel issues a session cookie that lasts at most 30 days (`maxAge = 60 * 60 * 24 * 30` in `kernel/model/session.go`). No client can extend that server side.

So the app stores your access code encrypted with an AES-256/GCM key held in the Android Keystore. When the cookie expires, the kernel redirects the WebView to `/check-auth`; the app intercepts that redirect, replays the login in the background over the shared cookie jar, and reloads the page you were heading to. You never see a login form again until you press **Sign out**, which is the single action that deletes the stored code.

That trade is explicit: the phone holds a credential that opens your whole knowledge base. That is why the **biometric app lock is enabled by default**. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#authentication) for the details and [SECURITY.md](SECURITY.md) for the threat model.

## Optional features

All of these are off or configurable in **Settings**. See [docs/FEATURES.md](docs/FEATURES.md) for what each one does and why.

- **Quick capture**: a text box that appends to today's daily note, reachable from the share sheet, a launcher shortcut, a home screen widget, the floating in-app button, or an inline reply in the notification shade.
- **Voice dictation** into the capture box.
- **Camera in the file picker**, so photos go into a note without a detour through the gallery.
- **`blob:` download rescue**, so SiYuan exports actually land in your Downloads folder.
- **Biometric app lock** with a 60 second grace period, so picking a file does not re-prompt.
- **Dual address with automatic fallback**, for the LAN at home and a VPN or reverse proxy outside.
- **Force desktop interface**, useful on tablets and foldables.
- **Sync on resume**, if your kernel has sync configured.
- **Text size**, **keep screen on**, **external links in a custom tab**.

## Building

```bash
git clone https://github.com/mateof/siyuan-remote.git
cd siyuan-remote
./gradlew assembleDebug     # app/build/outputs/apk/debug/
./gradlew installDebug      # onto a connected device or emulator
./gradlew test              # unit tests
```

Requires JDK 17 and the Android SDK (compileSdk 35). Create `local.properties` with `sdk.dir=/path/to/Android/sdk` if the build cannot find it.

Release signing is optional locally: without a keystore, `assembleRelease` produces an unsigned APK. See [docs/RELEASING.md](docs/RELEASING.md).

## Documentation

- [docs/SETUP.md](docs/SETUP.md): getting the server ready, Docker, HTTPS, Tailscale, self signed certificates.
- [docs/FEATURES.md](docs/FEATURES.md): every optional feature, what it costs, when to turn it on.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): module map, authentication flow, which SiYuan endpoints are used.
- [docs/RELEASING.md](docs/RELEASING.md): version bumps, signing secrets, the release workflow.
- [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md), [CHANGELOG.md](CHANGELOG.md).

## Known limitations

- **No offline access.** The notes live on your server. Out of reach of the server means out of reach of your notes. If you need offline editing, the official app with S3 or WebDAV sync is the right tool, not this one.
- The kernel's own docs state that Docker deployments support browsers only. A WebView is a browser, so this works, but do not expect upstream support if something breaks.
- Plugins that assume the desktop or the official mobile app may behave differently.

## License

[MIT](LICENSE). SiYuan itself is AGPL-3.0 and is not bundled, modified or redistributed here; this app only connects to it over HTTP.
