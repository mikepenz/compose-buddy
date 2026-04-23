# Compose Buddy

> Render, inspect, and analyze Jetpack Compose `@Preview` composables — outside the IDE, on devices, and from AI agents.

One command builds your app, deploys to a device or emulator, and opens an interactive inspector with full hierarchy, semantics, typography, colors, and pixel‑accurate screenshots. Headless rendering and an MCP server make the same data available to CI pipelines and AI coding agents.

---

## Highlights

- **On‑device preview** — real runtime, pixel‑accurate, with live re‑render, tap injection, and config changes (dark mode, font scale, locale).
- **Interactive inspector** — Compose Desktop window: component tree, preview canvas, properties panel, distance overlays, timeline scrubber.
- **Offline renderers** — Android (layoutlib / Paparazzi) and Desktop (Skia / `ImageComposeScene`) backends for CI and headless use.
- **Accessibility analysis** — content descriptions, touch targets, contrast, optional design‑token checks.
- **Rich hierarchy export** — bounds, text, colors, typography, shapes, semantics, modifier chain, source locations.
- **Multi‑preview support** — `@PreviewLightDark`, custom multi‑preview annotations, `@PreviewParameter`.
- **MCP server** — expose previews to Claude, Cursor, and any MCP client.
- **Agent skill installer** — one command installs compose‑buddy skills into Claude, Gemini, Copilot, Cursor, Windsurf, Cline, Junie.

---

## Install

### Homebrew (macOS / Linux)

```bash
brew tap mikepenz/tap
brew install mikepenz/tap/compose-buddy
```

This installs the `compose-buddy` CLI on your `PATH`.

### From source

```bash
git clone https://github.com/mikepenz/compose-buddy
cd compose-buddy
./gradlew :compose-buddy-cli:installDist
# binary at compose-buddy-cli/build/install/compose-buddy-cli/bin/compose-buddy
```

### Requirements

- JDK 21+
- Kotlin 2.3+
- Android device/emulator and `adb` on `PATH` (only for `device` command)

---

## Quick Start

### 1. Apply the Gradle plugin

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("dev.mikepenz.composebuddy")
}
```

The plugin auto‑configures everything: a `buddyDebug` build type, KSP preview registry generation, and the `buddyPreviewList` / `buddyPreviewDeploy` tasks. No extra Gradle wiring required.

### 2. Render a preview

```bash
compose-buddy render --module :app --preview com.example.ui.GreetingPreview
```

Output: PNG in `build/compose-buddy/`, plus metadata.

### 3. Open the interactive inspector on a device

```bash
compose-buddy device --module :app
```

Builds `:app:assembleBuddyDebug`, installs, launches `BuddyPreviewActivity`, opens the inspector window.

---

## CLI Reference

Global flags: `-v, --verbose`, `--version`.

### `render` — render `@Preview` composables to PNG

```bash
compose-buddy render [--module :app] [--preview <fqn|glob>] [options]
```

Key options:

| Flag | Description |
|---|---|
| `--project <dir>` | Gradle project root (default `.`) |
| `--module <path>` | Gradle module, e.g. `:app` |
| `--preview <fqn|glob>` | FQN or glob filter |
| `--output <dir>` | Output directory (default `build/compose-buddy`) |
| `--width`, `--height` | Override size in dp |
| `--density` | Override density dpi (e.g. 420, 560) |
| `--locale` | BCP‑47 locale (`en`, `ja`, …) |
| `--font-scale` | e.g. `1.0`, `1.5`, `2.0` |
| `--dark-mode` | Force night mode |
| `--uiMode <hex>` | Override raw uiMode bitmask |
| `--device <spec>` | `id:pixel_5` or `spec:width=1080px,height=1920px,dpi=420` |
| `--show-background`, `--background-color <argb>` | Background control |
| `--show-system-ui` | Include status/nav bars |
| `--api-level <n>` | Target SDK level |
| `--renderer` | `auto` \| `android` \| `android-direct` \| `desktop` |
| `--format` | `human` \| `json` \| `agent` |
| `--agent` | Condensed JSON for AI agents |
| `--hierarchy` | Include hierarchy JSON |
| `--semantics <fields>` | `all`, `default`, or comma list (`text,role,onClick`) |
| `--max-params <n>` | Cap `@PreviewParameter` expansions (default 10) |
| `--build` | Run Gradle build before rendering |
| `--diagnose` | Print diagnostics and exit |

### `inspect` — dump hierarchy + semantics

```bash
compose-buddy inspect --module :app --preview <fqn> [--ui]
```

`--ui` launches the interactive inspector window. Other options: `--semantics`, `--modifiers`, `--source-locations`, `--format json|human`, sizing/locale/font‑scale/dark‑mode overrides, `--renderer`, `--max-frames <n>`.

### `analyze` — accessibility & design analysis

```bash
compose-buddy analyze --module :app --checks all
```

`--checks`: `content-description`, `touch-target`, `contrast`, or `all`. Optional `--design-tokens <file.json>`. Same sizing/renderer overrides as `render`.

### `device` — on‑device preview with WebSocket bridge

```bash
compose-buddy device --module :app [--preview <fqn>] [--port 7890]
```

Builds `:<module>:assembleBuddyDebug`, installs the APK, forwards TCP `7890`, launches `BuddyPreviewActivity`, connects over WebSocket.

Options: `--device <adb-serial>`, `--format inspector|json|png`, `--output <file>` (capture single frame and exit), `--skip-build`, `--skip-install`, `--list`.

Subcommand `device connect` attaches to an already‑running activity:

```bash
compose-buddy device connect --host localhost --port 7890 [--stream]
```

### `serve` — MCP server

```bash
compose-buddy serve --module :app
```

Transport: `stdio` (default). Tools exposed to MCP clients:

- `list_previews` — filter by module/glob
- `render_preview` — PNG + optional hierarchy
- `inspect_hierarchy` — layout tree
- `analyze_accessibility` — a11y checks

### `install` — install agent skills

```bash
compose-buddy install --system all            # all supported agents
compose-buddy install --system claude --global
compose-buddy install --list
```

Supported targets: `claude`, `gemini`, `copilot`, `cursor`, `windsurf`, `cline`, `junie`, `all`. Installs `compose-buddy-render`, `compose-buddy-a11y`, `compose-ui-loop` skills project‑local or with `--global`.

---

## Rendering Backends

| Backend | Selected by | Notes |
|---|---|---|
| **android** | `--renderer android` (or auto with Android cp) | Paparazzi + layoutlib hybrid; highest fidelity |
| **android-direct** | `--renderer android-direct` | layoutlib only, no Paparazzi |
| **desktop** | `--renderer desktop` | Skia `ImageComposeScene`; fast, JVM‑only |
| **auto** | default | Desktop first, falls back to `android-direct` |

Each backend runs in an isolated classpath to prevent Android/JVM artifact mixing.

---

## On‑Device Protocol

The `buddyDebug` variant bundles `compose-buddy-device` (WebSocket server + `BuddyPreviewActivity`) and `compose-buddy-device-ksp` (generates `BuddyPreviewRegistry` from discovered `@Preview` functions).

Commands (`BuddyCommand`):

- `Rerender`
- `Tap(x, y)`
- `SetConfig(darkMode, fontScale, locale)`
- `Navigate(preview)`
- `RequestPreviewList`

Frames (`BuddyFrame`):

- `RenderFrame` — base64 PNG, semantics tree, slot table, timing, density
- `PreviewListFrame` — list of preview FQNs

Clients can embed `compose-buddy-device-client` to speak the protocol directly.

---

## Modules

| Module | Purpose |
|---|---|
| `compose-buddy-core` | Serialization, logging, version metadata |
| `compose-buddy-renderer` | Renderer abstraction, preview discovery |
| `compose-buddy-renderer-android` | layoutlib backend |
| `compose-buddy-renderer-android-paparazzi` | Paparazzi integration |
| `compose-buddy-renderer-desktop` | `ImageComposeScene` backend |
| `compose-buddy-gradle-plugin` | `dev.mikepenz.composebuddy` plugin |
| `compose-buddy-cli` | `compose-buddy` binary |
| `compose-buddy-inspector` | Desktop inspector UI |
| `compose-buddy-mcp` | MCP server |
| `compose-buddy-device` | Device runtime (WebSocket server, activity) |
| `compose-buddy-device-ksp` | KSP registry generator |
| `compose-buddy-device-client` | Client‑side protocol bindings |

All libraries publish under group `dev.mikepenz.composebuddy`. Current version: see `gradle.properties`.

---

## MCP + AI Agents

Start the MCP server from a project root:

```bash
compose-buddy serve --module :app
```

Point your MCP‑capable client (Claude Desktop, Cursor, etc.) at the `stdio` transport. Or install ready‑made skills:

```bash
compose-buddy install --system claude
```

---

## License

Apache 2.0 — see [LICENSE](./LICENSE).

Built by [@mikepenz](https://github.com/mikepenz).
