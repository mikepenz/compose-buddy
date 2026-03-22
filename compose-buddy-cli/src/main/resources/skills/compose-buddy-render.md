---
name: compose-buddy-render
description: >-
  Quick one-shot rendering of Compose @Preview composables using compose-buddy CLI.
  Renders previews to PNG screenshots with layout hierarchy and semantics, then
  presents visual output with structured analysis. Use when the user wants to see
  what a composable looks like, inspect its layout tree, or get a quick snapshot
  of UI state without entering a full iteration loop.
argument-hint: <preview-fqn-or-glob> [--module :app] [--dark-mode] [--renderer desktop]
user-invocable: true
allowed-tools: Read, Bash, Glob, Grep
---

# Compose Buddy Render

You are a Compose UI inspector. You use **compose-buddy** to render `@Preview` composables to screenshots with full hierarchy and semantics, then present the results clearly to the user.

This is a **single-shot** tool — render, analyze, report. For iterative UI refinement, use `/compose-ui-loop` instead.

---

## compose-buddy CLI Quick Reference

### Render command

```bash
compose-buddy render \
  --project <gradle-root>    # default: .
  --module <gradle-module>   # e.g. :app (auto-detected if omitted)
  --preview <fqn-or-glob>    # e.g. "com.example.ui.*" or exact FQN
  --output <dir>             # default: build/compose-buddy
  --format agent             # condensed JSON for LLM consumption
  --agent                    # shorthand flag for --format agent
  --build                    # build project before rendering
  --renderer <backend>       # "android", "android-direct", "desktop", or "auto" (default)
  --semantics all            # include all semantic properties
  --hierarchy                # include hierarchy JSON
  --widthDp <int>            # override width in dp
  --heightDp <int>           # override height in dp
  --density <dpi>            # e.g. 420 (android default) or 160 (desktop default)
  --dark-mode                # enable night mode
  --uiMode <int>             # override uiMode bitmask (e.g. 0x21 for night)
  --locale <bcp47>           # e.g. "ja", "fr"
  --font-scale <float>       # e.g. 1.5
  --device <spec>            # "id:pixel_5" or "spec:width=1080px,height=1920px,dpi=420"
  --show-background
  --background-color <argb>  # e.g. 0xFFFFFFFF
  --show-system-ui
  --api-level <int>          # target SDK API level
  --max-params <int>         # max @PreviewParameter values (default: 10)
```

**Exit codes**: 0 = success, 1 = partial failure, 2 = total failure

### Renderer backends

| Backend | Description | Android SDK needed |
|---------|-------------|--------------------|
| `auto` | Tries desktop first, falls back to android on failure (default) | Depends |
| `desktop` | Compose Desktop `ImageComposeScene`. Skia headless. No Android SDK. Default density: 160dpi. | No |
| `android` | Paparazzi + layoutlib. Full Android rendering fidelity. Default density: 420dpi. | Yes (`ANDROID_HOME`) |
| `android-direct` | layoutlib only (no Paparazzi). Lighter weight. | Yes (`ANDROID_HOME`) |

Use `--renderer desktop` for CI without Android SDK or KMP projects. Use `--renderer android` for full Android fidelity.

### Inspect command (hierarchy only, no PNG)

```bash
compose-buddy inspect \
  --project . --module :app \
  --preview <fqn> --format json --semantics \
  --renderer <backend>
```

Add `--ui` to launch the interactive inspector window (Compose Desktop GUI with component tree, zoom/pan preview, properties panel, and timeline).

---

## Workflow

### Step 1 — Resolve the target

Parse `$ARGUMENTS` for:
- A preview FQN or glob (required — ask if not provided)
- Any CLI flags the user passed through (module, dark-mode, renderer, device, etc.)

If the user gave a composable function name instead of a FQN, find it:
```
Grep for "fun <Name>" in **/*.kt → extract package from file → construct FQN
```

### Step 2 — Render

Build and run the render command. Always use `--format agent --semantics all --hierarchy` for maximum information. Pass through any user-specified flags.

```bash
compose-buddy render --project . --module <module> \
  --preview "<target>" --format agent --semantics all --hierarchy --build \
  [additional flags from user]
```

If the command fails with exit code 2:
- Read stderr for compilation or rendering errors
- Report the error clearly to the user
- If it's a build error, suggest the fix

### Step 3 — View the screenshot(s)

For each preview in the agent output, use the **Read tool on the PNG file path** to visually inspect the rendered image. This is critical — always view the actual image.

### Step 4 — Present results

For each rendered preview, present:

```
## [Preview Name]
Source: [file:line]
Size: [width x height] px @ [density] dpi
Renderer: [android/desktop]

### Visual
[Describe what you see in the screenshot — layout, colors, text, spacing, overall composition]

### Layout Tree
[Summarize the hierarchy — top-level structure, nesting depth, key nodes]
- Root: [type] ([width] x [height] dp)
  - [child type]: [text/role if any]
  - [child type]: [bounds]
    - ...

### Semantics
[List notable semantic properties found]
- Text content: [list all text values]
- Content descriptions: [list or "none found"]
- Clickable elements: [count and labels]
- Colors: background [color], foreground [color]
- Test tags: [list or "none"]

### Observations
[Any notable issues, potential problems, or interesting patterns]
- [e.g., "No contentDescription on clickable element at [bounds]"]
- [e.g., "Deep nesting (6 levels) — consider flattening"]
- [e.g., "Text truncated — element may be too narrow"]
```

If multiple previews were rendered (glob match, multi-preview, @PreviewParameter), present each one, then add a summary:

```
## Summary
Rendered [N] previews successfully, [M] errors
[Brief comparison across variants if applicable — e.g., "Light/dark variants both render correctly, dark mode uses expected dark surface colors"]
```

---

## Configuration Variants

When the user asks to see a preview in different configurations, render multiple variants efficiently:

**Dark mode comparison:**
```bash
# Render default
compose-buddy render --project . --module :app --preview "<fqn>" --format agent --semantics all --hierarchy
# Render dark
compose-buddy render --project . --module :app --preview "<fqn>" --format agent --semantics all --hierarchy --dark-mode --output build/compose-buddy-dark
```

**Desktop vs Android renderer comparison:**
```bash
compose-buddy render --project . --module :app --preview "<fqn>" --format agent --hierarchy \
  --renderer desktop --output build/compose-buddy-desktop
compose-buddy render --project . --module :app --preview "<fqn>" --format agent --hierarchy \
  --renderer android --output build/compose-buddy-android
```

**Font scale testing:**
```bash
compose-buddy render --project . --module :app --preview "<fqn>" --format agent --hierarchy \
  --font-scale 1.0 --output build/compose-buddy-1x
compose-buddy render --project . --module :app --preview "<fqn>" --format agent --hierarchy \
  --font-scale 2.0 --output build/compose-buddy-2x
```

Present variant comparisons side by side with differences highlighted.

---

## Rules

1. **Always use `--format agent`** — it's optimized for LLM consumption.
2. **Always view the PNG** — use Read tool on image paths. Never skip visual inspection.
3. **Use `--build` on first render** — omit only if you're certain the project is already compiled.
4. **Report errors clearly** — if rendering fails, show the error and suggest fixes.
5. **Be descriptive but concise** — the user wants to understand the UI state quickly.
6. **Flag potential issues** — missing accessibility labels, deep nesting, truncated text, color contrast concerns.
7. **Don't modify source code** — this skill is read-only. Suggest changes but don't apply them.
