package io.horizontalsystems.swapapp.components.cell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.horizontalsystems.swapapp.R
import io.horizontalsystems.swapapp.compose.ComposeAppTheme

@Composable
fun CellRightNavigation(
    title: HSString? = null,
    subtitle: HSString? = null,
    subtitle2: HSString? = null,
    icon: Painter? = null,
    iconTint: Color = LocalContentColor.current.copy(alpha = LocalContentAlpha.current),
) {
    Column {
        title?.let {
            Text(
                text = it.text,
                style = ComposeAppTheme.typography.headline2,
                color = it.color ?: ComposeAppTheme.colors.leah,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            subtitle?.let {
                Text(
                    modifier = Modifier.padding(start = 4.dp),
                    text = it.text,
                    style = ComposeAppTheme.typography.subheadSB,
                    color = it.color ?: ComposeAppTheme.colors.grey,
                )

                icon?.let {
                    Icon(
                        painter = it,
                        contentDescription = null,
                        tint = iconTint
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.arrow_b_right_24),
                contentDescription = null,
                tint = ComposeAppTheme.colors.grey
            )
        }
        subtitle2?.let {
            Text(
                modifier = Modifier.padding(start = 4.dp),
                text = it.text,
                style = ComposeAppTheme.typography.subheadSB,
                color = it.color ?: ComposeAppTheme.colors.grey,
            )
        }
    }
}
