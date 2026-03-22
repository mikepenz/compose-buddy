# Compose Buddy

Render and inspect Jetpack Compose `@Preview` composables outside the IDE. One command builds, deploys to a device/emulator, and opens an interactive inspector with full hierarchy, semantics, typography, colors, and pixel-accurate screenshots.

## Features

- **On-device preview** — renders on a real device/emulator for pixel-accurate output with full Android runtime
- **Interactive inspector UI** — Compose Desktop window with component tree, properties panel, zoom/pan, and distance overlays
- **Rich hierarchy extraction** — bounds, text, colors, typography (fontSize, fontWeight, fontFamily), shapes, semantics, relative positioning
- **Multi-preview support** — `@PreviewLightDark`, custom multi-preview annotations, `@PreviewParameter`
- **Accessibility analysis** — missing content descriptions, touch target sizes, contrast
- **Offline rendering** — Android (layoutlib) and Desktop (Skia) backends for CI/headless use
- **MCP server** for AI agent integration
- **AI agent skill installer** — one command to install skills into Claude, Gemini, Copilot, Cursor, and more

## Quick Start

### Prerequisites

- JDK 17+
- Android device or emulator (for on-device preview)
- `adb` on PATH

### 1. Apply the Gradle plugin

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("dev.mikepenz.composebuddy")  // auto-configures everything
}
```

The plugin automatically:
- Creates a `buddyDebug` build type
- Applies KSP and registers the preview scanner
- Registers `buddyPreviewList` and `buddyPreviewDeploy` Gradle tasks

### 2. Build the CLI

```bash
./gradlew :compose-buddy-cli:installDist
```

### 3. Run

```bash
# Build, install on device, and open the interactive inspector
compose-buddy device --project /path/to/project --module :app

# List available previews
compose-buddy device --project /path/to/project --module :app --list

# Launch a specific preview
compose-buddy device --project /path/to/project --module :app \
  --preview "com.example.PreviewsKt.MyPreview"

# Capture a single frame as PNG + JSON
compose-buddy device --project /path/to/project --module :app \
  --output ./out --preview "com.example.PreviewsKt.MyPreview"
```

That's it. One command handles the entire flow: Gradle build → APK install → ADB port forward → activity launch → WebSocket connect → inspector UI.

### Offline Rendering (CI / Headless)

For environments without a device/emulator:

```bash
# Render all @Preview composables as PNG images
compose-buddy render --project /path/to/project --module :app --output ./previews

# Use the desktop renderer (no Android SDK needed)
compose-buddy render --project /path/to/project --module :app \
  --renderer desktop --output ./previews
```

### Output

Each render produces PNG images and a JSON manifest:

```
previews/
├── manifest.json
├── com_example_ui_ButtonPreview.png
├── com_example_ui_CardPreview.png
├── com_example_ui_ThemePreview_Light.png      ← multi-preview variant
├── com_example_ui_ThemePreview_Dark.png
├── com_example_ui_GreetingPreview_0.png       ← @PreviewParameter index 0
├── com_example_ui_GreetingPreview_1.png
└── ...
```

## CLI Reference

### `compose-buddy render`

Render `@Preview` composables as PNG images.

**Project options:**

| Flag | Description | Default |
|------|-------------|---------|
| `--project` | Gradle project root | `.` |
| `--module` | Gradle module (e.g., `:app`) | auto-discover |
| `--preview` | FQN filter (supports `*` glob) | all |
| `--output` | Output directory | `build/compose-buddy` |
| `--build` | Build the project before rendering | `false` |
| `--renderer` | Renderer backend: `android`, `android-direct`, `desktop`, `auto` | `auto` |

**@Preview parameter overrides:**

| Flag | Description | Default |
|------|-------------|---------|
| `--widthDp` | Override width in dp | from `@Preview` |
| `--heightDp` | Override height in dp | from `@Preview` |
| `--density` | Override density in dpi (e.g., 420, 480, 560) | from `@Preview` |
| `--locale` | Override locale (BCP 47, e.g., `en`, `ja`) | from `@Preview` |
| `--fontScale` | Override font scale (e.g., 1.0, 1.5, 2.0) | from `@Preview` |
| `--dark-mode` | Enable dark mode (sets uiMode to `UI_MODE_NIGHT_YES`) | `false` |
| `--uiMode` | Override uiMode bitmask (e.g., `0x21`) | from `@Preview` |
| `--device` | Device ID or spec (e.g., `id:pixel_5`, `spec:width=1080px,height=1920px,dpi=420`) | from `@Preview` |
| `--showBackground` | Show background behind composable | from `@Preview` |
| `--backgroundColor` | Background ARGB color (e.g., `0xFFFFFFFF`) | from `@Preview` |
| `--showSystemUi` | Show system UI decorations | `false` |
| `--apiLevel` | Target SDK API level | from `@Preview` |

**Output options:**

| Flag | Description | Default |
|------|-------------|---------|
| `--format` | Output format: `json`, `human`, or `agent` | auto-detect |
| `--agent` | Condensed JSON for AI agent validation | `false` |
| `--hierarchy` | Include hierarchy JSON in output | `false` |
| `--semantics` | Semantic fields: `all`, `default`, or comma-separated (e.g., `text,role,onClick`) | `default` |
| `--max-params` | Max `@PreviewParameter` values to render | `10` |

### `compose-buddy inspect`

Inspect layout hierarchy of `@Preview` composables. Can export hierarchy JSON or launch an interactive inspector window.

| Flag | Description | Default |
|------|-------------|---------|
| `--project` | Gradle project root | `.` |
| `--module` | Gradle module | auto-discover |
| `--preview` | FQN filter | all |
| `--ui` | Launch interactive inspector window | `false` |
| `--semantics` | Include semantics | `true` |
| `--modifiers` | Include modifier chain | `false` |
| `--source-locations` | Include source file/line | `false` |
| `--format` | Output format: `json` or `human` | `json` |
| `--renderer` | Renderer backend | `auto` |
| `--max-frames` | Frame history limit for inspector UI | `100` |
| `--width` | Override width in dp | from `@Preview` |
| `--height` | Override height in dp | from `@Preview` |
| `--locale` | Override locale | from `@Preview` |
| `--font-scale` | Override font scale | from `@Preview` |
| `--dark-mode` | Enable dark mode | `false` |

### `compose-buddy analyze`

Run accessibility and design spec analysis on `@Preview` composables.

| Flag | Description | Default |
|------|-------------|---------|
| `--project` | Gradle project root | `.` |
| `--module` | Gradle module | auto-discover |
| `--preview` | FQN filter | all |
| `--output` | Output directory | `build/compose-buddy` |
| `--checks` | Checks to run: `content-description`, `touch-target`, `contrast`, `all` | `all` |
| `--design-tokens` | Path to design tokens JSON file | none |
| `--format` | Output format: `json` or `human` | auto-detect |
| `--renderer` | Renderer backend | `auto` |
| `--widthDp` | Override width | from `@Preview` |
| `--heightDp` | Override height | from `@Preview` |
| `--density` | Override density | from `@Preview` |
| `--fontScale` | Override font scale | from `@Preview` |
| `--dark-mode` | Enable dark mode | `false` |

Returns exit code 1 if any findings are reported, 0 if clean.

### `compose-buddy serve`

Start an MCP server for AI agent access.

| Flag | Description | Default |
|------|-------------|---------|
| `--project` | Gradle project root | `.` |
| `--module` | Gradle module | auto-discover |
| `--transport` | MCP transport protocol | `stdio` |

### `compose-buddy install`

Install compose-buddy skills into AI coding agents.

| Flag | Description | Default |
|------|-------------|---------|
| `--system, -s` | Target system: `claude`, `gemini`, `copilot`, `cursor`, `windsurf`, `cline`, `junie`, or `all` | `all` |
| `--project` | Project directory to install into | `.` |
| `--global` | Install globally (user-level) instead of project-level | `false` |
| `--list` | List available skills without installing | `false` |

Available skills: `compose-buddy-render`, `compose-buddy-a11y`, `compose-ui-loop`.

## Interactive Inspector UI

Launch the inspector with the `--ui` flag:

```bash
compose-buddy inspect --project /path/to/project --module :app --ui
```

This opens a Compose Desktop window (1280x800) with four panels:

### Component Tree (left panel)

Hierarchical view of all composable nodes. Each node shows:
- Type badge (`T` = Text, `L` = Layout, `E` = Element, `B` = Box, `C` = Column, `R` = Row, `I` = Image/Icon, `Btn` = Button, `S` = Screen)
- Component name and size in dp
- Position if not at origin

Click a node to select it. Hover to highlight.

### Preview Pane (center)

Rendered preview image with interactive overlays:
- **Zoom/pan** — scroll to zoom, drag to pan
- **Click to select** — click any component in the image to select it
- **Selection overlay** — blue border and semi-transparent fill on selected component, with dimension label (W x H dp)
- **Edge distances** — purple dashed lines showing distance from selected component to screen edges (left, right, top, bottom) in dp
- **Inset distances** — when hovering a parent or child of the selected component, orange dashed lines show the inset distances between their edges
- **Sibling gaps** — when hovering a sibling, shows horizontal and vertical gap measurements

### Properties Panel (right panel)

Detailed properties for the selected component:
- **Size** — width and height in dp
- **Margins** — distances from screen edges (L/T/R/B or compact format if uniform)
- **Padding** — offset from parent edges
- **Typography** — fontSize, fontWeight, fontFamily, lineHeight, letterSpacing
- **Colors** — all color properties with hex swatches, optional RGB values
- **Delta E** — CIE76 perceptual contrast between foreground and background colors
- **Semantics** — all semantic properties (text, contentDescription, role, etc.)

### Timeline (bottom)

Frame history scrubber for navigating through render history (up to 100 frames). Each re-render adds a new frame.

### Toolbar

- Preview dropdown selector and variant filter chips (Light/Dark, locale, font scale, dimensions)
- Re-render button with device preset selector (Pixel 9, Pixel 9 Pro, Pixel 9 Pro XL, Pixel 10, Galaxy S24, Galaxy S24 Ultra, Desktop 1080p, Desktop 1440p, Tablet 10", Custom)
- Settings toggle for theme mode (Auto/Light/Dark) and render configuration

## On-Device Preview

The recommended way to use Compose Buddy. Renders `@Preview` composables on a real Android device or emulator for pixel-accurate output with full Android runtime APIs — no classpath issues, no layoutlib quirks.

### What the CLI handles automatically

```
compose-buddy device --project . --module :app
```

This single command:
1. Builds the `buddyDebug` APK via Gradle
2. Installs it on the connected device/emulator
3. Discovers available `@Preview` composables
4. Forwards the WebSocket port via ADB
5. Launches the preview activity on device
6. Connects and opens the interactive inspector UI

### CLI Reference

```bash
# Build, install, and open inspector with first available preview
compose-buddy device --project . --module :app

# List available previews
compose-buddy device --project . --module :app --list

# Launch a specific preview
compose-buddy device --project . --module :app --preview "com.example.PreviewsKt.MyPreview"

# Capture a single frame as PNG + JSON to a directory
compose-buddy device --project . --module :app --output ./out \
  --preview "com.example.PreviewsKt.MyPreview"

# Stream JSON frames to stdout
compose-buddy device --project . --module :app --format json

# Skip build (reuse existing APK) for faster iteration
compose-buddy device --project . --module :app --skip-build

# Low-level: connect to an already-running preview (no build/install)
compose-buddy device connect --port 7890 --format inspector
```

| Flag | Description | Default |
|------|-------------|---------|
| `--project` | Gradle project root | `.` |
| `--module` | Gradle module (e.g., `:app`) | required |
| `--preview` | Preview FQN to launch | first available |
| `--port` | WebSocket port | `7890` |
| `--device` | ADB device serial | auto-detect |
| `--format` | Output: `inspector`, `json`, `png` | `inspector` |
| `--output` | Capture one frame to directory, then exit | stream |
| `--skip-build` | Skip Gradle build | `false` |
| `--skip-install` | Skip build and install | `false` |
| `--list` | List available previews and exit | `false` |

### Data Captured from Device

Each frame includes a pixel-accurate screenshot plus a rich hierarchy tree with:

| Data | Source |
|------|--------|
| **Screenshot** | PixelCopy (API 26+) or drawToBitmap fallback |
| **Semantics** | Full dynamic iteration of all `SemanticsPropertyKey` entries |
| **Typography** | fontSize, fontWeight, fontFamily, lineHeight, letterSpacing (via slot table) |
| **Colors** | backgroundColor, foregroundColor, contentColor (via slot table + pixel sampling) |
| **Layout** | bounds, boundsInParent, offsetFromParent, size (all in dp) |
| **Shapes** | RoundedCornerShape, CircleShape, etc. (via slot table) |
| **Source** | Source file path + line number (via slot table) |
| **Accessibility** | role, contentDescription, testTag, onClick, disabled, focused, etc. |

### How It Works

```
compose-buddy device --project . --module :app
        │
        ├─ ./gradlew :app:assembleBuddyDebug
        ├─ adb install -r app-buddyDebug.apk
        ├─ ./gradlew :app:buddyPreviewList → selects preview FQN
        ├─ adb forward tcp:7890 tcp:7890
        ├─ adb shell am start ... BuddyPreviewActivity -e preview <fqn>
        │
        ▼
   BuddyPreviewActivity (on device)
        ├─ Looks up preview in KSP-generated BuddyPreviewRegistry
        ├─ Hosts the composable via setContent {}
        ├─ Starts WebSocket server on port 7890
        └─ On "rerender" command: captures screenshot + semantics + slot table
                │
           WebSocket (BuddyFrame JSON)
                │
                ▼
   Host CLI / Inspector UI
        └─ FrameMapper → RenderResult + HierarchyNode → Inspector / JSON / PNG
```

### MCP Integration

The MCP tools `renderPreview` and `inspectHierarchy` accept a `source: "device"` parameter:

```json
{"tool": "renderPreview", "arguments": {"previewFqn": "...", "source": "device"}}
```

## Renderer Comparison

| | Device (recommended) | Layoutlib (offline) | Desktop (KMP) |
|---|---|---|---|
| **Command** | `compose-buddy device` | `compose-buddy render --renderer android` | `compose-buddy render --renderer desktop` |
| **Rendering** | Real Android runtime | Simulated via layoutlib | Skia (ImageComposeScene) |
| **Accuracy** | Pixel-identical to production | High (minor differences) | Approximate |
| **API support** | Full (camera, network, sensors) | Limited | Desktop APIs only |
| **Hierarchy depth** | Full (semantics + slot table) | Full (semantics + slot table) | Basic |
| **Requires** | Device/emulator + ADB | Android SDK (`ANDROID_HOME`) | Nothing |
| **Best for** | Development, interactive inspection | CI pipelines, headless environments | KMP Desktop projects |

## Hierarchy Data

Each rendered preview includes a layout hierarchy. All dimensions are in **dp** — convert to pixels: `px = dp * densityDpi / 160`.

| Field | Description |
|-------|-------------|
| `name` | Node type: `Text`, `Button`, `Layout`, `Element`, `Clickable`, `Container` |
| `sourceFile` | Source file path (e.g., `com/example/ui/Card.kt`) |
| `sourceLine` | Line number of the `@Preview` function |
| `bounds` | Absolute position in dp `{left, top, right, bottom}` |
| `boundsInParent` | Position relative to parent |
| `size` | Size in dp `{width, height}` |
| `offsetFromParent` | Distance from parent edges in dp (inferred padding) |
| `semantics` | Semantic properties map |
| `children` | Child nodes |

### Semantic Properties

Default set (always included):

| Property | Description |
|----------|-------------|
| `text` | Text content from `Text()` composables |
| `contentDescription` | Accessibility description |
| `role` | Semantic role: `Button`, `Checkbox`, `Switch`, etc. |
| `onClick` | Whether element is clickable |
| `testTag` | Test tag for UI testing |

Color properties (from pixel sampling):

| Property | Description |
|----------|-------------|
| `backgroundColor` | Dominant color at element edges |
| `foregroundColor` | Color at element center (if different from background) |
| `dominantColor` | Center color for leaf nodes |

Use `--semantics all` to include all available properties, or `--semantics text,role,onClick` for a custom set.

## JSON Output (for AI agents)

```bash
compose-buddy render --project ./my-app --module :app --output ./out --format json
```

```json
{
  "version": "1.0.0",
  "densityDpi": 420,
  "availableSemantics": ["text", "contentDescription", "role", "onClick", "backgroundColor"],
  "results": [{
    "previewName": "com.example.ui.CardPreview",
    "imagePath": "/out/com_example_ui_CardPreview.png",
    "imageWidth": 562,
    "imageHeight": 1000,
    "hierarchy": {
      "name": "Layout",
      "sourceFile": "com/example/ui/Card.kt",
      "sourceLine": 42,
      "size": {"width": 135.0, "height": 48.0},
      "offsetFromParent": {"left": 16.0, "top": 16.0, "right": 228.0, "bottom": 16.0},
      "semantics": {"backgroundColor": "#FFE6E0E9"},
      "children": [
        {
          "name": "Text",
          "size": {"width": 86.0, "height": 24.0},
          "semantics": {"text": "Hello World"}
        }
      ]
    }
  }]
}
```

For condensed AI agent output, use `--agent`:

```bash
compose-buddy render --project ./my-app --module :app --output ./out --agent
```

## Architecture

```
CLI (compose-buddy-cli)
  → PreviewDiscovery (bytecode scanning for @Preview)
  → Renderer selection (android | android-direct | desktop | auto)
    ├── Android path:
    │   → LayoutlibRenderer → ForkedRenderer → child JVM
    │     → RenderWorker → Bridge.init() → PaparazziSdk
    │     → ComposeView.setContent { previewFunction() }
    │     → Snapshot → PNG + HierarchyNode tree
    └── Desktop path:
        → DesktopRenderer → DesktopForkedRenderer → child JVM
          → DesktopRenderWorker → ImageComposeScene
          → Reflection-based composable invocation
          → Skia rendering → PNG + HierarchyNode tree
```

Key design decisions:
- **Bytecode scanning** — finds `@Preview` without loading classes (avoids Android framework deps)
- **Multi-preview** — recursively resolves `@Preview$Container` on annotation classes
- **Forked JVM** — isolates renderer + project classpath from host process
- **Long-running worker** — single JVM fork for entire batch, session reconfigured per uiMode/density
- **Layoutlib auto-download** — fetches from Google Maven to `~/.compose-buddy/cache/`

## Supported Projects

| Project Type | Status |
|-------------|--------|
| Android app/library | Supported |
| KMP with Android target | Supported |
| Multi-module builds | Supported (all module classes resolved) |
| Compose Desktop (KMP) | Supported (desktop renderer) |

## License

    Copyright 2026 Mike Penz

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
