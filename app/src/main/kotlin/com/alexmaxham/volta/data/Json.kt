package com.alexmaxham.volta.data

import com.alexmaxham.volta.core.AppTime
import com.alexmaxham.volta.core.Conditions
import com.alexmaxham.volta.core.Reading
import com.alexmaxham.volta.core.ReviewRun
import com.alexmaxham.volta.core.Source
import com.alexmaxham.volta.core.TestType
import org.json.JSONArray
import org.json.JSONObject

/** JSON for stored conditions, app-usage snapshots and review-run backups. */
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

    fun backup(run: ReviewRun): String = JSONObject().apply {
        put("format", BACKUP_FORMAT)
        put("version", 1)
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
                        },
                    )
                }
            },
        )
    }.toString()

    /** Parses a run backup, or throws [IllegalArgumentException] with a message for the user. */
    fun restore(text: String): ReviewRun {
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
                source = Source.REVIEW,
            )
        }
        require(samples.zipWithNext().all { (a, b) -> b.time > a.time }) { "Duplicate sample timestamps." }
        return ReviewRun(
            id = o.getString("id"),
            started = o.getLong("started"),
            ended = o.getLong("ended"),
            conditions = conditions(o.getJSONObject("conditions")),
            samples = samples,
        )
    }
}
