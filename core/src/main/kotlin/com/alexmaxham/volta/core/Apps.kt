package com.alexmaxham.volta.core

data class AppTime(val pkg: String, val foreground: Long, val opens: Int)

object Apps {
    /**
     * Foreground time per app in [from, to), longest first. An app still in front at the end of
     * the window is counted up to [to]; one already in front at the start is counted from [from]
     * if its first event in the window is it leaving.
     */
    fun usage(events: List<AppEvent>, from: Long, to: Long): List<AppTime> {
        val sorted = events.filter { it.time < to }.sortedBy { it.time }
        val openSince = HashMap<String, Long>()
        val total = HashMap<String, Long>()
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
                    opens.merge(e.pkg, 1, Int::plus)
                }
            } else {
                val since = openSince.remove(e.pkg) ?: if (e.pkg !in seen) from else null
                if (since != null) total.merge(e.pkg, e.time - since, Long::plus)
            }
            seen += e.pkg
        }
        for ((pkg, since) in openSince) total.merge(pkg, to - since, Long::plus)
        return total.filterValues { it > 0 }
            .map { (pkg, t) -> AppTime(pkg, t, opens[pkg] ?: 0) }
            .sortedByDescending { it.foreground }
    }

    fun csv(rows: List<Pair<String, AppTime>>): String = buildString {
        appendLine("app,package,foreground_minutes,opens")
        for ((label, a) in rows) {
            appendLine(listOf(Csv.cell(label), Csv.cell(a.pkg), "%.1f".format(java.util.Locale.US, a.foreground / 60_000.0), a.opens.toString()).joinToString(","))
        }
    }
}
