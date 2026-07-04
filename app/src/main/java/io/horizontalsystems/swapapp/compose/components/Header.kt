package io.horizontalsystems.swapapp.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.horizontalsystems.swapapp.compose.ComposeAppTheme

@Composable
fun HeaderStick(
    borderTop: Boolean = false,
    borderBottom: Boolean = false,
    text: String,
    color: Color = ComposeAppTheme.colors.tyler,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(55.dp)
            .background(color)
    ) {
        if (borderTop) {
            HsDivider(modifier = Modifier.align(Alignment.TopCenter))
        }

        subheadSB_andy(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 12.dp),
            text = text,
            maxLines = 1,
        )

        if (borderBottom) {
            HsDivider(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
