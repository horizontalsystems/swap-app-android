package io.horizontalsystems.swapapp.compose.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.horizontalsystems.swapapp.compose.ComposeAppTheme

@Composable
fun HSCircularProgressIndicator() {
    // CircularProgressIndicator doesn't allow to change its size; resized using
    // modifier size with padding — the ordering of modifiers is important.
    CircularProgressIndicator(
        modifier = Modifier
            .size(28.dp)
            .padding(top = 4.dp, end = 8.dp),
        color = ComposeAppTheme.colors.grey,
        strokeWidth = 2.dp
    )
}
