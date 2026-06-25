package io.horizontalsystems.swapapp.compose

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

sealed class TranslatableString {
    class PlainString(val text: String) : TranslatableString()
    class ResString(@StringRes val id: Int, vararg val formatArgs: Any) : TranslatableString()

    @Composable
    fun getString(): String {
        return when (this) {
            is PlainString -> text
            is ResString -> stringResource(id, *formatArgs)
        }
    }

    override fun toString(): String {
        return when (this) {
            is PlainString -> text
            // Resource strings can only be resolved inside a Composable via getString();
            // outside composition we have no Context, so fall back to an empty string.
            is ResString -> ""
        }
    }
}

interface WithTranslatableTitle {
    val title: TranslatableString
}
