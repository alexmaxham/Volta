package com.alexmaxham.volta.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.alexmaxham.volta.core.BenchSegment
import com.alexmaxham.volta.core.CapacityReading
import com.alexmaxham.volta.core.Charger
import com.alexmaxham.volta.core.Conditions
import com.alexmaxham.volta.core.Limits
import com.alexmaxham.volta.core.Reading
import com.alexmaxham.volta.core.ReviewRun
import com.alexmaxham.volta.core.Source

/** Everything Volta stores, kept on this device only. */
class Db private constructor(context: Context) : SQLiteOpenHelper(context, "volta.db", null, 2) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE readings (time INTEGER PRIMARY KEY, level INTEGER NOT NULL, plugged INTEGER NOT NULL, " +
                "charger TEXT, temp REAL, voltage REAL, current_ma INTEGER, counter_mah REAL, elapsed INTEGER, uptime INTEGER, source TEXT)",
        )
        db.execSQL("CREATE TABLE runs (id TEXT PRIMARY KEY, started INTEGER NOT NULL, ended INTEGER, conditions TEXT NOT NULL)")
        db.execSQL(
            "CREATE TABLE run_samples (run_id TEXT NOT NULL REFERENCES runs(id) ON DELETE CASCADE, time INTEGER NOT NULL, " +
                "level INTEGER NOT NULL, plugged INTEGER NOT NULL, temp REAL, voltage REAL, current_ma INTEGER, counter_mah REAL, PRIMARY KEY(run_id, time))",
        )
        createBench(db)
        db.execSQL("CREATE TABLE presets (name TEXT PRIMARY KEY, conditions TEXT NOT NULL, updated INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE capacity (time INTEGER PRIMARY KEY, full_mah REAL NOT NULL, gained INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE cycle_apps (start INTEGER PRIMARY KEY, end INTEGER NOT NULL, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE kv (key TEXT PRIMARY KEY, value TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE run_samples ADD COLUMN counter_mah REAL")
            createBench(db)
        }
    }

    /** App slots of benchmark runs. */
    private fun createBench(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE bench_segments (run_id TEXT NOT NULL REFERENCES runs(id) ON DELETE CASCADE, idx INTEGER NOT NULL, " +
                "round INTEGER NOT NULL, step TEXT NOT NULL, start INTEGER NOT NULL, end INTEGER, relaunches INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(run_id, idx))",
        )
    }

    // Readings

    fun insertReading(r: Reading) {
        val v = ContentValues().apply {
            put("time", r.time)
            put("level", r.level)
            put("plugged", if (r.plugged) 1 else 0)
            put("charger", r.charger.name)
            put("temp", r.tempC)
            put("voltage", r.voltageV)
            put("current_ma", r.currentMa)
            put("counter_mah", r.chargeCounterMah)
            put("elapsed", r.elapsed)
            put("uptime", r.uptime)
            put("source", r.source.name)
        }
        writableDatabase.insertWithOnConflict("readings", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun readings(since: Long): List<Reading> =
        readableDatabase.rawQuery("SELECT * FROM readings WHERE time >= ? ORDER BY time", arrayOf(since.toString())).use { c ->
            buildList { while (c.moveToNext()) add(c.reading()) }
        }

    fun lastReading(): Reading? =
        readableDatabase.rawQuery("SELECT * FROM readings ORDER BY time DESC LIMIT 1", null).use { c ->
            if (c.moveToFirst()) c.reading() else null
        }

    fun lastReadingFrom(source: Source): Reading? =
        readableDatabase.rawQuery("SELECT * FROM readings WHERE source = ? ORDER BY time DESC LIMIT 1", arrayOf(source.name)).use { c ->
            if (c.moveToFirst()) c.reading() else null
        }

    fun prune(now: Long) {
        writableDatabase.delete("readings", "time < ?", arrayOf((now - Limits.RETENTION).toString()))
    }

    fun clearHistory() {
        writableDatabase.delete("readings", null, null)
        writableDatabase.delete("cycle_apps", null, null)
        writableDatabase.delete("kv", "key LIKE 'night_%' OR key = 'capacity_session'", null)
    }

    private fun Cursor.reading() = Reading(
        time = getLong(getColumnIndexOrThrow("time")),
        level = getInt(getColumnIndexOrThrow("level")),
        plugged = getInt(getColumnIndexOrThrow("plugged")) == 1,
        charger = optString("charger")?.let { runCatching { Charger.valueOf(it) }.getOrNull() } ?: Charger.UNKNOWN,
        tempC = optFloat("temp"),
        voltageV = optFloat("voltage"),
        currentMa = optInt("current_ma"),
        chargeCounterMah = optFloat("counter_mah"),
        elapsed = optLong("elapsed"),
        uptime = optLong("uptime"),
        source = optString("source")?.let { runCatching { Source.valueOf(it) }.getOrNull() } ?: Source.APP,
    )

    // Review runs

    fun insertRun(run: ReviewRun) {
        val v = ContentValues().apply {
            put("id", run.id)
            put("started", run.started)
            put("ended", run.ended)
            put("conditions", Json.conditions(run.conditions).toString())
        }
        writableDatabase.insertWithOnConflict("runs", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertRunSamples(runId: String, samples: List<Reading>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (r in samples) {
                val v = ContentValues().apply {
                    put("run_id", runId)
                    put("time", r.time)
                    put("level", r.level)
                    put("plugged", if (r.plugged) 1 else 0)
                    put("temp", r.tempC)
                    put("voltage", r.voltageV)
                    put("current_ma", r.currentMa)
                    put("counter_mah", r.chargeCounterMah)
                }
                db.insertWithOnConflict("run_samples", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun finishRun(id: String, ended: Long) {
        writableDatabase.update("runs", ContentValues().apply { put("ended", ended) }, "id = ? AND ended IS NULL", arrayOf(id))
    }

    fun deleteRun(id: String) {
        writableDatabase.delete("runs", "id = ?", arrayOf(id))
    }

    fun activeRunId(): String? =
        readableDatabase.rawQuery("SELECT id FROM runs WHERE ended IS NULL ORDER BY started DESC LIMIT 1", null).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    /** All runs, newest first. Samples are only loaded when [withSamples] is set. */
    fun runs(withSamples: Boolean): List<ReviewRun> =
        readableDatabase.rawQuery("SELECT * FROM runs ORDER BY started DESC", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getString(c.getColumnIndexOrThrow("id"))
                    add(
                        ReviewRun(
                            id = id,
                            started = c.getLong(c.getColumnIndexOrThrow("started")),
                            ended = c.optLong("ended"),
                            conditions = Json.conditions(c.getString(c.getColumnIndexOrThrow("conditions"))),
                            samples = if (withSamples) runSamples(id) else emptyList(),
                        ),
                    )
                }
            }
        }

    fun run(id: String): ReviewRun? = runs(false).firstOrNull { it.id == id }?.let { it.copy(samples = runSamples(id)) }

    fun runSamples(id: String): List<Reading> =
        readableDatabase.rawQuery("SELECT * FROM run_samples WHERE run_id = ? ORDER BY time", arrayOf(id)).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Reading(
                            time = c.getLong(c.getColumnIndexOrThrow("time")),
                            level = c.getInt(c.getColumnIndexOrThrow("level")),
                            plugged = c.getInt(c.getColumnIndexOrThrow("plugged")) == 1,
                            tempC = c.optFloat("temp"),
                            voltageV = c.optFloat("voltage"),
                            currentMa = c.optInt("current_ma"),
                            chargeCounterMah = c.optFloat("counter_mah"),
                            source = Source.REVIEW,
                        ),
                    )
                }
            }
        }

    /** When the run ended, or null while it's active. Throws [NoSuchElementException] if the run is gone. */
    fun runEnded(id: String): Long? =
        readableDatabase.rawQuery("SELECT ended FROM runs WHERE id = ?", arrayOf(id)).use { c ->
            if (!c.moveToFirst()) throw NoSuchElementException(id)
            if (c.isNull(0)) null else c.getLong(0)
        }

    // Benchmark slots

    fun segments(runId: String): List<BenchSegment> =
        readableDatabase.rawQuery("SELECT * FROM bench_segments WHERE run_id = ? ORDER BY idx", arrayOf(runId)).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        BenchSegment(
                            index = c.getInt(c.getColumnIndexOrThrow("idx")),
                            round = c.getInt(c.getColumnIndexOrThrow("round")),
                            stepId = c.getString(c.getColumnIndexOrThrow("step")),
                            start = c.getLong(c.getColumnIndexOrThrow("start")),
                            end = c.optLong("end"),
                            relaunches = c.getInt(c.getColumnIndexOrThrow("relaunches")),
                        ),
                    )
                }
            }
        }

    fun saveSegment(runId: String, s: BenchSegment) {
        val v = ContentValues().apply {
            put("run_id", runId)
            put("idx", s.index)
            put("round", s.round)
            put("step", s.stepId)
            put("start", s.start)
            put("end", s.end)
            put("relaunches", s.relaunches)
        }
        writableDatabase.insertWithOnConflict("bench_segments", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // Presets

    fun presets(): List<Pair<String, Conditions>> =
        readableDatabase.rawQuery("SELECT name, conditions FROM presets ORDER BY updated DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to Json.conditions(c.getString(1))) }
        }

    fun savePreset(name: String, conditions: Conditions, now: Long) {
        val v = ContentValues().apply {
            put("name", name)
            put("conditions", Json.conditions(conditions).toString())
            put("updated", now)
        }
        writableDatabase.insertWithOnConflict("presets", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deletePreset(name: String) {
        writableDatabase.delete("presets", "name = ?", arrayOf(name))
    }

    // Capacity

    fun capacity(): List<CapacityReading> =
        readableDatabase.rawQuery("SELECT time, full_mah, gained FROM capacity ORDER BY time", null).use { c ->
            buildList { while (c.moveToNext()) add(CapacityReading(c.getLong(0), c.getFloat(1), c.getInt(2))) }
        }

    fun insertCapacity(r: CapacityReading) {
        val v = ContentValues().apply {
            put("time", r.time)
            put("full_mah", r.fullMah)
            put("gained", r.gained)
        }
        writableDatabase.insertWithOnConflict("capacity", null, v, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun clearCapacity() {
        writableDatabase.delete("capacity", null, null)
    }

    // App usage snapshots per battery cycle

    fun cycleApps(): Map<Long, Pair<Long, String>> =
        readableDatabase.rawQuery("SELECT start, end, payload FROM cycle_apps", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getLong(0), c.getLong(1) to c.getString(2)) }
        }

    fun saveCycleApps(start: Long, end: Long, payload: String) {
        val v = ContentValues().apply {
            put("start", start)
            put("end", end)
            put("payload", payload)
        }
        writableDatabase.insertWithOnConflict("cycle_apps", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // Key/value

    fun get(key: String): String? =
        readableDatabase.rawQuery("SELECT value FROM kv WHERE key = ?", arrayOf(key)).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun put(key: String, value: String?) {
        writableDatabase.insertWithOnConflict("kv", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    companion object {
        @Volatile private var instance: Db? = null

        fun get(context: Context): Db = instance ?: synchronized(this) {
            instance ?: Db(context.applicationContext).also { instance = it }
        }
    }
}

private fun Cursor.optString(col: String): String? = getColumnIndex(col).takeIf { it >= 0 && !isNull(it) }?.let { getString(it) }
private fun Cursor.optFloat(col: String): Float? = getColumnIndex(col).takeIf { it >= 0 && !isNull(it) }?.let { getFloat(it) }
private fun Cursor.optInt(col: String): Int? = getColumnIndex(col).takeIf { it >= 0 && !isNull(it) }?.let { getInt(it) }
private fun Cursor.optLong(col: String): Long? = getColumnIndex(col).takeIf { it >= 0 && !isNull(it) }?.let { getLong(it) }
