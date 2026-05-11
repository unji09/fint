package com.s14p31a301.fint.core.media.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 명함 촬영용 CameraX 컨트롤러.
 * Compose 화면에서 [bindPreview]로 PreviewView/ImageCapture 를 LifecycleOwner에 묶고,
 * [takePicture]로 캡처해 임시 파일에 저장한다.
 */
class CameraManager(private val context: Context) {

    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    /**
     * Preview + ImageCapture 를 LifecycleOwner 에 바인드.
     * @return 촬영에 사용할 [ImageCapture]
     */
    suspend fun bindPreview(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    ): ImageCapture = suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                cont.resume(imageCapture)
            } catch (t: Throwable) {
                cont.resumeWithException(t)
            }
        }, mainExecutor)
    }

    /** 사진 촬영 → 지정 [file] 에 저장 후 같은 [File] 반환. */
    suspend fun takePicture(imageCapture: ImageCapture, file: File): File =
        suspendCancellableCoroutine { cont ->
            val output = ImageCapture.OutputFileOptions.Builder(file).build()
            imageCapture.takePicture(
                output,
                mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                        cont.resume(file)
                    }
                    override fun onError(exception: ImageCaptureException) {
                        cont.resumeWithException(exception)
                    }
                }
            )
        }

    /**
     * 촬영된 이미지를 PreviewView 의 가이드 프레임 영역으로 잘라서 같은 파일에 저장한다.
     *
     * - PreviewView 가 [PreviewView.ScaleType.FILL_CENTER] 라고 가정 (Scan 화면 기본값).
     * - 회전된 JPEG (EXIF orientation) 도 정상 처리: 비트맵을 회전 적용 후 자른다.
     *
     * @param file        촬영된 JPEG (in/out)
     * @param previewW    PreviewView 폭 (px)
     * @param previewH    PreviewView 높이 (px)
     * @param frameLeft   프레임 좌상단 x (PreviewView 좌표, px)
     * @param frameTop    프레임 좌상단 y (PreviewView 좌표, px)
     * @param frameW      프레임 폭 (px)
     * @param frameH      프레임 높이 (px)
     */
    fun cropToFrame(
        file: File,
        previewW: Int,
        previewH: Int,
        frameLeft: Float,
        frameTop: Float,
        frameW: Float,
        frameH: Float,
    ) {
        if (previewW <= 0 || previewH <= 0 || frameW <= 0f || frameH <= 0f) return

        // 1) 비트맵 로드 + EXIF 회전 적용
        val raw = BitmapFactory.decodeFile(file.absolutePath) ?: return
        val rotated = raw.applyExifRotation(file.absolutePath)
        if (rotated !== raw) raw.recycle()

        val imgW = rotated.width
        val imgH = rotated.height
        if (imgW <= 0 || imgH <= 0) { rotated.recycle(); return }

        // 2) FILL_CENTER 매핑: scale = max(previewW/imgW, previewH/imgH)
        val scale = max(previewW.toFloat() / imgW, previewH.toFloat() / imgH)
        val displayedW = imgW * scale
        val displayedH = imgH * scale
        // PreviewView 좌표 → 이미지 픽셀 좌표
        val offsetX = (displayedW - previewW) / 2f
        val offsetY = (displayedH - previewH) / 2f

        var cropX = ((frameLeft + offsetX) / scale).roundToInt()
        var cropY = ((frameTop + offsetY) / scale).roundToInt()
        var cropW = (frameW / scale).roundToInt()
        var cropH = (frameH / scale).roundToInt()

        // 3) 안전 clamp
        if (cropX < 0) { cropW += cropX; cropX = 0 }
        if (cropY < 0) { cropH += cropY; cropY = 0 }
        if (cropX + cropW > imgW) cropW = imgW - cropX
        if (cropY + cropH > imgH) cropH = imgH - cropY
        if (cropW <= 0 || cropH <= 0) { rotated.recycle(); return }

        // 4) 잘라서 같은 파일에 덮어쓰기 (JPEG 90%)
        val cropped = Bitmap.createBitmap(rotated, cropX, cropY, cropW, cropH)
        rotated.recycle()
        FileOutputStream(file).use { out ->
            cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        cropped.recycle()
    }

    private fun Bitmap.applyExifRotation(path: String): Bitmap {
        val exif = runCatching { ExifInterface(path) }.getOrNull() ?: return this
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return this
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}
