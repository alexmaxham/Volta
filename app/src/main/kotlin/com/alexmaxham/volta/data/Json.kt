package com.alexmaxham.volta.data

import com.alexmaxham.volta.core.AppTime
import com.alexmaxham.volta.core.BenchPlan
import com.alexmaxham.volta.core.BenchSegment
import com.alexmaxham.volta.core.BenchStep
import com.alexmaxham.volta.core.Benchmark
import com.alexmaxham.volta.core.Conditions
import com.alexmaxham.volta.core.Reading
import com.alexmaxham.volta.core.ReviewRun
import com.alexmaxham.volta.core.Source
import com.alexmaxham.volta.core.TestType
import org.json.JSONArray
import org.json.JSONObject

/** JSON for stored conditions, app-usage snapshots, benchmark plans and review-run backups. */
object Json {
    const val BACKUP_FORMAT = "volta-review-run"
    const val MAX_BACKUP_BYTES = 20 * 1024 * 1024
    const val MAX_BACKUP_SAMPLES = 200_000

    fun conditions(c: Conditions): JSONObject = JSONObject().apply {
        put("phone", c.phone)
        put("software", c.software)
        put("protocol", c.protocol)
        put("type", c.type.name)
        put("brightness", c.brightness)
        put("refreshRate", c.refreshRate)
        put("resolution", c.resolution)
        put("network", c.network)
        put("performance", c.performance)
        put("charger", c.charger)
        put("notes", c.notes)
        put("reviewRecording", c.reviewRecording)
    }

    fun conditions(s: String): Conditions = conditions(JSONObject(s))

    fun conditions(o: JSONObject): Conditions = Conditions(
        phone = o.optString("phone"),
        software = o.optString("software"),
        protocol = o.optString("protocol"),
        type = runCatching { TestType.valueOf(o.optString("type")) }.getOrDefault(TestType.DRAIN),
        brightness = o.optString("brightness"),
        refreshRate = o.optString("refreshRate"),
        resolution = o.optString("resolution"),
        network = o.optString("network"),
        performance = o.optString("performance"),
        charger = o.optString("charger"),
        notes = o.optString("notes"),
        reviewRecording = o.optBoolean("reviewRecording", true),
    )

    fun apps(list: List<AppTime>): String = JSONArray().apply {
        for (a in list) put(JSONObject().put("pkg", a.pkg).put("ms", a.foreground).put("opens", a.opens))
    }.toString()

    fun apps(s: String): List<AppTime> {
        val a = JSONArray(s)
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            AppTime(o.getString("pkg"), o.getLong("ms"), o.optInt("opens"))
        }
    }

    fun plan(p: BenchPlan): String = JSONObject().apply {
        put("runId", p.runId)
        put("stopLevel", p.stopLevel)
        put("steps", steps(p.steps))
    }.toString()

    fun plan(s: String): BenchPlan {
        val o = JSONObject(s)
        return BenchPlan(o.getString("runId"), steps(o.getJSONArray("steps")), o.optInt("stopLevel", Benchmark.STOP_LEVEL))
    }

    fun steps(list: List<BenchStep>): JSONArray = JSONArray().apply {
        for (st in list) put(JSONObject().put("id", st.id).put("name", st.name).put("minutes", st.minutes).put("link", st.link))
    }

    fun steps(a: JSONArray): List<BenchStep> = (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        BenchStep(o.getString("id"), o.optString("name", Benchmark.name(o.getString("id"))), o.optInt("minutes", 60), o.optString("link"))
    }

    fun backup(run: ReviewRun, segments: List<BenchSegment> = emptyList()): String = JSONObject().apply {
        put("format", BACKUP_FORMAT)
        put("version", 2)
        put("id", run.id)
        put("started", run.started)
        put("ended", run.ended)
        put("conditions", conditions(run.conditions))
        put(
            "samples",
            JSONArray().apply {
                for (r in run.samples) {
                    put(
                        JSONArray().apply {
                            put(r.time)
                            put(r.level)
                            put(if (r.plugged) 1 else 0)
                            put(r.tempC?.toDouble() ?: JSONObject.NULL)
                            put(r.voltageV?.toDouble() ?: JSONObject.NULL)
                            put(r.currentMa ?: JSONObject.NULL)
                            put(r.chargeCounterMah?.toDouble() ?: JSONObject.NULL)
                        },
                    )
                }
            },
        )
        if (segments.isNotEmpty()) {
            put(
                "segments",
                JSONArray().apply {
                    for (sg in segments) {
                        put(
                            JSONObject().put("index", sg.index).put("round", sg.round).put("step", sg.stepId)
                                .put("start", sg.start).put("end", sg.end ?: JSONObject.NULL).put("relaunches", sg.relaunches),
                        )
                    }
                },
            )
        }
    }.toString()

    /** Parses a run backup and any benchmark slots, or throws [IllegalArgumentException] with a message for the user. */
    fun restore(text: String): Pair<ReviewRun, List<BenchSegment>> {
        require(text.length <= MAX_BACKUP_BYTES) { "Backup is larger than 20 MB." }
        val o = runCatching { JSONObject(text) }.getOrElse { throw IllegalArgumentException("Couldn't read the selected backup.") }
        require(o.optString("format") == BACKUP_FORMAT) { "This file isn't a Volta review run." }
        require(!o.isNull("ended")) { "Only finished runs can be imported." }
        val arr = o.getJSONArray("samples")
        require(arr.length() <= MAX_BACKUP_SAMPLES) { "Too many readings in this backup." }
        val samples = (0 until arr.length()).map { i ->
            val s = arr.getJSONArray(i)
            Reading(
                time = s.getLong(0),
                level = s.getInt(1).coerceIn(0, 100),
                plugged = s.getInt(2) == 1,
                tempC = if (s.isNull(3)) null else s.getDouble(3).toFloat(),
                voltageV = if (s.isNull(4)) null else s.getDouble(4).toFloat(),
                currentMa = if (s.isNull(5)) null else s.getInt(5),
                chargeCounterMah = if (s.length() < 7 || s.isNull(6)) null else s.getDouble(6).toFloat(),
                source = Source.REVIEW,
            )
        }
        require(samples.zipWithNext().all { (a, b) -> b.time > a.time }) { "Duplicate sample timestamps." }
        val segs = o.optJSONArray("segments")?.let { a ->
            require(a.length() <= 10_000) { "Too many benchmark slots in this backup." }
            (0 until a.length()).map { i ->
                val g = a.getJSONObject(i)
                BenchSegment(
                    index = g.getInt("index"),
                    round = g.getInt("round"),
                    stepId = g.getString("step").take(40),
                    start = g.getLong("start"),
                    end = if (g.isNull("end")) null else g.getLong("end"),
                    relaunches = g.optInt("relaunches"),
                )
            }
        }.orEmpty()
        val run = ReviewRun(
            id = o.getString("id"),
            started = o.getLong("started"),
            ended = o.getLong("ended"),
            conditions = conditions(o.getJSONObject("conditions")),
            samples = samples,
        )
        return run to segs
    }
}
