package io.horizontalsystems.swapapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.onboarding.OnboardingScreen
import io.horizontalsystems.swapapp.swap.MainSwapScreen
import io.horizontalsystems.swapapp.swap.MainSwapViewModel
import io.horizontalsystems.swapapp.swap.SwapProvider
import io.horizontalsystems.swapapp.swap.SwapToken
import io.horizontalsystems.swapapp.swap.execution.ActiveSwapTrackingScreen
import io.horizontalsystems.swapapp.swap.execution.AddressInputScreen
import io.horizontalsystems.swapapp.swap.execution.ResumedSwapViewModel
import io.horizontalsystems.swapapp.swap.execution.SwapExecutionViewModel
import io.horizontalsystems.swapapp.swap.execution.SwapStatus
import io.horizontalsystems.swapapp.swap.history.SwapHistoryStore
import io.horizontalsystems.swapapp.swap.history.SwapInfoScreen
import io.horizontalsystems.swapapp.swap.history.SwapInfoViewModel
import io.horizontalsystems.swapapp.swap.history.SwapHistoryScreen
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
    val amountOut: BigDecimal?,
    val fiatIn: BigDecimal?,
    val fiatOut: BigDecimal?,
)

/**
 * Top-level flow: Swap → recipient address → (refund address, CEX only) → active swap tracking.
 * Each step is a simple piece of state; advancing sets the next, going back clears it.
 */
@Composable
private fun SwapApp() {
    val context = LocalContext.current

    val prefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var onboarded by remember { mutableStateOf(prefs.getBoolean("onboarding_complete", false)) }
    if (!onboarded) {
        OnboardingScreen(
            onFinish = {
                prefs.edit().putBoolean("onboarding_complete", true).apply()
                onboarded = true
            },
        )
        return
    }

    val history = remember { SwapHistoryStore(context) }
    // Same activity-scoped instance MainSwapScreen uses, so "Done" can reset the entered amount.
    val mainSwapViewModel = viewModel<MainSwapViewModel>()

    var proceed by remember { mutableStateOf<ProceedData?>(null) }
    var destination by remember { mutableStateOf<String?>(null) }
    var refund by remember { mutableStateOf<String?>(null) }
    // Last-confirmed addresses, kept across back-navigation so editing the amount and returning
    // doesn't force re-entering them — they pre-fill the address screens. Cleared only on a new swap.
    var savedDestination by remember { mutableStateOf<String?>(null) }
    var savedRefund by remember { mutableStateOf<String?>(null) }
    var historyOpen by remember { mutableStateOf(false) }
    var infoUuid by remember { mutableStateOf<String?>(null) }
    // A history swap still awaiting its deposit, reopened on the deposit-instructions screen.
    var resumeUuid by remember { mutableStateOf<String?>(null) }

    val data = proceed
    // CEX providers reject a swap without a refund address; on-chain DEXes (THORChain/MayaChain)
    // auto-refund the sender, so that step is skipped for them.
    val needsRefund = data?.provider?.requiresRefundAddress == true

    when {
        resumeUuid != null -> {
            BackHandler { resumeUuid = null }
            val viewModel = viewModel<ResumedSwapViewModel>(
                key = "resume-$resumeUuid",
                factory = ResumedSwapViewModel.Factory(uuid = resumeUuid!!, history = history),
            )
            ActiveSwapTrackingScreen(
                uiState = viewModel.uiState,
                onBack = { resumeUuid = null },
                onDone = {
                    // "Go to Main" — close both this screen and the history list underneath.
                    resumeUuid = null
                    historyOpen = false
                },
                onRetry = { /* no intent creation to retry when resumed from history */ },
            )
        }

        infoUuid != null -> {
            BackHandler { infoUuid = null }
            val viewModel = viewModel<SwapInfoViewModel>(
                key = "info-$infoUuid",
                factory = SwapInfoViewModel.Factory(uuid = infoUuid!!, history = history),
            )
            SwapInfoScreen(uiState = viewModel.uiState, onBack = { infoUuid = null })
        }

        historyOpen -> {
            BackHandler { historyOpen = false }
            SwapHistoryScreen(
                store = history,
                onBack = { historyOpen = false },
                onOpen = { record ->
                    // A swap still awaiting its deposit reopens the deposit instructions (address,
                    // QR, steps); anything further along — or an old record saved before deposit
                    // details were persisted — opens the read-only info screen.
                    if (record.swapStatus == SwapStatus.NotStarted && record.depositAddress != null) {
                        resumeUuid = record.uuid
                    } else {
                        infoUuid = record.uuid
                    }
                },
            )
        }

        data == null -> MainSwapScreen(
            onClose = { /* root screen — nothing to go back to */ },
            onOpenHistory = { historyOpen = true },
            onProceed = { amountIn, tokenIn, tokenOut, provider, amountOut, fiatIn, fiatOut ->
                proceed = ProceedData(amountIn, tokenIn, tokenOut, provider, amountOut, fiatIn, fiatOut)
            },
        )

        destination == null -> {
            // System back gesture/button mirrors the on-screen back button.
            BackHandler { proceed = null }
            AddressInputScreen(
                token = data.tokenOut,
                title = "Recipient Address",
                description = "The ${data.tokenOut.networkName} wallet that will receive the swapped funds.",
                onBack = { proceed = null },
                onConfirm = { savedDestination = it; destination = it },
                initial = savedDestination,
            )
        }

        needsRefund && refund == null -> {
            BackHandler { destination = null }
            AddressInputScreen(
                token = data.tokenIn,
                title = "Refund Address",
                description = "A ${data.tokenIn.networkName} wallet you control, used only if the swap fails.",
                onBack = { destination = null },
                onConfirm = { savedRefund = it; refund = it },
                initial = savedRefund,
            )
        }

        else -> {
            val viewModel = viewModel<SwapExecutionViewModel>(
                // Key on every input that defines the swap so editing the amount (or token/provider)
                // and returning rebuilds the intent instead of reusing a stale cached ViewModel.
                key = listOf(
                    data.tokenIn.identifier,
                    data.tokenOut.identifier,
                    data.amountIn.stripTrailingZeros().toPlainString(),
                    data.provider.id,
                    destination,
                    refund,
                ).joinToString("-"),
                factory = SwapExecutionViewModel.Factory(
                    tokenIn = data.tokenIn,
                    tokenOut = data.tokenOut,
                    amountIn = data.amountIn,
                    amountOut = data.amountOut,
                    fiatIn = data.fiatIn,
                    fiatOut = data.fiatOut,
                    provider = data.provider,
                    destinationAddress = destination!!,
                    refundAddress = refund,
                    history = history,
                ),
            )
            val onBack = { if (needsRefund) refund = null else destination = null }
            BackHandler { onBack() }
            ActiveSwapTrackingScreen(
                uiState = viewModel.uiState,
                onBack = onBack,
                onDone = {
                    // Swap finished — reset the amount and addresses so the next swap starts fresh.
                    mainSwapViewModel.onEnterAmount(null)
                    savedDestination = null
                    savedRefund = null
                    refund = null
                    destination = null
                    proceed = null
                },
                onRetry = viewModel::retry,
            )
        }
    }
}
