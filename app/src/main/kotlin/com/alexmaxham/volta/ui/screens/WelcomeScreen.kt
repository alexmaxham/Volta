package com.alexmaxham.volta.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexmaxham.volta.data.UsageReader
import com.alexmaxham.volta.ui.LocalVoltaLayout
import com.alexmaxham.volta.ui.VoltaViewModel
import com.alexmaxham.volta.ui.components.PillButton
import com.alexmaxham.volta.ui.components.VIcons
import com.alexmaxham.volta.ui.theme.ExpressiveShapes
import com.alexmaxham.volta.ui.theme.PolygonShape

@Composable
fun WelcomeScreen(vm: VoltaViewModel) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val pop by animateFloatAsState(if (shown) 1f else 0.6f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "pop")

    val art: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(340.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(300.dp)
                    .graphicsLayer { scaleX = pop; scaleY = pop }
                    .clip(PolygonShape(ExpressiveShapes.cookie9))
                    .background(c.primaryContainer),
            )
            Box(
                Modifier
                    .size(92.dp)
                    .offset(x = 110.dp, y = (-120).dp)
                    .graphicsLayer { scaleX = pop; scaleY = pop; rotationZ = (1 - pop) * 90 }
                    .clip(PolygonShape(ExpressiveShapes.sunny))
                    .background(c.tertiaryContainer),
            )
            Icon(VIcons.Bolt, null, tint = c.onPrimaryContainer, modifier = Modifier.size(88.dp))
        }
    }
    val copy: @Composable () -> Unit = {
        Text("A little battery clarity.", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(14.dp))
        Text(
            "Small moments. A bigger picture. See where your charge goes, how you charge, and how your battery is ageing.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in listOf("No root required", "Your data, your device")) {
                Text(
                    t,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(c.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Screen-on time and apps need Usage Access. Battery readings work without it.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        PillButton(
            "Allow usage access",
            onClick = { runCatching { context.startActivity(UsageReader.accessIntent()) } },
            tonal = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        PillButton(
            "Get started",
            onClick = { vm.update { it.copy(onboarded = true) } },
            modifier = Modifier.fillMaxWidth().height(60.dp),
        )
    }

    if (LocalVoltaLayout.current.rail) {
        // Tablets and unfolded foldables: the artwork on one side, the words and buttons on the other.
        Row(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 40.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text("VOLTA", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp), color = c.onSurfaceVariant)
                art()
            }
            Column(Modifier.weight(1f).widthIn(max = 480.dp).verticalScroll(rememberScrollState())) { copy() }
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Text("VOLTA", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp), color = c.onSurfaceVariant)
        art()
        copy()
    }
}
