# Compose Buddy Validation Projects

This document lists projects used to validate compose-buddy's CLI rendering.
It should be expanded as new projects are tested. Use it to verify that changes
don't break rendering across different project types.

## Quick Start

```bash
# Build compose-buddy CLI
./gradlew :compose-buddy-cli:installDist

# Render a project (auto-detects renderer + compiles if --build is passed)
./compose-buddy-cli/build/install/compose-buddy-cli/bin/compose-buddy-cli render \
  --project /path/to/project \
  --module :moduleName \
  --build \
  --output /tmp/cb-validate/project-name \
  --format human
```

### Key CLI Options

| Option | Description |
|--------|-------------|
| `--project` | Path to Gradle project root |
| `--module` | Gradle module path (e.g., `:app`, `:composeApp`) |
| `--build` | Auto-detect and run the correct compile task before rendering |
| `--renderer` | `auto` (default), `android`, `android-direct`, `desktop` |
| `--output` | Output directory for PNGs and manifest.json |
| `--format` | `human` (readable) or `json` (manifest to stdout) |
| `--preview` | Filter by FQN or glob (e.g., `com.example.*`) |

## Validation Projects

### agent-approver

| | |
|---|---|
| **Path** | `/Users/mikepenz/Development/Misc/agent-approver` |
| **Module** | `:composeApp` |
| **Type** | KMP Desktop (Compose Multiplatform) |
| **Renderer** | `desktop` (auto-detected) |
| **Build task** | `compileKotlinJvm` (auto-detected) |
| **Previews** | 30 discovered |
| **Result** | 30/30 rendered, 0 errors |

```bash
compose-buddy render \
  --project /Users/mikepenz/Development/Misc/agent-approver \
  --module :composeApp --build --output /tmp/cb-validate/agent-approver
```

### sample (internal)

| | |
|---|---|
| **Path** | `./sample` (relative to compose-buddy root) |
| **Module** | `:app` |
| **Type** | Android app (Compose Material 3) |
| **Renderer** | `android` (auto-detected, Paparazzi) |
| **Build task** | `compileDebugKotlin` (auto-detected) |
| **Previews** | 26 discovered (13 unique + multi-preview + parameterized) |
| **Result** | 26/26 rendered, 0 errors |

```bash
compose-buddy render \
  --project ./sample \
  --module :app --build --output /tmp/cb-validate/sample
```

### AboutLibraries

| | |
|---|---|
| **Path** | `/Users/mikepenz/Development/AndroidLibraries/AboutLibraries` |
| **Module** | `:aboutlibraries-compose-m3` |
| **Type** | KMP library (Android + JVM + iOS + JS + Wasm) |
| **Renderer** | `desktop` (auto-detected via jvmMain) |
| **Build task** | `compileKotlinJvm` (auto-detected) |
| **Previews** | 1 discovered |
| **Result** | 1/1 rendered, 0 errors |

```bash
compose-buddy render \
  --project /Users/mikepenz/Development/AndroidLibraries/AboutLibraries \
  --module :aboutlibraries-compose-m3 --build --output /tmp/cb-validate/aboutlibraries
```

### multiplatform-markdown-renderer

| | |
|---|---|
| **Path** | `/Users/mikepenz/Development/AndroidLibraries/multiplatform-markdown-renderer` |
| **Module** | `:multiplatform-markdown-renderer-m3` |
| **Type** | KMP library (Android + JVM + iOS + JS + Wasm) |
| **Renderer** | `desktop` (auto-detected) |
| **Build task** | `compileKotlinJvm` (auto-detected) |
| **Previews** | 52 discovered |
| **Result** | 52/52 rendered, 0 errors |

```bash
compose-buddy render \
  --project /Users/mikepenz/Development/AndroidLibraries/multiplatform-markdown-renderer \
  --module :multiplatform-markdown-renderer-m3 --build --output /tmp/cb-validate/markdown-renderer
```

### MegaYatzyNextGen

| | |
|---|---|
| **Path** | `/Users/mikepenz/Development/AndroidApps/MegaYatzyNextGen` |
| **Module** | `:app:shared` |
| **Type** | KMP app (Android + Desktop + iOS + Web) |
| **Renderer** | `desktop` (auto-detected) |
| **Build task** | `compileKotlinJvm` (auto-detected) |
| **Previews** | 3 discovered |
| **Result** | 3/3 rendered, 0 errors |

```bash
compose-buddy render \
  --project /Users/mikepenz/Development/AndroidApps/MegaYatzyNextGen \
  --module :app:shared --build --output /tmp/cb-validate/megayatzy
```

## Adding a New Validation Project

To add a project to this list:

1. Run the CLI against it and record the results
2. Add an entry with the project path, module, type, renderer, expected preview count
3. Note any known issues or special requirements
4. Include the exact CLI command to reproduce

## Validation Checklist

When validating a change, run all projects and verify:

- [ ] All expected previews are discovered
- [ ] All expected previews render without errors
- [ ] Output images contain visible, correct content
- [ ] `manifest.json` includes hierarchy with semantics
- [ ] `--build` flag correctly compiles the project
- [ ] Auto-detection selects the correct renderer

## Known Limitations

- **Theme background**: The `android-direct` renderer renders on transparent background
  instead of Material theme background. Use `android` (Paparazzi) renderer for themed output.
- **Desktop renderer**: Does not support Android-only composables (e.g., those using
  `LocalContext.current` for Android Context).
- **Build detection**: The `--build` flag auto-detects compile tasks for most project types
  (Android, KMP, plain JVM). For non-standard setups, build manually and omit `--build`.
