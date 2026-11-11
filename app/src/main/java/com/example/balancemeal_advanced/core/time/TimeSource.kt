package com.example.balancemeal_advanced.core.time

import java.time.Clock
import java.time.LocalDateTime

fun interface TimeSource {
    fun now(): LocalDateTime
}

/** An injected clock is stable; default mode follows changes to the device's current time zone. */
class ClockTimeSource(private val clock: Clock? = null) : TimeSource {
    override fun now(): LocalDateTime = LocalDateTime.now(clock ?: Clock.systemDefaultZone())
}

