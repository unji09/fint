package com.s14p31a301.fint.feature.common.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.s14p31a301.fint.ui.theme.BrandCyan
import com.s14p31a301.fint.ui.theme.CyanLight
import com.s14p31a301.fint.ui.theme.Placeholder
import com.s14p31a301.fint.ui.theme.SurfaceCard
import com.s14p31a301.fint.ui.theme.TextMuted
import com.s14p31a301.fint.ui.theme.TextPrimary
import com.s14p31a301.fint.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Step 1 — F!NT 데이터베이스 조회 카드.
 * 검색바 → 회사 칩 → 신규 담당자 칩 순으로 페이드 인.
 */
@Composable
fun DatabaseSearchCard(visible: Boolean) {
    var showSearch by remember { mutableStateOf(false) }
    var showCompany by remember { mutableStateOf(false) }
    var showContact by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            delay(250); showSearch = true
            delay(550); showCompany = true
            delay(550); showContact = true
        } else {
            showSearch = false; showCompany = false; showContact = false
        }
    }

    val itemEnter = fadeIn(tween(550)) + expandVertically(tween(500))

    StepCard {
        // 검색 박스
        AnimatedVisibility(visible = showSearch, enter = itemEnter) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF8FAFC))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, tint = Placeholder, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("김담당", color = TextSecondary, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        AnimatedVisibility(visible = showCompany, enter = itemEnter) {
            ResultRow(
                iconBg = CyanLight,
                iconShape = ShapeKind.Building,
                iconTint = BrandCyan,
                title = "ABC 주식회사",
                subtitle = "등록된 고객사",
                trailing = {
                    Icon(Icons.Default.Check, null, tint = BrandCyan, modifier = Modifier.size(20.dp))
                },
            )
        }

        Spacer(Modifier.height(8.dp))

        AnimatedVisibility(visible = showContact, enter = itemEnter) {
            ResultRow(
                iconBg = Color(0xFFF1F5F9),
                iconShape = ShapeKind.Person,
                iconTint = Placeholder,
                title = "김담당",
                subtitle = "신규 담당자",
                trailing = {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .border(2.dp, BrandCyan, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Add, null, tint = BrandCyan, modifier = Modifier.size(14.dp))
                    }
                },
            )
        }
    }
}

/**
 * Step 2 — 페이지 생성 카드.
 * 회사 헤더 → 회사 개요 → 담당자 목록 → 신규 항목 펼침.
 */
@Composable
fun PageCreationCard(visible: Boolean) {
    var showHeader by remember { mutableStateOf(false) }
    var show1 by remember { mutableStateOf(false) }
    var show2 by remember { mutableStateOf(false) }
    var show3 by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            delay(200); showHeader = true
            delay(450); show1 = true
            delay(400); show2 = true
            delay(400); show3 = true
        } else {
            showHeader = false; show1 = false; show2 = false; show3 = false
        }
    }

    val itemEnter = fadeIn(tween(500)) + expandVertically(tween(450))

    StepCard {
        AnimatedVisibility(visible = showHeader, enter = itemEnter) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CyanLight),
                    contentAlignment = Alignment.Center,
                ) {
                    BuildingGlyph(tint = BrandCyan, size = 12.dp)
                }
                Spacer(Modifier.width(8.dp))
                Text("ABC 주식회사", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(modifier = Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedVisibility(visible = show1, enter = itemEnter) {
                DocLabel(label = "회사 개요", color = TextMuted)
            }
            AnimatedVisibility(visible = show2, enter = itemEnter) {
                DocLabel(label = "담당자 목록", color = TextMuted)
            }
            AnimatedVisibility(visible = show3, enter = itemEnter) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null, tint = Placeholder, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("김담당", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    SmallSpinner()
                }
            }
        }
    }
}

/**
 * Step 3 — 담당자 정보 업데이트 카드.
 * 아바타 + 이름 + 2×2 정보 그리드.
 */
@Composable
fun ContactUpdateCard(visible: Boolean) {
    var showHeader by remember { mutableStateOf(false) }
    val showCells = remember { mutableStateOf(listOf(false, false, false, false)) }

    LaunchedEffect(visible) {
        if (visible) {
            delay(200); showHeader = true
            delay(450); showCells.value = listOf(true, false, false, false)
            delay(300); showCells.value = listOf(true, true, false, false)
            delay(300); showCells.value = listOf(true, true, true, false)
            delay(300); showCells.value = listOf(true, true, true, true)
        } else {
            showHeader = false
            showCells.value = listOf(false, false, false, false)
        }
    }

    val headerEnter = fadeIn(tween(500)) + expandVertically(tween(450))

    StepCard {
        AnimatedVisibility(visible = showHeader, enter = headerEnter) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Person, null, tint = Placeholder, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("김담당", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("ABC 주식회사", color = TextMuted, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GridCell(showCells.value[0], "직책", "마케팅 팀장", Modifier.weight(1f))
                GridCell(showCells.value[1], "최근 미팅", "2024.01.15", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GridCell(showCells.value[2], "관심사", "AI 솔루션", Modifier.weight(1f), accent = true)
                GridCell(showCells.value[3], "미팅 메모", "Q1 도입 검토 중", Modifier.weight(1f))
            }
        }
    }
}

// ---------------- internal helpers ----------------

@Composable
private fun StepCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        color = SurfaceCard,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun ResultRow(
    iconBg: Color,
    iconShape: ShapeKind,
    iconTint: Color,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF8FAFC))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            when (iconShape) {
                ShapeKind.Building -> BuildingGlyph(tint = iconTint, size = 14.dp)
                ShapeKind.Person -> Icon(Icons.Default.Person, null, tint = iconTint, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextMuted, fontSize = 11.sp)
        }
        trailing()
    }
}

private enum class ShapeKind { Building, Person }

/** 빌딩 모양 글리프 — material-icons-extended 의존 없이 도형으로 표현. */
@Composable
private fun BuildingGlyph(tint: Color, size: androidx.compose.ui.unit.Dp) {
    // 외곽 사각형 + 안쪽 가로줄 두 개로 빌딩 느낌
    Box(
        Modifier
            .size(size)
            .background(tint, RoundedCornerShape(2.dp)),
    )
}

/** 문서 라벨 — 작은 사각형 + 텍스트. */
@Composable
private fun DocLabel(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp, 14.dp)
                .background(color.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, fontSize = 13.sp)
    }
}

@Composable
private fun GridCell(
    visible: Boolean,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(450)) + expandVertically(tween(450)),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (accent) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(BrandCyan),
                    )
                } else {
                    Box(Modifier.size(10.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    label,
                    color = if (accent) BrandCyan else Placeholder,
                    fontSize = 11.sp,
                )
            }
            Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SmallSpinner() {
    androidx.compose.material3.CircularProgressIndicator(
        color = BrandCyan,
        strokeWidth = 1.5.dp,
        modifier = Modifier.size(12.dp),
    )
}

