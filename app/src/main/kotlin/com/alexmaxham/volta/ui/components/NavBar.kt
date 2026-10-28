package com.alexmaxham.volta.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class NavItem(val key: String, val label: String, val icon: ImageVector)

/** A floating pill navigation bar; the selected destination expands to show its label. */
@Composable
fun FloatingNavBar(items: List<NavItem>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier
            .shadow(12.dp, RoundedCornerShape(36.dp), clip = false)
            .clip(RoundedCornerShape(36.dp))
            .background(c.surfaceContainerHigh)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (item in items) {
            val on = item.key == selected
            Row(
                Modifier
                    .height(52.dp)
                    .widthIn(min = 56.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (on) c.primary else c.surfaceContainerHigh)
                    .clickable(role = Role.Tab, onClickLabel = item.label) { onSelect(item.key) }
                    .semantics { this.selected = on }
                    .animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                    .padding(horizontal = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(item.icon, if (on) null else item.label, tint = if (on) c.onPrimary else c.onSurfaceVariant, modifier = Modifier.size(22.dp))
                AnimatedVisibility(on, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    Text(item.label, style = MaterialTheme.typography.labelLarge, color = c.onPrimary, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
