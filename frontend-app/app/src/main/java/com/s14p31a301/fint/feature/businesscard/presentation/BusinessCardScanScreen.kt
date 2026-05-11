package com.s14p31a301.fint.feature.businesscard.presentation

import android.Manifest
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.s14p31a301.fint.core.media.camera.CameraManager
import com.s14p31a301.fint.core.media.file.FileManager
import com.s14p31a301.fint.core.permission.PermissionGate
import com.s14p31a301.fint.feature.common.ui.FintTopHeader
import com.s14p31a301.fint.ui.theme.BrandCyan
import com.s14p31a301.fint.ui.theme.TextPrimary
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
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val cameraManager: CameraManager = koinInject()
    val fileManager: FileManager = koinInject()

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var bindError by remember { mutableStateOf<String?>(null) }

    // 가이드 프레임의 화면(=Box) 내 좌표/크기. dim overlay 가 이 영역만 비워서 시선을 집중시킨다.
    var frameRect by remember { mutableStateOf<Rect?>(null) }
    // PreviewView 의 실제 크기 (px). 촬영 후 frameRect 와 함께 crop 좌표 계산에 사용.
    var previewSize by remember { mutableStateOf<androidx.compose.ui.unit.IntSize?>(null) }

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

    val cornerRadiusPx = with(density) { 12.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 1. Camera preview (제일 아래)
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    previewSize = androidx.compose.ui.unit.IntSize(
                        coords.size.width,
                        coords.size.height,
                    )
                },
        )

        // 2. Dim overlay — 가이드 프레임 영역만 hole 로 뚫어 명함만 밝게.
        //    Canvas 는 입력을 받지 않으므로 위 UI 의 클릭에 영향 없음.
        Canvas(Modifier.fillMaxSize()) {
            val rect = frameRect
            if (rect != null) {
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = rect,
                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        )
                    )
                }
                // 전체를 그리되 가이드 영역(path) 은 차이(Difference) 로 비움 → 명함 영역은 원본 카메라 그대로
                clipPath(path, clipOp = ClipOp.Difference) {
                    drawRect(color = Color.Black.copy(alpha = 0.6f), size = size)
                }
            } else {
                // 첫 프레임(레이아웃 측정 전) 에는 전체 dim — 깜빡임 방지
                drawRect(color = Color.Black.copy(alpha = 0.6f))
            }
        }

        // 3. UI 레이어 (헤더/가이드/문구/셔터) — dim 위에 올라감
        Column(Modifier.fillMaxSize()) {
            FintTopHeader(
                title = "명함 등록",
                onBack = onCancel,
                onClose = onCancel,
                background = Color.Transparent,
            )

            Spacer(Modifier.weight(0.18f))

            // Cyan glow guide frame — onGloballyPositioned 로 frameRect 갱신
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
                        .onGloballyPositioned { coords ->
                            // boundsInParent — Column 내 Box 기준이라 Column/외부 Box 좌표계가 동일하므로 그대로 사용 가능.
                            // 더 안전하게 가려면 boundsInWindow 를 쓰지만 외부 Box 도 fillMaxSize 라 동일.
                            val pos = coords.positionInRoot()
                            frameRect = Rect(
                                offset = Offset(pos.x, pos.y),
                                size = Size(coords.size.width.toFloat(), coords.size.height.toFloat()),
                            )
                        }
                        .border(3.dp, BrandCyan, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Transparent),
                )
            }

            Spacer(Modifier.height(32.dp))

            // 안내 문구 — dim 배경 위라 자체 그림자 효과는 없지만 흰색 + 약한 dim padding 으로 가독성 확보.
            // 텍스트가 묻히지 않도록 Box wrapper + 약간의 어두운 반투명 배경(pill) 추가.
            HintText(
                primary = "프레임 안에 명함을 두고",
                secondary = "깔끔한 배경에서 촬영해 주세요.",
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
                                .onSuccess { saved ->
                                    // 가이드 프레임 영역만 잘라 같은 파일에 덮어쓴다.
                                    val frame = frameRect
                                    val pSize = previewSize
                                    if (frame != null && pSize != null) {
                                        runCatching {
                                            cameraManager.cropToFrame(
                                                file = saved,
                                                previewW = pSize.width,
                                                previewH = pSize.height,
                                                frameLeft = frame.left,
                                                frameTop = frame.top,
                                                frameW = frame.width,
                                                frameH = frame.height,
                                            )
                                        }
                                    }
                                    onCaptured(saved.absolutePath)
                                }
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
private fun HintText(primary: String, secondary: String) {
    // 텍스트는 dim 위에서도 묻히지 않도록 약간 어두운 pill 배경에 얹는다.
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = primary,
                    color = Color.White,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = secondary,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    // 카메라 셔터 — 흰 외곽 + 내부 짙은 원 (시안의 카메라 아이콘 대체)
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
                    .border(2.dp, TextPrimary, CircleShape),
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

// onGloballyPositioned 콜백에서 쓰는 positionInRoot 헬퍼 — import 줄임
private fun androidx.compose.ui.layout.LayoutCoordinates.positionInRoot(): Offset =
    this.localToRoot(Offset.Zero)
