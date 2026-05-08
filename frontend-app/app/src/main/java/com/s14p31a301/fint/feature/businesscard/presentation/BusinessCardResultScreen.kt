package com.s14p31a301.fint.feature.businesscard.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.s14p31a301.fint.feature.common.ui.BusinessCardPreview
import com.s14p31a301.fint.feature.common.ui.EditableFormRow
import com.s14p31a301.fint.feature.common.ui.FintTopHeader
import com.s14p31a301.fint.feature.common.ui.RegisterProgressContent
import com.s14p31a301.fint.feature.common.ui.RegistrationCompletionContent
import com.s14p31a301.fint.ui.theme.BrandCyan
import com.s14p31a301.fint.ui.theme.DarkHeader
import com.s14p31a301.fint.ui.theme.SurfaceCard
import com.s14p31a301.fint.ui.theme.TextPrimary
import com.s14p31a301.fint.ui.theme.TextSecondary
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 명함 OCR 결과 확인/수정 → 담당자 등록 화면.
 * 단일 화면 내부에서 [BusinessCardUiState.Phase] 에 따라 confirm / saving / done 전환.
 */
@Composable
fun BusinessCardResultScreen(
    imagePath: String?,
    onRegistered: () -> Unit,
    onRetake: () -> Unit,
    onCancel: () -> Unit,
) {
    val viewModel: BusinessCardViewModel = koinViewModel { parametersOf(imagePath) }
    val state by viewModel.state.collectAsState()

    // Done 단계 도달 후 자동 종료까지 약간 더 보여주는 건 사용자 클릭으로 처리.
    AnimatedContent(
        targetState = state.phase,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "phase",
    ) { phase ->
        when (phase) {
            BusinessCardUiState.Phase.Confirm -> ConfirmContent(
                state = state,
                viewModel = viewModel,
                onRetake = onRetake,
                onCancel = onCancel,
            )
            BusinessCardUiState.Phase.Saving -> RegisterProgressContent(
                onComplete = viewModel::onSavingProgressFinished,
            )
            BusinessCardUiState.Phase.Done -> RegistrationCompletionContent(
                onCta = onRegistered,
            )
        }
    }
}

@Composable
private fun ConfirmContent(
    state: BusinessCardUiState,
    viewModel: BusinessCardViewModel,
    onRetake: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(DarkHeader),
    ) {
        Column(Modifier.fillMaxSize()) {
            FintTopHeader(
                title = "명함 등록",
                onBack = onRetake,
                onClose = onCancel,
                backTint = BrandCyan,
            )

            // White rounded sheet
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 20.dp, bottom = 120.dp),
                ) {
                    // Card preview
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(Modifier.padding(12.dp)) {
                            BusinessCardPreview(
                                name = state.form.name.ifBlank { null },
                                company = state.form.company.ifBlank { null },
                                position = state.form.position.ifBlank { null },
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text = "정보를 확인해주세요.",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(Modifier.height(8.dp))

                    if (state.isOcrInProgress) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                color = BrandCyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                            Text(
                                "명함을 분석 중이에요…",
                                color = TextSecondary,
                                fontSize = 14.sp,
                            )
                        }
                    } else {
                        EditableFormRow("이름", state.form.name, viewModel::updateName, placeholder = "예) 이민정")
                        EditableFormRow("회사", state.form.company, viewModel::updateCompany, placeholder = "예) 삼성SDS")
                        EditableFormRow("직책", state.form.position, viewModel::updatePosition, placeholder = "예) 수석 아키텍트")
                        EditableFormRow("전화번호", state.form.phone, viewModel::updatePhone, placeholder = "010-0000-0000")
                        EditableFormRow("이메일", state.form.email, viewModel::updateEmail, placeholder = "name@company.com", isLast = true)
                    }
                }
            }
        }

        // Bottom action bar
        Surface(
            color = SurfaceCard,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shadowElevation = 4.dp,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onRetake,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF3F4F6),
                        contentColor = TextSecondary,
                    ),
                ) {
                    Text("다시 찍기", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = viewModel::register,
                    enabled = !state.isOcrInProgress,
                    modifier = Modifier.weight(1.2f).height(52.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandCyan),
                ) {
                    Text("담당자로 저장", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
