package com.s14p31a301.fint.feature.debug

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.s14p31a301.fint.ui.theme.BrandCyan

/**
 * 디버그 빌드 전용 진입점 패널.
 *
 * 웹 프론트가 아직 배포되지 않은 상황에서 WebView 호출 없이
 * Native 화면(명함 OCR / 담당자 추가 / 미팅 녹음)으로 바로 이동하기 위한 도구.
 * `BuildConfig.DEBUG` 가 true 일 때만 표시.
 */
@Composable
fun DebugEntryOverlay(
    onOpenBusinessCardScanner: () -> Unit,
    onOpenDeviceContactPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // 펼침 패널 — FAB 위에 표시
            AnimatedVisibility(visible = expanded) {
                Surface(
                    color = Color(0xCC000000),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "DEBUG ENTRY",
                            color = BrandCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        DebugButton("📇 명함 OCR") {
                            expanded = false; onOpenBusinessCardScanner()
                        }
                        DebugButton("👤 담당자 추가") {
                            expanded = false; onOpenDeviceContactPicker()
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // FAB
            Surface(
                color = BrandCyan,
                shape = CircleShape,
                shadowElevation = 6.dp,
                modifier = Modifier.size(44.dp),
                onClick = { expanded = !expanded },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (expanded) "×" else "+",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
        ),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

