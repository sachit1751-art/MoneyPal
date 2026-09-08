package com.sachit.moneypal.presentation.ui.onboarding

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sachit.moneypal.R
import com.sachit.moneypal.presentation.LocalWindowInsets
import com.sachit.moneypal.presentation.ui.analytics.Size
import com.sachit.moneypal.presentation.ui.theme.MinusTheme
import com.sachit.moneypal.presentation.ui.theme.bodyMediumCondensed
import com.sachit.moneypal.presentation.ui.theme.component.DescriptionButton
import com.sachit.moneypal.presentation.ui.theme.component.LocalBottomSheetScrollState
import com.sachit.moneypal.presentation.util.combineColors
import kotlinx.coroutines.launch
import logcat.logcat

private const val TAG = "SACHIT:OnboardingScreen"

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onOnboardingCompleted: () -> Unit = {},
    onRequestSmsPermission: () -> Unit = {},
) {
    logcat(TAG) { "OnboardingScreen composed" }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            logcat(TAG) { "OnboardingScreen received effect: $effect" }
            when (effect) {
                OnboardingUiEffect.OnboardingCompleted -> {
                    logcat(TAG) { "OnboardingScreen -> invoking onOnboardingCompleted()" }
                    onOnboardingCompleted()
                }
                is OnboardingUiEffect.OnboardingFailed -> {
                    // Failures are also reflected in [OnboardingUiState.error];
                    // the parent screen (or activity) may surface them.
                }
            }
        }
    }

    OnboardingScreenContent(
        onContinue = {
            logcat(TAG) { "Get started tapped -> dispatching OnWelcomeDismissed" }
            viewModel.processIntent(OnboardingUiIntent.OnWelcomeDismissed)
        },
        onSmsEnable = {
            viewModel.processIntent(OnboardingUiIntent.OnSmsCaptureDecision(enabled = true))
            onRequestSmsPermission()
            viewModel.processIntent(OnboardingUiIntent.OnWelcomeDismissed)
        },
        onSmsNotNow = {
            viewModel.processIntent(OnboardingUiIntent.OnSmsCaptureDecision(enabled = false))
            viewModel.processIntent(OnboardingUiIntent.OnWelcomeDismissed)
        },
    )
}

/** Number of pages in the welcome carousel. */
private const val PAGE_COUNT = 4

@Composable
internal fun OnboardingScreenContent(
    onContinue: () -> Unit = {},
    onSmsEnable: () -> Unit = {},
    onSmsNotNow: () -> Unit = {},
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val coroutineScope = rememberCoroutineScope()

    val localBottomSheetScrollState = LocalBottomSheetScrollState.current
    val statusBarHeight = LocalWindowInsets.current.calculateTopPadding()
    val navigationBarHeight =
        LocalWindowInsets.current.calculateBottomPadding().coerceAtLeast(16.dp)

    val localDensity = LocalDensity.current
    var pageSize by remember { mutableStateOf(Size(0.dp, 0.dp)) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = if (localBottomSheetScrollState.topPadding > 0.dp) {
                    localBottomSheetScrollState.topPadding
                } else {
                    statusBarHeight
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    pageSize = Size(
                        width = with(localDensity) { it.size.width.toDp() },
                        height = with(localDensity) { it.size.height.toDp() }
                    )
                }
        ) {
            WelcomeBackground(pageSize = pageSize)

            Column(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) { page ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        when (page) {
                            0 -> CarouselPage(
                                titleRes = R.string.carousel_page_welcome_title,
                                bodyRes = R.string.carousel_page_welcome_body,
                                iconRes = R.drawable.ic_notification,
                            )

                            1 -> CarouselPage(
                                titleRes = R.string.carousel_page_budget_title,
                                bodyRes = R.string.carousel_page_budget_body,
                                iconRes = R.drawable.ic_tile_new_transaction,
                            )

                            2 -> CarouselPage(
                                titleRes = R.string.carousel_page_numpad_title,
                                bodyRes = R.string.carousel_page_numpad_body,
                                iconRes = R.drawable.shape_soft_star_1,
                            )

                            else -> SmsOptInPage(
                                onEnable = onSmsEnable,
                                onNotNow = onSmsNotNow,
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }

                // Bottom controls: Skip | dots | Next / Get started
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = navigationBarHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onContinue,
                        enabled = pagerState.currentPage < PAGE_COUNT - 1,
                    ) {
                        Text(stringResource(R.string.carousel_skip))
                    }

                    Spacer(Modifier.weight(1f))

                    PagerDotsIndicator(
                        pageCount = PAGE_COUNT,
                        currentPage = pagerState.currentPage,
                    )

                    Spacer(Modifier.weight(1f))

                    if (pagerState.currentPage < PAGE_COUNT - 1) {
                        DescriptionButton(
                            title = { Text(stringResource(R.string.carousel_next)) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 24.dp, vertical = 16.dp
                            ),
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                        )
                    } else {
                        DescriptionButton(
                            title = { Text(stringResource(R.string.carousel_get_started)) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 24.dp, vertical = 16.dp
                            ),
                            onClick = onContinue,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CarouselPage(
    titleRes: Int,
    bodyRes: Int,
    iconRes: Int,
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .requiredSize(96.dp)
            .alpha(0.9f),
    )
    Spacer(Modifier.height(32.dp))
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.headlineLargeEmphasized,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(bodyRes),
        style = MaterialTheme.typography.bodyMediumCondensed,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SmsOptInPage(
    onEnable: () -> Unit,
    onNotNow: () -> Unit,
) {
    Icon(
        painter = painterResource(R.drawable.ic_notification),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.requiredSize(96.dp),
    )
    Spacer(Modifier.height(32.dp))
    Text(
        text = stringResource(R.string.carousel_page_sms_title),
        style = MaterialTheme.typography.headlineLargeEmphasized,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.carousel_page_sms_body),
        style = MaterialTheme.typography.bodyMediumCondensed,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    DescriptionButton(
        title = { Text(stringResource(R.string.carousel_sms_enable)) },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        onClick = onEnable,
    )
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onNotNow) {
        Text(
            text = stringResource(R.string.carousel_sms_not_now),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PagerDotsIndicator(
    pageCount: Int,
    currentPage: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            Box(
                modifier = Modifier
                    .size(if (selected) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
            )
        }
    }
}

@Composable
private fun WelcomeBackground(pageSize: Size) {
    val halfWidth = pageSize.width / 2
    val halfHeight = pageSize.height / 2

    val starColor = combineColors(
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.surface,
        0.5f,
    )

    val infiniteTransition = rememberInfiniteTransition(label = "backgroundTransitions")

    val angle1 by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(10000), RepeatMode.Reverse),
        label = "angle1"
    )

    val angle2 by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(tween(18000), RepeatMode.Reverse),
        label = "angle2"
    )

    val angle3 by infiniteTransition.animateFloat(
        initialValue = 30f,
        targetValue = -30f,
        animationSpec = infiniteRepeatable(tween(14000), RepeatMode.Reverse),
        label = "angle3"
    )

    val angle4 by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 170f,
        animationSpec = infiniteRepeatable(tween(25000), RepeatMode.Reverse),
        label = "angle4"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Shape 1: Top Right
        Icon(
            modifier = Modifier
                .requiredSize(256.dp)
                .absoluteOffset(x = halfWidth * 0.8f, y = -halfHeight * 0.7f)
                .rotate(angle1),
            painter = painterResource(R.drawable.shape_soft_star_1),
            tint = starColor,
            contentDescription = null,
        )

        // Shape 2: Bottom Left
        Icon(
            modifier = Modifier
                .requiredSize(256.dp)
                .absoluteOffset(x = -halfWidth * 0.8f, y = halfHeight * 0.6f)
                .rotate(angle2),
            painter = painterResource(R.drawable.shape_soft_star_2),
            tint = starColor,
            contentDescription = null,
        )

        // Shape 3: Center Left
        Icon(
            modifier = Modifier
                .requiredSize(180.dp)
                .absoluteOffset(x = -halfWidth * 0.9f, y = -halfHeight * 0.2f)
                .rotate(angle3),
            painter = painterResource(R.drawable.shape_soft_star_1),
            tint = starColor,
            contentDescription = null,
        )

        // Shape 4: Bottom Right
        Icon(
            modifier = Modifier
                .requiredSize(220.dp)
                .absoluteOffset(x = halfWidth * 0.6f, y = halfHeight * 0.2f)
                .rotate(angle4),
            painter = painterResource(R.drawable.shape_soft_star_2),
            tint = starColor,
            contentDescription = null,
        )
    }
}

@PreviewLightDark
@Composable
private fun OnboardingScreenPreview() {
    MinusTheme {
        OnboardingScreenContent()
    }
}
