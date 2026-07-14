package io.horizontalsystems.swapapp.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.horizontalsystems.swapapp.components.BoxBordered
import io.horizontalsystems.swapapp.components.HSScaffold
import io.horizontalsystems.swapapp.components.cell.CellPrimary
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.compose.components.HsSwitch

/**
 * Debug-build-only settings, opened from the gear icon on the main swap screen. Backed by
 * [DebugSettings], so toggles apply immediately and persist across launches.
 */
@Composable
fun DebugSettingsScreen(onBack: () -> Unit) {
    HSScaffold(title = "Debug Settings", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ComposeAppTheme.colors.lawrence)
        ) {
            BoxBordered(bottom = true) {
                CellPrimary(
                    middle = {
                        Column(verticalArrangement = spacedBy(3.dp)) {
                            Text(
                                text = "Provider names",
                                style = ComposeAppTheme.typography.subhead,
                                color = ComposeAppTheme.colors.leah,
                            )
                            Text(
                                text = "Show each route's provider on the route selection page",
                                style = ComposeAppTheme.typography.caption,
                                color = ComposeAppTheme.colors.grey,
                            )
                        }
                    },
                    right = {
                        HsSwitch(
                            checked = DebugSettings.showRouteProviderNames,
                            onCheckedChange = { DebugSettings.setShowRouteProviderNames(it) },
                        )
                    },
                    onClick = {
                        DebugSettings.setShowRouteProviderNames(!DebugSettings.showRouteProviderNames)
                    },
                )
            }
        }
    }
}