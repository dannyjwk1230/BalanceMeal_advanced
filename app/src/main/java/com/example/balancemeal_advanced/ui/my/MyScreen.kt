package com.example.balancemeal_advanced.ui.my

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.saveable.rememberSaveable
import java.time.LocalDate
import com.example.balancemeal_advanced.R
import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.ui.components.*
import com.example.balancemeal_advanced.ui.theme.BalanceColors

@Composable
fun ProfileAvatar(state: ProfileUiState, modifier: Modifier = Modifier) {
    val frame = modifier.clip(CircleShape).background(BalanceColors.SurfaceLow)
    if (state.bitmap != null) Image(state.bitmap.asImageBitmap(), "현재 프로필 사진", frame, contentScale = ContentScale.Crop)
    else Icon(painterResource(R.drawable.bm_ic_profile), "기본 프로필", frame.padding(4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyScreen(
    state: MyUiState, profile: ProfileUiState, onNickname: (String)->Unit, onDraft: (BodyDraft)->Unit,
    onRequestNickname: ()->Unit, onRequestBody: ()->Unit, onConfirm: ()->Unit, onDismiss: ()->Unit,
    onDiscard: ()->Unit, onPhoto: ()->Unit, focusBody: Boolean, entry: String,
    onCalculationEnabled: (Boolean)->Unit = {},
    today: LocalDate = LocalDate.now(),
) {
    var showBirthPicker by rememberSaveable { mutableStateOf(false) }
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(focusBody, entry) { if (focusBody) { withFrameNanos { }; requester.bringIntoView() } }
    val editable = state.loaded && !state.busy && state.dialog == null
    val bodyDisabled = state.savedBody?.calculationEnabled == false
    val bodyAccent = if (bodyDisabled) BalanceColors.SecondaryText else BalanceColors.Mint
    val bodyAccentBorder = if (bodyDisabled) BalanceColors.SecondaryText else BalanceColors.MintDark
    val bodyColors = MaterialTheme.colorScheme.copy(primary = bodyAccent)
    val chipColors=FilterChipDefaults.filterChipColors(containerColor=androidx.compose.ui.graphics.Color.White.copy(alpha=.85f),labelColor=BalanceColors.SecondaryText,selectedContainerColor=bodyAccent,selectedLabelColor=androidx.compose.ui.graphics.Color.White)
    AdaptivePage("destination_MY",header={
        GlassCard(Modifier.fillMaxWidth(), radius = 16.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ProfileAvatar(profile, Modifier.size(64.dp).clickable(enabled = editable && !profile.loading && !profile.savingPhoto, onClickLabel = "프로필 사진 변경", onClick = onPhoto))
                Column(Modifier.weight(1f)) {
                    Text(profile.profile.nickname, style = MaterialTheme.typography.headlineSmall)
                    Text("기본 계정", style = MaterialTheme.typography.bodySmall)
                }
            }
            AppTextField(state.nicknameDraft, onNickname, label = "닉네임", enabled = editable, modifier = Modifier.fillMaxWidth().testTag("nickname_input"))
            Button(onRequestNickname, enabled = editable && !profile.loading && !profile.savingPhoto, modifier = Modifier.testTag("nickname_save")) { Text("변경 사항 저장") }
            profile.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

    },primary={
        MaterialTheme(colorScheme = bodyColors) {
        GlassCard(Modifier.fillMaxWidth().bringIntoViewRequester(requester).testTag("my_body_settings"), radius = 16.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("신체 정보 설정", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Switch(
                    checked = state.savedBody?.calculationEnabled == true,
                    onCheckedChange = onCalculationEnabled,
                    enabled = editable && state.savedBody != null,
                    modifier = Modifier.testTag("body_calculation_toggle")
                        .semantics { contentDescription = "신체 정보 계산 적용" },
                )
            }
            Text("정확한 권장 섭취량을 계산하기 위해 정보를 입력해주세요", style = MaterialTheme.typography.bodyMedium)
            val birthMissing = state.loaded && state.draft.birth.isBlank()
            val birthColor = if (birthMissing) MaterialTheme.colorScheme.error else BalanceColors.Ink
            OutlinedButton(
                onClick = { showBirthPicker = true }, enabled = editable,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("body_birth"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (birthMissing) MaterialTheme.colorScheme.error else BalanceColors.InputOutline),
            ) {
                Text(state.draft.birth.ifEmpty { "생년월일" }, style = MaterialTheme.typography.bodyLarge,
                    color = birthColor, modifier = Modifier.weight(1f))
                Icon(painterResource(R.drawable.bm_ic_calendar), null, Modifier.size(20.dp), tint = birthColor)
            }
            Text("성별")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Sex.entries.forEach { sex -> FilterChip(state.draft.sex == sex, { onDraft(state.draft.copy(sex = sex)) }, label = { Text(if (sex == Sex.MALE) "남성" else "여성") }, enabled = editable,colors=chipColors,
                    border=BorderStroke(1.dp,if(state.draft.sex==sex) bodyAccentBorder.copy(alpha=.55f) else BalanceColors.Outline.copy(alpha=.25f))) }
            }
            val heightInput:@Composable (Modifier)->Unit={fieldModifier->
                AppTextField(state.draft.height, { onDraft(state.draft.copy(height = it)) }, label = "키", suffix = "cm", enabled = editable, isError = state.loaded && state.draft.height.toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } != true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = fieldModifier.testTag("body_height"))
            }
            val weightInput:@Composable (Modifier)->Unit={fieldModifier->
                AppTextField(state.draft.weight, { onDraft(state.draft.copy(weight = it)) }, label = "체중", suffix = "kg", enabled = editable, isError = state.loaded && state.draft.weight.toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } != true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = fieldModifier.testTag("body_weight"))
            }
            if(LocalDensity.current.fontScale>1.3f)Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                heightInput(Modifier.fillMaxWidth());weightInput(Modifier.fillMaxWidth())
            } else Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                heightInput(Modifier.weight(1f));weightInput(Modifier.weight(1f))
            }
            Text("활동 수준")
            // Wrap rather than clipping four source segments on narrow windows/font scaling.
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                ActivityLevel.entries.forEach { level -> FilterChip(state.draft.activity == level, { onDraft(state.draft.copy(activity = level)) }, label = { Text(level.label()) }, enabled = editable,colors=chipColors,
                    border=BorderStroke(1.dp,if(state.draft.activity==level) bodyAccentBorder.copy(alpha=.55f) else BalanceColors.Outline.copy(alpha=.25f))) }
            }
            state.draft.activity?.let { Text(it.description(), style = MaterialTheme.typography.bodyMedium) }
            Button(onRequestBody, enabled = editable, modifier = Modifier.fillMaxWidth().testTag("body_save")) { Text("신체 정보 저장") }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("settings_error")) }
            if (!state.loaded) Text("설정을 불러오는 중입니다.")
        }
        }

    },secondary={
        GlassCard(Modifier.fillMaxWidth(), radius = 16.dp) {
            Text("기준 안내 및 정보 출처", style = MaterialTheme.typography.titleMedium)
            Text(MyMessages.BASIS, style = MaterialTheme.typography.bodySmall)
            Text(MyMessages.ESTIMATE, style = MaterialTheme.typography.bodySmall)
            Text(MyMessages.SOURCE, style = MaterialTheme.typography.bodySmall)
        }
    
    })
    if (showBirthPicker) WheelDatePicker(
        mode = DatePickerMode.BIRTH,
        initialDate = runCatching { LocalDate.parse(state.draft.birth) }.getOrDefault(today),
        today = today,
        onSelect = { onDraft(state.draft.copy(birth = it)) },
        onDismissRequest = { showBirthPicker = false },
    )
    state.dialog?.let { dialog ->
        if (dialog == MyDialog.Discard) UnsavedChangesDialog(onDiscard, onDismiss)
        else if (dialog is MyDialog.InvalidBody) Dialog(onDismissRequest = onDismiss) {
            GlassCard(
                modifier = Modifier.fillMaxWidth().wrapContentHeight().testTag("invalid_body_dialog"),
                opacity = .94f,
                padding = 16.dp,
            ) {
                Text("신체 정보를 확인해주세요", style = MaterialTheme.typography.titleMedium)
                Text(dialog.message, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("확인") }
                }
            }
        }
        else if (dialog == MyDialog.UnderAge) AlertDialog(onDismissRequest = onDismiss, title = { Text("생년월일 확인") }, text = { Text(MyMessages.UNDER_AGE) }, confirmButton = { TextButton(onDismiss) { Text("확인") } })
        else CommonConfirmationDialog(
            title = if (dialog is MyDialog.Nickname) "프로필 변경" else "신체 정보 저장",
            message = if (dialog is MyDialog.Nickname) "${dialog.value}(으)로 변경할까요?" else MyMessages.ESTIMATE,
            confirmLabel = "저장", onConfirm = onConfirm, onDismiss = onDismiss,
        )
    }
}

