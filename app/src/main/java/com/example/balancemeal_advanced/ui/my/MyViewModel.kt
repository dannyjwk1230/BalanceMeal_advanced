package com.example.balancemeal_advanced.ui.my

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.core.time.TimeSource
import com.example.balancemeal_advanced.core.nutrition.EnergyTargets
import com.example.balancemeal_advanced.data.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.util.Locale

class MyViewModel(private val repository: SettingsRepository, private val time: TimeSource) : ViewModel() {
    private val mutable = MutableStateFlow(MyUiState())
    val state: StateFlow<MyUiState> = mutable.asStateFlow()
    var saveProfile: (suspend (String) -> Unit)? = null
    var hasPhotoChanges: () -> Boolean = { false }
    var photoBusy: () -> Boolean = { false }
    var discardPhoto: () -> Unit = {}
    private var bodyLoaded = false
    private var profileLoaded = false
    private var pendingLeave: ((Boolean) -> Unit)? = null
    init {
        viewModelScope.launch { repository.profile.catch { fail(it) }.collect { profile ->
            mutable.update { it.copy(profile = profile, nicknameDraft = if (!profileLoaded) profile.nickname else it.nicknameDraft) }
            profileLoaded = true
            updateLoaded()
        } }
        viewModelScope.launch { repository.bodySettings.catch { fail(it) }.collect { body ->
            mutable.update { it.copy(savedBody = body, draft = if (!bodyLoaded || !hasBodyChanges()) body.toDraft() else it.draft) }
            bodyLoaded = true
            refreshTargets()
            updateLoaded()
        } }
    }
    private fun updateLoaded() { mutable.update { it.copy(loaded = bodyLoaded && profileLoaded) } }
    fun nickname(value: String) { if (!state.value.busy && state.value.dialog == null) mutable.update { it.copy(nicknameDraft = value, error = null) } }
    fun edit(value: BodyDraft) { if (!state.value.busy && state.value.dialog == null) mutable.update { it.copy(draft = value, error = null) } }
    fun cancelEdits() { if (!state.value.busy) mutable.update { it.copy(draft = it.savedBody.toDraft(), nicknameDraft = it.profile.nickname, error = null) } }
    fun requestNickname() {
        if (!state.value.loaded || state.value.busy || state.value.dialog != null) return
        val name = state.value.nicknameDraft.trim()
        mutable.update { if (name.isEmpty()) it.copy(error = "닉네임을 입력해주세요.") else it.copy(dialog = MyDialog.Nickname(name), error = null) }
    }
    fun requestBody() {
        if (!state.value.loaded || state.value.busy || state.value.dialog != null) return
        val draft = state.value.draft
        val missing = buildList {
            if (draft.birth.isBlank()) add("생년월일")
            if (draft.sex == null) add("성별")
            if (draft.height.isBlank()) add("키")
            if (draft.weight.isBlank()) add("체중")
            if (draft.activity == null) add("활동 수준")
        }
        if (missing.isNotEmpty()) {
            mutable.update { it.copy(dialog = MyDialog.InvalidBody("아래 항목을 입력한 뒤 다시 저장해주세요.\n" + missing.joinToString(" · ")), error = null) }
            return
        }
        try {
            val value = state.value.draft.validated(time.now().toLocalDate())
                .copy(calculationEnabled = state.value.savedBody?.calculationEnabled ?: true)
            mutable.update { it.copy(dialog = MyDialog.Body(value), error = null) }
        } catch (e: UnderAgeException) { mutable.update { it.copy(dialog = MyDialog.UnderAge, error = null) }
        } catch (e: IllegalArgumentException) { mutable.update { it.copy(dialog = MyDialog.InvalidBody("생년월일과 키·체중을 확인해주세요.\n키와 체중은 0보다 큰 숫자로 입력해야 합니다."), error = null) } }
    }
    fun dismiss() {
        if (state.value.busy) return
        val callback = if (state.value.dialog == MyDialog.Discard) pendingLeave else null
        pendingLeave = null
        mutable.update { it.copy(dialog = null) }
        callback?.invoke(false)
    }
    private fun hasBodyChanges() = state.value.draft.fingerprint() != state.value.savedBody.toDraft().fingerprint()
    fun guardLeave(decide: (Boolean) -> Unit) {
        if (state.value.busy || state.value.dialog != null || photoBusy()) { decide(false); return }
        if (!hasBodyChanges() && state.value.nicknameDraft == state.value.profile.nickname && !hasPhotoChanges()) { cancelEdits(); decide(true); return }
        pendingLeave = decide
        mutable.update { it.copy(dialog = MyDialog.Discard) }
    }
    fun confirmDiscard() {
        if (state.value.busy || state.value.dialog != MyDialog.Discard) return
        val callback = pendingLeave ?: return
        pendingLeave = null
        mutable.update { it.copy(draft = it.savedBody.toDraft(), nicknameDraft = it.profile.nickname, dialog = null, error = null) }
        discardPhoto()
        callback(true)
    }
    fun cancelPendingNavigation() { if (state.value.dialog == MyDialog.Discard) dismiss() }
    override fun onCleared() { pendingLeave?.invoke(false); pendingLeave = null }
    fun confirm() {
        val dialog = state.value.dialog ?: return
        if (state.value.busy) return
        if (dialog == MyDialog.Discard) { confirmDiscard(); return }
        if (dialog == MyDialog.UnderAge || dialog is MyDialog.InvalidBody) { dismiss(); return }
        mutable.update { it.copy(busy = true, dialog = null, error = null) }
        viewModelScope.launch {
            try {
                when (dialog) {
                    is MyDialog.Nickname -> {
                        saveProfile?.invoke(dialog.value) ?: repository.updateNickname(dialog.value)
                        mutable.update { it.copy(nicknameDraft = dialog.value) }
                    }
                    is MyDialog.Body -> {
                        EnergyTargets.calculate(dialog.value, time.now().toLocalDate())
                        repository.saveBodySettings(dialog.value)
                        mutable.update { it.copy(savedBody = dialog.value, draft = dialog.value.toDraft()) }
                    }
                    MyDialog.UnderAge, MyDialog.Discard, is MyDialog.InvalidBody -> Unit
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { fail(e)
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun setCalculationEnabled(enabled: Boolean) {
        val current = state.value
        val body = current.savedBody ?: return
        if (!current.loaded || current.busy || current.dialog != null || body.calculationEnabled == enabled) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                repository.saveBodySettings(body.copy(calculationEnabled = enabled))
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { fail(e)
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun refreshTargets() {
        try { mutable.update { it.copy(target = it.savedBody?.takeIf { body -> body.calculationEnabled }?.let { body -> EnergyTargets.calculate(body, time.now().toLocalDate()).display() }) } }
        catch (e: IllegalArgumentException) { mutable.update { it.copy(target = null, error = "저장된 신체 정보의 목표를 계산할 수 없습니다. 값을 확인해주세요.") } }
    }
    private fun fail(error: Throwable) {
        if (error is CancellationException) throw error
        mutable.update { it.copy(error = "설정을 읽거나 저장하지 못했습니다. 기존 저장값은 유지됩니다.") }
    }
}

private fun BodyDraft.fingerprint(): List<Any?> = listOf(
    runCatching { LocalDate.parse(birth.trim()) }.getOrNull() ?: birth.trim(), sex,
    height.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: height.trim(),
    weight.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: weight.trim(), activity,
)
private fun BodySettings?.toDraft() = this?.let { BodyDraft(it.birthDate.toString(), it.sex, it.heightCm.toString(), it.weightKg.toString(), it.activityLevel) } ?: BodyDraft()
class UnderAgeException : IllegalArgumentException()
fun BodyDraft.validated(today: LocalDate): BodySettings {
    val birthday = try { LocalDate.parse(birth.trim()) } catch (e: java.time.DateTimeException) { throw IllegalArgumentException("Invalid date", e) }
    require(!birthday.isAfter(today))
    val body = BodySettings(birthday, requireNotNull(sex), requireNotNull(height.toDoubleOrNull()), requireNotNull(weight.toDoubleOrNull()), requireNotNull(activity))
    if (EnergyTargets.age(birthday, today) < 19) throw UnderAgeException()
    EnergyTargets.calculate(body, today)
    return body
}
private fun EnergyTargets.display(): TargetDisplay {
    fun range(r: NutrientRange) = String.format(Locale.KOREA, "%.1f ~ %.1f g", r.lowerG, r.upperG)
    return TargetDisplay(String.format(Locale.KOREA, "%.0f", energyKcal), range(carbohydrate), range(protein), range(fat))
}
