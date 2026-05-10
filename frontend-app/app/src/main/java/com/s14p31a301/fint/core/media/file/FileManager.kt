package com.s14p31a301.fint.core.media.file

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 캐시/임시 파일 관리.
 * - 명함 이미지(`cacheDir/images`), 녹음 파일(`cacheDir/audio`)
 */
class FileManager(private val context: Context) {

    private fun ensureDir(name: String): File =
        File(context.cacheDir, name).apply { if (!exists()) mkdirs() }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    fun newImageFile(): File =
        File(ensureDir("images"), "IMG_${timestamp()}.jpg")

    fun newAudioFile(): File =
        File(ensureDir("audio"), "REC_${timestamp()}.m4a")

    fun cleanup(file: File) { runCatching { file.delete() } }
}
