package com.example.balancemeal_advanced.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.core.nutrition.MealNutrition
import com.example.balancemeal_advanced.core.time.TimeSource
import com.example.balancemeal_advanced.data.repository.*
import com.example.balancemeal_advanced.data.image.*
import com.example.balancemeal_advanced.core.validation.SaveValidation
import com.example.balancemeal_advanced.ui.presentation.NutritionUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class RecordViewModel(
    private val meals: MealRepository, private val foods: FoodRepository, private val time: TimeSource,
    private val selectionPermission: RecordSelectionPermission = RecordSelectionPermission { _, decide -> decide(true) },
    private val images:ImageStorage?=null, private val photos:PhotoLifecycle?=null, private val camera:CameraCaptureStore?=null,
) : ViewModel() {
    private val mutable = MutableStateFlow(RecordUiState(RecordKey(time.now().toLocalDate(), MealType.BREAKFAST)))
    val state: StateFlow<RecordUiState> = mutable.asStateFlow()
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var loadVersion = 0L
    private var searchVersion = 0L
    private val heldPhotos=mutableSetOf<String>()
    private var cleared=false
    private var pendingLeave:((Boolean)->Unit)?=null
    private var cameraTicket:CameraTicket?=null
    private var previewJob:Job?=null
    private val photoChannel=Channel<RecordPhotoRequest>(Channel.BUFFERED)
    val photoRequests=photoChannel.receiveAsFlow()
    init { load(state.value.key) }

    fun requestSelection(selection: RecordSelection) {
        val target = when(selection) {
            is RecordSelection.Date -> RecordKey(selection.date, MealType.BREAKFAST)
            is RecordSelection.Meal -> state.value.key.copy(type = selection.type)
        }
        if (target == state.value.key) return
        val version = loadVersion
        val answered = AtomicBoolean(false)
        selectionPermission.check(selection) { allowed ->
            if (answered.compareAndSet(false,true) && allowed && version == loadVersion) guardLeave { proceed -> if(proceed) load(target) }
        }
    }
    fun retry() { guardLeave {if(it) load(state.value.key)} }
    fun onEnter() { if(!state.value.loading && !state.value.isDirty && !state.value.saving) load(state.value.key) }
    private fun load(key: RecordKey) {
        val version = ++loadVersion
        loadJob?.cancel(); searchJob?.cancel(); searchVersion++
        mutable.value = RecordUiState(key)
        loadJob = viewModelScope.launch {
            try {
                releaseExcept(null)
                val saved = meals.getMeal(key.date,key.type)
                val catalogue = foods.getAll().associateBy { it.code }
                ensureActive()
                if (version != loadVersion) return@launch
                val draft = saved?.let { record -> RecordDraft(record.photoReference,record.items.map { item ->
                    DraftFood(item.id,requireNotNull(catalogue[item.foodCode]) { "Unknown saved food" },item.amount.toString())
                }) } ?: RecordDraft()
                mutable.value = RecordUiState(key,loading=false,original=saved,start=draft,draft=draft)
                draft.photoReference?.let {photos?.retain(it);heldPhotos.add(it)}
                refresh()
                showPhoto(draft.photoReference)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { if (version == loadVersion) mutable.update { it.copy(loading=false,error="기록 또는 음식 데이터를 불러오지 못했습니다. 다시 시도해주세요.") } }
        }
    }
    fun setAmount(itemId: String, value: String) { edit { draft -> draft.copy(foods=draft.foods.map { if (it.itemId == itemId) it.copy(amountInput=value) else it }) } }
    fun remove(itemId: String) { edit { it.copy(foods=it.foods.filterNot { row -> row.itemId == itemId }) } }
    private fun edit(change: (RecordDraft)->RecordDraft) {
        if (state.value.loading || state.value.error != null || locked()) return
        mutable.update { it.copy(draft=change(it.draft),operationError=null,message=null) }; refresh()
    }
    private fun refresh() {
        val current=state.value
        val rows=current.draft.foods.map { row ->
            val amount=positiveAmount(row.amountInput)
            val portion=amount?.let { runCatching { row.food.nutrientsPer100.forPortion(it) }.getOrNull() }
            RecordRow(row.itemId,row.food.name,row.food.unit.symbol,row.amountInput,
                if (portion == null) "0보다 큰 유한한 수량을 입력해주세요." else null,NutritionUiState.from(portion))
        }
        val total=if (rows.any { it.error != null }) null else runCatching {
            MealNutrition.total(current.draft.foods.map { MealItem(it.food.code,requireNotNull(positiveAmount(it.amountInput)),it.itemId) },current.draft.foods.associate { it.food.code to it.food })
        }.getOrNull()
        val dateError=if (current.key.date.isAfter(time.now().toLocalDate())) "미래 날짜는 기록할 수 없습니다." else null
        val valid=current.draft.foods.isNotEmpty() && total!=null && dateError==null
        mutable.update { it.copy(rows=rows,total=total,nutrition=NutritionUiState.from(total),dateError=dateError,
            isDirty=fingerprint(it.draft) != fingerprint(it.start),
            action=recordAction(it.original,it.draft),
            inputValid=valid,persistenceEnabled=!it.loading && it.error==null && !locked() && (valid || recordAction(it.original,it.draft)==RecordAction.DELETE)) }
    }
    fun openPicker() {
        if (state.value.loading || state.value.error != null || locked()) return
        mutable.update { it.copy(pickerOpen=true) }; search(state.value.search)
    }
    fun closePicker() { searchJob?.cancel(); searchVersion++; mutable.update { it.copy(pickerOpen=false,selectedFood=null,addAmount="",addError=null,addNutrition=NutritionUiState.Initial,searching=false) } }
    fun search(query: String) {
        val version=++searchVersion; searchJob?.cancel()
        mutable.update { it.copy(search=query,searching=true,searchError=null) }
        searchJob=viewModelScope.launch {
            try {
                val result=if (query.isBlank()) foods.getAll() else foods.searchByName(query)
                ensureActive()
                if (version == searchVersion) mutable.update { it.copy(searching=false,choices=result.map { food ->
                    val n=food.nutrientsPer100
                    FoodChoice(food,String.format(Locale.KOREA,"100%s · C %.2fg · P %.2fg · F %.2fg · %.2f kcal",food.unit.symbol,n.carbohydrateG,n.proteinG,n.fatG,n.energyKcal))
                }) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { if (version == searchVersion) mutable.update { it.copy(searching=false,choices=emptyList(),searchError="음식 목록을 읽지 못했습니다. 다시 시도해주세요.") } }
        }
    }
    fun choose(foodCode: String) {
        val food=state.value.choices.firstOrNull { it.food.code == foodCode }?.food ?: return
        mutable.update { it.copy(selectedFood=food,addAmount="",addError=null,addNutrition=NutritionUiState.Initial) }
    }
    fun addAmount(value: String) {
        val amount=positiveAmount(value)
        val portion=amount?.let { runCatching { state.value.selectedFood?.nutrientsPer100?.forPortion(it) }.getOrNull() }
        mutable.update { it.copy(addAmount=value,addError=if (portion==null) "0보다 큰 유한한 수량을 입력해주세요." else null,addNutrition=NutritionUiState.from(portion)) }
    }
    fun addSelected() {
        val current=state.value; val food=current.selectedFood ?: return
        val amount=positiveAmount(current.addAmount)
        if (amount == null || runCatching { food.nutrientsPer100.forPortion(amount) }.isFailure) {
            mutable.update { it.copy(addError="0보다 큰 유한한 수량을 입력해주세요.") };return
        }
        edit { it.copy(foods=it.foods+DraftFood(UUID.randomUUID().toString(),food,current.addAmount)) }
        mutable.update { it.copy(selectedFood=null,addAmount="",addError=null,addNutrition=NutritionUiState.Initial) }
    }
    private fun locked()=state.value.saving || state.value.photoBusy || state.value.leaving || state.value.dialog!=null

    fun requestPersist() {
        if(locked() || state.value.loading || state.value.error!=null) return
        refresh()
        val current=state.value
        if(!current.persistenceEnabled) {
            mutable.update {it.copy(operationError="음식·유효한 수량·식사 날짜를 확인해주세요.")};return
        }
        mutable.update {it.copy(dialog=RecordDialog.Persist(it.key,it.original,it.draft,it.action),persistenceEnabled=false,message=null,operationError=null)}
    }
    fun dismissDialog() {
        if(state.value.saving || state.value.leaving) return
        val callback=pendingLeave;pendingLeave=null
        mutable.update {it.copy(dialog=null)};refresh();callback?.invoke(false)
    }
    /** Configuration disposal cancels the old UI's destination callback, never the draft. */
    fun cancelPendingNavigation() {
        if(state.value.dialog==RecordDialog.Discard) dismissDialog()
    }
    fun confirmPersist() {
        val snapshot=state.value.dialog as? RecordDialog.Persist ?: return
        if(state.value.saving) return
        mutable.update {it.copy(dialog=null,saving=true,persistenceEnabled=false,operationError=null,message=null)}
        viewModelScope.launch {
            try { withContext(NonCancellable) {
                require(snapshot.key==state.value.key)
                val saved=if(snapshot.action==RecordAction.DELETE) {
                    val original=requireNotNull(snapshot.original)
                    if(!meals.deleteMeal(original.id)) throw MealNotFoundException(original.id)
                    null
                } else {
                    val record=validatedRecord(snapshot)
                    if(snapshot.original==null) meals.createMeal(record.date,record.mealType,record.items,record.photoReference)
                    else meals.updateMeal(record)
                }
                val catalogue=snapshot.draft.foods.associate {it.food.code to it.food}
                val baseline=saved?.let { record -> RecordDraft(record.photoReference,record.items.map {DraftFood(it.id,requireNotNull(catalogue[it.foodCode]),it.amount.toString())}) } ?: RecordDraft()
                mutable.update {it.copy(original=saved,start=baseline,draft=baseline,isDirty=false,message=when(snapshot.action){RecordAction.SAVE->"식단을 저장했습니다.";RecordAction.UPDATE->"식단을 변경했습니다.";RecordAction.DELETE->"식단을 삭제했습니다."})}
                releaseExcept(baseline.photoReference)
                showPhoto(baseline.photoReference)
            } } catch(e:CancellationException) {throw e
            } catch(e:Exception) {mutable.update {it.copy(operationError=if(e is MealConflictException) "같은 날짜·끼니의 기록이 이미 있습니다. 초안을 유지했습니다. 다시 불러오기를 선택해주세요." else "저장 작업에 실패했습니다. 초안과 기존 기록을 유지합니다.",message=null)}
            } finally {mutable.update {it.copy(saving=false)};refresh()}
        }
    }
    private fun validatedRecord(snapshot:RecordDialog.Persist):MealRecord {
        val items=snapshot.draft.foods.map {MealItem(it.food.code,requireNotNull(positiveAmount(it.amountInput)),it.itemId)}
        MealNutrition.total(items,snapshot.draft.foods.associate {it.food.code to it.food})
        return MealRecord(snapshot.original?.id?:UUID.randomUUID().toString(),snapshot.key.date,snapshot.key.type,
            items,snapshot.draft.photoReference).also {SaveValidation.meal(it,time.now().toLocalDate())}
    }
    fun guardLeave(decide:(Boolean)->Unit) {
        if(locked()) {decide(false);return}
        if(!state.value.isDirty) {decide(true);return}
        pendingLeave=decide
        mutable.update {it.copy(dialog=RecordDialog.Discard,persistenceEnabled=false)}
    }
    fun confirmDiscard() {
        if(state.value.dialog!=RecordDialog.Discard || state.value.leaving) return
        val callback=pendingLeave ?: return
        pendingLeave=null
        mutable.update {it.copy(dialog=null,leaving=true,persistenceEnabled=false)}
        viewModelScope.launch {
            try {withContext(NonCancellable) {
                val baseline=state.value.start
                mutable.update {it.copy(draft=baseline,isDirty=false,pickerOpen=false,selectedFood=null,message=null,operationError=null)}
                releaseExcept(baseline.photoReference);showPhoto(baseline.photoReference)
                mutable.update {it.copy(leaving=false)};refresh();callback(true)
            }} finally {mutable.update {it.copy(leaving=false)}}
        }
    }
    private suspend fun releaseExcept(keep:String?) {
        for(reference in heldPhotos.toList()) if(reference!=keep) {
            heldPhotos.remove(reference)
            try {photos?.release(reference)} catch(e:CancellationException){throw e} catch(e:Exception){mutable.update {it.copy(photoError="사용하지 않는 사진 정리를 완료하지 못했습니다. 다음 실행에서 다시 정리합니다.")}}
        }
    }
    private fun showPhoto(reference:String?) {
        previewJob?.cancel()
        mutable.update {it.copy(photoBitmap=null)}
        if(reference==null || images==null) return
        previewJob=viewModelScope.launch {
            try {val bitmap=images.load(reference);ensureActive();if(state.value.draft.photoReference==reference) mutable.update {it.copy(photoBitmap=bitmap)}}
            catch(e:CancellationException){throw e} catch(e:Exception){mutable.update {it.copy(photoError="사진을 읽지 못했습니다. 기존 사진 참조는 유지합니다.")}}
        }
    }
    fun beginGallery() {
        if(locked() || state.value.loading || state.value.error!=null) return
        mutable.update {it.copy(photoBusy=true,photoError=null,persistenceEnabled=false)}
        viewModelScope.launch {photoChannel.send(RecordPhotoRequest.Gallery)}
    }
    fun beginCamera() {
        if(locked() || state.value.loading || state.value.error!=null) return
        mutable.update {it.copy(photoBusy=true,photoError=null,persistenceEnabled=false)}
        viewModelScope.launch {
            try {
                val ticket=withContext(NonCancellable) {checkNotNull(camera).prepare().also {if(cleared)camera.discard(it) else cameraTicket=it}}
                if(!cleared)photoChannel.send(RecordPhotoRequest.Camera(ticket.uri))
            }
            catch(e:CancellationException){throw e} catch(e:Exception){photoLaunchFailed()}
        }
    }
    fun photoLaunchFailed() { finishPhoto(null,true) }
    fun galleryResult(uri:String?) {finishPhoto(uri,false)}
    fun cameraResult(success:Boolean) {finishPhoto(if(success) cameraTicket?.uri else null,false)}
    private fun finishPhoto(uri:String?,launchFailed:Boolean) {
        if(!state.value.photoBusy) return
        val ticket=cameraTicket;cameraTicket=null
        viewModelScope.launch {
            var imported:String?=null
            try {
                if(launchFailed) throw IllegalStateException("Photo action unavailable or denied")
                if(uri!=null) {
                    withContext(NonCancellable) {
                        imported=checkNotNull(photos).importPhoto(uri)
                        if(cleared) {photos?.release(checkNotNull(imported));imported=null;return@withContext}
                        heldPhotos.add(checkNotNull(imported))
                        mutable.update {it.copy(draft=it.draft.copy(photoReference=imported),photoError=null,message=null)}
                        imported=null
                        val keep=setOfNotNull(state.value.start.photoReference,state.value.draft.photoReference)
                        for(reference in heldPhotos.toList()) if(reference !in keep) {
                            heldPhotos.remove(reference)
                            try {photos?.release(reference)} catch(e:Exception){mutable.update {it.copy(photoError="사진은 교체했으나 사용하지 않는 파일 정리를 완료하지 못했습니다.")}}
                        }
                    }
                    showPhoto(state.value.draft.photoReference)
                }
            } catch(e:CancellationException){throw e} catch(e:Exception){mutable.update {it.copy(photoError="사진 선택·촬영을 완료하지 못했습니다. 기존 초안과 사진을 유지합니다.")}}
            finally {withContext(NonCancellable) {
                imported?.let {photos?.release(it)}
                ticket?.let {try {camera?.discard(it)} catch(e:Exception){mutable.update {it.copy(photoError="촬영 임시 파일 정리를 완료하지 못했습니다.")}}}
                mutable.update {it.copy(photoBusy=false)};refresh()
            }}
        }
    }
    fun removePhoto() {
        val old=state.value.draft.photoReference ?: return
        edit {it.copy(photoReference=null)}
        if(state.value.draft.photoReference==null) {
            showPhoto(null)
            if(old!=state.value.start.photoReference && heldPhotos.remove(old)) photos?.releaseLater(old)
        }
    }
    override fun onCleared() {
        cleared=true
        pendingLeave?.invoke(false);pendingLeave=null
        heldPhotos.forEach {photos?.releaseLater(it)};heldPhotos.clear()
        cameraTicket?.let {ticket -> CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {camera?.discard(ticket)}}
        photoChannel.close()
    }
}

fun positiveAmount(text: String): Double? = text.trim().takeIf { it.matches(Regex("(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)")) }
    ?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
private fun fingerprint(draft: RecordDraft): List<Any?> = listOf(draft.photoReference,
    draft.foods.map { listOf(it.itemId,it.food.code,positiveAmount(it.amountInput) ?: it.amountInput) })
