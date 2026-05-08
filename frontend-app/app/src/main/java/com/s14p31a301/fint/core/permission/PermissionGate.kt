package com.s14p31a301.fint.core.permission

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.s14p31a301.fint.ui.theme.PageBg
import com.s14p31a301.fint.ui.theme.TextMuted
import com.s14p31a301.fint.ui.theme.TextPrimary

/**
 * 단일 권한 요청 래퍼.
 *
 * - granted 이면 [content] 표시
 * - denied 이면 안내 + "권한 허용" 버튼 (영구 거부 시 "설정 열기")
 * - 첫 진입 시 자동으로 1회 요청
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionGate(
    permission: String,
    title: String,
    rationale: String,
    onCancel: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberPermissionState(permission)
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (state.status is PermissionStatus.Denied && !state.status.shouldShowRationale) {
            state.launchPermissionRequest()
        }
    }

    when (val s = state.status) {
        is PermissionStatus.Granted -> content()
        is PermissionStatus.Denied -> {
            val permanentlyDenied = !s.shouldShowRationale
            Box(
                Modifier
                    .fillMaxSize()
                    .background(PageBg),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = rationale,
                        fontSize = 14.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            if (permanentlyDenied) {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                )
                                context.startActivity(intent)
                            } else {
                                state.launchPermissionRequest()
                            }
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(if (permanentlyDenied) "설정 열기" else "권한 허용")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(50),
                    ) { Text("닫기") }
                }
            }
        }
    }
}

