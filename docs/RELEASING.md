# Releasing

Merging to `main` publishes a release. There is no manual step beyond bumping the version.

## The flow

1. Bump `appVersionName` and `appVersionCode` in [`app/build.gradle.kts`](../app/build.gradle.kts).
2. Add an entry to [`CHANGELOG.md`](../CHANGELOG.md).
3. Merge to `main`.
4. `.github/workflows/release.yml` builds a signed release APK, creates the tag `v<version>` and attaches the APK to a GitHub Release.

**Bump the version before every merge to `main`.** If the tag `v<version>` already exists the workflow stops with a clear message rather than overwriting a published release.

## Signing secrets

The release job is skipped, not failed, when `KEYSTORE_BASE64` is absent, so a fresh clone of this repo stays green before you set signing up.

Create a keystore once:

```bash
keytool -genkeypair -v \
  -keystore siyuan-remote-release.jks \
  -alias siyuan-remote \
  -keyalg RSA -keysize 4096 -validity 10000
```

Keep it out of the repo. `.gitignore` already excludes `*.jks`, `*.keystore` and `keystore.properties`, but the safe place is a password manager, not the project folder. Losing it means users cannot upgrade in place: a differently signed APK is a different app to Android.

Then add these under **Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -i siyuan-remote-release.jks \| pbcopy` |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | `siyuan-remote` |
| `KEY_PASSWORD` | Key password |

## Signing locally

Either create `keystore.properties` in the project root (git ignored):

```properties
storeFile=/absolute/path/siyuan-remote-release.jks
storePassword=...
keyAlias=siyuan-remote
keyPassword=...
```

or export `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` in the environment. With neither, `./gradlew assembleRelease` still runs and produces an unsigned APK.

## Version numbering

`appVersionName` is semantic (`0.2.0`). `appVersionCode` is a monotonically increasing integer; Android refuses to install an APK whose code is lower than the installed one, so it must go up on every release even for a patch.
