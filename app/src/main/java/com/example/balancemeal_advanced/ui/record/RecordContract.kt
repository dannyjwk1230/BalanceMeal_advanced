package com.example.balancemeal_advanced.ui.record

import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.ui.presentation.NutritionUiState
import java.time.LocalDate
import android.graphics.Bitmap

data class RecordKey(val date: LocalDate, val type: MealType)
data class DraftFood(val itemId: String, val food: Food, val amountInput: String)
data class RecordDraft(val photoReference: String? = null, val foods: List<DraftFood> = emptyList())
data class FoodChoice(val food: Food, val referenceLabel: String)
data class RecordRow(val itemId: String, val name: String, val unit: String, val amountInput: String, val error: String?, val nutrition: NutritionUiState)
enum class RecordAction(val label: String) { SAVE("저장"), UPDATE("변경"), DELETE("삭제") }
sealed interface RecordDialog {
    data class Persist(val key:RecordKey,val original:MealRecord?,val draft:RecordDraft,val action:RecordAction):RecordDialog
    data object Discard:RecordDialog
}
sealed interface RecordPhotoRequest {
    data object Gallery:RecordPhotoRequest
    data class Camera(val uri:String):RecordPhotoRequest
}
sealed interface RecordSelection {
    data class Date(val date: LocalDate) : RecordSelection
    data class Meal(val type: MealType) : RecordSelection
}
/** P07 can defer a date/meal transition until the draft warning is resolved. */
fun interface RecordSelectionPermission {
    fun check(selection: RecordSelection, decide: (Boolean)->Unit)
}
data class RecordUiState(
    val key: RecordKey, val loading: Boolean = true, val saving: Boolean = false,
    val original: MealRecord? = null, val start: RecordDraft = RecordDraft(), val draft: RecordDraft = RecordDraft(),
    val rows: List<RecordRow> = emptyList(), val total: NutrientAmounts? = null,
    val nutrition: NutritionUiState = NutritionUiState.Initial,
    val isDirty: Boolean = false, val error: String? = null, val dateError: String? = null,
    val action: RecordAction = RecordAction.SAVE, val inputValid: Boolean = false,
    val persistenceEnabled: Boolean = false,
    val dialog:RecordDialog?=null,val operationError:String?=null,val message:String?=null,
    val photoBusy:Boolean=false,val photoBitmap:Bitmap?=null,val photoError:String?=null,
    val leaving:Boolean=false,
    val pickerOpen: Boolean = false, val search: String = "", val searching: Boolean = false,
    val choices: List<FoodChoice> = emptyList(), val searchError: String? = null,
    val selectedFood: Food? = null, val addAmount: String = "", val addError: String? = null,
    val addNutrition: NutritionUiState = NutritionUiState.Initial,
)
fun MealType.label() = when(this) {
    MealType.BREAKFAST -> "조식"
    MealType.LUNCH -> "중식"
    MealType.DINNER -> "석식"
    MealType.SNACK -> "간식·음료"
}
fun recordAction(original: MealRecord?, draft: RecordDraft): RecordAction =
    if (original == null) RecordAction.SAVE else if (draft.foods.isEmpty()) RecordAction.DELETE else RecordAction.UPDATE
