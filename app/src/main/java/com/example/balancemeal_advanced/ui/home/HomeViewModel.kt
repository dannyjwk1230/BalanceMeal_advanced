package com.example.balancemeal_advanced.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.balancemeal_advanced.core.time.TimeSource
import com.example.balancemeal_advanced.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val meals:MealRepository,private val settings:SettingsRepository,private val foods:FoodRepository,private val time:TimeSource):ViewModel() {
    private val retries=MutableStateFlow(0)
    private data class Activation(val busy: Boolean = false, val error: String? = null)
    private val activation = MutableStateFlow(Activation())
    private fun clock()=flow {
        while(currentCoroutineContext().isActive) {
            val now=time.now();emit(now);delay(HomeRules.nextRefreshMillis(now))
        }
    }.distinctUntilChangedBy {it.toLocalDate() to HomeRules.greeting(it.toLocalTime())}
    val state:StateFlow<HomeUiState> = combine(clock(),retries) {_,_->time.now()}.flatMapLatest {now ->
        flow {
            emit(HomeUiState(now.toLocalDate()))
            val catalogue=foods.getAll().associateBy {it.code}
            emitAll(combine(meals.observeOnDate(now.toLocalDate()),settings.bodySettings) {records,body ->
                HomeRules.present(now,records,body,catalogue)
            })
        }.catch {e ->if(e is CancellationException)throw e
            emit(HomeUiState(now.toLocalDate(),loading=false,error="오늘의 식단 또는 신체 정보를 불러오지 못했습니다. 다시 시도해주세요."))
        }
    }.combine(activation) { home, action ->
        home.copy(activatingBody = action.busy, activationError = action.error)
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(stopTimeoutMillis=0),HomeUiState(time.now().toLocalDate()))
    fun retry() {retries.update {it+1}}
    fun enableBodyCalculation() {
        if (activation.value.busy) return
        activation.value = Activation(busy = true)
        viewModelScope.launch {
            try {
                val body = settings.bodySettings.first()
                if (body != null && !body.calculationEnabled) {
                    settings.saveBodySettings(body.copy(calculationEnabled = true))
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                activation.value = Activation(busy = true, error = "신체 정보를 활성화하지 못했습니다. 다시 시도해주세요.")
            } finally { activation.update { it.copy(busy = false) } }
        }
    }
}
