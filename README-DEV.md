# compose-buddy — Developer / Local Build Guide

This guide covers how to run and test compose-buddy from source without a globally installed binary.

---

## Prerequisites

- JDK 17+
- Android SDK with `adb` on your `PATH`
- A running Android emulator **or** a connected device
- `aapt2` available (shipped with Android SDK build-tools)

---

## Running the CLI from source

All commands below are run from the **repository root** (`compose-buddy/`).

### Option A — Gradle `run` task (simplest, no prior build step)

```bash
./gradlew :compose-buddy-cli:run --args="<command and flags>"
```

The `run` task's working directory is set to the **repository root**, so relative paths like `./sample` resolve correctly:

```bash
./gradlew :compose-buddy-cli:run --args="device --project ./sample --module :app"
```

### Option B — Install distribution locally (fastest for repeated runs)

```bash
./gradlew :compose-buddy-cli:installDist
```

The binary is placed at:

```
compose-buddy-cli/build/install/compose-buddy-cli/bin/compose-buddy-cli   # Unix
compose-buddy-cli/build/install/compose-buddy-cli/bin/compose-buddy-cli.bat  # Windows
```

Add it to your shell for the session:

```bash
export PATH="$PWD/compose-buddy-cli/build/install/compose-buddy-cli/bin:$PATH"
compose-buddy-cli device --project ./sample --module :app
```

---

## Device preview — full pipeline

The `device` command builds, installs, launches the preview activity, and opens the inspector in one step.

```bash
# Full pipeline (build + install + open inspector)
./gradlew :compose-buddy-cli:run --args="device --project ./sample --module :app"

# List available previews without building or installing
./gradlew :compose-buddy-cli:run --args="device --project ./sample --module :app --list --skip-build --skip-install"

# Target a specific preview by FQN
./gradlew :compose-buddy-cli:run --args="device --project ./sample --module :app --preview com.example.sample.MainPreview"

# Skip build/install if the app is already on the device
./gradlew :compose-buddy-cli:run --args="device --project ./sample --module :app --skip-build --skip-install"

# Capture one frame to disk (JSON + PNG) and exit — no inspector UI
./gradlew :compose-buddy-cli:run --args="device --project ./sample --module :app --format json --output ./out"

# Target a specific emulator/device by ADB serial
./gradlew :compose-buddy-cli:run --args="device --project ./sample --module :app --device emulator-5554"
```

### Low-level connect (manual flow)

If you have already launched the preview activity and forwarded the port manually, use `device connect` to attach:

```bash
# adb forward tcp:7890 tcp:7890   ← run this first
./gradlew :compose-buddy-cli:run --args="device connect --port 7890"

# Capture to file
./gradlew :compose-buddy-cli:run --args="device connect --port 7890 --format json --output ./out"
```

---

## Desktop inspector UI only

Open the inspector UI without triggering any build or device flow:

```bash
./gradlew :compose-buddy-inspector:run
```

---

## Building modules individually

```bash
# Build everything
./gradlew build

# Build a single module
./gradlew :compose-buddy-cli:build
./gradlew :compose-buddy-device:assembleDebug

# Run tests
./gradlew test

# Run tests for a single module
./gradlew :compose-buddy-core:test
./gradlew :compose-buddy-device-client:test
```

---

## Publishing to Maven Local

Use local publishing to test the plugin and libraries in an external project (one that is **not** using a composite build).

### Publish all modules

```bash
./gradlew :compose-buddy-core:publishToMavenLocal \
          :compose-buddy-renderer:publishToMavenLocal \
          :compose-buddy-gradle-plugin:publishToMavenLocal \
          :compose-buddy-device:publishToMavenLocal \
          :compose-buddy-device-ksp:publishToMavenLocal \
          :compose-buddy-device-client:publishToMavenLocal
```

Artifacts are published under `dev.mikepenz.composebuddy` at the version set in `gradle.properties` (`app.version`) to `~/.m2/repository`.

### Consume from an external project

Add `mavenLocal()` **before** other repositories in both `pluginManagement` and `dependencyResolutionManagement`:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}
```

Then apply the plugin as usual:

```kotlin
// build.gradle.kts
plugins {
    id("dev.mikepenz.composebuddy") version "0.1.0"
}
```

---

## Sample app

The `sample/` directory contains an Android app pre-configured with the compose-buddy Gradle plugin via a [composite build](https://docs.gradle.org/current/userguide/composite_builds.html). No local publishing is needed — Gradle resolves the plugin and library modules directly from source.

```bash
# Build the buddyDebug variant of the sample
./gradlew -p sample :app:assembleBuddyDebug

# List previews discovered by KSP in the sample
./gradlew -p sample :app:buddyPreviewList

# Install the buddyDebug APK
./gradlew -p sample :app:installBuddyDebug
```

---

## Checking ADB connectivity

```bash
adb devices          # list connected devices/emulators
adb forward --list   # check active port forwards
```

The CLI sets up `adb forward tcp:<port> tcp:<port>` automatically during the `device` pipeline. To tear it down manually:

```bash
adb forward --remove tcp:7890
```

---

## Module overview

| Module | Description |
|--------|-------------|
| `compose-buddy-core` | Shared models, `Preview`, `RenderResult`, `HierarchyNode` |
| `compose-buddy-device` | Android library — preview activity, slot-table capture, WebSocket server |
| `compose-buddy-device-ksp` | KSP processor — generates `BuddyPreviewRegistryImpl` from `@Preview` annotations |
| `compose-buddy-device-client` | JVM — WebSocket client, `FrameMapper` (device JSON → `RenderResult`) |
| `compose-buddy-gradle-plugin` | Gradle plugin — `buddyDebug` build type, KSP wiring, deploy/list tasks |
| `compose-buddy-inspector` | Compose Desktop inspector UI |
| `compose-buddy-cli` | CLI entry point (`compose-buddy-cli` binary) |
| `compose-buddy-renderer-android` | Layoutlib-based offline renderer (CI / no-device path) |
| `sample` | Example Android app using the plugin via composite build |
