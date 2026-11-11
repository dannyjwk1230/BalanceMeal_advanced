package com.example.balancemeal_advanced.ui

import androidx.lifecycle.ViewModelStore
import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.core.nutrition.EnergyTargets
import com.example.balancemeal_advanced.core.time.ClockTimeSource
import com.example.balancemeal_advanced.core.time.TimeSource
import com.example.balancemeal_advanced.data.repository.SettingsRepository
import com.example.balancemeal_advanced.ui.my.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class MySettingsTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val male = BodySettings(today.minusYears(30), Sex.MALE, 175.0, 70.0, ActivityLevel.LOW_ACTIVE)
    private val draft = BodyDraft(male.birthDate.toString(), male.sex, "175", "70", male.activityLevel)
    @Test fun exactEnergyAndUnroundedRanges() {
        assertEquals(2660.48, EnergyTargets.calculate(male, today).energyKcal, 1e-9)
        assertEquals(2024.268, EnergyTargets.calculate(male.copy(sex=Sex.FEMALE,heightCm=160.0,weightKg=55.0),today).energyKcal,1e-9)
        val target = EnergyTargets.fromEnergy(2000.0)
        assertEquals(NutrientRange(250.0,325.0),target.carbohydrate)
        assertEquals(NutrientRange(50.0,100.0),target.protein)
        assertEquals(300.0/9,target.fat.lowerG,1e-12)
        assertEquals(600.0/9,target.fat.upperG,1e-12)
    }
    @Test fun fixedClockBirthdayAndNineteenthBoundary() {
        val time=ClockTimeSource(Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"),ZoneId.of("Asia/Seoul")))
        val day=time.now().toLocalDate()
        val birth=day.minusYears(19)
        assertEquals(18,EnergyTargets.age(birth,day.minusDays(1)))
        assertEquals(19,EnergyTargets.age(birth,day))
        assertEquals(29,EnergyTargets.age(male.birthDate,day.minusDays(1)))
        assertEquals(30,EnergyTargets.age(male.birthDate,day.plusDays(1)))
        assertThrows(UnderAgeException::class.java) { draft.copy(birth=birth.plusDays(1).toString()).validated(day) }
        draft.copy(birth=birth.toString()).validated(day)
    }
    @Test fun allSexAndActivityCoefficients() {
        val expected=listOf(1.0 to 1.0,1.11 to 1.12,1.25 to 1.27,1.48 to 1.45)
        ActivityLevel.entries.forEachIndexed { index, level ->
            assertEquals(expected[index].first,EnergyTargets.pa(Sex.MALE,level),0.0)
            assertEquals(expected[index].second,EnergyTargets.pa(Sex.FEMALE,level),0.0)
        }
    }
    private class Settings(body: BodySettings) : SettingsRepository {
        override val profile=MutableStateFlow(LocalProfile())
        override val bodySettings=MutableStateFlow<BodySettings?>(body)
        var failBodyWrites=false
        var bodyWrites=0
        var nameWrites=0
        override suspend fun updateNickname(nickname:String) { nameWrites++; profile.value=profile.value.copy(nickname=nickname) }
        override suspend fun updateProfilePhoto(photoReference:String?) { profile.value=profile.value.copy(photoReference=photoReference) }
        override suspend fun saveBodySettings(settings:BodySettings) { if (failBodyWrites) error("Injected write failure"); bodyWrites++; bodySettings.value=settings }
    }
    private fun scenario(test: suspend TestScope.(MyViewModel,Settings)->Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo=Settings(male)
        val vm=MyViewModel(repo,TimeSource { today.atStartOfDay() })
        val store=ViewModelStore().apply { put("my",vm) }
        try { runCurrent(); test(vm,repo) } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun everyMissingOrInvalidFieldPreservesStoredBodyAndTargets() = scenario { vm,repo ->
        val original=vm.state.value.target
        val bad=listOf(draft.copy(birth=""),draft.copy(sex=null),draft.copy(height=""),draft.copy(weight=""),draft.copy(activity=null),
            draft.copy(birth=today.plusDays(1).toString()),draft.copy(height="0"),draft.copy(weight="-1"),draft.copy(height="NaN"),draft.copy(weight="Infinity"),draft.copy(birth="2026-02-30"))
        bad.forEach { vm.edit(it); vm.requestBody(); vm.confirm(); runCurrent(); assertNull(vm.state.value.dialog); assertEquals(male,repo.bodySettings.value); assertEquals(original,vm.state.value.target) }
        assertEquals(0,repo.bodyWrites)
    }
    @Test fun nicknameAndBodyConfirmCancelAndDuplicateConfirmation() = scenario { vm,repo ->
        vm.nickname("Changed");vm.requestNickname();vm.dismiss();runCurrent()
        assertEquals("First Account",repo.profile.value.nickname)
        vm.requestNickname();vm.confirm();vm.confirm();runCurrent()
        assertEquals("Changed",repo.profile.value.nickname);assertEquals(1,repo.nameWrites)
        vm.edit(draft.copy(weight="71"));vm.requestBody();vm.dismiss();runCurrent()
        assertEquals(male,repo.bodySettings.value)
        vm.requestBody();vm.confirm();vm.confirm();runCurrent()
        assertEquals(71.0,repo.bodySettings.value!!.weightKg,0.0);assertEquals(1,repo.bodyWrites)
    }
    @Test fun calculationTogglePreservesDraftAndStoredBodyAndSurvivesBodyEdits() = scenario { vm,repo ->
        val originalTarget = vm.state.value.target
        vm.edit(draft.copy(weight="82"))
        vm.setCalculationEnabled(false)
        vm.setCalculationEnabled(false)
        runCurrent()
        assertEquals(male.copy(calculationEnabled=false),repo.bodySettings.value)
        assertEquals("82",vm.state.value.draft.weight)
        assertNull(vm.state.value.target)
        assertEquals(1,repo.bodyWrites)
        vm.requestBody();vm.confirm();runCurrent()
        assertFalse(repo.bodySettings.value!!.calculationEnabled)
        assertEquals(82.0,repo.bodySettings.value!!.weightKg,0.0)
        assertNull(vm.state.value.target)
        vm.setCalculationEnabled(true);runCurrent()
        assertTrue(repo.bodySettings.value!!.calculationEnabled)
        assertNotNull(vm.state.value.target)
        assertNotEquals(originalTarget,vm.state.value.target)
    }
    @Test fun failedToggleLeavesEnabledBodyAndTargetsUnchanged() = scenario { vm,repo ->
        val originalTarget = vm.state.value.target
        repo.failBodyWrites=true
        vm.setCalculationEnabled(false);runCurrent()
        assertEquals(male,repo.bodySettings.value)
        assertEquals(originalTarget,vm.state.value.target)
        assertFalse(vm.state.value.busy)
        assertNotNull(vm.state.value.error)
    }
    @Test fun noSavedBodyDoesNotAllowCalculationToggle() = scenario { vm,repo ->
        repo.bodySettings.value=null;runCurrent()
        vm.setCalculationEnabled(true);runCurrent()
        assertNull(repo.bodySettings.value)
        assertEquals(0,repo.bodyWrites)
    }
    @Test fun incompleteBodyShowsMissingFieldsAndNeverWrites() = scenario { vm,repo ->
        vm.edit(BodyDraft());vm.requestBody()
        val dialog=vm.state.value.dialog as MyDialog.InvalidBody
        listOf("생년월일","성별","키","체중","활동 수준").forEach { assertTrue(dialog.message.contains(it)) }
        vm.confirm();runCurrent()
        assertEquals(0,repo.bodyWrites);assertEquals(male,repo.bodySettings.value)
    }
    @Test fun profileChangesPromptAndDiscardWithoutSaving() = scenario { vm,repo ->
        var photoChanged = true
        vm.hasPhotoChanges = { photoChanged }
        vm.discardPhoto = { photoChanged = false }
        vm.nickname("New name")
        var allowed: Boolean? = null
        vm.guardLeave { allowed = it }
        assertEquals(MyDialog.Discard, vm.state.value.dialog)
        vm.dismiss()
        assertEquals(false, allowed)
        assertEquals("New name", vm.state.value.nicknameDraft)
        assertTrue(photoChanged)
        vm.guardLeave { allowed = it }
        vm.confirmDiscard()
        assertEquals(true, allowed)
        assertFalse(photoChanged)
        assertEquals(repo.profile.value.nickname, vm.state.value.nicknameDraft)
        assertEquals(0, repo.nameWrites)
    }
    @Test fun profileSaveUsesCombinedOperationAndLeavesClean() = scenario { vm,repo ->
        var photoChanged = true
        var saves = 0
        vm.hasPhotoChanges = { photoChanged }
        vm.saveProfile = { name ->
            saves++
            repo.saveProfile(repo.profile.value.copy(nickname = name, photoReference = "photo"))
            photoChanged = false
        }
        vm.nickname("Updated")
        vm.requestNickname(); vm.confirm(); runCurrent()
        assertEquals(1, saves)
        assertEquals("Updated", repo.profile.value.nickname)
        assertEquals("photo", repo.profile.value.photoReference)
        var allowed = false
        vm.guardLeave { allowed = it }
        assertTrue(allowed)
    }
    @Test fun bodyNavigationCancelPreservesDraftAndConfirmDiscardsOnce() = scenario { vm,repo ->
        vm.edit(draft.copy(weight="81"))
        var first:Boolean?=null;var second:Boolean?=null
        vm.guardLeave {first=it};vm.guardLeave {second=it}
        assertEquals(MyDialog.Discard,vm.state.value.dialog)
        assertNull(first);assertEquals(false,second)
        vm.dismiss();assertEquals(false,first);assertEquals("81",vm.state.value.draft.weight)
        var calls=0
        vm.guardLeave {if(it)calls++};vm.confirmDiscard();vm.confirmDiscard()
        assertEquals(1,calls);assertEquals(male.weightKg,vm.state.value.draft.weight.toDouble(),0.0)
        assertEquals(male,repo.bodySettings.value);assertEquals(0,repo.bodyWrites)
        var clean=false;vm.guardLeave {clean=it};assertTrue(clean)
    }
    @Test fun navigationDisposalCancelsDestinationButKeepsDraft() = scenario { vm,_ ->
        vm.edit(draft.copy(height="181"))
        var allowed:Boolean?=null;vm.guardLeave {allowed=it}
        vm.cancelPendingNavigation();vm.confirmDiscard()
        assertEquals(false,allowed);assertEquals("181",vm.state.value.draft.height)
        assertNull(vm.state.value.dialog)
    }
    @Test fun equivalentNumericFormattingDoesNotPromptAndSavedChangesAreClean() = scenario { vm,_ ->
        vm.edit(draft)
        var allowed=false;vm.guardLeave {allowed=it};assertTrue(allowed)
        vm.edit(draft.copy(weight="81"));vm.requestBody();vm.confirm();runCurrent()
        allowed=false;vm.guardLeave {allowed=it};assertTrue(allowed);assertNull(vm.state.value.dialog)
    }
    @Test fun warningConfirmationNeverSavesAndCancelRestoresDraft() = scenario { vm,repo ->
        vm.edit(draft.copy(birth=today.minusYears(18).toString()));vm.requestBody()
        assertEquals(MyDialog.UnderAge,vm.state.value.dialog)
        vm.confirm();runCurrent();assertEquals(0,repo.bodyWrites)
        vm.cancelEdits();assertEquals(draft.birth,vm.state.value.draft.birth)
        assertEquals(male,vm.state.value.savedBody)
    }
}
