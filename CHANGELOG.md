# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[semantic versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/mateof/siyuan-remote/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/mateof/siyuan-remote/releases/tag/v0.1.0
