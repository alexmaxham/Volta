package com.alexmaxham.volta.core

import java.util.Locale
import kotlin.math.abs

/** One app in the benchmark loop. [link] is content to open, such as a video or playlist, where the app takes one. */
data class BenchStep(val id: String, val name: String, val minutes: Int = 60, val link: String = "")

/** The apps a benchmark run loops through, in order, and the level it stops at. */
data class BenchPlan(val runId: String, val steps: List<BenchStep>, val stopLevel: Int = Benchmark.STOP_LEVEL)

/** Where the loop is: the [index]th app slot overall, in [round] (from 0), and when that slot starts and ends, relative to the run start. */
data class BenchPosition(val index: Int, val round: Int, val step: BenchStep, val start: Long, val end: Long)

/**
 * One app slot as it actually ran, in [round] (from 0). [relaunches] counts the times the app was
 * brought back after something else took over.
 */
data class BenchSegment(val index: Int, val round: Int, val stepId: String, val start: Long, val end: Long?, val relaunches: Int = 0)

data class SegmentResult(
    val segment: BenchSegment,
    val name: String,
    val round: Int,
    val duration: Long,
    val startLevel: Int?,
    val endLevel: Int?,
    /** Battery points used, start minus end level. */
    val used: Int?,
    /** Charge used, from the charge counter where the phone reports it, otherwise from current. */
    val mah: Float?,
    val avgPowerW: Float?,
    val avgTempC: Float?,
    val peakTempC: Float?,
) {
    val hours: Double get() = duration.toDouble() / Time.HOUR
    val perHour: Double? get() = used?.takeIf { duration >= 10 * Time.MINUTE }?.let { it / hours }
    val mahPerHour: Double? get() = mah?.takeIf { duration >= 10 * Time.MINUTE }?.let { it / hours }
}

/** One app across every round it ran in. */
data class AppResult(
    val stepId: String,
    val name: String,
    val slots: Int,
    val duration: Long,
    val used: Int,
    val mah: Float?,
    val avgPowerW: Float?,
    val avgTempC: Float?,
    val peakTempC: Float?,
    val relaunches: Int,
) {
    val perHour: Double? get() = if (duration >= 10 * Time.MINUTE) used * Time.HOUR.toDouble() / duration else null
    val mahPerHour: Double? get() = mah?.takeIf { duration >= 10 * Time.MINUTE }?.let { it * Time.HOUR.toDouble() / duration }
}

data class BenchReport(
    val elapsed: Long,
    val startLevel: Int?,
    val endLevel: Int?,
    /** Rounds started; the last one may be partial. */
    val rounds: Int,
    val apps: List<AppResult>,
    val segments: List<SegmentResult>,
    val avgTempC: Float?,
    val peakTempC: Float?,
)

object Benchmark {
    const val PROTOCOL = "App benchmark"

    /** The run ends when the battery reaches this level, before the phone shuts itself down. */
    const val STOP_LEVEL = 5

    /** The default loop: an hour in each app, in this order, then round again. */
    val DEFAULT = listOf(
        BenchStep("chrome", "Chrome"),
        BenchStep("subway", "Subway Surfers"),
        BenchStep("x", "X (Twitter)"),
        BenchStep("camera", "Camera 4K60"),
        BenchStep("tiktok", "TikTok"),
        BenchStep("youtube", "YouTube", link = "https://www.youtube.com/watch?v=u-dEnJpCGAQ"),
        BenchStep("instagram", "Instagram"),
        BenchStep("ytmusic", "YouTube Music", link = "https://music.youtube.com/playlist?list=RDCLAK5uy_mplKe9BIYCO3ZuNWSHZr48bm9DUDzbWnE&playnext=1"),
    )

    fun name(stepId: String): String = DEFAULT.firstOrNull { it.id == stepId }?.name ?: stepId

    /** The link a step plays unless the user picks another, or "" for steps without one. */
    fun defaultLink(stepId: String): String = DEFAULT.firstOrNull { it.id == stepId }?.link.orEmpty()

    /** The slot running [elapsed] ms after the start, or null with no steps. */
    fun position(steps: List<BenchStep>, elapsed: Long): BenchPosition? {
        if (steps.isEmpty()) return null
        val lengths = steps.map { it.minutes.coerceAtLeast(1) * Time.MINUTE }
        val cycle = lengths.sum()
        val t = elapsed.coerceAtLeast(0)
        val round = (t / cycle).toInt()
        var start = round * cycle
        for ((i, len) in lengths.withIndex()) {
            if (t < start + len) return BenchPosition(round * steps.size + i, round, steps[i], start, start + len)
            start += len
        }
        error("unreachable")
    }

    fun report(run: ReviewRun, segments: List<BenchSegment>, now: Long): BenchReport {
        val samples = run.samples.sortedBy { it.time }
        val runEnd = run.ended ?: now
        val results = segments.sortedBy { it.index }.map { seg ->
            segment(seg, samples, (seg.end ?: runEnd).coerceAtLeast(seg.start))
        }
        val apps = results.groupBy { it.segment.stepId }.map { (id, rs) ->
            val time = rs.sumOf { it.duration }
            AppResult(
                stepId = id,
                name = name(id),
                slots = rs.size,
                duration = time,
                used = rs.sumOf { it.used ?: 0 },
                mah = if (rs.all { it.mah != null }) rs.sumOf { it.mah!!.toDouble() }.toFloat() else null,
                avgPowerW = weighted(rs) { it.avgPowerW },
                avgTempC = weighted(rs) { it.avgTempC },
                peakTempC = rs.mapNotNull { it.peakTempC }.maxOrNull(),
                relaunches = rs.sumOf { it.segment.relaunches },
            )
        }
        val temps = samples.mapNotNull { it.tempC }
        return BenchReport(
            elapsed = runEnd - run.started,
            startLevel = samples.firstOrNull()?.level,
            endLevel = samples.lastOrNull()?.level,
            rounds = segments.maxOfOrNull { it.round + 1 } ?: 0,
            apps = apps,
            segments = results,
            avgTempC = temps.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            peakTempC = temps.maxOrNull(),
        )
    }

    private fun segment(seg: BenchSegment, s: List<Reading>, end: Long): SegmentResult {
        val first = s.lastOrNull { it.time <= seg.start } ?: s.firstOrNull { it.time in seg.start..end }
        val last = s.lastOrNull { it.time <= end }?.takeIf { first != null && it.time >= first.time }
        val inside = s.filter { it.time in seg.start..end }
        val temps = inside.mapNotNull { it.tempC }
        val powers = inside.mapNotNull { it.powerW }.map { abs(it) }
        return SegmentResult(
            segment = seg,
            name = name(seg.stepId),
            round = seg.round,
            duration = end - seg.start,
            startLevel = first?.level,
            endLevel = last?.level,
            used = if (first != null && last != null) first.level - last.level else null,
            mah = charge(first, last) ?: integrate(s, seg.start, end),
            avgPowerW = powers.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            avgTempC = temps.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            peakTempC = temps.maxOrNull(),
        )
    }

    /** Charge used between two readings from the charge counter, when it moved the right way. */
    private fun charge(a: Reading?, b: Reading?): Float? {
        val from = a?.chargeCounterMah ?: return null
        val to = b?.chargeCounterMah ?: return null
        return (from - to).takeIf { it > 0f }
    }

    /** Charge used from [from] to [to] by adding up current between readings no more than a minute apart. */
    private fun integrate(s: List<Reading>, from: Long, to: Long): Float? {
        var mah = 0.0
        var covered = 0L
        for ((a, b) in s.zipWithNext()) {
            if (b.time - a.time > Time.MINUTE || a.plugged || b.plugged) continue
            val ca = a.currentMa ?: continue
            val cb = b.currentMa ?: continue
            val overlap = minOf(b.time, to) - maxOf(a.time, from)
            if (overlap <= 0) continue
            mah += (abs(ca) + abs(cb)) / 2.0 * overlap / Time.HOUR
            covered += overlap
        }
        // Too little coverage would understate the slot.
        return if (to > from && covered >= (to - from) * 0.8) mah.toFloat() else null
    }

    private fun weighted(rs: List<SegmentResult>, value: (SegmentResult) -> Float?): Float? {
        val with = rs.filter { value(it) != null && it.duration > 0 }
        val total = with.sumOf { it.duration }
        if (total <= 0) return null
        return (with.sumOf { value(it)!!.toDouble() * it.duration } / total).toFloat()
    }

    /** A plain-text summary to paste into a message or post. */
    fun text(run: ReviewRun, report: BenchReport, fahrenheit: Boolean): String = buildString {
        val c = run.conditions
        appendLine("Volta app benchmark · ${c.phone}")
        if (c.software.isNotBlank()) appendLine(c.software)
        appendLine(listOf(c.brightness, c.refreshRate, c.network).filter { it.isNotBlank() }.joinToString(" · "))
        appendLine()
        val levels = if (report.startLevel != null) " (${report.startLevel}% → ${report.endLevel}%)" else ""
        appendLine((if (run.active) "Running for " else "Lasted ") + Format.duration(report.elapsed) + levels)
        report.peakTempC?.let { appendLine("Peak battery temperature ${Format.temp(it, fahrenheit)}") }
        appendLine()
        for (a in report.apps) {
            val parts = listOfNotNull(
                a.perHour?.let { Format.rate(it) },
                a.mahPerHour?.let { Format.mah(it.toFloat()) + "/h" },
                a.avgPowerW?.let { Format.watts(it) },
                a.peakTempC?.let { "peak " + Format.temp(it, fahrenheit) },
            )
            appendLine("${a.name}: " + parts.ifEmpty { listOf("under 10 min") }.joinToString(" · "))
        }
        appendLine()
        append("Recorded with Volta. Battery temperature, not surface temperature.")
    }

    /** Per-app slots as CSV, one row per slot in the order they ran. */
    fun csv(run: ReviewRun, report: BenchReport): String = buildString {
        appendLine("# Volta app benchmark")
        appendLine("# phone,${Csv.cell(run.conditions.phone)}")
        appendLine("# software,${Csv.cell(run.conditions.software)}")
        appendLine("# brightness,${Csv.cell(run.conditions.brightness)}")
        appendLine("# notes,${Csv.cell(run.conditions.notes)}")
        appendLine("slot,round,app,start_ms,minutes,start_level,end_level,points_used,points_per_hour,mah_used,mah_per_hour,avg_power_w,avg_temp_c,peak_temp_c,relaunches")
        for (r in report.segments) {
            appendLine(
                listOf(
                    (r.segment.index + 1).toString(),
                    (r.round + 1).toString(),
                    Csv.cell(r.name),
                    r.segment.start.toString(),
                    "%.1f".format(Locale.US, r.duration.toDouble() / Time.MINUTE),
                    r.startLevel?.toString() ?: "",
                    r.endLevel?.toString() ?: "",
                    r.used?.toString() ?: "",
                    r.perHour?.let { "%.2f".format(Locale.US, it) } ?: "",
                    r.mah?.let { "%.0f".format(Locale.US, it) } ?: "",
                    r.mahPerHour?.let { "%.0f".format(Locale.US, it) } ?: "",
                    r.avgPowerW?.let { "%.2f".format(Locale.US, it) } ?: "",
                    r.avgTempC?.let { "%.1f".format(Locale.US, it) } ?: "",
                    r.peakTempC?.let { "%.1f".format(Locale.US, it) } ?: "",
                    r.segment.relaunches.toString(),
                ).joinToString(","),
            )
        }
    }
}
