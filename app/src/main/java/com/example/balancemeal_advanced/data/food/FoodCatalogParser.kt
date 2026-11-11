package com.example.balancemeal_advanced.data.food

import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.data.repository.FoodDataException
import kotlinx.serialization.json.*

object FoodCatalogParser {
    fun parse(text: String): List<Food> = try {
        val root = Json.parseToJsonElement(text)
        require(root is JsonArray) { "Food root must be an array" }
        val foods = root.map { element ->
            val item = element.jsonObject
            fun textField(name: String): String {
                val value = item.getValue(name).jsonPrimitive
                require(value.isString) { "Expected string: " + name }
                return value.content
            }
            val nutrients = item.getValue("nutrientsPer100").jsonObject
            fun number(name: String): Double {
                val value = nutrients.getValue(name).jsonPrimitive
                require(!value.isString) { "Expected number: " + name }
                return value.double
            }
            Food(
                code = textField("code"), name = textField("name"),
                unit = FoodUnit.valueOf(textField("unit")),
                nutrientsPer100 = NutrientAmounts(
                    number("carbohydrateG"), number("proteinG"), number("fatG"), number("energyKcal"),
                ),
            )
        }
        require(foods.size == 100) { "Expected the provided 100 foods" }
        require(foods.map { it.code }.distinct().size == foods.size) { "Duplicate food code" }
        require(foods.count { it.unit == FoodUnit.G } == 80 && foods.count { it.unit == FoodUnit.ML } == 20) {
            "Expected 80 G and 20 ML foods"
        }
        foods
    } catch (error: Exception) {
        throw FoodDataException("Invalid foods_sample.json", error)
    }
}

