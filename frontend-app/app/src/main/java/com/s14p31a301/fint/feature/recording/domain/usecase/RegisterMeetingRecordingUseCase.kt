package com.s14p31a301.fint.feature.recording.domain.usecase

import com.s14p31a301.fint.feature.recording.domain.model.RecordingFile

/**
 * 녹음 파일 → S3 업로드 → activities/{id}/files 첨부 → /ai/stt 요청 흐름.
 */
class RegisterMeetingRecordingUseCase {
    suspend operator fun invoke(recording: RecordingFile): Result<String /* sttJobId */> {
        // TODO
        return Result.failure(NotImplementedError())
    }
}

