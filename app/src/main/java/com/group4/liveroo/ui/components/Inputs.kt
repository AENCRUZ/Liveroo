package com.group4.liveroo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.ui.theme.LiverooDimens
import com.group4.liveroo.ui.theme.LiverooRadius
import com.group4.liveroo.ui.theme.LiverooTheme
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Text field
// ---------------------------------------------------------------------------------------------

@Composable
private fun fieldColors() = LiverooTheme.colors.let { c ->
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
        focusedContainerColor = c.surface, unfocusedContainerColor = c.surface, errorContainerColor = c.surface,
        focusedBorderColor = c.accent, unfocusedBorderColor = c.outlineControl, errorBorderColor = c.error,
        focusedLabelColor = c.accent, unfocusedLabelColor = c.textSecondary, errorLabelColor = c.error,
        cursorColor = c.accent, errorCursorColor = c.error,
        focusedPlaceholderColor = c.textSecondary, unfocusedPlaceholderColor = c.textSecondary
    )
}

/** Helper text on the left, counter on the right, or the error (icon + message) when [error] is set. */
@Composable
private fun FieldSupport(helper: String?, error: String?, count: Int?, max: Int?) {
    val c = LiverooTheme.colors
    if (helper == null && error == null && max == null) return
    Row(Modifier.fillMaxWidth().semantics { if (error != null) liveRegion = LiveRegionMode.Polite }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (error != null) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(16.dp), tint = c.error)
                Text(error, style = LiverooTheme.type.caption, color = c.error)
            }
        } else {
            Text(helper ?: "", style = LiverooTheme.type.caption, color = c.textSecondary, modifier = Modifier.weight(1f))
        }
        if (max != null) Text("${count ?: 0}/$max", style = LiverooTheme.type.caption, color = c.textSecondary)
    }
}

/** Outlined field with floating label. 56 dp (multi-line: min 104 dp), radius 12. */
@Composable
fun LiverooTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    error: String? = null,
    maxLength: Int? = null,
    placeholder: String? = null,
    multiline: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (maxLength == null || it.length <= maxLength) onValueChange(it) },
            label = { Text(label, style = LiverooTheme.type.chip) },
            placeholder = placeholder?.let { { Text(it, style = LiverooTheme.type.bodyLarge) } },
            textStyle = LiverooTheme.type.bodyLarge,
            isError = error != null,
            enabled = enabled,
            singleLine = !multiline,
            minLines = if (multiline) 3 else 1,
            keyboardOptions = keyboardOptions,
            shape = LiverooRadius.small,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = if (multiline) LiverooDimens.fieldHeightMultiline else LiverooDimens.fieldHeight)
                .semantics { if (error != null) error(error) }
        )
        FieldSupport(helper, error, value.length, maxLength)
    }
}

/**
 * Same frame as the text field with a leading avatar, the value and a chevron.
 * [options] are the same choices as today; each is a menu row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiverooSelectField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    leadingName: String? = null,
    helper: String? = null,
    enabled: Boolean = true,
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val c = LiverooTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
            OutlinedTextField(
                value = value, onValueChange = {}, readOnly = true, enabled = enabled,
                label = { Text(label, style = LiverooTheme.type.chip) },
                textStyle = LiverooTheme.type.bodyLarge,
                singleLine = true,
                leadingIcon = leadingName?.let { { Avatar(it, 28) } },
                trailingIcon = { Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(if (expanded) 180f else 0f), tint = c.textSecondary) },
                shape = LiverooRadius.small,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth().heightIn(min = LiverooDimens.fieldHeight).menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = c.surface) {
                menu { expanded = false }
            }
        }
        FieldSupport(helper, null, null, null)
    }
}

// ---------------------------------------------------------------------------------------------
// Status selector
// ---------------------------------------------------------------------------------------------

/** Segmented control, three equal segments, radio-group semantics. Min 60 dp, grows with text. */
@Composable
fun StatusSelector(selected: TaskStatus, onSelect: (TaskStatus) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = LiverooTheme.colors
    val shape = LiverooRadius.small
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = LiverooDimens.segmentHeight)
            .border(1.5.dp, c.outlineControl, shape).background(c.surface, shape).selectableGroup()
    ) {
        TaskStatus.entries.forEach { s ->
            val isSel = s == selected
            val (icon, tint) = statusIcon(s)
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .then(if (isSel) Modifier.background(c.accentContainer, shape).border(2.dp, c.accent, shape) else Modifier)
                    .selectable(selected = isSel, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(s) })
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Icon(icon, null, Modifier.size(20.dp), tint = if (isSel && s == TaskStatus.TODO) c.onAccentContainer else tint)
                Text(
                    statusLabel(s),
                    style = if (isSel) LiverooTheme.type.chip.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold) else LiverooTheme.type.chip,
                    color = if (isSel) c.onAccentContainer else c.textPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Filter chips
// ---------------------------------------------------------------------------------------------

data class FilterOption(val label: String, val count: Int)

/**
 * Horizontally scrolling chips with a fade and a round arrow button on the right.
 * Stateless: the screen keeps the selected index (rememberSaveable, default 0 = All).
 */
@Composable
fun FilterChipRow(options: List<FilterOption>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LiverooTheme.colors
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val overflow = scroll.maxValue > 0
    val atEnd = overflow && scroll.value >= scroll.maxValue
    val rotation by animateFloatAsState(if (atEnd) 180f else 0f, tween(200), label = "filterArrow")
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier.horizontalScroll(scroll).padding(end = if (overflow) 56.dp else 0.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { i, o ->
                val sel = i == selectedIndex
                Box(
                    Modifier.heightIn(min = LiverooDimens.touchMin)
                        .selectable(selected = sel, role = Role.RadioButton, onClick = { onSelect(i) }),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        Modifier.heightIn(min = LiverooDimens.chipHeight)
                            .background(if (sel) c.accent else c.surface, CircleShape)
                            .then(if (sel) Modifier else Modifier.border(1.5.dp, c.outlineControl, CircleShape))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (sel) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = c.onAccent)
                        Text("${o.label} ${o.count}", style = LiverooTheme.type.chip, color = if (sel) c.onAccent else c.textPrimary)
                    }
                }
            }
        }
        if (overflow) {
            Box(
                Modifier.align(Alignment.CenterEnd).width(64.dp).fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, c.background))),
                contentAlignment = Alignment.CenterEnd
            ) {
                IconButton(
                    onClick = { scope.launch { scroll.animateScrollTo(if (atEnd) 0 else scroll.maxValue, tween(250)) } },
                    modifier = Modifier.size(LiverooDimens.touchMin).shadow(4.dp, CircleShape).background(c.surface, CircleShape)
                        .border(1.5.dp, c.outlineControl, CircleShape)
                ) {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = if (atEnd) "Scroll filters back" else "More filters",
                        modifier = Modifier.rotate(rotation), tint = c.textPrimary
                    )
                }
            }
        }
    }
}
