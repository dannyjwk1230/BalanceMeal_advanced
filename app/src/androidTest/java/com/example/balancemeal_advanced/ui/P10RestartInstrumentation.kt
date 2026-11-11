package com.example.balancemeal_advanced.ui

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.Process
import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.ui.home.HomeRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Explicit restart check of isolated production Room/DataStore/images; no runtime account writes. */
class P10RestartInstrumentation : Instrumentation() {
    private var mode = ""
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments);mode=arguments?.getString("mode").orEmpty();start() }
    override fun onStart() { runBlocking {
        val result=Bundle()
        val marker=File(targetContext.filesDir,"p10-isolated-restart.json")
        var fixture:P07Fixture?=null
        try {
            if(mode=="prepare") {
                check(!marker.exists()) {"Run cleanup for the previous P10 fixture first"}
                marker.writeText(JSONObject().put("id","p10-"+UUID.randomUUID()).toString())
            }
            if(mode=="cleanup" && !marker.exists()) {
                result.putString("result","No isolated fixture remains")
            } else {
                val json=JSONObject(marker.readText())
                val id=json.getString("id");check(id.startsWith("p10-") && UUID.fromString(id.removePrefix("p10-")).toString()==id.removePrefix("p10-"))
                val f=P07Fixture(id=id,context=targetContext);fixture=f
                when(mode) {
                    "prepare" -> {
                        val ticket=f.imageUri()
                        val photo=try {f.photos.importPhoto(ticket.uri)} finally {f.camera.discard(ticket)}
                        try {
                            f.settings.updateNickname("P10 Restart Account")
                            f.settings.updateProfilePhoto(photo)
                            f.settings.saveBodySettings(BodySettings(LocalDate.of(1996,11,11),Sex.MALE,175.0,70.0,ActivityLevel.LOW_ACTIVE))
                            val rice=f.foods.getAll().single {it.name=="쌀밥"}
                            val meal=f.meals.createMeal(f.time.now().toLocalDate(),MealType.BREAKFAST,listOf(MealItem(rice.code,150.0)),photo)
                            json.put("mealId",meal.id).put("itemId",meal.items.single().id).put("photo",photo).put("preparePid",Process.myPid())
                            marker.writeText(json.toString())
                        } finally {f.photos.release(photo)}
                        f.closeKeepingFiles();fixture=null
                    }
                    "verify" -> {
                        check(Process.myPid()!=json.getInt("preparePid")) {"A new process is required"}
                        val profile=f.settings.profile.first();val body=checkNotNull(f.settings.bodySettings.first())
                        val meal=checkNotNull(f.meals.getMeal(json.getString("mealId")))
                        check(profile.nickname=="P10 Restart Account" && profile.photoReference==json.getString("photo"))
                        check(body==BodySettings(LocalDate.of(1996,11,11),Sex.MALE,175.0,70.0,ActivityLevel.LOW_ACTIVE))
                        check(meal.photoReference==profile.photoReference)
                        check(meal.items.single().id==json.getString("itemId") && meal.items.single().amount==150.0)
                        f.images.load(checkNotNull(meal.photoReference)).let {check(it.width==12 && it.height==12);it.recycle()}
                        val home=HomeRules.present(f.time.now(),f.meals.observeOnDate(meal.date).first(),body,f.foods.getAll().associateBy {it.code})
                        check(home.total!!.energyKcal==249.0 && kotlin.math.abs(home.targets!!.energyKcal-2660.48)<1e-8)
                        check(home.mainComment=="오늘의 칼로리가 목표보다 부족해요.")
                        json.put("verifyPid",Process.myPid()).put("restoredAfterRestart",true).put("homeKcal",home.total.energyKcal).put("targetKcal",home.targets.energyKcal)
                        f.closeKeepingFiles();fixture=null
                    }
                    "cleanup" -> { f.close();fixture=null;check(marker.delete());json.put("temporaryDataRemoved",true) }
                    else -> error("Explicit prepare/verify/cleanup mode required")
                }
                result.putString("result",json.toString())
            }
            finish(Activity.RESULT_OK,result)
        } catch(error:Exception) {
            fixture?.closeKeepingFiles()
            result.putString("error",error.stackTraceToString());finish(Activity.RESULT_CANCELED,result)
        }
    } }
}
