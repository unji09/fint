package com.s14p31a301.fint.feature.common.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.s14p31a301.fint.ui.theme.BrandCyan
import com.s14p31a301.fint.ui.theme.PageBg
import com.s14p31a301.fint.ui.theme.TextMuted
import com.s14p31a301.fint.ui.theme.TextPrimary
import kotlinx.coroutines.delay

/**
 * 시안의 완료 화면. 체크 아이콘 → 타이틀 → 설명 → CTA 순으로 페이드인.
 */
@Composable
fun RegistrationCompletionContent(
    title: String = "담당자 등록 완료",
    description: String = "고객사 및 담당자 정보를 업데이트했습니다.",
    ctaText: String = "고객사 페이지 이동",
    onCta: () -> Unit,
) {
    var showIcon by remember { mutableStateOf(false) }
    var showTitle by remember { mutableStateOf(false) }
    var showDesc by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100); showIcon = true
        delay(400); showTitle = true
        delay(200); showDesc = true
        delay(300); showButton = true
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBg)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedVisibility(
            visible = showIcon,
            enter = fadeIn(tween(700)) + scaleIn(tween(700), initialScale = 0.3f),
            exit = fadeOut() + scaleOut(),
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF5EEAD4), BrandCyan))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        AnimatedVisibility(
            visible = showTitle,
            enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 4 },
        ) {
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
        }

        Spacer(Modifier.height(8.dp))

        AnimatedVisibility(
            visible = showDesc,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 },
        ) {
            Text(
                text = description,
                fontSize = 14.sp,
                color = TextMuted,
            )
        }

        Spacer(Modifier.height(60.dp))

        AnimatedVisibility(
            visible = showButton,
            enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 2 },
        ) {
            Button(
                onClick = onCta,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = BrandCyan),
            ) {
                Text(ctaText, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }
        }
    }
}

