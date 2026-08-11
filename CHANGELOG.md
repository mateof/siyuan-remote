# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[semantic versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.4.0]

### Changed
- The app lock now waits **30 minutes** by default before asking again, instead
  of 60 seconds, and the wait is a setting: every time you leave, 1, 5, 15, 30
  or 60 minutes, or only when the app starts. A minute was short enough that
  reading one linked page and coming back meant another fingerprint, which is
  friction without much security to show for it.

## [0.3.0]

### Fixed
- Pasting with formatting. SiYuan's own paste calls
  `navigator.clipboard.read()`, which needs the `clipboard-read` permission, and
  a WebView has no way to grant it: there is no prompt and no API. The call
  rejected and SiYuan told you to allow clipboard access in the browser address
  bar, advice with no meaning inside an app, so pasting kept plain text only.

  The app now supplies that API instead of asking for the permission, backed by
  Android's clipboard where the HTML copy actually lives. SiYuan's code is
  untouched. Turn it off in Settings → Advanced.

### Changed
- Relicensed from MIT to **GPL-3.0-or-later**. The full licence text is in
  LICENSE and every source file carries an SPDX notice. All dependencies are
  Apache-2.0, which is compatible with GPL-3.0.

## [0.2.2]

### Changed
- The floating button can be dragged anywhere on screen and stays where you put
  it. Pinned to the bottom end corner it kept landing on top of controls
  underneath and swallowing taps meant for them. The position is stored as a
  fraction of the free space, so it holds its relative place across rotation and
  screen sizes, and it is clamped so the control is always fully visible,
  including when expanding makes it taller near an edge.

## [0.2.1]

### Fixed
- Coming back from a link opened in the browser no longer loses your place and no
  longer asks for biometrics again. Three separate faults added up to that:
  - The lock grace period was measured from when you unlocked, not from when you
    left the app, so a minute of ordinary use was enough to re-arm it on every
    return.
  - Locking replaced the content instead of covering it, which released the
    WebView and destroyed the page you were on. The lock is now an opaque
    overlay drawn on top, and it swallows touches since the content is live
    underneath.
  - The WebView kept no state at all, so any activity recreation started over.
    Its navigation history is now saved and restored, guarded by an origin check
    so a history from a previous server address cannot resurface.

## [0.2.0]

### Added
- In app updates. A daily background check asks this project's GitHub releases for a
  newer version, notifies you when one exists, and downloads plus installs it without
  leaving the app. Reachable from **Settings → Updates** with a manual check, and each
  version can be skipped individually. Turn the whole thing off with one switch.

### Changed
- The credential field is now labelled **Password**, not "access authorization code".
  SiYuan's own interface calls it the lock screen password (Settings → About), and the
  old label matched nothing the user could see anywhere. The hint now points at both
  that label and the `--accessAuthCode` Docker flag, which are the same value.

### Fixed
- Release notes no longer swallow the changelog's trailing link definitions.

## [0.1.0]

First release.

### Added
- WebView client for a self-hosted SiYuan kernel, with the SiYuan mobile UI served by your own server.
- Session that does not expire: the access authorization code is stored encrypted in the Android Keystore and replayed silently when the kernel's 30 day cookie runs out.
- Biometric app lock, on by default, with a 60 second grace period.
- Primary and fallback server addresses with automatic probing and a remembered winner.
- Quick capture into today's daily note from the share sheet, a launcher shortcut, a home screen widget, a floating in-app button and an inline notification reply.
- Voice dictation in the capture box.
- Camera offered alongside the document picker when the editor asks for an image.
- `blob:` download rescue, so SiYuan exports reach the Downloads folder.
- Captcha rendering when the kernel demands one after repeated failed logins.
- Back button integrated with the SiYuan frontend panel and dialog stack.
- Force desktop interface, text size, keep screen on, external links in a custom tab, sync on resume, allow mixed content.
- Android System WebView version check with a warning below Chromium 108.
- English and Spanish translations.

[Unreleased]: https://github.com/mateof/siyuan-remote/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/mateof/siyuan-remote/releases/tag/v0.4.0
[0.3.0]: https://github.com/mateof/siyuan-remote/releases/tag/v0.3.0
[0.2.2]: https://github.com/mateof/siyuan-remote/releases/tag/v0.2.2
[0.2.1]: https://github.com/mateof/siyuan-remote/releases/tag/v0.2.1
[0.2.0]: https://github.com/mateof/siyuan-remote/releases/tag/v0.2.0
[0.1.0]: https://github.com/mateof/siyuan-remote/releases/tag/v0.1.0
