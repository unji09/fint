package com.s14p31a301.fint.feature.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.s14p31a301.fint.ui.theme.BrandCyan
import com.s14p31a301.fint.ui.theme.CardAccentRed
import com.s14p31a301.fint.ui.theme.CardGradEnd
import com.s14p31a301.fint.ui.theme.CardGradMid
import com.s14p31a301.fint.ui.theme.CardGradStart
import com.s14p31a301.fint.ui.theme.DarkHeader

/**
 * 시안의 상단 다크 헤더 ( ◀  타이틀  ✕ )
 */
@Composable
fun FintTopHeader(
    title: String,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    backTint: Color = Color.White,
    background: Color = DarkHeader,
) {
    Surface(color = background, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "뒤로",
                    tint = backTint,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "닫기", tint = Color.White)
            }
        }
    }
}

/**
 * 시안의 명함 미리보기 (그라디언트 카드).
 * 정보 없을 시에도 표시되는 placeholder.
 */
@Composable
fun BusinessCardPreview(
    name: String?,
    company: String?,
    position: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.8f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(listOf(CardGradStart, CardGradMid, CardGradEnd))
            )
            .padding(20.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = company ?: "Company",
                color = CardAccentRed,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = name ?: "Name",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = position ?: "Position",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
            )
        }
    }
}

