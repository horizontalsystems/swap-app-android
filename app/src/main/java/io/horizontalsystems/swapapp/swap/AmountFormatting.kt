package io.horizontalsystems.swapapp.swap

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Smart coin-amount rounding ported from Unstoppable's `NumberFormatter`/`NumberRounding`: show
 * only the significant fraction digits instead of a token's full (up to 18) decimal precision.
 *
 * For values below 1 the leading zeros after the dot don't count, so small amounts still keep
 * [SIGNIFICANT_DECIMALS] meaningful digits (e.g. `0.000012345678`); everything else is capped at
 * [SIGNIFICANT_DECIMALS] places. The result is never more precise than [maxDecimals] (the token's
 * own decimals) and has trailing zeros stripped.
 */
private const val SIGNIFICANT_DECIMALS = 8

fun roundCoinAmount(value: BigDecimal, maxDecimals: Int): BigDecimal {
    if (value.signum() == 0) return BigDecimal.ZERO

    val abs = value.abs()
    val decimals = if (abs < BigDecimal.ONE) {
        zerosAfterDot(abs) + SIGNIFICANT_DECIMALS
    } else {
        SIGNIFICANT_DECIMALS
    }.coerceIn(0, maxDecimals)

    return value.setScale(decimals, RoundingMode.DOWN).stripTrailingZeros()
}

/** Display string for a coin amount, smart-rounded via [roundCoinAmount]; "0" when null or zero. */
fun formatCoinAmount(value: BigDecimal?, maxDecimals: Int): String {
    val rounded = value?.let { roundCoinAmount(it, maxDecimals) }
    return if (rounded == null || rounded.signum() == 0) "0" else rounded.toPlainString()
}

/** Zeros between the decimal point and the first significant digit (0 for values ≥ 1). */
private fun zerosAfterDot(value: BigDecimal): Int =
    (value.scale() - value.precision()).coerceAtLeast(0)
