package com.s14p31a301.fint.feature.devicecontact.presentation

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.s14p31a301.fint.feature.common.ui.EditableFormRow
import com.s14p31a301.fint.feature.common.ui.FintTopHeader
import com.s14p31a301.fint.feature.common.ui.RegisterProgressContent
import com.s14p31a301.fint.feature.common.ui.RegistrationCompletionContent
import com.s14p31a301.fint.ui.theme.BrandCyan
import com.s14p31a301.fint.ui.theme.CyanLight
import com.s14p31a301.fint.ui.theme.DeepCyan
import com.s14p31a301.fint.ui.theme.PageBg
import com.s14p31a301.fint.ui.theme.SurfaceCard
import com.s14p31a301.fint.ui.theme.TextMuted
import com.s14p31a301.fint.ui.theme.TextPrimary
import com.s14p31a301.fint.ui.theme.TextSecondary

/**
 * 선택된 기기 연락처 → 담당자 등록 확인/수정 화면.
 *
 * @param viewModel List/Select 단계를 공유하는 NavGraph-scoped VM
 * @param contactId list 화면에서 받은 id (state.source 가 비어있을 경우 fallback 으로 조회)
 */
@Composable
fun DeviceContactSelectScreen(
    viewModel: DeviceContactViewModel,
    contactId: String,
    onRegistered: () -> Unit,
    onCancel: () -> Unit,
) {
    val state by viewModel.select.collectAsState()

    // 프로세스 사망 등으로 select 상태가 비어있는 경우 list에서 다시 채움
    LaunchedEffect(contactId) {
        if (state.source == null) {
            viewModel.findById(contactId)?.let(viewModel::openSelected)
        }
    }

    AnimatedContent(
        targetState = state.phase,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "phase",
    ) { phase ->
        when (phase) {
            DeviceContactSelectUiState.Phase.Confirm -> ConfirmContent(
                state = state,
                viewModel = viewModel,
                onCancel = onCancel,
            )
            DeviceContactSelectUiState.Phase.Saving -> RegisterProgressContent(
                name = state.form.name,
                company = state.form.company,
                position = state.form.position,
                phone = state.form.phone,
                email = state.form.email,
                onComplete = viewModel::onSavingProgressFinished,
            )
            DeviceContactSelectUiState.Phase.Done -> RegistrationCompletionContent(
                onCta = onRegistered,
            )
        }
    }
}

@Composable
private fun ConfirmContent(
    state: DeviceContactSelectUiState,
    viewModel: DeviceContactViewModel,
    onCancel: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(DeepCyan),
    ) {
        Column(Modifier.fillMaxSize()) {
            FintTopHeader(
                title = "담당자 추가",
                onBack = onCancel,
                onClose = onCancel,
                backTint = Color.White,
            )

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
                        .padding(top = 24.dp, bottom = 120.dp),
                ) {
                    // Header avatar
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(CyanLight),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                state.form.name.firstOrNull()?.toString() ?: "?",
                                color = BrandCyan,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.size(14.dp))
                        Column {
                            Text(
                                state.form.name.ifBlank { "이름" },
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "기기 연락처에서 가져왔어요",
                                color = TextMuted,
                                fontSize = 13.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "정보를 확인해주세요.",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(Modifier.height(8.dp))

                    EditableFormRow("이름", state.form.name, viewModel::updateName, placeholder = "이름")
                    EditableFormRow("회사", state.form.company, viewModel::updateCompany, placeholder = "회사")
                    EditableFormRow("직책", state.form.position, viewModel::updatePosition, placeholder = "직책")
                    EditableFormRow("전화번호", state.form.phone, viewModel::updatePhone, placeholder = "전화번호")
                    EditableFormRow("이메일", state.form.email, viewModel::updateEmail, placeholder = "이메일", isLast = true)
                }
            }
        }

        // Bottom action bar — 단일 등록 버튼
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
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PageBg,
                        contentColor = TextSecondary,
                    ),
                ) { Text("취소", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                Button(
                    onClick = viewModel::register,
                    enabled = state.form.name.isNotBlank(),
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
