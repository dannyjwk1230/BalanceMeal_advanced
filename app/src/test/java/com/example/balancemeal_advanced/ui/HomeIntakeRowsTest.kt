package com.example.balancemeal_advanced.ui

import com.example.balancemeal_advanced.core.model.NutrientAmounts
import com.example.balancemeal_advanced.core.nutrition.EnergyTargets
import com.example.balancemeal_advanced.core.nutrition.IntakeStatus
import com.example.balancemeal_advanced.ui.home.HomeUiState
import com.example.balancemeal_advanced.ui.home.homeIntakeRows
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HomeIntakeRowsTest {
    private val targets = EnergyTargets.fromEnergy(2000.0)
    private fun state(total: NutrientAmounts?) = HomeUiState(
        date = LocalDate.of(2026, 11, 11), loading = false, total = total, targets = targets)

    @Test fun rangeBoundsAreInclusiveForAllFourRows() {
        val reference = homeIntakeRows(state(NutrientAmounts.ZERO))
        for (index in reference.indices) {
            val range = checkNotNull(reference[index].range)
            for ((value, expected) in listOf(
                Math.nextDown(range.lowerG) to IntakeStatus.LOW,
                range.lowerG to IntakeStatus.ADEQUATE,
                range.upperG to IntakeStatus.ADEQUATE,
                Math.nextUp(range.upperG) to IntakeStatus.HIGH,
            )) {
                val values = DoubleArray(4).also { it[index] = value }
                val rows = homeIntakeRows(state(NutrientAmounts(values[0], values[1], values[2], values[3])))
                assertEquals("row $index at $value", expected, rows[index].status)
            }
        }
        assertEquals(1700.0, reference.last().range!!.lowerG, 0.0)
        assertEquals(2300.0, reference.last().range!!.upperG, 0.0)
    }

    @Test fun noBodyPreservesAbsoluteIntakeWithoutInventingRangesOrStatuses() {
        val rows = homeIntakeRows(state(NutrientAmounts(120.0, 50.0, 30.0, 1500.0)).copy(targets = null))
        assertEquals(listOf(120.0, 50.0, 30.0, 1500.0), rows.map { it.current })
        assertTrue(rows.all { it.range == null && it.status == null })
        assertTrue(rows.all { it.scale > 0 && it.scale >= it.current!! })
    }

    @Test fun unknownLoadingAndErrorsDoNotBecomeZeroOrDeficient() {
        for (s in listOf(state(null),
            state(NutrientAmounts.ZERO).copy(loading = true),
            state(NutrientAmounts.ZERO).copy(error = "read failed"))) {
            val rows = homeIntakeRows(s)
            assertTrue(rows.all { it.current == null && it.status == null })
            assertTrue(rows.all { it.range != null })
        }
    }

    @Test fun fixedScalesSaturateAtLimitAndPreserveActualIntake() {
        for (target in listOf(null, targets)) {
            for ((total, expectedPosition) in listOf(
                NutrientAmounts.ZERO to 0f,
                NutrientAmounts(250.0,250.0,250.0,2000.0) to .5f,
                NutrientAmounts(500.0,500.0,500.0,4000.0) to 1f,
                NutrientAmounts(500.1,500.1,500.1,4000.1) to 1f,
                NutrientAmounts(10000.0,10000.0,10000.0,50000.0) to 1f,
            )) {
                val rows=homeIntakeRows(state(total).copy(targets=target))
                assertEquals(listOf(500.0,500.0,500.0,4000.0),rows.map {it.scale})
                assertEquals(listOf(total.carbohydrateG,total.proteinG,total.fatG,total.energyKcal),rows.map {it.current})
                rows.forEach {row -> assertEquals(expectedPosition,row.position(row.current!!),0f)}
            }
        }
    }

    @Test fun recommendedRangesOutsideDisplayScaleAreClippedWithoutChangingJudgment() {
        val highTargets=EnergyTargets.fromEnergy(10000.0)
        val rows=homeIntakeRows(state(NutrientAmounts(1300.0,300.0,200.0,10000.0)).copy(targets=highTargets))
        assertTrue(rows.first().range!!.lowerG > rows.first().scale)
        assertEquals(1f,rows.first().position(rows.first().range!!.lowerG),0f)
        assertEquals(1f,rows.first().position(rows.first().range!!.upperG),0f)
        assertEquals(IntakeStatus.ADEQUATE,rows.first().status)
        assertEquals(1f,rows.last().position(rows.last().current!!),0f)
        assertEquals(IntakeStatus.ADEQUATE,rows.last().status)
    }
}
