package com.alexmaxham.volta.core

data class AppTime(val pkg: String, val foreground: Long, val opens: Int)

object Apps {
    /**
     * Foreground time per app in [from, to), longest first. An app still in front at the end of
     * the window is counted up to [to]; one already in front at the start is counted from [from]
     * if its first event in the window is it leaving.
     */
    fun usage(events: List<AppEvent>, from: Long, to: Long): List<AppTime> {
        val (spans, opens) = foreground(events, from, to)
        return spans.mapValues { (_, list) -> list.sumOf { it.length } }
            .filterValues { it > 0 }
            .map { (pkg, t) -> AppTime(pkg, t, opens[pkg] ?: 0) }
            .sortedByDescending { it.foreground }
    }

    /**
     * Estimated battery points each app used in [from, to). Android doesn't share per-app battery
     * use with other apps, so this splits each drop between two readings across the apps that
     * were in front during it, by how long each was in front. Drain while no app was in front
     * (screen off, background work) isn't given to any app, and two apps open for the same time
     * get the same share however hard they worked the phone.
     */
    fun drain(events: List<AppEvent>, readings: List<Reading>, from: Long, to: Long): Map<String, Double> {
        val spans = foreground(events, from, to).first
        val out = HashMap<String, Double>()
        for ((a, b) in readings.sortedBy { it.time }.zipWithNext()) {
            val dt = b.time - a.time
            val drop = a.level - b.level
            if (dt <= 0 || dt > Limits.GAP || drop <= 0 || a.plugged || b.plugged) continue
            if (b.time <= from || a.time >= to) continue
            for ((pkg, list) in spans) {
                val inFront = list.sumOf { it.overlap(a.time, b.time) }
                if (inFront > 0) out[pkg] = (out[pkg] ?: 0.0) + drop * inFront.toDouble() / dt
            }
        }
        return out
    }

    /** Each app's stretches in front within [from, to), and how many times it was opened. */
    private fun foreground(events: List<AppEvent>, from: Long, to: Long): Pair<Map<String, List<Interval>>, Map<String, Int>> {
        val sorted = events.filter { it.time < to }.sortedBy { it.time }
        val openSince = HashMap<String, Long>()
        val spans = HashMap<String, MutableList<Interval>>()
        val opens = HashMap<String, Int>()
        val seen = HashSet<String>()

        for (e in sorted) {
            if (e.time < from) {
                if (e.resumed) openSince[e.pkg] = from else openSince.remove(e.pkg)
                seen += e.pkg
                continue
            }
            if (e.resumed) {
                if (e.pkg !in openSince) {
                    openSince[e.pkg] = e.time
                    opens[e.pkg] = (opens[e.pkg] ?: 0) + 1
                }
            } else {
                val since = openSince.remove(e.pkg) ?: if (e.pkg !in seen) from else null
                if (since != null) spans.getOrPut(e.pkg) { mutableListOf() } += Interval(since, e.time)
            }
            seen += e.pkg
        }
        for ((pkg, since) in openSince) spans.getOrPut(pkg) { mutableListOf() } += Interval(since, to)
        return spans to opens
    }

    fun csv(rows: List<Pair<String, AppTime>>): String = buildString {
        appendLine("app,package,foreground_minutes,opens")
        for ((label, a) in rows) {
            appendLine(listOf(Csv.cell(label), Csv.cell(a.pkg), fixed(a.foreground / 60_000.0, 1), a.opens.toString()).joinToString(","))
        }
    }
}
