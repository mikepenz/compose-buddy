package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PALETTE_TOP_OFFSET = 80.dp
private val PALETTE_MIN_WIDTH = 300.dp
private val PALETTE_MAX_WIDTH = 700.dp

@Composable
fun SpotlightSearch(
    visible: Boolean,
    items: List<SpotlightItem>,
    selectedPreviewName: String?,
    selectedVariantIndex: Int = 0,
    onItemSelected: (previewName: String, variantIndex: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            // 50% of the window width, clamped to [300dp, 700dp] per the design.
            // Compute explicitly from BoxWithConstraints — widthIn().fillMaxWidth(0.5f)
            // doesn't reliably reach the 50% target inside a Column.
            val panelWidth = (maxWidth * 0.5f).coerceIn(PALETTE_MIN_WIDTH, PALETTE_MAX_WIDTH)
            val panelHeight = (maxHeight * 0.7f).coerceAtMost(560.dp)
            Column(
                modifier = Modifier.fillMaxSize().padding(top = PALETTE_TOP_OFFSET),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SpotlightPanel(
                    items = items,
                    panelWidth = panelWidth,
                    panelHeight = panelHeight,
                    selectedPreviewName = selectedPreviewName,
                    selectedVariantIndex = selectedVariantIndex,
                    onItemSelected = onItemSelected,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

private data class FileGroup(
    val file: String,
    val items: List<SpotlightItem>,
)

/** `pkg.FooKt.PreviewBar` → `Foo.kt`; falls back to the class name when no `Kt` suffix. */
private fun fileFromFqn(fqn: String): String {
    val parts = fqn.split('.')
    val cls = parts.getOrNull(parts.size - 2) ?: parts.first()
    val base = if (cls.endsWith("Kt")) cls.dropLast(2) else cls
    return "$base.kt"
}

@Composable
private fun SpotlightPanel(
    items: List<SpotlightItem>,
    panelWidth: Dp,
    panelHeight: Dp,
    selectedPreviewName: String?,
    selectedVariantIndex: Int,
    onItemSelected: (previewName: String, variantIndex: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = InspectorTokens.current
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Filter: match preview short-name OR file-name OR package-name (group).
    val filtered by remember(query, items) {
        derivedStateOf {
            if (query.isBlank()) items else {
                val q = query.lowercase()
                items.filter {
                    it.shortName.lowercase().contains(q) ||
                        fileFromFqn(it.previewName).lowercase().contains(q) ||
                        it.packageName.lowercase().contains(q)
                }
            }
        }
    }

    val groups by remember(filtered) {
        derivedStateOf {
            val seen = LinkedHashMap<String, MutableList<SpotlightItem>>()
            filtered.forEach { item ->
                seen.getOrPut(fileFromFqn(item.previewName)) { mutableListOf() }.add(item)
            }
            seen.map { (file, items) -> FileGroup(file, items) }
        }
    }

    // Flat list used for arrow-key navigation (rows only — group headers skipped).
    val flat by remember(groups) {
        derivedStateOf { groups.flatMap { it.items } }
    }
    var highlighted by remember { mutableStateOf(0) }
    // Reset highlight when the result list changes; clamp into range.
    LaunchedEffect(flat) {
        highlighted = if (flat.isEmpty()) 0 else highlighted.coerceIn(0, flat.lastIndex)
    }

    // Auto-scroll to keep the highlighted row visible. Translate flat-index
    // (rows only) → LazyColumn item-index (headers + rows interleaved).
    LaunchedEffect(highlighted, groups) {
        if (flat.isEmpty()) return@LaunchedEffect
        val target = flat.getOrNull(highlighted) ?: return@LaunchedEffect
        var lazyIdx = 0
        var found = -1
        outer@ for (group in groups) {
            lazyIdx++ // header
            for (item in group.items) {
                if (item.previewName == target.previewName && item.variantIndex == target.variantIndex) {
                    found = lazyIdx
                    break@outer
                }
                lazyIdx++
            }
        }
        if (found < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull()?.index
        val last = info.visibleItemsInfo.lastOrNull()?.index
        if (first == null || last == null || found < first || found > last) {
            listState.animateScrollToItem(found)
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val keyHandler: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { event ->
        if (event.type != KeyEventType.KeyDown) false
        else when (event.key) {
            Key.Escape -> { onDismiss(); true }
            Key.DirectionDown -> {
                if (flat.isNotEmpty()) highlighted = (highlighted + 1).coerceAtMost(flat.lastIndex)
                true
            }
            Key.DirectionUp -> {
                if (flat.isNotEmpty()) highlighted = (highlighted - 1).coerceAtLeast(0)
                true
            }
            Key.Enter, Key.NumPadEnter -> {
                flat.getOrNull(highlighted)?.let {
                    onItemSelected(it.previewName, it.variantIndex)
                    onDismiss()
                }
                true
            }
            else -> false
        }
    }

    Box(
        modifier = Modifier
            .width(panelWidth)
            .height(panelHeight)
            .clip(RoundedCornerShape(InspectorRadius.window))
            .background(tokens.bgPanel)
            .border(0.5.dp, tokens.line2, RoundedCornerShape(InspectorRadius.window))
            // Prevent click-through to the dismiss-overlay scrim.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SpotlightSearchField(
                query = query,
                onQueryChange = { query = it; highlighted = 0 },
                focusRequester = focusRequester,
                keyHandler = keyHandler,
            )
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(tokens.line1))
            if (groups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No previews match \"$query\"",
                        color = tokens.fg3,
                        fontSize = 12.sp,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(vertical = 6.dp),
                ) {
                    groups.forEach { group ->
                        item(key = "file#${group.file}") {
                            FileGroupHeader(group.file)
                        }
                        items(items = group.items, key = { "row#${it.previewName}#${it.variantIndex}" }) { item ->
                            val isHighlighted = flat.getOrNull(highlighted)?.let {
                                it.previewName == item.previewName && it.variantIndex == item.variantIndex
                            } ?: false
                            val isCurrent = item.previewName == selectedPreviewName &&
                                item.variantIndex == selectedVariantIndex
                            SpotlightRow(
                                item = item,
                                highlighted = isHighlighted || isCurrent,
                                onClick = {
                                    onItemSelected(item.previewName, item.variantIndex)
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotlightSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    keyHandler: (androidx.compose.ui.input.key.KeyEvent) -> Boolean,
) {
    val tokens = InspectorTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = tokens.fg3,
        )
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent(keyHandler),
                textStyle = TextStyle(
                    color = tokens.fg1,
                    fontSize = 14.sp,
                    fontFamily = LocalTextStyle.current.fontFamily,
                ),
                cursorBrush = SolidColor(tokens.accent),
            )
            if (query.isEmpty()) {
                Text(
                    text = "Search previews…",
                    color = tokens.fg3,
                    fontSize = 14.sp,
                )
            }
        }
        KbdBadge("esc")
    }
}

@Composable
private fun FileGroupHeader(file: String) {
    val tokens = InspectorTokens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Text(
            text = file.uppercase(),
            color = tokens.fg3,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
        )
    }
}

@Composable
private fun SpotlightRow(
    item: SpotlightItem,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val tokens = InspectorTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlighted) tokens.bgSelected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(InspectorRadius.sm))
                .background(tokens.bgPanelMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = tokens.fg3,
            )
        }
        Text(
            text = item.shortName,
            modifier = Modifier.weight(1f),
            color = if (highlighted) tokens.accent else tokens.fg1,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!item.variantLabel.isNullOrBlank()) {
            Text(
                text = item.variantLabel,
                color = tokens.fg3,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
