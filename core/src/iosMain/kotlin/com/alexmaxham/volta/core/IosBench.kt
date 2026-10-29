package com.alexmaxham.volta.core

/**
 * Builders for the iOS benchmark. Swift can't use Kotlin's default arguments and sees nullable
 * numbers as boxed objects, so these take plain values: a negative end means still running.
 */
object IosBench {
    /** An iPhone reading: level and charger only, as iOS reports nothing else. */
    fun reading(time: Long, level: Int, plugged: Boolean): Reading =
        Reading(time = time, level = level, plugged = plugged, source = Source.REVIEW)

    fun conditions(phone: String, software: String, brightness: String, network: String, notes: String): Conditions =
        Conditions(
            phone = phone,
            software = software,
            protocol = Benchmark.PROTOCOL,
            brightness = brightness,
            network = network,
            notes = notes,
        )

    fun reviewRun(id: String, started: Long, ended: Long, conditions: Conditions, samples: List<Reading>): ReviewRun =
        ReviewRun(id, started, ended.takeIf { it >= 0 }, conditions, samples)

    fun segment(index: Int, round: Int, stepId: String, start: Long, end: Long, relaunches: Int): BenchSegment =
        BenchSegment(index, round, stepId, start, end.takeIf { it >= 0 }, relaunches)

    /** Points used per hour, or -1 under ten minutes. */
    fun appPerHour(result: AppResult): Double = result.perHour ?: -1.0

    fun slotPerHour(result: SegmentResult): Double = result.perHour ?: -1.0

    /** Points used, or -1 without readings at both ends. */
    fun slotUsed(result: SegmentResult): Int = result.used ?: -1
}
