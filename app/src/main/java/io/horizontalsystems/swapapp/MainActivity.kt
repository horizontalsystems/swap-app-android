package io.horizontalsystems.swapapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.swap.MainSwapScreen
import io.horizontalsystems.swapapp.swap.SwapProvider
import io.horizontalsystems.swapapp.swap.SwapToken
import io.horizontalsystems.swapapp.swap.execution.ActiveSwapTrackingScreen
import io.horizontalsystems.swapapp.swap.execution.DestinationAddressScreen
import io.horizontalsystems.swapapp.swap.execution.SwapExecutionViewModel
import java.math.BigDecimal

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposeAppTheme {
                SwapApp()
            }
        }
    }
}

private data class ProceedData(
    val amountIn: BigDecimal,
    val tokenIn: SwapToken,
    val tokenOut: SwapToken,
    val provider: SwapProvider,
)

/**
 * Top-level flow: Swap → enter destination address → active swap tracking.
 * Each step is a simple piece of state; advancing sets the next, going back clears it.
 */
@Composable
private fun SwapApp() {
    var proceed by remember { mutableStateOf<ProceedData?>(null) }
    var destination by remember { mutableStateOf<String?>(null) }

    val data = proceed

    when {
        data == null -> MainSwapScreen(
            onClose = { /* root screen — nothing to go back to */ },
            onProceed = { amountIn, tokenIn, tokenOut, provider ->
                proceed = ProceedData(amountIn, tokenIn, tokenOut, provider)
            },
        )

        destination == null -> DestinationAddressScreen(
            tokenOut = data.tokenOut,
            onBack = { proceed = null },
            onConfirm = { destination = it },
        )

        else -> {
            val viewModel = viewModel<SwapExecutionViewModel>(
                key = "${data.tokenIn.identifier}-$destination",
                factory = SwapExecutionViewModel.Factory(
                    tokenIn = data.tokenIn,
                    tokenOut = data.tokenOut,
                    amountIn = data.amountIn,
                    provider = data.provider,
                    destinationAddress = destination!!,
                ),
            )
            ActiveSwapTrackingScreen(
                uiState = viewModel.uiState,
                onBack = { destination = null },
                onDone = {
                    // Swap finished — return to a fresh swap screen.
                    destination = null
                    proceed = null
                },
                onRetry = viewModel::retry,
            )
        }
    }
}
