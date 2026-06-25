package io.horizontalsystems.swapapp.compose.components

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import io.horizontalsystems.swapapp.compose.TranslatableString

sealed class IMenuItem

data object MenuItemLoading : IMenuItem()

data class MenuItemTimeoutIndicator(
    val progress: Float
) : IMenuItem()

data class MenuItem(
    val title: TranslatableString,
    @DrawableRes val icon: Int? = null,
    val enabled: Boolean = true,
    val tint: Color = Color.Unspecified,
    val showAlertDot: Boolean = false,
    val onClick: () -> Unit,
) : IMenuItem()

data class MenuItemDropdown(
    val title: TranslatableString,
    @DrawableRes val icon: Int,
    val enabled: Boolean = true,
    val items: List<MenuItem>,
    val iconTint: Color? = null
) : IMenuItem()