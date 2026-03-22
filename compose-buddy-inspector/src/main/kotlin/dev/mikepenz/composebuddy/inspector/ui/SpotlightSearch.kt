package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mikepenz.composebuddy.inspector.ui.icons.RadixChevronDown

private val GROUP_HEADER_HEIGHT = 40.dp
private val VARIANT_ROW_HEIGHT = 32.dp
private val TOP_OFFSET = 80.dp

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
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            val panelHeight = maxHeight * 0.8f
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SpotlightPanel(
                    items = items,
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

private data class SpotlightGroup(
    val previewName: String,
    val shortName: String,
    val packageName: String,
    val hasError: Boolean,
    val variants: List<SpotlightItem>,
)

@Composable
private fun SpotlightPanel(
    items: List<SpotlightItem>,
    panelHeight: Dp,
    selectedPreviewName: String?,
    selectedVariantIndex: Int,
    onItemSelected: (previewName: String, variantIndex: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    val filtered by remember(query, items) {
        derivedStateOf {
            if (query.isBlank()) {
                items
            } else {
                val q = query.lowercase()
                items.filter { item ->
                    item.shortName.lowercase().contains(q) ||
                        item.variantLabel?.lowercase()?.contains(q) == true
                }
            }
        }
    }

    val groups by remember(filtered) {
        derivedStateOf {
            filtered.groupBy { it.previewName }.map { (name, variants) ->
                SpotlightGroup(
                    previewName = name,
                    shortName = variants.first().shortName,
                    packageName = variants.first().packageName,
                    hasError = variants.all { it.hasError },
                    variants = variants,
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .padding(top = TOP_OFFSET)
            .widthIn(min = 360.dp, max = 720.dp)
            .fillMaxWidth(0.7f)
            .height(panelHeight)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = false, onClick = {})
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search previews...") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Escape -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    },
            )

            HorizontalDivider()

            if (groups.isEmpty()) {
                Text(
                    "No matching previews",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 13.sp,
                )
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize(),
                    ) {
                        groups.forEach { group ->
                            val isMultiVariant = group.variants.size > 1
                            val isExpanded = expandedGroups[group.previewName] ?: false

                            item(key = "header#${group.previewName}") {
                                val isSelected = group.previewName == selectedPreviewName && !isMultiVariant

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(GROUP_HEADER_HEIGHT)
                                        .clickable {
                                            if (isMultiVariant) {
                                                expandedGroups[group.previewName] = !isExpanded
                                            } else {
                                                onItemSelected(group.previewName, group.variants.first().variantIndex)
                                                onDismiss()
                                            }
                                        }
                                        .padding(horizontal = 16.dp),
                                ) {
                                    if (isMultiVariant) {
                                        Icon(
                                            imageVector = RadixChevronDown,
                                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .rotate(if (isExpanded) 0f else -90f),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        group.shortName,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else null,
                                        color = if (group.hasError) MaterialTheme.colorScheme.outline
                                        else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (isMultiVariant) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "${group.variants.size} variants",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1,
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        group.packageName,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.widthIn(max = 180.dp),
                                    )
                                }
                            }

                            if (isMultiVariant && isExpanded) {
                                items(
                                    count = group.variants.size,
                                    key = { idx -> "variant#${group.previewName}#${group.variants[idx].variantIndex}" },
                                ) { idx ->
                                    val item = group.variants[idx]
                                    val isVariantSelected = item.previewName == selectedPreviewName &&
                                        item.variantIndex == selectedVariantIndex

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(VARIANT_ROW_HEIGHT)
                                            .clickable {
                                                onItemSelected(item.previewName, item.variantIndex)
                                                onDismiss()
                                            }
                                            .padding(start = 36.dp, end = 16.dp),
                                    ) {
                                        Text(
                                            item.variantLabel ?: "Variant ${item.variantIndex}",
                                            fontSize = 12.sp,
                                            fontWeight = if (isVariantSelected) FontWeight.Bold else null,
                                            color = if (item.hasError) MaterialTheme.colorScheme.outline
                                            else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listState),
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}
