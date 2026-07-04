package io.horizontalsystems.swapapp.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.horizontalsystems.swapapp.R
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.compose.components.ButtonPrimaryYellow
import io.horizontalsystems.swapapp.compose.components.VSpacer
import kotlinx.coroutines.launch

private data class OnboardingSlide(
    @DrawableRes val image: Int,
    val title: String,
    val description: String,
)

private val slides = listOf(
    OnboardingSlide(
        image = R.drawable.slide_1,
        title = "Any coin. Any chain. No questions.",
        description = "Swap thousands of tokens across Bitcoin, Ethereum, Solana, Tron, Cosmos and more — no exchange account in the way.",
    ),
    OnboardingSlide(
        image = R.drawable.slide_2,
        title = "Nothing to sign up for.",
        description = "Swap App never holds your coins and never asks who you are. Your keys stay in your wallet, where they belong.",
    ),
    OnboardingSlide(
        image = R.drawable.slide_3,
        title = "The best rate finds you.",
        description = "Every swap is quoted across multiple providers at once. You see them all — the best one is already picked.",
    ),
    OnboardingSlide(
        image = R.drawable.slide_4,
        title = "Three steps. Done.",
        description = "Send to a one-time deposit address; the swapped coins land in your wallet. Every address is screened for safety first.",
    ),
)

/**
 * First-launch intro: four swipeable slides, each an illustration over a bottom-anchored
 * title/description block, with an animated page indicator and a Next / "Start Swapping" button.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == slides.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            //.background(ComposeAppTheme.colors.tyler)
            .background(Color(0xFFF0F0F2))
            .systemBarsPadding(),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            val slide = slides[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(slide.image),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                Text(
                    text = slide.title,
                    style = ComposeAppTheme.typography.title3.copy(
                        fontSize = 32.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = ComposeAppTheme.colors.leah,
                )
                VSpacer(12.dp)
                Text(
                    text = slide.description,
                    style = ComposeAppTheme.typography.body.copy(lineHeight = 24.sp),
                    color = ComposeAppTheme.colors.grey,
                )
                VSpacer(24.dp)
            }
        }

        PagerIndicator(
            count = slides.size,
            currentPage = pagerState.currentPage,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        VSpacer(20.dp)
        ButtonPrimaryYellow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            title = if (isLastPage) "Start Swapping" else "Next",
            onClick = {
                if (isLastPage) {
                    onFinish()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
        )
        VSpacer(16.dp)
    }
}

@Composable
private fun PagerIndicator(count: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) { index ->
            val active = index == currentPage
            val width by animateDpAsState(if (active) 28.dp else 8.dp, label = "indicatorWidth")
            val color by animateColorAsState(
                if (active) ComposeAppTheme.colors.yellowD else ComposeAppTheme.colors.blade,
                label = "indicatorColor",
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
    }
}
