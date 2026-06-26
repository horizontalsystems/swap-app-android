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
import io.horizontalsystems.swapapp.swap.execution.AddressInputScreen
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
 * Top-level flow: Swap → recipient address → (refund address, CEX only) → active swap tracking.
 * Each step is a simple piece of state; advancing sets the next, going back clears it.
 */
@Composable
private fun SwapApp() {
    var proceed by remember { mutableStateOf<ProceedData?>(null) }
    var destination by remember { mutableStateOf<String?>(null) }
    var refund by remember { mutableStateOf<String?>(null) }

    val data = proceed
    // CEX providers reject a swap without a refund address; on-chain DEXes (THORChain/MayaChain)
    // auto-refund the sender, so that step is skipped for them.
    val needsRefund = data?.provider?.requiresRefundAddress == true

    when {
        data == null -> MainSwapScreen(
            onClose = { /* root screen — nothing to go back to */ },
            onProceed = { amountIn, tokenIn, tokenOut, provider ->
                proceed = ProceedData(amountIn, tokenIn, tokenOut, provider)
            },
        )

        destination == null -> AddressInputScreen(
            token = data.tokenOut,
            title = "Recipient",
            heading = "Where should we send your ${data.tokenOut.name}?",
            description = "Enter the ${data.tokenOut.ticker} address that will receive the swapped funds.",
            onBack = { proceed = null },
            onConfirm = { destination = it },
        )

        needsRefund && refund == null -> AddressInputScreen(
            token = data.tokenIn,
            title = "Refund address",
            heading = "Where should we refund your ${data.tokenIn.name} if the swap fails?",
            description = "This provider requires a refund address. If the swap can't complete, your ${data.tokenIn.ticker} is returned here.",
            onBack = { destination = null },
            onConfirm = { refund = it },
        )

        else -> {
            val viewModel = viewModel<SwapExecutionViewModel>(
                key = "${data.tokenIn.identifier}-$destination-$refund",
                factory = SwapExecutionViewModel.Factory(
                    tokenIn = data.tokenIn,
                    tokenOut = data.tokenOut,
                    amountIn = data.amountIn,
                    provider = data.provider,
                    destinationAddress = destination!!,
                    refundAddress = refund,
                ),
            )
            ActiveSwapTrackingScreen(
                uiState = viewModel.uiState,
                onBack = { if (needsRefund) refund = null else destination = null },
                onDone = {
                    // Swap finished — return to a fresh swap screen.
                    refund = null
                    destination = null
                    proceed = null
                },
                onRetry = viewModel::retry,
            )
        }
    }
}
