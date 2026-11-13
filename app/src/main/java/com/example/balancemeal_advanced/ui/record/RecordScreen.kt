package com.example.balancemeal_advanced.ui.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.balancemeal_advanced.core.model.MealType
import com.example.balancemeal_advanced.R
import com.example.balancemeal_advanced.data.repository.FoodRepository
import com.example.balancemeal_advanced.ui.components.*
import com.example.balancemeal_advanced.ui.theme.BalanceColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordScreen(
    state: RecordUiState, onDate: ()->Unit, onMeal: (MealType)->Unit,
    onAmount: (String,String)->Unit, onRemove: (String)->Unit, onOpenPicker: ()->Unit,
    onClosePicker: ()->Unit, onSearch: (String)->Unit, onChoose: (String)->Unit,
    onAddAmount: (String)->Unit, onAdd: ()->Unit, onRetry: ()->Unit,
    onPersist:()->Unit={},onConfirmPersist:()->Unit={},onDismissDialog:()->Unit={},onDiscard:()->Unit={},
    onGallery:()->Unit={},onCamera:()->Unit={},onRemovePhoto:()->Unit={},
) {
    val locked=state.saving || state.photoBusy || state.leaving || state.dialog!=null
    AdaptivePage("destination_RECORD",primary={
GlassCard(Modifier.fillMaxWidth(),radius=16.dp) {
            Text("식사 날짜",style=MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = onDate, enabled = !locked,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("record_date"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BalanceColors.InputOutline),
            ) {
                Text(state.key.date.toString(), style = MaterialTheme.typography.bodyLarge,
                    color = BalanceColors.Ink, modifier = Modifier.weight(1f))
                Icon(androidx.compose.ui.res.painterResource(R.drawable.bm_ic_calendar), null, Modifier.size(20.dp))
            }
            state.dateError?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error) }
        } 
         GlassCard(Modifier.fillMaxWidth(),radius=16.dp) {
            Text("식사 종류",style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                MealType.entries.forEach { meal -> FilterChip(state.key.type==meal,{onMeal(meal)},label={Text(meal.label())},
                    enabled=!locked,shape=RoundedCornerShape(24.dp),
                    border=BorderStroke(1.dp,if(state.key.type==meal) BalanceColors.MintDark.copy(alpha=.55f) else BalanceColors.Outline.copy(alpha=.25f)),
                    colors=FilterChipDefaults.filterChipColors(containerColor=Color.White.copy(alpha=.85f),labelColor=BalanceColors.SecondaryText,selectedContainerColor=BalanceColors.Mint,selectedLabelColor=Color.White),
                    modifier=Modifier.testTag("record_meal_"+meal.name)) }
            }
        } 
         GlassCard(Modifier.fillMaxWidth(),radius=16.dp) {
            Text("식사 사진",style=MaterialTheme.typography.titleMedium)
            if(state.photoBitmap!=null) Image(state.photoBitmap.asImageBitmap(),"식단 사진",Modifier.fillMaxWidth().height(192.dp).testTag("record_photo"),contentScale=ContentScale.Crop)
            else MealPhotoPlaceholder(Modifier.fillMaxWidth().height(192.dp),showCamera=true)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onGallery,enabled=!locked && !state.loading,modifier=Modifier.weight(1f).testTag("record_gallery")) {Text("갤러리 선택")}
                    Button(onCamera,enabled=!locked && !state.loading,modifier=Modifier.weight(1f).testTag("record_camera")) {Text("사진 촬영")}
                }
                if(state.draft.photoReference!=null) Button(onRemovePhoto,enabled=!locked,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error,contentColor=MaterialTheme.colorScheme.onError)) {Text("삭제")}
            }
            if(state.photoBusy) Text("사진 작업을 진행하고 있습니다.")
            state.photoError?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        } 
 
    },secondary={
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("추가한 음식 (${state.rows.size}개)",style=MaterialTheme.typography.titleMedium)
            Button(onOpenPicker,enabled=!state.loading && state.error==null && !locked,modifier=Modifier.testTag("record_add_food")) { Text("+ 음식 추가") }
        } 
        state.rows.forEach { row -> GlassCard(Modifier.fillMaxWidth(),radius=16.dp) {
            Text(row.name,style=MaterialTheme.typography.titleMedium)
            AppTextField(row.amountInput,{onAmount(row.itemId,it)},label="섭취량",suffix=row.unit,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),enabled=!locked,isError=row.error!=null,modifier=Modifier.fillMaxWidth().testTag("amount_"+row.itemId))
            EnergyValue("칼로리",row.nutrition.energyKcal)
            NutritionTotals(row.nutrition)
            Button({onRemove(row.itemId)},enabled=!locked,modifier=Modifier.testTag("remove_"+row.itemId),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error,contentColor=MaterialTheme.colorScheme.onError)) { Text("음식 삭제") }
        } }
        if (state.loading)  Text("기록을 불러오는 중입니다.") 
        if (state.error!=null) { Text(state.error,color=MaterialTheme.colorScheme.error);TextButton(onRetry) { Text("다시 시도") } }
        if (!state.loading && state.error==null && state.rows.isEmpty())  Text("음식을 추가해 식단을 구성해보세요.") 
         GlassCard(Modifier.fillMaxWidth(),radius=16.dp) {
            Text("식단 영양 정보",style=MaterialTheme.typography.titleMedium)
            EnergyValue("총 칼로리",state.nutrition.energyKcal)
            NutritionTotals(state.nutrition)
            if (state.rows.any { it.error!=null }) Text("수량을 확인하면 전체 합계를 표시합니다.",style=MaterialTheme.typography.bodySmall)
            Text(FoodRepository.SOURCE_TEXT,style=MaterialTheme.typography.bodySmall)
        } 
        
            Button(onPersist,enabled=state.persistenceEnabled && !locked,modifier=Modifier.fillMaxWidth().testTag("record_persistence"),colors=if(state.action==RecordAction.DELETE) ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error,contentColor=MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors()) { Text(if(state.saving) "처리 중" else state.action.label) }
            state.operationError?.let {Text(it,color=MaterialTheme.colorScheme.error);TextButton(onRetry,enabled=!locked) {Text("다시 불러오기")}}
            state.message?.let {Text(it,modifier=Modifier.testTag("record_success"))}
        
    
    })
    if (state.pickerOpen) FoodPicker(state,onClosePicker,onSearch,onChoose,onAddAmount,onAdd)
    when(val dialog=state.dialog) {
        RecordDialog.Discard -> UnsavedChangesDialog(onDiscard,onDismissDialog)
        is RecordDialog.Persist -> CommonConfirmationDialog("식단 ${dialog.action.label}",
            if(dialog.action==RecordAction.DELETE) "${dialog.key.date} ${dialog.key.type.label()} 식단을 삭제할까요?" else "${dialog.key.date} ${dialog.key.type.label()} 식단을 ${dialog.action.label}할까요?",
            dialog.action.label,onConfirmPersist,onDismissDialog)
        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodPicker(state: RecordUiState,onDismiss:()->Unit,onSearch:(String)->Unit,onChoose:(String)->Unit,onAmount:(String)->Unit,onAdd:()->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=600.dp).imePadding().testTag("food_picker_list").semantics {
            stateDescription=if (state.searching) "검색 중" else "${state.choices.size}개 검색 결과"
        },contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Text("음식 선택 및 섭취량",style=MaterialTheme.typography.titleLarge) }
            item { AppTextField(state.search,onSearch,label="음식 이름",modifier=Modifier.fillMaxWidth().testTag("food_search")) }
            state.selectedFood?.let { food -> item {
                Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(food.name,style=MaterialTheme.typography.titleMedium)
                        AppTextField(state.addAmount,onAmount,label="섭취량",suffix=food.unit.symbol,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),isError=state.addError!=null,modifier=Modifier.fillMaxWidth().testTag("food_add_amount"))
                    }
                    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        EnergyValue("칼로리",state.addNutrition.energyKcal)
                        NutritionTotals(state.addNutrition)
                    }
                    Button(onAdd,modifier=Modifier.testTag("food_add_confirm")) { Text("음식 추가") }
                }
            } }
            if (state.searching) item { Text("검색 중입니다.") }
            state.searchError?.let { error -> item { Text(error,color=MaterialTheme.colorScheme.error);TextButton({onSearch(state.search)}) { Text("다시 시도") } } }
            if (!state.searching && state.searchError==null && state.choices.isEmpty()) item { Text("검색 결과가 없습니다.") }
            items(state.choices,key={it.food.code}) { choice -> GlassCard(Modifier.fillMaxWidth().clickable {onChoose(choice.food.code)},radius=16.dp) {
                Text(choice.food.name,style=MaterialTheme.typography.titleMedium)
                Text(choice.referenceLabel,style=MaterialTheme.typography.bodySmall)
            } }
        }
    }
}
