package com.s14p31a301.fint.feature.common.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.s14p31a301.fint.ui.theme.Border
import com.s14p31a301.fint.ui.theme.BrandCyan
import com.s14p31a301.fint.ui.theme.CyanBorder
import com.s14p31a301.fint.ui.theme.PageBg
import com.s14p31a301.fint.ui.theme.Placeholder
import com.s14p31a301.fint.ui.theme.TextPrimary
import kotlinx.coroutines.delay

/**
 * 시안의 "데이터베이스 조회 → 페이지 생성 → 담당자 정보 업데이트" 3-step 진행 화면.
 * 각 step 활성화 시 아래로 카드가 펼쳐지고, 다음 step 으로 넘어가면 닫힌다.
 */
@Composable
fun RegisterProgressContent(
    title: String = "새로운 담당자를\n등록할게요",
    stepDurationMs: Long = 3000L,
    name: String? = null,
    company: String? = null,
    position: String? = null,
    phone: String? = null,
    email: String? = null,
    onComplete: () -> Unit,
) {
    val steps = listOf(
        StepDef("F!NT 데이터베이스 조회") { vis ->
            DatabaseSearchCard(visible = vis, name = name, company = company)
        },
        StepDef("페이지 생성") { vis ->
            PageCreationCard(visible = vis, name = name, company = company)
        },
        StepDef("담당자 정보 업데이트") { vis ->
            ContactUpdateCard(
                visible = vis,
                name = name,
                company = company,
                position = position,
                phone = phone,
                email = email,
            )
        },
    )

    var currentStep by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        for (i in steps.indices) {
            currentStep = i
            delay(stepDurationMs)
        }
        currentStep = steps.size
        delay(500L)
        onComplete()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBg)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 24.dp)
            .padding(top = 40.dp, bottom = 32.dp),
    ) {
        Text(
            text = title,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            lineHeight = 36.sp,
        )
        Spacer(Modifier.height(40.dp))

        steps.forEachIndexed { index, step ->
            StepRow(
                title = step.title,
                status = when {
                    currentStep > index -> StepStatus.Completed
                    currentStep == index -> StepStatus.Loading
                    else -> StepStatus.Pending
                },
                showConnector = index < steps.lastIndex,
                expanded = currentStep == index,
                cardContent = step.card,
            )
        }
    }
}

private data class StepDef(
    val title: String,
    val card: @Composable (visible: Boolean) -> Unit,
)

private enum class StepStatus { Pending, Loading, Completed }

@Composable
private fun StepRow(
    title: String,
    status: StepStatus,
    showConnector: Boolean,
    expanded: Boolean,
    cardContent: @Composable (Boolean) -> Unit,
) {
    // 외부 Box 의 높이는 내부 Row(=인디케이터+우측 컨텐츠) 의 실제 측정 높이를 따라감.
    // shrinkVertically/expandVertically 가 우측 Column 의 layout 높이를 매 프레임 변경하면
    // Box 높이도 같이 변하고, matchParentSize() 로 깔린 연결선도 함께 줄었다 늘었다 한다.
    Box(Modifier.fillMaxWidth()) {
        // 좌측 인디케이터 아래에 깔리는 연결선 (Row 자식들이 그려지기 전, 같은 z-order 하단)
        if (showConnector) {
            Box(
                Modifier
                    .matchParentSize()
                    // 인디케이터(32dp) 아래부터 시작
                    .padding(start = 15.dp, top = 32.dp),
            ) {
                Box(
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(
                            if (status == StepStatus.Pending) Border else BrandCyan
                        ),
                )
            }
        }

        Row(Modifier.fillMaxWidth()) {
            StepIndicator(status)

            Spacer(Modifier.width(16.dp))

            // 우측 타이틀 + (활성 시) 카드
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (showConnector) 20.dp else 0.dp),
            ) {
                Text(
                    text = title,
                    color = if (status == StepStatus.Pending) Placeholder else TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp),
                )

                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(450)) + expandVertically(tween(500)),
                    exit = fadeOut(tween(300)) + shrinkVertically(tween(400)),
                ) {
                    Box(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                        cardContent(expanded)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(status: StepStatus) {
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        when (status) {
            StepStatus.Completed -> Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(BrandCyan),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check, null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            StepStatus.Loading -> {
                // 정적 외곽(연한 cyan border) + 회전하는 indeterminate progress 를 겹쳐서 표현
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
                CircularProgressIndicator(
                    color = BrandCyan,
                    trackColor = CyanBorder,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(28.dp),
                )
            }
            StepStatus.Pending -> Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Border),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check, null,
                    tint = Placeholder,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
