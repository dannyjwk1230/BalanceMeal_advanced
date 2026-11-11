package com.example.balancemeal_advanced.ui

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import com.example.balancemeal_advanced.BalanceMealApplication
import com.example.balancemeal_advanced.core.model.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File

/** Explicit two-process smoke helper, never run by the JUnit suite or included in the runtime APK. */
class P07SmokeInstrumentation:Instrumentation() {
    private var mode=""
    override fun onCreate(arguments:Bundle?) {super.onCreate(arguments);mode=arguments?.getString("mode").orEmpty();start()}
    override fun onStart() {runBlocking {
        val result=Bundle()
        try {
            val app=(targetContext.applicationContext as BalanceMealApplication).container
            val marker=File(targetContext.filesDir,"p07-restart-smoke.json")
            when(mode) {
                "prepare" -> {
                    check(!marker.exists()) {"A previous smoke record must be cleaned up first"}
                    val now=app.timeSource.now().minusMinutes(1).withSecond(0).withNano(0)
                    var key:Pair<java.time.LocalDate,MealType>?=null
                    for(offset in 0..6) {
                        for(type in MealType.entries) if(app.mealRepository.getMeal(now.toLocalDate().minusDays(offset.toLong()),type)==null) {key=now.toLocalDate().minusDays(offset.toLong()) to type;break}
                        if(key!=null)break
                    }
                    val chosen=checkNotNull(key)
                    val ticket=app.cameraCaptureStore.prepare()
                    var photo:String?=null
                    var saved:MealRecord?=null
                    try {
                        val bitmap=Bitmap.createBitmap(12,12,Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.GREEN)
                        targetContext.contentResolver.openOutputStream(Uri.parse(ticket.uri)).use {checkNotNull(it);check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
                        photo=app.photoFiles.importPhoto(ticket.uri)
                        val food=app.foodRepository.getAll().single {it.name=="쌀밥"}
                        saved=app.mealRepository.createMeal(chosen.first,chosen.second,listOf(MealItem(food.code,80.25)),photo)
                        val json=JSONObject().put("id",saved.id).put("date",saved.date.toString()).put("type",saved.mealType.name).put("foodCode",food.code).put("amount",80.25).put("photo",photo)
                        marker.writeText(json.toString());result.putString("result",json.toString())
                    } catch(e:Exception) {saved?.let {app.mealRepository.deleteMeal(it.id)};throw e
                    } finally {photo?.let {app.photoFiles.release(it)};app.cameraCaptureStore.discard(ticket)}
                }
                "verify" -> {
                    val json=JSONObject(marker.readText());val saved=checkNotNull(app.mealRepository.getMeal(json.getString("id")))
                    check(saved.date.toString()==json.getString("date") && saved.mealType.name==json.getString("type"))
                    check(saved.items.single().amount==json.getDouble("amount"))
                    check(saved.photoReference==json.getString("photo"))
                    val bitmap=app.imageStorage.load(checkNotNull(saved.photoReference));check(bitmap.width==12 && bitmap.height==12);bitmap.recycle()
                    result.putString("result",json.put("restoredAfterRestart",true).toString())
                }
                "cleanup" -> {
                    val json=JSONObject(marker.readText())
                    app.mealRepository.getMeal(json.getString("id"))?.let {saved ->
                        check(saved.date.toString()==json.getString("date") && saved.mealType.name==json.getString("type"))
                        check(app.mealRepository.deleteMeal(saved.id))
                    }
                    app.photoFiles.retire(json.getString("photo"));check(marker.delete())
                    result.putString("result","Temporary smoke record removed; other records were preserved")
                }
                else -> error("Explicit mode prepare/verify/cleanup is required")
            }
            finish(Activity.RESULT_OK,result)
        } catch(e:Exception){result.putString("error",e.stackTraceToString());finish(Activity.RESULT_CANCELED,result)}
    }}
}
