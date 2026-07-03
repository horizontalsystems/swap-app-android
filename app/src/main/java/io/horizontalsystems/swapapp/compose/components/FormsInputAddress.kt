package io.horizontalsystems.swapapp.compose.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.horizontalsystems.swapapp.R
import io.horizontalsystems.swapapp.compose.ColoredTextStyle
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Pre-processes text that arrives whole (clipboard paste, QR scan, clear) before it lands in the
 * field — e.g. extracting a bare address from a BIP21/EIP-681 payment URI.
 */
interface TextPreprocessor {
    fun process(text: String): String
}

object TextPreprocessorImpl : TextPreprocessor {
    override fun process(text: String): String = text
}

/** Validation state rendered by [FormsInputAddress]: field spinner, state icon and caution text. */
sealed class AddressInputState {
    object Loading : AddressInputState()
    data class Error(val message: String) : AddressInputState()
    object Success : AddressInputState()
}

/**
 * Address input field, ported from the reference wallet's `FormsInputAddress` with the same specs:
 * 16dp-rounded surface with a 0.5dp border, min height 44dp, `body` text with 16/12dp padding,
 * 28dp circular scan / clear buttons and a 28dp "Paste" pill while empty, a spinner / state icon
 * for [state], and the error message in `caption` below the field.
 *
 * Paste, scan and clear are handled internally (zxing scanner via [scanPrompt]); text arriving
 * whole through paste/scan is run through [textPreprocessor] and delivered to [onWholeInput] (so
 * the caller can validate eagerly), falling back to [onValueChange], which receives typed edits.
 */
@Composable
fun FormsInputAddress(
    modifier: Modifier = Modifier,
    value: String,
    hint: String,
    state: AddressInputState? = null,
    showStateIcon: Boolean = true,
    textPreprocessor: TextPreprocessor = TextPreprocessorImpl,
    scanPrompt: String = "",
    onValueChange: (String) -> Unit,
    onWholeInput: ((String) -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current
    val deliverWholeInput = onWholeInput ?: onValueChange

    val textColor = when (state) {
        is AddressInputState.Error -> ComposeAppTheme.colors.lucian
        else -> ComposeAppTheme.colors.leah
    }

    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 44.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(0.5.dp, ComposeAppTheme.colors.blade, RoundedCornerShape(16.dp))
                .background(ComposeAppTheme.colors.lawrence),
            verticalAlignment = Alignment.CenterVertically
        ) {

            BasicTextField(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .weight(1f),
                enabled = true,
                value = value,
                onValueChange = onValueChange,
                textStyle = ColoredTextStyle(
                    color = textColor,
                    textStyle = ComposeAppTheme.typography.body
                ),
                singleLine = false,
                cursorBrush = SolidColor(ComposeAppTheme.colors.leah),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            hint,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            color = ComposeAppTheme.colors.andy,
                            style = ComposeAppTheme.typography.body
                        )
                    }
                    innerTextField()
                },
                visualTransformation = VisualTransformation.None,
                keyboardOptions = KeyboardOptions.Default,
            )

            when (state) {
                is AddressInputState.Loading -> {
                    HSCircularProgressIndicator()
                }
                is AddressInputState.Error -> {
                    if (showStateIcon) {
                        Icon(
                            modifier = Modifier.padding(end = 8.dp),
                            painter = painterResource(id = R.drawable.ic_attention_20),
                            contentDescription = null,
                            tint = ComposeAppTheme.colors.lucian
                        )
                    } else {
                        HSpacer(28.dp)
                    }
                }
                is AddressInputState.Success -> {
                    if (showStateIcon) {
                        Icon(
                            modifier = Modifier.padding(end = 8.dp),
                            painter = painterResource(id = R.drawable.ic_check_20),
                            contentDescription = null,
                            tint = ComposeAppTheme.colors.remus
                        )
                    } else {
                        HSpacer(28.dp)
                    }
                }
                else -> {
                    Spacer(modifier = Modifier.width(28.dp))
                }
            }

            if (value.isNotEmpty()) {
                ButtonSecondaryCircle(
                    modifier = Modifier.padding(end = 16.dp),
                    icon = R.drawable.ic_delete_20,
                    contentDescription = "Clear address",
                    onClick = {
                        onValueChange.invoke(textPreprocessor.process(""))
                        focusRequester.requestFocus()
                    }
                )
            } else {
                val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
                    result.contents?.let {
                        deliverWholeInput.invoke(textPreprocessor.process(it))
                    }
                }

                ButtonSecondaryCircle(
                    modifier = Modifier.padding(end = 8.dp),
                    icon = R.drawable.ic_qr_scan_20,
                    contentDescription = "Scan QR code",
                    onClick = {
                        scanLauncher.launch(
                            ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt(scanPrompt)
                                setBeepEnabled(false)
                                setOrientationLocked(false)
                            }
                        )
                    }
                )

                ButtonSecondaryDefault(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .height(28.dp),
                    title = "Paste",
                    onClick = {
                        clipboard.getText()?.text?.let { textInClipboard ->
                            deliverWholeInput.invoke(textPreprocessor.process(textInClipboard))
                        }
                    },
                )
            }
        }

        (state as? AddressInputState.Error)?.message?.let {
            Text(
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp),
                text = it,
                color = ComposeAppTheme.colors.lucian,
                style = ComposeAppTheme.typography.caption
            )
        }
    }
}
