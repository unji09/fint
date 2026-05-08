package com.s14p31a301.fint.feature.devicecontact.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.s14p31a301.fint.feature.businesscard.presentation.ContactForm
import com.s14p31a301.fint.feature.devicecontact.domain.model.DeviceContact
import com.s14p31a301.fint.feature.devicecontact.domain.repository.DeviceContactRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeviceContactListUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val contacts: List<DeviceContact> = emptyList(),
    val error: String? = null,
)

data class DeviceContactSelectUiState(
    val phase: Phase = Phase.Confirm,
    val source: DeviceContact? = null,
    val form: ContactForm = ContactForm(),
    val error: String? = null,
) {
    enum class Phase { Confirm, Saving, Done }
}

/**
 * 기기 연락처 리스트/검색 + 선택된 연락처 → 담당자 등록 ViewModel.
 *
 * 단일 VM에서 list/select 두 화면 상태를 모두 다룸. select 진입 시 [openSelected] 호출.
 */
class DeviceContactViewModel(
    private val repository: DeviceContactRepository,
) : ViewModel() {

    private val _list = MutableStateFlow(DeviceContactListUiState())
    val list: StateFlow<DeviceContactListUiState> = _list.asStateFlow()

    private val _select = MutableStateFlow(DeviceContactSelectUiState())
    val select: StateFlow<DeviceContactSelectUiState> = _select.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _list.update { it.copy(isLoading = true, error = null) }
            repository.loadAll()
                .onSuccess { contacts ->
                    _list.update { it.copy(isLoading = false, contacts = contacts) }
                }
                .onFailure { e ->
                    _list.update {
                        it.copy(isLoading = false, error = e.message ?: "연락처를 불러오지 못했어요.")
                    }
                }
        }
    }

    fun search(query: String) {
        _list.update { it.copy(query = query) }
        viewModelScope.launch {
            repository.search(query)
                .onSuccess { contacts -> _list.update { it.copy(contacts = contacts) } }
        }
    }

    /** 리스트에서 한 항목 선택 → select 화면 폼 채우기. */
    fun openSelected(contact: DeviceContact) {
        _select.value = DeviceContactSelectUiState(
            phase = DeviceContactSelectUiState.Phase.Confirm,
            source = contact,
            form = ContactForm(
                name = contact.name,
                phone = contact.phone.orEmpty(),
                email = contact.email.orEmpty(),
            ),
        )
    }

    fun findById(id: String): DeviceContact? = _list.value.contacts.firstOrNull { it.id == id }

    fun updateName(v: String) = _select.update { it.copy(form = it.form.copy(name = v)) }
    fun updateCompany(v: String) = _select.update { it.copy(form = it.form.copy(company = v)) }
    fun updatePosition(v: String) = _select.update { it.copy(form = it.form.copy(position = v)) }
    fun updatePhone(v: String) = _select.update { it.copy(form = it.form.copy(phone = v)) }
    fun updateEmail(v: String) = _select.update { it.copy(form = it.form.copy(email = v)) }

    fun register() {
        val current = _select.value
        if (current.phase != DeviceContactSelectUiState.Phase.Confirm) return
        _select.update { it.copy(phase = DeviceContactSelectUiState.Phase.Saving) }
        viewModelScope.launch {
            val payload = DeviceContact(
                id = current.source?.id ?: "",
                name = current.form.name,
                phone = current.form.phone.ifBlank { null },
                email = current.form.email.ifBlank { null },
            )
            repository.registerContact(payload)
                .onFailure { e ->
                    if (e !is NotImplementedError) {
                        _select.update { it.copy(error = e.message) }
                    }
                }
        }
    }

    fun onSavingProgressFinished() {
        if (_select.value.phase == DeviceContactSelectUiState.Phase.Saving) {
            _select.update { it.copy(phase = DeviceContactSelectUiState.Phase.Done) }
        }
    }
}
