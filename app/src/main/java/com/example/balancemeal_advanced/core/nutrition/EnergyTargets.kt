package com.example.balancemeal_advanced.core.nutrition

import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.core.validation.SaveValidation
import java.time.LocalDate
import java.time.Period

data class EnergyTargets(val energyKcal: Double, val carbohydrate: NutrientRange, val protein: NutrientRange, val fat: NutrientRange) {
    companion object {
        fun age(birth: LocalDate, today: LocalDate): Int {
            require(!birth.isAfter(today))
            return Period.between(birth, today).years
        }
        fun pa(sex: Sex, level: ActivityLevel): Double = when (level) {
            ActivityLevel.INACTIVE -> 1.0
            ActivityLevel.LOW_ACTIVE -> if (sex == Sex.MALE) 1.11 else 1.12
            ActivityLevel.ACTIVE -> if (sex == Sex.MALE) 1.25 else 1.27
            ActivityLevel.VERY_ACTIVE -> if (sex == Sex.MALE) 1.48 else 1.45
        }
        fun calculate(body: BodySettings, today: LocalDate): EnergyTargets {
            SaveValidation.body(body, today)
            val age = age(body.birthDate, today)
            val m = body.heightCm / 100.0
            val energy = if (body.sex == Sex.MALE) 662 - 9.53 * age + pa(body.sex, body.activityLevel) * (15.91 * body.weightKg + 539.6 * m)
                else 354 - 6.91 * age + pa(body.sex, body.activityLevel) * (9.36 * body.weightKg + 726 * m)
            return fromEnergy(energy)
        }
        fun fromEnergy(energy: Double): EnergyTargets {
            require(energy.isFinite() && energy > 0)
            return EnergyTargets(energy, NutrientRange(energy*.50/4, energy*.65/4), NutrientRange(energy*.10/4, energy*.20/4), NutrientRange(energy*.15/9, energy*.30/9))
        }
    }
}
