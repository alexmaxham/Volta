package com.alexmaxham.volta.core


enum class TestType { DRAIN, CHARGE }

/** Conditions for a review run, entered by the reviewer. */
data class Conditions(
    val phone: String = "",
    val software: String = "",
    val protocol: String = "",
    val type: TestType = TestType.DRAIN,
    val brightness: String = "",
    val refreshRate: String = "",
    val resolution: String = "",
    val network: String = "",
    val performance: String = "",
    val charger: String = "",
    val notes: String = "",
    /** True when the run records every 15 seconds with a visible notification. */
    val reviewRecording: Boolean = true,
) {
    /** Runs with the same key are comparable. Notes and software can still differ. */
    val compareKey: String
        get() = listOf(protocol, type.name, brightness, refreshRate, resolution, network, performance, charger, reviewRecording.toString())
            .joinToString("|") { it.trim().lowercase() }
}

data class ReviewRun(
    val id: String,
    val started: Long,
    val ended: Long?,
    val conditions: Conditions,
    val samples: List<Reading>,
) {
    val active: Boolean get() = ended == null
}

data class ReviewSummary(
    val elapsed: Long,
    val startLevel: Int?,
    val endLevel: Int?,
    val ratePerHour: Double?,
    val avgTempC: Float?,
    val peakTempC: Float?,
    val avgPowerW: Float?,
    val peakPowerW: Float?,
    val to50: Long?,
    val to80: Long?,
    val to100: Long?,
    val sampleCount: Int,
    /** Share of the run covered by samples no more than one minute apart. */
    val coverage: Double,
)

data class CurvePoint(val minutes: Double, val level: Int)

object Review {
    const val INTERVAL = 15 * Time.SECOND

    fun summary(run: ReviewRun, now: Long): ReviewSummary {
        val s = run.samples.sortedBy { it.time }
        val end = run.ended ?: now
        val first = s.firstOrNull()
        val last = s.lastOrNull()
        val span = if (first != null && last != null) last.time - first.time else 0L
        val change = if (first != null && last != null) last.level - first.level else 0
        val rate = if (span >= 10 * Time.MINUTE) {
            val pts = if (run.conditions.type == TestType.DRAIN) -change else change
            pts * Time.HOUR.toDouble() / span
        } else null
        val temps = s.mapNotNull { it.tempC }
        val powers = s.mapNotNull { it.powerW }.map { kotlin.math.abs(it) }
        fun reach(target: Int): Long? =
            if (run.conditions.type != TestType.CHARGE || first == null || first.level >= target) null
            else s.firstOrNull { it.level >= target }?.let { it.time - first.time }
        var covered = 0L
        for ((a, b) in s.zipWithNext()) if (b.time - a.time <= Time.MINUTE) covered += b.time - a.time
        return ReviewSummary(
            elapsed = end - run.started,
            startLevel = first?.level,
            endLevel = last?.level,
            ratePerHour = rate,
            avgTempC = temps.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            peakTempC = temps.maxOrNull(),
            avgPowerW = powers.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            peakPowerW = powers.maxOrNull(),
            to50 = reach(50),
            to80 = reach(80),
            to100 = reach(100),
            sampleCount = s.size,
            coverage = if (end > run.started) covered.toDouble() / (end - run.started) else 0.0,
        )
    }

    /** Battery level against minutes since the run started, for comparison charts. */
    fun curve(run: ReviewRun): List<CurvePoint> =
        run.samples.sortedBy { it.time }.map { CurvePoint((it.time - run.started).toDouble() / Time.MINUTE, it.level) }

    /** Finished runs comparable with [run], including it, newest first. */
    fun matching(run: ReviewRun, all: List<ReviewRun>): List<ReviewRun> =
        all.filter { !it.active && it.conditions.compareKey == run.conditions.compareKey }.sortedByDescending { it.started }

    fun csv(run: ReviewRun): String = buildString {
        val c = run.conditions
        appendLine("# Volta review run")
        appendLine("# phone,${Csv.cell(c.phone)}")
        appendLine("# software,${Csv.cell(c.software)}")
        appendLine("# protocol,${Csv.cell(c.protocol)}")
        appendLine("# type,${c.type.name.lowercase()}")
        appendLine("# brightness,${Csv.cell(c.brightness)}")
        appendLine("# refresh_rate,${Csv.cell(c.refreshRate)}")
        appendLine("# resolution,${Csv.cell(c.resolution)}")
        appendLine("# network,${Csv.cell(c.network)}")
        appendLine("# performance,${Csv.cell(c.performance)}")
        appendLine("# charger,${Csv.cell(c.charger)}")
        appendLine("# notes,${Csv.cell(c.notes)}")
        appendLine("time_ms,elapsed_min,level,plugged,temp_c,voltage_v,current_ma,power_w")
        for (r in run.samples.sortedBy { it.time }) {
            appendLine(
                listOf(
                    r.time.toString(),
                    fixed((r.time - run.started).toDouble() / Time.MINUTE, 2),
                    r.level.toString(),
                    if (r.plugged) "1" else "0",
                    r.tempC?.let { fixed(it, 1) } ?: "",
                    r.voltageV?.let { fixed(it, 3) } ?: "",
                    r.currentMa?.toString() ?: "",
                    r.powerW?.let { fixed(it, 2) } ?: "",
                ).joinToString(","),
            )
        }
    }
}

object Csv {
    /** Quotes a value when it contains a comma, quote or newline, and defuses spreadsheet formulas. */
    fun cell(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in "=+-@") "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
