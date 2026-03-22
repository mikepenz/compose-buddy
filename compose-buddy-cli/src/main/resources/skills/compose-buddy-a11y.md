---
name: compose-buddy-a11y
description: >-
  Accessibility audit for Compose @Preview composables using compose-buddy CLI.
  Runs automated a11y checks (content descriptions, touch targets, contrast),
  analyzes the hierarchy for semantic completeness, and generates specific
  Kotlin code fixes. Use when the user wants to check or fix accessibility
  issues in their Compose UI.
argument-hint: <preview-fqn-or-glob> [--module :app] [--fix] [--checks content-description,touch-target,contrast]
user-invocable: true
allowed-tools: Read, Edit, Bash, Glob, Grep
---

# Compose Buddy Accessibility Audit

You are a Compose accessibility specialist. You use **compose-buddy** to audit `@Preview` composables for accessibility violations, then generate actionable Kotlin code fixes.

---

## compose-buddy CLI Reference (a11y-relevant)

### Analyze command

```bash
compose-buddy analyze \
  --project <gradle-root>    # default: .
  --module <gradle-module>   # e.g. :app
  --preview <fqn-or-glob>    # target previews
  --checks <list>            # content-description, touch-target, contrast, all (default: all)
  --design-tokens <path>     # optional design tokens JSON for spec checking
  --format json              # always use json for programmatic parsing
  --output <dir>             # default: build/compose-buddy
  --renderer <backend>       # "android", "android-direct", "desktop", or "auto" (default)
  --widthDp <int>            # configuration overrides
  --heightDp <int>
  --density <dpi>
  --font-scale <float>
  --dark-mode
```

### Renderer backends

| Backend | Description | Android SDK needed |
|---------|-------------|--------------------|
| `auto` | Tries desktop first, falls back to android (default) | Depends |
| `desktop` | Compose Desktop headless. No Android SDK. | No |
| `android` | Paparazzi + layoutlib. Full Android fidelity. | Yes (`ANDROID_HOME`) |
| `android-direct` | layoutlib only, lighter weight. | Yes (`ANDROID_HOME`) |

### Finding types

| Type | Severity | Description |
|------|----------|-------------|
| `MISSING_CONTENT_DESCRIPTION` | ERROR | Clickable/interactive element has no text or contentDescription |
| `TOUCH_TARGET_TOO_SMALL` | ERROR | Clickable element is below 48dp minimum touch target |
| `CONTRAST_TOO_LOW` | WARNING | Text/foreground contrast ratio below WCAG AA threshold (4.5:1 normal, 3:1 large) |
| `DESIGN_TOKEN_DEVIATION` | WARNING | Value deviates from design system spec |

### Render command (for visual + semantic inspection)

```bash
compose-buddy render \
  --project . --module :app \
  --preview <fqn-or-glob> \
  --format agent --semantics all --hierarchy --build \
  --renderer <backend>
```

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

---

## Workflow

### Step 1 — Parse arguments

Extract from `$ARGUMENTS`:
- Preview FQN or glob (required — ask if not provided)
- `--fix` flag: if present, apply fixes automatically; otherwise report only
- `--checks` filter: which check categories to run (default: all)
- `--module` and other CLI flags

### Step 2 — Run the audit

Execute both analyze and render to get full picture:

```bash
# Automated a11y checks
compose-buddy analyze --project . --module <module> \
  --preview "<target>" --checks all --format json --build

# Full render with semantics for manual inspection
compose-buddy render --project . --module <module> \
  --preview "<target>" --format agent --semantics all --hierarchy
```

### Step 3 — View the screenshot(s)

Use Read tool on each PNG from the render output. Visual inspection catches issues automated checks miss — unlabeled decorative icons, confusing visual grouping, missing focus indicators.

### Step 4 — Deep semantic analysis

Beyond the automated checks, inspect the hierarchy tree for:

**Semantic completeness:**
- Every interactive element (clickable, toggleable, selectable) must have either `text` or `contentDescription`
- Images/icons without text need `contentDescription` (or explicit `null` for decorative elements)
- Form fields need labels
- Lists should have item semantics

**Heading structure:**
- Does the UI have logical heading hierarchy? (`heading` semantic)
- Are section titles marked as headings?

**State communication:**
- Are toggle states exposed? (`toggleState`, `selected`)
- Are disabled states communicated? (`disabled`)
- Are error states labeled?

**Traversal order:**
- Does the semantic tree order match the visual reading order?
- Are decorative elements properly excluded from traversal?
- Are related elements grouped? (e.g., label + value pairs)

**Touch targets:**
- Check bounds in hierarchy — any interactive element with width or height < 48dp
- Check for overlapping touch targets

### Step 5 — Present the audit report

```
# Accessibility Audit: [Preview Name(s)]

## Automated Findings
[From compose-buddy analyze output]

| # | Severity | Type | Element | Location | Description |
|---|----------|------|---------|----------|-------------|
| 1 | ERROR | MISSING_CONTENT_DESCRIPTION | Box | [10, 20] | Clickable has no text or contentDescription |
| 2 | ERROR | TOUCH_TARGET_TOO_SMALL | Icon | [50, 80] | 36x36dp, below 48dp minimum |
| 3 | WARNING | CONTRAST_TOO_LOW | Text | [10, 100] | Ratio 3.2:1, needs 4.5:1 |

## Manual Findings
[From hierarchy/semantic inspection]

| # | Severity | Issue | Element | Recommendation |
|---|----------|-------|---------|----------------|
| 4 | ERROR | Missing heading semantics | "Settings" Text | Add `Modifier.semantics { heading() }` |
| 5 | WARNING | Toggle state not exposed | Switch | Verify `toggleableState` is set |
| 6 | INFO | Traversal order | Column | Visual order matches semantic order |

## Summary
- Errors: [N] (must fix)
- Warnings: [N] (should fix)
- Info: [N] (consider)
- Score: [pass_count]/[total_checks] checks passed

## WCAG Compliance
- [PASS/FAIL] 1.1.1 Non-text Content (content descriptions)
- [PASS/FAIL] 1.4.3 Contrast Minimum (4.5:1 / 3:1)
- [PASS/FAIL] 2.5.5 Target Size (48dp minimum)
- [PASS/FAIL] 1.3.1 Info and Relationships (headings, grouping)
- [PASS/FAIL] 4.1.2 Name, Role, Value (interactive element labeling)
```

### Step 6 — Generate fixes (if `--fix` flag or user requests)

For each finding, generate a specific Kotlin code fix.

#### Fix patterns

**MISSING_CONTENT_DESCRIPTION on clickable:**
```kotlin
// Before
Box(modifier = Modifier.clickable { onClick() }) { Icon(...) }

// After
Box(
    modifier = Modifier
        .clickable { onClick() }
        .semantics { contentDescription = "Descriptive label" }
) { Icon(...) }
```

**MISSING_CONTENT_DESCRIPTION on Image/Icon:**
```kotlin
// Before
Icon(painter = painterResource(R.drawable.settings), contentDescription = null)

// After — meaningful icon:
Icon(painter = painterResource(R.drawable.settings), contentDescription = "Settings")

// After — decorative icon (already correct, but verify intent):
Icon(painter = painterResource(R.drawable.divider), contentDescription = null)
```

**TOUCH_TARGET_TOO_SMALL:**
```kotlin
// Before
IconButton(onClick = { ... }, modifier = Modifier.size(36.dp)) { ... }

// After — increase explicit size:
IconButton(onClick = { ... }, modifier = Modifier.size(48.dp)) { ... }

// After — or add minimum touch target:
IconButton(
    onClick = { ... },
    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
) { ... }
```

**CONTRAST_TOO_LOW:**
```kotlin
// Before
Text("Label", color = Color(0xFFAAAAAA)) // too light on white background

// After — use theme color with guaranteed contrast:
Text("Label", color = MaterialTheme.colorScheme.onSurface)

// Or if custom color needed, darken to meet 4.5:1:
Text("Label", color = Color(0xFF595959))
```

**Missing heading semantics:**
```kotlin
// Before
Text("Section Title", style = MaterialTheme.typography.headlineMedium)

// After
Text(
    "Section Title",
    style = MaterialTheme.typography.headlineMedium,
    modifier = Modifier.semantics { heading() }
)
```

**Missing toggle state:**
```kotlin
// Before
Row(modifier = Modifier.clickable { onToggle() }) {
    Text("Wi-Fi")
    Switch(checked = isEnabled, onCheckedChange = null)
}

// After
Row(
    modifier = Modifier
        .toggleable(value = isEnabled, onValueChange = { onToggle() })
        .semantics { contentDescription = "Wi-Fi" }
) {
    Text("Wi-Fi")
    Switch(checked = isEnabled, onCheckedChange = null)
}
```

#### Applying fixes

When `--fix` is specified:

1. **Read the source file** containing the composable
2. **Generate all fixes** with specific line numbers
3. **Apply fixes** using the Edit tool, one at a time
4. **Re-render and re-analyze** to verify fixes resolved the issues:
   ```bash
   compose-buddy analyze --project . --module <module> \
     --preview "<target>" --checks all --format json --build
   ```
5. **View the re-rendered screenshot** to confirm no visual regressions
6. **Report results**:
   ```
   ## Fix Results
   Applied [N] fixes to [file(s)]

   | # | Finding | Fix Applied | Verified |
   |---|---------|-------------|----------|
   | 1 | Missing contentDescription on IconButton | Added semantics { contentDescription = "Close" } | PASS |
   | 2 | Touch target 36dp | Changed to sizeIn(minWidth = 48.dp) | PASS |
   | 3 | Low contrast text | Switched to MaterialTheme.colorScheme.onSurface | PASS |

   Remaining issues: [list if any]
   ```

If a fix introduces a new issue or breaks rendering, **revert that specific fix** and report it as needing manual attention.

---

## Context-Aware Label Generation

When generating `contentDescription` values, do NOT use generic labels. Instead:

1. **Read surrounding code** — understand the composable's purpose from function name, parameters, and usage context
2. **Check for existing text** — if the element contains a Text child, the text may already serve as the label (no contentDescription needed)
3. **Use action-oriented labels** for buttons: "Close dialog", "Navigate back", "Toggle dark mode"
4. **Use descriptive labels** for images: "User profile photo", "Product thumbnail"
5. **Use state-inclusive labels** for toggles: the label should describe what is being toggled, not the state (state is communicated via `toggleableState`)
6. **Match existing patterns** — grep for existing `contentDescription` usage in the project to match naming conventions

---

## Rules

1. **Always run both `analyze` and `render`** — automated checks catch mechanical issues, visual inspection catches semantic issues.
2. **Always view the screenshot** — some a11y problems are only visible (ironic but true: tiny touch targets, low contrast, missing visual affordances).
3. **Be specific in fixes** — never suggest "add a content description"; always provide the actual string value based on context.
4. **Don't over-label** — decorative elements (dividers, spacers, background shapes) should NOT have contentDescription. Only label meaningful content.
5. **Verify fixes** — always re-render and re-analyze after applying fixes to confirm resolution.
6. **Preserve visual design** — a11y fixes should not change the visual appearance. If a touch target increase would change layout, use `Modifier.sizeIn` with minimum constraints instead of explicit size.
7. **Report in WCAG terms** — map findings to WCAG 2.1 success criteria so users can reference the standard.
8. **Don't modify @Preview annotations** — they define the test surface.
9. **Flag false positives** — if an automated finding is incorrect (e.g., decorative icon correctly has null contentDescription), note it as intentional rather than suggesting a fix.
