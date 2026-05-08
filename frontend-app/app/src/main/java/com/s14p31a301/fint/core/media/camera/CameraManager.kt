package com.s14p31a301.fint.core.media.camera

import android.content.Context
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
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
}
