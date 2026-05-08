package com.s14p31a301.fint.feature.businesscard.presentation

import android.Manifest
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.s14p31a301.fint.core.media.camera.CameraManager
import com.s14p31a301.fint.core.media.file.FileManager
import com.s14p31a301.fint.core.permission.PermissionGate
import com.s14p31a301.fint.feature.common.ui.FintTopHeader
import com.s14p31a301.fint.ui.theme.BrandCyan
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 카메라 미리보기 + 촬영 화면. CAMERA 권한 처리 포함.
 *
 * @param onCaptured 캡처된 이미지의 절대경로 전달 (NavHost 가 result 화면으로 navigate)
 */
@Composable
fun BusinessCardScanScreen(
    onCaptured: (imagePath: String) -> Unit,
    onCancel: () -> Unit,
) {
    PermissionGate(
        permission = Manifest.permission.CAMERA,
        title = "카메라 권한이 필요해요",
        rationale = "명함을 촬영해 자동으로 정보를 인식하려면 카메라 권한이 필요합니다.",
        onCancel = onCancel,
    ) {
        ScanContent(onCaptured = onCaptured, onCancel = onCancel)
    }
}

@Composable
private fun ScanContent(
    onCaptured: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val cameraManager: CameraManager = koinInject()
    val fileManager: FileManager = koinInject()

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var bindError by remember { mutableStateOf<String?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(Unit) {
        runCatching { cameraManager.bindPreview(lifecycleOwner, previewView) }
            .onSuccess { imageCapture = it }
            .onFailure { bindError = it.message ?: "카메라 초기화에 실패했어요." }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Camera preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.fillMaxSize()) {
            FintTopHeader(
                title = "명함 등록",
                onBack = onCancel,
                onClose = onCancel,
            )

            Spacer(Modifier.weight(0.18f))

            // Cyan glow guide frame
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(280.dp)
                        .height(175.dp)
                        .border(3.dp, BrandCyan, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Transparent),
                )
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "프레임 안에 명함을 두고",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "깔끔한 배경에서 촬영해 주세요.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Spacer(Modifier.weight(0.4f))

            // Shutter button
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 56.dp),
                contentAlignment = Alignment.Center,
            ) {
                ShutterButton(
                    enabled = imageCapture != null && !isCapturing,
                    onClick = {
                        val capture = imageCapture ?: return@ShutterButton
                        isCapturing = true
                        scope.launch {
                            val file = fileManager.newImageFile()
                            runCatching { cameraManager.takePicture(capture, file) }
                                .onSuccess { onCaptured(it.absolutePath) }
                            isCapturing = false
                        }
                    },
                )
            }
        }

        if (bindError != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(bindError ?: "", color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    // 카메라 셔터 — 흰 외곽 + 내부 어두운 원 (시안의 카메라 아이콘 대체)
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color.White)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (enabled) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color(0xFF374151), CircleShape),
            )
        } else {
            CircularProgressIndicator(
                color = BrandCyan,
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
