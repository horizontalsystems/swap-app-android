package io.horizontalsystems.swapapp.swap.execution

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import io.horizontalsystems.swapapp.swap.SwapToken
import io.horizontalsystems.swapapp.components.HSScaffold
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.compose.components.ButtonPrimaryYellow
import io.horizontalsystems.swapapp.compose.components.VSpacer

/**
 * Asks the user for an address on [token]'s chain (recipient or refund). Provides clipboard paste,
 * basic per-chain validation and a Confirm button that hands the validated address back via
 * [onConfirm].
 */
@Composable
fun AddressInputScreen(
    token: SwapToken,
    title: String,
    heading: String,
    description: String,
    onBack: () -> Unit,
    onConfirm: (address: String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var address by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    HSScaffold(title = title, onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = heading,
                style = ComposeAppTheme.typography.headline1,
                color = ComposeAppTheme.colors.leah,
            )
            VSpacer(8.dp)
            Text(
                text = description,
                style = ComposeAppTheme.typography.subhead,
                color = ComposeAppTheme.colors.grey,
            )

            VSpacer(24.dp)

            // Address input + paste
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ComposeAppTheme.colors.lawrence)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = address,
                            onValueChange = {
                                address = it
                                error = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = ComposeAppTheme.typography.subhead.copy(
                                color = ComposeAppTheme.colors.leah
                            ),
                            cursorBrush = SolidColor(ComposeAppTheme.colors.jacob),
                            decorationBox = { inner ->
                                if (address.isEmpty()) {
                                    Text(
                                        text = "Paste or type address",
                                        style = ComposeAppTheme.typography.subhead,
                                        color = ComposeAppTheme.colors.grey,
                                    )
                                }
                                inner()
                            },
                        )
                    }
                    Text(
                        text = "Paste",
                        style = ComposeAppTheme.typography.subheadSB,
                        color = ComposeAppTheme.colors.jacob,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                clipboard.getText()?.text?.let {
                                    address = it.trim()
                                    error = null
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            error?.let {
                VSpacer(8.dp)
                Text(
                    text = it,
                    style = ComposeAppTheme.typography.subhead,
                    color = ComposeAppTheme.colors.lucian,
                )
            }

            VSpacer(24.dp)

            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = "Confirm",
                enabled = address.isNotBlank(),
                onClick = {
                    val validationError = SwapAddressValidator.validate(address, token)
                    if (validationError == null) {
                        onConfirm(address.trim())
                    } else {
                        error = validationError
                    }
                },
            )
        }
    }
}
