# Publishing compose-buddy

This document describes how to publish `compose-buddy` to **Maven Central** (library modules + the Gradle plugin as a classpath dependency) and to the **Gradle Plugin Portal** (`id("dev.mikepenz.composebuddy")` application).

Publishing is wired through the shared [`com.mikepenz:version-catalog`](https://github.com/mikepenz/convention) convention plugins (`com.mikepenz.convention.publishing`), so most configuration comes from `gradle.properties` (POM metadata) and credentials from `~/.gradle/gradle.properties` / CI env vars.

---

## What gets published

| Module                           | Target                                | Coordinates                                                   |
|----------------------------------|----------------------------------------|---------------------------------------------------------------|
| `compose-buddy-core`             | Maven Central                          | `dev.mikepenz.composebuddy:compose-buddy-core`                |
| `compose-buddy-renderer`         | Maven Central                          | `dev.mikepenz.composebuddy:compose-buddy-renderer`            |
| `compose-buddy-device`           | Maven Central                          | `dev.mikepenz.composebuddy:compose-buddy-device`              |
| `compose-buddy-device-ksp`       | Maven Central                          | `dev.mikepenz.composebuddy:compose-buddy-device-ksp`          |
| `compose-buddy-device-client`    | Maven Central                          | `dev.mikepenz.composebuddy:compose-buddy-device-client`       |
| `compose-buddy-gradle-plugin`    | Maven Central **+** Gradle Plugin Portal | `dev.mikepenz.composebuddy:compose-buddy-gradle-plugin` / `dev.mikepenz.composebuddy` plugin id |

Internal / non-publishable modules: `compose-buddy-cli`, `compose-buddy-inspector`, `compose-buddy-mcp`, `compose-buddy-renderer-android`, `compose-buddy-renderer-android-paparazzi`, `compose-buddy-renderer-desktop`, `sample`.

---

## One-time setup

### 1. Central Portal account

Sign in at https://central.sonatype.com. The `dev.mikepenz` namespace is already verified, so no new namespace request is needed.

*View Account → Generate User Token* → gives you `username` + `password`.

### 2. GPG signing key

```bash
gpg --list-secret-keys --keyid-format=long
gpg --export-secret-keys --armor <KEY_ID> > /tmp/cb-signing.key
gpg --keyserver hkps://keys.openpgp.org --send-keys <KEY_ID>
```

### 3. Gradle Plugin Portal API key

https://plugins.gradle.org/u/mikepenz → **API Keys** → *Request new API key* → gives you `gradle.publish.key` + `gradle.publish.secret`.

### 4. Local `~/.gradle/gradle.properties`

Place credentials here (never commit):

```properties
# Maven Central (Central Portal)
mavenCentralUsername=<portal token username>
mavenCentralPassword=<portal token password>

# GPG signing (vanniktech in-memory)
signingInMemoryKeyId=<8-char key id>
signingInMemoryKey=<multi-line armored key; escape newlines as \n>
signingInMemoryKeyPassword=<GPG passphrase>

# Gradle Plugin Portal
gradle.publish.key=<plugin portal key>
gradle.publish.secret=<plugin portal secret>
```

Alternatively, set the matching `ORG_GRADLE_PROJECT_*` environment variables — this is what the CI workflow does.

---

## Release checklist

1. Bump `app.version` **and** `VERSION_NAME` in `gradle.properties` (they must match).
2. Update `CHANGELOG.md` (once it exists) with the new version's notes.
3. Run `./gradlew apiCheck` — if intentional API changes, run `./gradlew apiDump` and commit the updated `.api` files.
4. Smoke-test locally (see *Verify locally* below).
5. Publish.
6. Tag + push.

---

## Verify locally

Before hitting Central, dry-run to `mavenLocal`:

```bash
./gradlew clean publishToMavenLocal --no-configuration-cache
```

Check `~/.m2/repository/dev/mikepenz/composebuddy/` for each module. For every artifact verify:

- `*.pom` contains `name`, `description`, `url`, `licenses`, `developers`, `scm`
- `*-sources.jar` and `*-javadoc.jar` exist (Dokka-generated)
- `*.asc` signatures exist alongside every file

If any POM field is missing, check the `POM_*` entries in `gradle.properties`.

---

## Publish the libraries to Maven Central

The convention plugin enables auto-release (`com.mikepenz.publishing.autorelease=true` by default), so a single task signs + uploads + validates + auto-releases:

```bash
./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
```

Publishes:
- `compose-buddy-core`
- `compose-buddy-renderer`
- `compose-buddy-device`
- `compose-buddy-device-ksp`
- `compose-buddy-device-client`
- `compose-buddy-gradle-plugin` (as a classpath dependency)

### Manual validation before release

For the first release of a new artifact ID (or any time you want to inspect the staging repo before it's final), set `com.mikepenz.publishing.autorelease=false` in `gradle.properties`, run the task, then manually click *Release* on https://central.sonatype.com/publishing. Revert the flag for subsequent releases.

### Publish a single module

```bash
./gradlew :compose-buddy-core:publishAndReleaseToMavenCentral --no-configuration-cache
```

---

## Publish the Gradle plugin to the Plugin Portal

This is a **second** publish step — Central covers the classpath dep; the Plugin Portal is what makes `id("dev.mikepenz.composebuddy")` resolvable in `plugins { }` blocks.

```bash
./gradlew :compose-buddy-gradle-plugin:publishPlugins --no-configuration-cache
```

First-time submissions go through moderation — expect a delay before the plugin appears at https://plugins.gradle.org/plugin/dev.mikepenz.composebuddy.

---

## Tag and push

```bash
VERSION=$(grep '^VERSION_NAME=' gradle.properties | cut -d= -f2)
git tag -a "v$VERSION" -m "Release $VERSION"
git push origin main --tags
```

---

## Verify

- **Maven Central** (up to ~30 min CDN sync): `https://repo1.maven.org/maven2/dev/mikepenz/composebuddy/compose-buddy-core/<version>/`
- **Plugin Portal**: `https://plugins.gradle.org/plugin/dev.mikepenz.composebuddy/<version>`
- Pull into a clean test project via Maven Central and apply the plugin via `id("dev.mikepenz.composebuddy") version "<version>"`.

---

## Troubleshooting

**`unsupported-protocol` / `401` on upload** — Central Portal requires user tokens, not your login password. Regenerate on the Portal *View Account* page.

**`PGP signature invalid`** — confirm the key is published to a keyserver (`gpg --keyserver hkps://keys.openpgp.org --send-keys <KEY_ID>`) and that `signingInMemoryKeyId` matches the subkey you exported.

**`POM is missing required fields`** — check every `POM_*` entry exists in `gradle.properties`, and that the module applies `com.mikepenz.convention.publishing`. Non-convention modules (e.g. `compose-buddy-gradle-plugin`) get POM metadata via `com.vanniktech.maven.publish` reading the same properties.

**`Unknown host target` / Kotlin/Native errors on publish** — you're on an unusual host arch. The `com.mikepenz.native.enabled=false` + `composeNative.enabled=false` flags in `gradle.properties` already disable native targets for the publish pipeline; verify they're set.

**Plugin Portal says "plugin not found"** — the first publish goes through manual moderation. Give it 1–2 business days.

**ABI break from `apiCheck`** — either intentional (run `apiDump` and commit), or a genuine regression (fix the module).

---

## CI

CI is configured in `.github/workflows/ci.yml`. On a tag push it runs:

```
./gradlew publishAndReleaseToMavenCentral
./gradlew :compose-buddy-gradle-plugin:publishPlugins
```

The following GitHub Secrets must be configured on the repository:

| Secret                    | Purpose                          |
|---------------------------|----------------------------------|
| `NEXUS_USERNAME`          | Central Portal username          |
| `NEXUS_PASSWORD`          | Central Portal password          |
| `SIGNING_KEY_ID`          | GPG key ID                       |
| `SIGNING_PRIVATE_KEY`     | ASCII-armored private key        |
| `SIGNING_PASSWORD`        | GPG passphrase                   |
| `GRADLE_PUBLISH_KEY`      | Gradle Plugin Portal key         |
| `GRADLE_PUBLISH_SECRET`   | Gradle Plugin Portal secret      |

The workflow maps them to `ORG_GRADLE_PROJECT_*` env vars that vanniktech's plugin reads.
