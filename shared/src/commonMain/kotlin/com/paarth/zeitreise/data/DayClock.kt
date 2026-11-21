package com.paarth.zeitreise.data

import kotlin.time.Clock

private const val MILLIS_PER_DAY = 86_400_000L

/** Tells which day it is. */
public fun interface DayClock {
    /** Days since 1 January 1970 in UTC, so every device agrees on the date. */
    public fun today(): Long
}

/** The device's clock. */
internal object SystemDayClock : DayClock {
    override fun today(): Long = Clock.System.now().toEpochMilliseconds() / MILLIS_PER_DAY
}
