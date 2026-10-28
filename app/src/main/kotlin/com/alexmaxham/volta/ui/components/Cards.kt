package com.alexmaxham.volta.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val CardShape = RoundedCornerShape(28.dp)

@Composable
fun VCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape: Shape = CardShape,
    padding: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** A coloured tile with a label and a big value. */
@Composable
fun StatTile(
    label: String,
    value: String,
    sub: String?,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: Color = MaterialTheme.colorScheme.onSurface,
    shape: Shape = CardShape,
) {
    Column(
        modifier
            .clip(shape)
            .background(container)
            .heightIn(min = 112.dp)
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = content.copy(alpha = 0.85f))
        Spacer(Modifier.height(10.dp))
        Column {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = content, maxLines = 1)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.8f), maxLines = 2)
        }
    }
}

/** Label/value rows separated by thin gaps, like a grouped list. */
@Composable
fun RowsCard(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clip(CardShape).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        rows.forEachIndexed { i, (k, v) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.background))
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp)) {
                val valueMax = maxWidth * 0.6f
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(k, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    // Short values sit at the end; long ones wrap within 60% of the row.
                    Text(v, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = valueMax))
                }
            }
        }
    }
}

/** A tappable list row inside a grouped card. */
@Composable
fun ListRow(
    title: String,
    sub: String? = null,
    modifier: Modifier = Modifier,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = titleColor)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke(this)
    }
}

@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(CardShape).background(MaterialTheme.colorScheme.surfaceContainerLow), content = content)
}

@Composable
fun GroupDivider() {
    Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.background))
}

@Composable
fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListRow(title, sub, onClick = { onChange(!checked) }, trailing = { Switch(checked = checked, onCheckedChange = onChange) })
}

@Composable
fun ValueRow(title: String, sub: String?, value: String, onClick: (() -> Unit)? = null) {
    ListRow(title, sub, onClick = onClick, trailing = { Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold) })
}

@Composable
fun ChevronRow(title: String, sub: String? = null, titleColor: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    ListRow(title, sub, titleColor = titleColor, onClick = onClick, trailing = {
        Icon(VIcons.Chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    })
}

/** A round tonal icon button. */
@Composable
fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

/** A selectable chip: tonal when selected, outlined otherwise. */
@Composable
fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (selected) Modifier.background(c.secondaryContainer) else Modifier.border(1.dp, c.outlineVariant, RoundedCornerShape(12.dp)))
            .clickable(onClick = onClick)
            .semantics { this.selected = selected; role = Role.Tab }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) Icon(VIcons.Check, null, tint = c.onSecondaryContainer, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) c.onSecondaryContainer else c.onSurface, maxLines = 1)
    }
}

/**
 * An expressive connected button group: the selected segment fills with the primary colour and
 * its corners spring fully round.
 */
@Composable
fun ConnectedButtons(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val outerStart = if (i == 0 || on) 24.dp else 8.dp
            val outerEnd = if (i == options.lastIndex || on) 24.dp else 8.dp
            val spec = spring<Dp>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            val start by animateDpAsState(outerStart, spec, label = "start")
            val end by animateDpAsState(outerEnd, spec, label = "end")
            val shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(shape)
                    .background(if (on) c.primary else c.surfaceContainerHigh)
                    .clickable(role = Role.RadioButton) { onSelect(i) }
                    .semantics { this.selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) c.onPrimary else c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A small icon in a tonal container. */
@Composable
fun IconBadge(icon: ImageVector, container: Color, content: Color, shape: Shape = RoundedCornerShape(14.dp), size: Dp = 44.dp) {
    Box(Modifier.size(size).clip(shape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = content, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A note card used for empty, partial and permission states. */
@Composable
fun NoteCard(title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    VCard(color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            PillButton(action, onAction)
        }
    }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tonal: Boolean = false, icon: ImageVector? = null, enabled: Boolean = true) {
    val c = MaterialTheme.colorScheme
    val bg = when {
        !enabled -> c.onSurface.copy(alpha = 0.12f)
        tonal -> c.secondaryContainer
        else -> c.primary
    }
    val fg = when {
        !enabled -> c.onSurface.copy(alpha = 0.38f)
        tonal -> c.onSecondaryContainer
        else -> c.onPrimary
    }
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
fun Gap(h: Dp = 12.dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(w: Dp = 12.dp) = Spacer(Modifier.width(w))
