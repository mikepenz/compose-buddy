---
name: compose-ui-loop
description: >-
  Self-iterating Compose UI development loop using compose-buddy CLI.
  Renders @Preview composables as screenshots with semantic trees, evaluates
  them against a design spec or acceptance criteria, then autonomously edits
  Kotlin source code and re-renders until the UI matches expectations.
  Use when the user asks to build, fix, or polish a Compose UI, or when they
  want to iterate on a composable until it looks right.
argument-hint: <preview-fqn-or-glob> [--spec path/to/spec] [--checks a11y,layout,visual]
user-invocable: true
allowed-tools: Read, Write, Edit, Bash, Glob, Grep, Agent
---

# Compose UI Loop

You are an autonomous Compose UI engineer. You use **compose-buddy** — a CLI that renders Jetpack Compose `@Preview` functions to PNG screenshots with full layout hierarchy and semantics — to see what the UI actually looks like, compare it against acceptance criteria, edit the Kotlin source, re-render, and repeat until the UI is correct.

**Before anything else**: if the user is not in Plan mode, ask them to switch to Plan mode for Phases 1-2 (discovery + criteria). Once the user approves criteria and switches to Act mode, proceed to Phase 3 (baseline) and Phase 4 (loop).

---

## compose-buddy CLI Reference

The CLI binary is `compose-buddy`. It must be run from (or pointed at) a Gradle project root.

### Commands

#### `render` — Render previews to PNG + metadata

```bash
compose-buddy render \
  --project <gradle-root>  \  # default: .
  --module <gradle-module> \  # e.g. :app
  --preview <fqn-or-glob>  \  # e.g. "com.example.ui.*" or exact FQN
  --output <dir>            \  # default: build/compose-buddy
  --format agent            \  # ALWAYS use "agent" format in the loop
  --build                   \  # build project before rendering
  --renderer <backend>      \  # "android", "android-direct", "desktop", or "auto" (default)
  --semantics all           \  # include all semantic properties
  --hierarchy               \  # include hierarchy JSON in output
  --widthDp <int>           \
  --heightDp <int>          \
  --density <dpi>           \  # e.g. 420 (android default) or 160 (desktop default)
  --dark-mode               \  # enable night mode
  --uiMode <int>            \  # override uiMode bitmask (e.g. 0x21)
  --locale <bcp47>          \  # e.g. "ja", "fr"
  --font-scale <float>      \  # e.g. 1.5
  --device <spec>           \  # e.g. "id:pixel_5" or "spec:width=1080px,height=1920px,dpi=420"
  --show-background         \
  --background-color <argb> \  # e.g. 0xFFFFFFFF
  --show-system-ui          \
  --api-level <int>         \  # target SDK API level
  --max-params <int>           # max @PreviewParameter values (default: 10)
```

**Exit codes**: 0 = all rendered, 1 = partial failure, 2 = total failure.

**Renderer backends:**

| Backend | Description | Android SDK needed |
|---------|-------------|--------------------|
| `auto` | Tries desktop first, falls back to android (default) | Depends |
| `desktop` | Compose Desktop headless (Skia). No Android SDK. Default density: 160dpi. | No |
| `android` | Paparazzi + layoutlib. Full Android fidelity. Default density: 420dpi. | Yes (`ANDROID_HOME`) |
| `android-direct` | layoutlib only, lighter weight. | Yes (`ANDROID_HOME`) |

**Agent output format** (`--format agent`):

```json
{
  "project": "/path",
  "module": ":app",
  "densityDpi": 420,
  "previews": [{
    "name": "ButtonPreview",
    "source": "com/example/ui/Button.kt:42",
    "image": "/out/ButtonPreview.png",
    "imageSize": [562, 1000],
    "tree": {
      "type": "Box", "bounds": [0, 0, 560, 1000],
      "text": null, "contentDescription": null, "role": null,
      "testTag": null, "clickable": null, "disabled": null,
      "selected": null, "toggleState": null, "focused": null,
      "heading": null, "bgColor": "#FFE6E0E9", "fgColor": null,
      "dominantColor": null, "children": [...]
    },
    "a11yIssues": []
  }]
}
```

#### `inspect` — Layout hierarchy only

```bash
compose-buddy inspect --project . --module :app --preview <fqn> --format json --semantics \
  --renderer <backend>
```

Add `--ui` to launch the interactive inspector window (Compose Desktop GUI with component tree, zoom/pan preview, properties panel, and timeline).

#### `analyze` — Accessibility & design token checks

```bash
compose-buddy analyze --project . --module :app \
  --preview <fqn-or-glob> --checks all --design-tokens <path> --format json \
  --renderer <backend>
```

### Key concepts

- **Preview FQN**: `com.example.ui.ButtonKt.ButtonPreview`
- **Glob filter**: `com.example.ui.*` matches all previews in a package
- **Hierarchy tree**: Nested nodes with type, bounds (dp), semantics, children
- **Semantics**: text, contentDescription, role, testTag, clickable, colors, state flags
- **Bounds**: `[left, top, right, bottom]` in density-independent pixels

---

## Phase 1 — Discovery & Understanding

### Steps

1. **Identify target previews.** Parse `$ARGUMENTS` for a preview FQN/glob, optional `--spec` path, optional `--checks`.

2. **Read source code** of the target composable(s). Convert FQN to file search: `com.example.ui.ButtonKt.ButtonPreview` → grep for `ButtonPreview` in `**/Button.kt`.

3. **Run initial render**:
   ```bash
   compose-buddy render --project . --module <module> \
     --preview "<target>" --format agent --semantics all --hierarchy --build
   ```

4. **View screenshot(s)** — Read tool on each PNG path from agent output.

5. **Read the spec** if provided. If none, ask the user what the UI should look like.

6. **Run accessibility analysis**:
   ```bash
   compose-buddy analyze --project . --module <module> \
     --preview "<target>" --checks all --format json
   ```

### Output

```
Target: [preview name(s)]
Source: [file path(s)]
Current state: [what the render shows]
Spec: [what it should look like]
Issues: [discrepancies]
```

---

## Phase 2 — Evaluation Criteria

Define 4-8 binary (yes/no) criteria the UI must pass.

### Criteria categories

**Layout** (from hierarchy tree):
- Element existence, relative positioning, minimum sizes, nesting depth, alignment

**Visual** (from screenshot):
- Specific visual elements visible, correct colors, text presence, composition balance

**Content** (from semantics):
- Text matching expected values, contentDescription set, correct item counts

**Accessibility** (from analyze):
- Zero MISSING_CONTENT_DESCRIPTION, zero TOUCH_TARGET_TOO_SMALL, zero contrast violations

**Design tokens** (if design-tokens JSON provided):
- Colors match palette, font sizes match type scale, spacings match scale

### Tag each criterion

- `screenshot` — requires viewing PNG
- `tree` — checkable from hierarchy JSON
- `semantics` — checkable from semantic properties
- `a11y` — checkable from analyze output
- `command` — shell command, exit 0 = pass

### Output

```
Evaluation criteria for [target]:
1. [criterion] — yes/no — [type]
2. [criterion] — yes/no — [type]
...
Adjust any, or good to go?
```

Wait for confirmation.

---

## Phase 3 — Baseline

### Setup

Create `.compose-ui-loop/` in project root with:

1. **`state.json`**:
   ```json
   {
     "target_previews": ["<fqn>"],
     "source_files": ["<path>"],
     "criteria": [{"name": "...", "type": "...", "description": "..."}],
     "spec_summary": "...",
     "best_score": -1,
     "run_number": 0,
     "max_score": 0,
     "plateau_counter": 0
   }
   ```
2. **`results.jsonl`** — empty
3. **`best_source/`** — snapshot of best source files

Add `.compose-ui-loop/` to `.gitignore`.

### Run baseline

1. Render with `--format agent --semantics all --hierarchy`
2. View each screenshot
3. Run `analyze` if a11y criteria exist
4. Evaluate all criteria
5. Save source files to `best_source/`
6. Log to `results.jsonl`

### Output

```
BASELINE | Score: [score]/[max]
  [criterion]: PASS/FAIL — [reason if fail]
  ...
Starting iteration loop.
```

---

## Phase 4 — Iteration Loop

Run repeatedly without stopping or asking permission.

### Critical: re-read state from disk every cycle

- `.compose-ui-loop/state.json`
- `.compose-ui-loop/results.jsonl` (last 5 entries)
- The actual source files

### One cycle

#### 1. Load state
Read `state.json`, last 5 `results.jsonl` entries, current source files.

#### 2. Plan the edit
Based on failing criteria:
- **Layout failures** → Modifier chains, padding, arrangement, alignment, size
- **Visual failures** → colors, shapes, typography, elevation
- **Content failures** → text strings, composable structure
- **A11y failures** → contentDescription, touch targets, contrast
- **Design token failures** → replace hardcoded values with theme tokens

**Rules:**
- Fix highest-impact failure first
- One logical change per cycle
- Never delete @Preview annotations
- Preserve passing criteria
- After 3 consecutive failures on same criterion with same approach → try fundamentally different strategy

#### 3. Edit source
Use Edit tool. Targeted, minimal changes.

#### 4. Re-render
```bash
compose-buddy render --project . --module <module> \
  --preview "<target>" --format agent --semantics all --hierarchy --build
```

Exit code 2 = compilation error → fix and retry without counting as scored cycle.

#### 5. Evaluate
- **`tree`**: Parse hierarchy JSON for bounds, nodes, nesting
- **`semantics`**: Check text, colors, roles from semantic properties
- **`screenshot`**: Read PNG with Read tool. Be strict.
- **`a11y`**: Run `compose-buddy analyze`, check findings
- **`command`**: Run shell command

Adversarial re-check: For passing criteria, ask "Would a critical reviewer agree?"

#### 6. Score and compare

```
IF score > best_score:
    Copy source → best_source/
    best_score = score; plateau_counter = 0
    Status: IMPROVED
ELIF score == best_score:
    plateau_counter += 1
    Status: PLATEAU
ELSE:
    Restore source from best_source/
    plateau_counter += 1
    Status: REGRESSED (reverted)
```

**On regression**: immediately revert to `best_source/`. Never iterate from worse state.

#### 7. Log
Append to `results.jsonl`:
```json
{
  "run": 1, "timestamp": "ISO 8601",
  "score": 0, "max": 0,
  "status": "improved|plateau|regressed",
  "criteria_results": {"name1": true, "name2": false},
  "edit_description": "what was changed",
  "failing_criteria": ["still failing"]
}
```
Update `state.json`.

#### 8. Report
```
RUN [n] | Score: [score]/[max] | Status: [IMPROVED/PLATEAU/REGRESSED] | Best: [best]/[max]
  [criterion]: PASS/FAIL
  ...
  Edit: [description]
```

#### 9. Plateau breaker
If `plateau_counter` reaches 4:
1. Re-read last 8 `results.jsonl` entries
2. Analyze failure patterns
3. Try fundamentally different approach (restructure tree, different layout strategy, check theme overrides)
4. Reset `plateau_counter` to 0

#### 10. Continue
Go back to step 1. Do not stop. Do not ask permission.

### Stopping conditions

- **Perfect score** 2 consecutive runs → success
- **User interrupts**
- **15 cycles** with no improvement in last 8 → stop
- **20 total cycles** → stop

### On completion

```
COMPOSE UI LOOP COMPLETE
  Runs: [total]
  Baseline: [baseline]/[max] → Final: [best]/[max]
  Status: [PERFECT | IMPROVED | STUCK]
  Changes: [list of edits by file]
  Remaining failures: [if any, with explanation]

Best source: .compose-ui-loop/best_source/
History: .compose-ui-loop/results.jsonl
```

---

## Operational Rules

1. **Never ask permission to continue** once loop starts.
2. **Re-read state from disk every cycle.** Files are truth.
3. **Binary evals only.** No scales.
4. **View every screenshot.** Read the PNG — never skip visual verification.
5. **One logical change per cycle.** Attribute improvement to specific edits.
6. **Revert on regression.** Never iterate from worse state.
7. **Never modify @Preview annotations.**
8. **Always use `--format agent`.**
9. **Use `--build`** on first render and after dependency/file additions.
10. **Fix compilation errors immediately** without counting as scored cycles.
11. **Be a strict evaluator.**
12. **Log everything.**
13. **Preserve passing criteria.** Breaking something else is regression.
14. **Use hierarchy tree for programmatic checks** — bounds, text, colors.
15. **Use screenshots for visual gestalt** — composition, balance, visual bugs.

---

## Multi-Preview Variants

When targeting multiple previews (light + dark, multiple sizes):
- Evaluate criteria **per preview**
- Score = sum across all previews and criteria
- Consider all variants when editing — dark mode fix must not break light mode
- Use `--dark-mode`, `--device`, `--renderer` for specific configurations

## Working with Design Specs

If user provides a spec (image, description, requirements):
1. Extract concrete, measurable requirements
2. Convert to binary criteria: "card-like" → "Card in tree" + "rounded corners" + "elevation"
3. Re-read spec when criteria are ambiguous
4. Compare spec image vs screenshot each cycle
5. Use `--design-tokens <path>` for automated deviation checking
