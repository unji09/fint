package com.s14p31a301.fint.feature.devicecontact.presentation

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.s14p31a301.fint.core.permission.PermissionGate
import com.s14p31a301.fint.feature.common.ui.FintTopHeader
import com.s14p31a301.fint.feature.devicecontact.domain.model.DeviceContact
import com.s14p31a301.fint.ui.theme.BrandCyan
import com.s14p31a301.fint.ui.theme.CyanLight
import com.s14p31a301.fint.ui.theme.Border
import com.s14p31a301.fint.ui.theme.PageBg
import com.s14p31a301.fint.ui.theme.Placeholder
import com.s14p31a301.fint.ui.theme.SurfaceCard
import com.s14p31a301.fint.ui.theme.TextMuted
import com.s14p31a301.fint.ui.theme.TextPrimary

/**
 * 기기 연락처 목록 + 검색. 선택 시 [onSelect] 로 contactId 전달.
 *
 * @param viewModel List/Select 단계를 공유하는 NavGraph-scoped VM
 */
@Composable
fun DeviceContactListScreen(
    viewModel: DeviceContactViewModel,
    onSelect: (contactId: String) -> Unit,
    onCancel: () -> Unit,
) {
    PermissionGate(
        permission = Manifest.permission.READ_CONTACTS,
        title = "연락처 권한이 필요해요",
        rationale = "기기에 저장된 연락처에서 담당자를 빠르게 등록하려면 권한이 필요합니다.",
        onCancel = onCancel,
    ) {
        ListContent(viewModel = viewModel, onSelect = onSelect, onCancel = onCancel)
    }
}

@Composable
private fun ListContent(
    viewModel: DeviceContactViewModel,
    onSelect: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val state by viewModel.list.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBg),
    ) {
        FintTopHeader(title = "담당자 추가", onBack = onCancel, onClose = onCancel)

        Box(Modifier.background(PageBg).padding(horizontal = 16.dp, vertical = 12.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::search,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("이름 또는 전화번호로 검색", color = Placeholder, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Placeholder)
                },
                shape = RoundedCornerShape(50),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard,
                    focusedBorderColor = BrandCyan,
                    unfocusedBorderColor = Border,
                ),
            )
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandCyan)
            }
            state.error != null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(state.error!!, color = TextMuted, fontSize = 14.sp)
            }
            state.contacts.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("일치하는 연락처가 없어요.", color = TextMuted, fontSize = 14.sp)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.contacts, key = { it.id }) { contact ->
                    ContactRow(contact = contact, onClick = {
                        viewModel.openSelected(contact)
                        onSelect(contact.id)
                    })
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: DeviceContact, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(CyanLight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = contact.name.firstOrNull()?.toString() ?: "?",
                color = BrandCyan,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                text = contact.phone ?: contact.email ?: "-",
                color = TextMuted,
                fontSize = 13.sp,
            )
        }
    }
}
