package com.s14p31a301.fint.feature.common.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.s14p31a301.fint.ui.theme.BrandCyan
import com.s14p31a301.fint.ui.theme.Border
import com.s14p31a301.fint.ui.theme.Placeholder
import com.s14p31a301.fint.ui.theme.TextPrimary

/**
 * 시안의 인라인 편집 행 (라벨 - 값 - "수정" 버튼).
 * tap 또는 "수정" 클릭 시 편집 모드로 전환.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditableFormRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isLast: Boolean = false,
) {
    var editing by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier.fillMaxWidth().bringIntoViewRequester(bringIntoViewRequester)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = Placeholder,
                fontSize = 14.sp,
                modifier = Modifier.width(56.dp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .then(
                        if (!editing) Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { editing = true }
                        else Modifier,
                    ),
            ) {
                if (editing) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        cursorBrush = SolidColor(BrandCyan),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { st ->
                                if (st.isFocused) hadFocus = true
                                if (!st.isFocused && hadFocus) {
                                    editing = false
                                    hadFocus = false
                                }
                            },
                    )
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(100)
                        focusRequester.requestFocus()
                        keyboard?.show()
                        kotlinx.coroutines.delay(350)
                        bringIntoViewRequester.bringIntoView()
                    }
                } else {
                    val display = value.ifBlank { placeholder }
                    Text(
                        text = display,
                        color = if (value.isBlank()) Placeholder else TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Text(
                text = "수정",
                color = BrandCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { editing = true }
                    .padding(horizontal = 4.dp),
            )
        }
        if (!isLast) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Border),
            )
        }
    }
}

