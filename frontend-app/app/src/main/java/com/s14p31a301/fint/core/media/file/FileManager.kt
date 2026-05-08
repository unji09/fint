package com.s14p31a301.fint.core.media.file

import java.io.File

/**
 * 캐시/임시 파일 관리.
 * - 명함 이미지, 녹음 파일의 저장 위치 정책
 */
class FileManager {
    fun newImageFile(): File = TODO()
    fun newAudioFile(): File = TODO()
    fun cleanup(file: File) { runCatching { file.delete() } }
}

