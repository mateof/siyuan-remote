# Contributing

Issues and pull requests are welcome.

## Before opening an issue

- **Is it about SiYuan itself?** Report it at [siyuan-note/siyuan](https://github.com/siyuan-note/siyuan/issues). This app is unofficial and the SiYuan maintainers do not support it.
- **Does it also happen in Chrome on the same phone, pointed at the same server?** If yes, it is a SiYuan or a server issue, not an app issue. Saying so in the report saves a round trip.

Include the app version and the Android System WebView version, both shown in **Settings → About**.

## Development setup

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew test
./gradlew lint
```

JDK 17 and the Android SDK with compileSdk 35. Put `sdk.dir` in `local.properties` if the build cannot find the SDK.

You need a running SiYuan kernel to test against. See [docs/SETUP.md](docs/SETUP.md); the Docker compose snippet there is enough.

## Scope

The value of this app is in being thin. Pull requests that reimplement parts of the SiYuan editor natively will be declined; see the "Deliberately not implemented" section of [docs/FEATURES.md](docs/FEATURES.md) for the reasoning.

Anything that makes the mobile experience better *around* the editor is in scope: entry points, capture, sharing, offline-adjacent conveniences, accessibility, reliability of the connection.

## Code style

- Kotlin official style, four space indentation, trailing commas.
- Comments explain **why**, not what. If a line needs a comment to say what it does, rename something instead.
- New user-visible strings go in `res/values/strings.xml` and `res/values-es/strings.xml`.
- No em dashes in prose.

## Commits and pull requests

- Commit messages in English, imperative mood: "Add captcha rendering to setup".
- One logical change per pull request.
- Run `./gradlew test lint` before pushing. CI runs the same on every pull request.
