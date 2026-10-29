package com.alexmaxham.volta.bench

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.alexmaxham.volta.core.BenchPlan
import com.alexmaxham.volta.core.BenchStep
import com.alexmaxham.volta.core.Benchmark
import com.alexmaxham.volta.data.Db
import com.alexmaxham.volta.data.Json
import org.json.JSONObject

/** The benchmark's stored state: the plan of the run in progress and the links chosen for each app. */
object Bench {
    private const val PLAN = "bench_plan"
    private const val LINKS = "bench_links"

    /** The app the runner is in, for the recording notification. */
    const val NOW = "bench_now"

    fun plan(context: Context): BenchPlan? = Db.get(context).get(PLAN)?.let { runCatching { Json.plan(it) }.getOrNull() }

    fun save(context: Context, plan: BenchPlan) = Db.get(context).put(PLAN, Json.plan(plan))

    fun clear(context: Context) {
        Db.get(context).put(PLAN, null)
        Db.get(context).put(NOW, null)
    }

    private fun links(context: Context): JSONObject =
        Db.get(context).get(LINKS)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()

    /** Saves the link a step plays. An empty link goes back to the default. */
    fun setLink(context: Context, stepId: String, link: String) {
        Db.get(context).put(LINKS, links(context).put(stepId, link.trim()).toString())
    }

    /** The default loop, with any links chosen on this phone in place of the defaults. */
    fun steps(context: Context): List<BenchStep> {
        val l = links(context)
        return Benchmark.DEFAULT.map { st -> l.optString(st.id).takeIf { it.isNotBlank() }?.let { st.copy(link = it) } ?: st }
    }

    /** Whether Volta's benchmark runner is turned on in Accessibility settings. */
    fun enabled(context: Context): Boolean {
        val me = ComponentName(context, BenchService::class.java)
        val on = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return on.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** True when adaptive brightness is on, false when off, null if the phone doesn't say. */
    fun autoBrightness(context: Context): Boolean? = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }.getOrNull()
}
