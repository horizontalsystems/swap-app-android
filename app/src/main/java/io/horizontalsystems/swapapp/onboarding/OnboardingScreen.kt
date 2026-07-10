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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        image = R.drawable.image1,
        title = "Any Coin, Any Chain",
        description = "Cross-chain Swap",
    ),
    OnboardingSlide(
        image = R.drawable.image2,
        title = "No KYC, No Signup",
        description = "Swap without verifications",
    ),
    OnboardingSlide(
        image = R.drawable.image3,
        title = "Best rates",
        description = "Multiple Providers in one",
    ),
)

/**
 * First-launch intro: three swipeable slides, each a centered illustration with a centered
 * title/subtitle below, an animated page indicator and a Next / "Get Started" button.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == slides.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeAppTheme.colors.lawrence)
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
                horizontalAlignment = Alignment.CenterHorizontally,
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
                            .padding(vertical = 24.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                Text(
                    text = slide.title,
                    style = ComposeAppTheme.typography.title3,
                    color = ComposeAppTheme.colors.leah,
                    textAlign = TextAlign.Center,
                )
                VSpacer(8.dp)
                Text(
                    text = slide.description,
                    style = ComposeAppTheme.typography.body,
                    color = ComposeAppTheme.colors.grey,
                    textAlign = TextAlign.Center,
                )
                VSpacer(38.dp)
            }
        }

        PagerIndicator(
            count = slides.size,
            currentPage = pagerState.currentPage,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        VSpacer(54.dp)
        ButtonPrimaryYellow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            title = if (isLastPage) "Get Started" else "Next",
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
            val width by animateDpAsState(if (active) 24.dp else 16.dp, label = "indicatorWidth")
            val color by animateColorAsState(
                if (active) ComposeAppTheme.colors.yellowD else ComposeAppTheme.colors.blade,
                label = "indicatorColor",
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}
