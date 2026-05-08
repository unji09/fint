package com.s14p31a301.fint.feature.businesscard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.s14p31a301.fint.feature.businesscard.domain.model.BusinessCardOcrResult
import com.s14p31a301.fint.feature.businesscard.domain.repository.BusinessCardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * 명함 OCR + 담당자 등록 ViewModel.
 *
 * - [imagePath] 가 주어지면 init 단계에서 [uploadAndOcr] 호출
 * - Repository 가 [NotImplementedError] (stub) 를 반환해도 빈 폼으로 fallback 하여 화면 흐름은 그대로 검증 가능
 */
class BusinessCardViewModel(
    private val imagePath: String?,
    private val repository: BusinessCardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        BusinessCardUiState(
            phase = BusinessCardUiState.Phase.Confirm,
            imagePath = imagePath,
            isOcrInProgress = imagePath != null,
        )
    )
    val state: StateFlow<BusinessCardUiState> = _state.asStateFlow()

    init {
        if (imagePath != null) startOcr(imagePath)
    }

    private fun startOcr(path: String) {
        viewModelScope.launch {
            val result = repository.uploadAndOcr(File(path))
            result
                .onSuccess { ocr ->
                    _state.update {
                        it.copy(
                            isOcrInProgress = false,
                            ocrResult = ocr,
                            form = ocr.toForm(),
                        )
                    }
                }
                .onFailure { e ->
                    // stub (NotImplementedError) or 실제 실패 → 빈 폼으로 수동 입력 fallback
                    _state.update {
                        it.copy(
                            isOcrInProgress = false,
                            error = if (e is NotImplementedError) null else e.message,
                        )
                    }
                }
        }
    }

    fun updateName(v: String) = _state.update { it.copy(form = it.form.copy(name = v)) }
    fun updateCompany(v: String) = _state.update { it.copy(form = it.form.copy(company = v)) }
    fun updatePosition(v: String) = _state.update { it.copy(form = it.form.copy(position = v)) }
    fun updatePhone(v: String) = _state.update { it.copy(form = it.form.copy(phone = v)) }
    fun updateEmail(v: String) = _state.update { it.copy(form = it.form.copy(email = v)) }

    /** "담당자로 저장" 클릭. */
    fun register() {
        val current = _state.value
        if (current.phase != BusinessCardUiState.Phase.Confirm) return
        _state.update { it.copy(phase = BusinessCardUiState.Phase.Saving) }

        viewModelScope.launch {
            val payload = BusinessCardOcrResult(
                name = current.form.name.ifBlank { null },
                company = current.form.company.ifBlank { null },
                position = current.form.position.ifBlank { null },
                phone = current.form.phone.ifBlank { null },
                email = current.form.email.ifBlank { null },
                address = null,
                rawText = current.ocrResult?.rawText,
                imageFileKey = current.ocrResult?.imageFileKey,
            )
            repository.registerContact(payload)
                .onSuccess { id ->
                    _state.update { it.copy(registeredContactId = id) }
                }
                .onFailure { e ->
                    if (e !is NotImplementedError) {
                        _state.update { it.copy(error = e.message) }
                    }
                    // stub or 실패에도 데모용으로 진행 화면 표시
                }
            // RegisterProgressContent 가 onComplete 시 [completeRegistration] 호출하므로 여기서는 phase 유지
        }
    }

    /** RegisterProgressContent 의 onComplete 콜백 → "완료" 화면으로 전환. */
    fun onSavingProgressFinished() {
        if (_state.value.phase == BusinessCardUiState.Phase.Saving) {
            _state.update { it.copy(phase = BusinessCardUiState.Phase.Done) }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}

private fun BusinessCardOcrResult.toForm() = ContactForm(
    name = name.orEmpty(),
    company = company.orEmpty(),
    position = position.orEmpty(),
    phone = phone.orEmpty(),
    email = email.orEmpty(),
)
