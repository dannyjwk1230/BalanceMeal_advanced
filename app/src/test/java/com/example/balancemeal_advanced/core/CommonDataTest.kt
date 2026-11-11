package com.example.balancemeal_advanced.core

import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.core.nutrition.MealNutrition
import com.example.balancemeal_advanced.core.time.ClockTimeSource
import com.example.balancemeal_advanced.core.validation.SaveValidation
import com.example.balancemeal_advanced.data.food.CachedFoodRepository
import com.example.balancemeal_advanced.data.food.FoodCatalogParser
import com.example.balancemeal_advanced.data.repository.FoodDataException
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.*

class CommonDataTest {
    private fun source() = checkNotNull(javaClass.getResourceAsStream("/foods_sample.json"))
        .bufferedReader(Charsets.UTF_8).use { it.readText() }
    private fun foods() = FoodCatalogParser.parse(source())
    private fun meal(items: List<MealItem>) = MealRecord(
        "meal-1", LocalDate.of(2026, 11, 10), MealType.BREAKFAST,items,
    )
    private inline fun <reified T : Throwable> fails(block: () -> Unit) {
        try { block(); fail("Expected " + T::class.java.simpleName) }
        catch (error: Throwable) { if (error !is T) throw error }
    }
    private fun assertNutrients(actual: NutrientAmounts, c: Double, p: Double, f: Double, kcal: Double) {
        assertEquals(c, actual.carbohydrateG, 1e-10)
        assertEquals(p, actual.proteinG, 1e-10)
        assertEquals(f, actual.fatG, 1e-10)
        assertEquals(kcal, actual.energyKcal, 1e-10)
    }

    @Test fun catalogHasExactlyOriginalUnitsAndUniqueCodes() {
        val all = foods()
        assertEquals(100, all.size)
        assertEquals(80, all.count { it.unit == FoodUnit.G })
        assertEquals(20, all.count { it.unit == FoodUnit.ML })
        assertEquals(100, all.map { it.code }.toSet().size)
        assertEquals("g", FoodUnit.G.symbol)
        assertEquals("ml", FoodUnit.ML.symbol)
    }
    @Test fun rice150GramsUsesSourceKcalWithoutRounding() {
        val rice = foods().single { it.name == "쌀밥" }
        assertNutrients(rice.nutrientsPer100.forPortion(150.0), 55.995, 5.04, 0.48, 249.0)
    }
    @Test fun americano250MlKeepsMlAndSourceEnergy() {
        val coffee = foods().single { it.name == "커피_아메리카노" }
        assertEquals(FoodUnit.ML, coffee.unit)
        assertNutrients(coffee.nutrientsPer100.forPortion(250.0), 0.875, 0.575, 0.325, 7.5)
    }
    @Test fun zeroKcalFoodIsValid() {
        val food = foods().first { it.nutrientsPer100.energyKcal == 0.0 }
        assertEquals(0.0, food.nutrientsPer100.forPortion(27.5).energyKcal, 0.0)
    }
    @Test fun fractionalPortionPreservesPrecision() {
        val rice = foods().single { it.name == "쌀밥" }
        assertNutrients(rice.nutrientsPer100.forPortion(12.5), 4.66625, 0.42, 0.04, 20.75)
    }
    @Test fun invalidPortionsAreRejectedByItemsAndCalculation() {
        for (amount in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            fails<IllegalArgumentException> { MealItem("rice", amount) }
            fails<IllegalArgumentException> { NutrientAmounts.ZERO.forPortion(amount) }
        }
    }
    @Test fun duplicateFoodRowsRemainSeparateAndBothContribute() {
        val rice = foods().single { it.name == "쌀밥" }
        val items = listOf(MealItem(rice.code, 100.0), MealItem(rice.code, 50.0))
        assertNotEquals(items[0].id, items[1].id)
        assertNutrients(MealNutrition.total(items, mapOf(rice.code to rice)), 55.995, 5.04, 0.48, 249.0)
    }
    @Test fun unknownCodeAndOverflowFailInsteadOfProducingFakeTotals() {
        fails<IllegalArgumentException> { MealNutrition.total(listOf(MealItem("unknown", 1.0)), emptyMap()) }
        fails<IllegalArgumentException> {
            NutrientAmounts(Double.MAX_VALUE, 0.0, 0.0, 0.0).forPortion(Double.MAX_VALUE)
        }
    }
    @Test fun rootObjectMalformedAndMissingFieldFail() {
        for (text in listOf("{}", "[", source().replaceFirst("\"code\"", "\"missingCode\""))) {
            fails<FoodDataException> { FoodCatalogParser.parse(text) }
        }
    }
    @Test fun duplicateCodesAndWrongUnitCountsFail() {
        val root = Json.parseToJsonElement(source()).jsonArray
        val duplicate = root.toMutableList()
        duplicate[1] = JsonObject(root[1].jsonObject + ("code" to root[0].jsonObject.getValue("code")))
        fails<FoodDataException> { FoodCatalogParser.parse(JsonArray(duplicate).toString()) }
        val units = root.toMutableList()
        val index = units.indexOfFirst { it.jsonObject.getValue("unit").jsonPrimitive.content == "G" }
        units[index] = JsonObject(units[index].jsonObject + ("unit" to JsonPrimitive("ML")))
        fails<FoodDataException> { FoodCatalogParser.parse(JsonArray(units).toString()) }
    }
    @Test fun concurrentReadsParseOnceAndSearchUsesCatalog() = runBlocking {
        var reads = 0
        val repository = CachedFoodRepository(readText = { reads++; delay(10); source() })
        val results = coroutineScope { List(8) { async(Dispatchers.Default) { repository.getAll() } }.awaitAll() }
        assertEquals(1, reads)
        assertTrue(results.all { it === results[0] })
        val coffee = repository.searchByName("  아메리카노 ").single()
        assertEquals(coffee, repository.getByCode(coffee.code))
        assertNull(repository.getByCode("not-a-code"))
        assertTrue(repository.searchByName("does-not-exist").isEmpty())
        assertEquals(100, repository.searchByName("").size)
    }
    @Test fun readFailurePropagatesAndIsNotCachedAsEmptySuccess() = runBlocking {
        var reads = 0
        val repository = CachedFoodRepository(readText = { if (++reads == 1) throw IOException("read failure"); source() })
        fails<FoodDataException> { repository.getAll() }
        assertEquals(100, repository.getAll().size)
        assertEquals(2, reads)
    }
    @Test fun cancellationIsNotConvertedToFoodDataFailure() = runBlocking {
        val repository = CachedFoodRepository(readText = { throw CancellationException("cancelled") })
        fails<CancellationException> { repository.getAll() }
    }
    @Test fun futureDatesEmptyMealsAndDuplicateItemIdsFail() {
        val item = MealItem("rice", 1.0, "row")
        val now = LocalDate.of(2026, 11, 10)
        SaveValidation.meal(meal(listOf(item)), now)
        fails<IllegalArgumentException> { SaveValidation.meal(meal(listOf(item)).copy(date = now.plusDays(1)), now) }
        fails<IllegalArgumentException> { SaveValidation.meal(meal(emptyList()), now) }
        fails<IllegalArgumentException> { SaveValidation.meal(meal(listOf(item, item.copy(amount = 2.0))), now) }
    }
    @Test fun clockUsesLocalDateAtMidnightBoundary() {
        val instant = Instant.parse("2026-11-09T15:10:00Z")
        val source = ClockTimeSource(Clock.fixed(instant, ZoneId.of("Asia/Seoul")))
        assertEquals(LocalDateTime.of(2026, 11, 10, 0, 10), source.now())
        val savedDate = source.now().toLocalDate()
        assertEquals(savedDate, LocalDate.parse(savedDate.toString()))
    }
    @Test fun adultAgeUsesBirthdayAndInvalidBodyCannotOverwrite() {
        val today = LocalDate.of(2026, 11, 10)
        val body = BodySettings(LocalDate.of(2007, 11, 10), Sex.FEMALE, 168.0, 58.0, ActivityLevel.ACTIVE)
        SaveValidation.body(body, today)
        fails<IllegalArgumentException> { SaveValidation.body(body.copy(birthDate = LocalDate.of(2007, 11, 11)), today) }
        fails<IllegalArgumentException> { SaveValidation.body(body.copy(birthDate = today.plusDays(1)), today) }
        for (value in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            fails<IllegalArgumentException> { body.copy(heightCm = value) }
            fails<IllegalArgumentException> { body.copy(weightKg = value) }
        }
    }
    @Test fun nutrientRangeIncludesBothBoundaries() {
        val range = NutrientRange(50.0, 100.0)
        assertTrue(range.contains(50.0))
        assertTrue(range.contains(100.0))
        assertFalse(range.contains(49.9999))
        assertFalse(range.contains(100.0001))
        assertFalse(range.contains(Double.NaN))
    }
}

