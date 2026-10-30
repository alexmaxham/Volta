package com.alexmaxham.volta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private const val M = Time.MINUTE
private const val H = Time.HOUR

private fun r(min: Long, level: Int, plugged: Boolean = false, tempC: Float? = null, currentMa: Int? = null, voltage: Float? = null, counter: Float? = null) =
    Reading(time = min * M, level = level, plugged = plugged, tempC = tempC, currentMa = currentMa, voltageV = voltage, chargeCounterMah = counter)

class ScreenTimelineTest {
    @Test fun splitsOnAndOff() {
        val events = listOf(ScreenEvent(10 * M, true), ScreenEvent(40 * M, false), ScreenEvent(70 * M, true))
        val t = ScreenTimeline.build(events, 0, 100 * M)
        // Before the first event (screen on) the screen was off.
        assertEquals(60 * M, t.on)
        assertEquals(40 * M, t.off)
        assertEquals(0L, t.unclassified)
        assertEquals(2, t.wakes)
    }

    @Test fun eventBeforeWindowSetsState() {
        val t = ScreenTimeline.build(listOf(ScreenEvent(-5 * M, true)), 0, 30 * M)
        assertEquals(30 * M, t.on)
    }

    @Test fun noEventsIsUnclassifiedUnlessStateKnown() {
        assertEquals(30 * M, ScreenTimeline.build(emptyList(), 0, 30 * M).unclassified)
        assertEquals(30 * M, ScreenTimeline.build(emptyList(), 0, 30 * M, stateAtEnd = false).off)
    }

    @Test fun repeatedEventsAreIgnored() {
        val events = listOf(ScreenEvent(10 * M, true), ScreenEvent(12 * M, true), ScreenEvent(20 * M, false))
        val t = ScreenTimeline.build(events, 0, 30 * M)
        assertEquals(10 * M, t.on)
        assertEquals(1, t.wakes)
    }
}

class DrainTest {
    @Test fun estimateNeedsEnoughContinuousUse() {
        val e = Drain.estimate(listOf(r(0, 80), r(15, 79)))
        assertTrue(e is Estimate.Learning)
    }

    @Test fun estimateFromRecentPace() {
        // 10 points in 2 hours = 5 pts/h; 70% left = 14 h.
        val e = Drain.estimate(listOf(r(0, 80), r(30, 78), r(60, 76), r(90, 73), r(120, 70)))
        assertTrue(e is Estimate.Remaining)
        e as Estimate.Remaining
        assertEquals(5.0, e.ratePerHour, 0.01)
        assertEquals(14 * H, e.millis)
    }

    @Test fun gapRestartsLearning() {
        val e = Drain.estimate(listOf(r(0, 90), r(30, 85), r(200, 70), r(215, 69)))
        assertTrue(e is Estimate.Learning)
    }

    @Test fun estimateIsCappedAtThreeDays() {
        val e = Drain.estimate(listOf(r(0, 100), r(30, 99), r(60, 98), r(600, 97), r(640, 96), r(680, 95), r(720, 94), r(760, 93), r(800, 92), r(1200, 91)))
        // Gap at 640->600? none over 45 min except 60->600 and 800->1200, so learning.
        assertTrue(e is Estimate.Learning)
        val slow = (0..20).map { r(it * 40L, 100 - it / 4) }
        val s = Drain.estimate(slow)
        assertTrue(s is Estimate.Remaining)
        assertTrue((s as Estimate.Remaining).millis <= Drain.MAX_REMAINING)
    }

    @Test fun chargingEstimatesTimeToFull() {
        val e = Drain.estimate(listOf(r(0, 40, true), r(10, 45, true), r(20, 50, true)))
        assertTrue(e is Estimate.UntilFull)
        assertTrue(Drain.estimate(listOf(r(0, 99, true), r(10, 100, true))) is Estimate.Full)
    }

    @Test fun usageSplitsScreenOnAndOff() {
        val readings = listOf(r(0, 90), r(30, 88), r(60, 80), r(90, 79), r(120, 70, plugged = true))
        val on = listOf(Interval(30 * M, 60 * M))
        val u = Drain.usage(readings, on, 0, 120 * M)
        assertEquals(20, u.used)
        assertEquals(8, u.screenOn.points)
        assertEquals(30 * M, u.screenOn.duration)
        assertEquals(3, u.screenOff.points)
        assertEquals(60 * M, u.screenOff.duration)
        assertEquals(70, u.lowest)
    }
}

class ChargingTest {
    @Test fun findsSessionsAndMilestones() {
        val rs = listOf(
            r(0, 30), r(10, 23, true, tempC = 30f, currentMa = 3000, voltage = 4f), r(30, 50, true, tempC = 34f, currentMa = 2500, voltage = 4.1f),
            r(60, 80, true, tempC = 36f, currentMa = 1500, voltage = 4.2f), r(90, 100, true, tempC = 33f, currentMa = 200, voltage = 4.35f), r(100, 100),
        )
        val s = Charging.sessions(rs)
        assertEquals(1, s.size)
        val c = s.first()
        assertEquals(23, c.startLevel)
        assertEquals(100, c.endLevel)
        assertEquals(20 * M, c.to50)
        assertEquals(50 * M, c.to80)
        assertEquals(80 * M, c.to100)
        assertEquals(36f, c.peakTempC!!, 0.01f)
        assertEquals(12f, c.peakPowerW!!, 0.01f)
        assertFalse(c.ongoing)
    }

    @Test fun gapsSplitSessions() {
        val rs = listOf(r(0, 20, true), r(20, 40, true), r(120, 50, true), r(140, 70, true))
        val s = Charging.sessions(rs)
        assertEquals(2, s.size)
        assertTrue(s.first().ongoing)
        assertEquals(50, s.first().startLevel)
    }

}

class CapacityTest {
    @Test fun fromChargeCounter() {
        val rs = listOf(r(0, 20, true, counter = 1000f), r(30, 45, true, counter = 2150f), r(60, 70, true, counter = 3300f))
        val session = Charging.sessions(rs).first()
        val c = Capacity.fromSession(session)
        assertNotNull(c)
        assertEquals(4600f, c!!.fullMah, 1f)
    }

    @Test fun fromCurrentIntegration() {
        // 2300 mA for an hour = 2300 mAh for 50 points = 4600 mAh.
        val rs = (0..6).map { r(it * 10L, 20 + it * 50 / 6, true, currentMa = 2300) }
        val c = Capacity.fromSession(Charging.sessions(rs).first())
        assertNotNull(c)
        assertEquals(4600f, c!!.fullMah, 120f)
    }

    @Test fun smallGainsAreIgnored() {
        val rs = listOf(r(0, 50, true, counter = 0f), r(30, 70, true, counter = 900f))
        assertNull(Capacity.fromSession(Charging.sessions(rs).first()))
    }

    @Test fun healthIsMedianOverDesign() {
        val h = Capacity.health(listOf(CapacityReading(1, 4500f, 40), CapacityReading(2, 4700f, 40), CapacityReading(3, 4600f, 40)), 5000)!!
        assertEquals(4600f, h.estimateMah, 0.1f)
        assertEquals(92, h.percent)
        assertNull(Capacity.health(emptyList(), 5000))
    }
}

class HistoryTest {
    private val zone = ZoneId.of("UTC")

    @Test fun dailyUseAndWeek() {
        val day = Time.DAY / M
        val rs = listOf(r(0, 100), r(60, 80), r(120, 90, true), r(day + 10, 90), r(day + 60, 60))
        val today = LocalDate.of(1970, 1, 2)
        val d = History.daily(rs, today, 2, zone)
        assertEquals(20, d[0].used)
        assertEquals(10, d[0].charged)
        assertEquals(30, d[1].used)
        val w = History.week(rs, today, zone)
        assertEquals(50, w.thisWeek)
        assertNull(w.change)
    }

    @Test fun cycleStartsWhenUnpluggedNotWhenLevelDrops() {
        // Unplugged at 100% and still reading 100% for half an hour: the cycle starts at the unplug.
        val rs = listOf(r(0, 90, true), r(10, 100, true), r(20, 100), r(35, 100), r(50, 99))
        assertEquals(20 * M, History.cycleStart(rs))
        assertEquals(20 * M, History.cycles(rs, 60 * M).first().start)
    }

    @Test fun unpluggingBeforeFullDoesNotStartACycle() {
        val rs = listOf(r(0, 100, true), r(10, 100), r(60, 70), r(70, 75, true), r(90, 85, true), r(100, 85))
        assertEquals(10 * M, History.cycleStart(rs))
    }

    @Test fun cycleStartWithoutPlugData() {
        assertEquals(0L, History.cycleStart(listOf(r(0, 100), r(30, 98))))
        assertEquals(40 * M, History.cycleStart(listOf(r(0, 60), r(40, 100), r(70, 97))))
        assertNull(History.cycleStart(listOf(r(0, 50), r(30, 48))))
    }

    @Test fun cyclesRunFromFullToFull() {
        val rs = listOf(r(0, 70), r(10, 100, true), r(20, 100), r(30, 60), r(40, 100, true), r(50, 100), r(60, 90))
        val c = History.cycles(rs, 70 * M)
        assertEquals(3, c.size)
        assertTrue(c.last().partial)
        assertTrue(c.first().ongoing)
        assertEquals(50 * M, c.first().start)
        assertEquals(10, c.first().used)
        assertEquals(40, c[1].used)
    }
}

class NightTest {
    private val zone = ZoneId.of("UTC")

    @Test fun windowCrossesMidnight() {
        val w = Night.window(LocalDate.of(1970, 1, 2), LocalTime.of(23, 0), LocalTime.of(7, 0), zone)
        assertEquals(23 * H, w.start)
        assertEquals(31 * H, w.end)
    }

    @Test fun reportsDrainAndDeepSleep() {
        val w = NightWindow(0, 8 * H)
        val rs = (0..16).map { i ->
            Reading(time = i * 30 * M, level = 81 - i / 5, plugged = false, elapsed = i * 30 * M, uptime = i * 3 * M)
        }
        val rep = Night.report(w, rs, emptyList(), 10 * H)
        assertEquals(81, rep.startLevel)
        assertEquals(78, rep.endLevel)
        assertEquals(3, rep.drop)
        assertEquals(0.375, rep.ratePerHour!!, 0.001)
        assertEquals(0.9, rep.deepSleep.fraction, 0.001)
        assertEquals(1.0, rep.coverage, 0.001)
        assertFalse(rep.inProgress)
    }

    @Test fun chargingHidesRate() {
        val rs = listOf(r(0, 50), r(30, 60, true), r(480, 100, true))
        val rep = Night.report(NightWindow(0, 8 * H), rs, emptyList(), 9 * H)
        assertTrue(rep.chargingSeen)
        assertNull(rep.drop)
    }

    @Test fun deepSleepSkipsReboots() {
        val rs = listOf(
            Reading(time = 0, level = 50, plugged = false, elapsed = 10 * H, uptime = 2 * H),
            Reading(time = 30 * M, level = 50, plugged = false, elapsed = 5 * M, uptime = 4 * M),
        )
        assertEquals(0L, SleepClock.measure(rs, 0, H).covered)
    }
}

class AppsTest {
    @Test fun foregroundTime() {
        val events = listOf(
            AppEvent(-10 * M, "a", true),
            AppEvent(5 * M, "a", false),
            AppEvent(10 * M, "b", true),
            AppEvent(30 * M, "b", false),
            AppEvent(40 * M, "b", true),
        )
        val u = Apps.usage(events, 0, 60 * M)
        assertEquals(listOf("b", "a"), u.map { it.pkg })
        assertEquals(40 * M, u[0].foreground)
        assertEquals(2, u[0].opens)
        assertEquals(5 * M, u[1].foreground)
    }

    @Test fun drainSplitsDropsByTimeInFront() {
        // "a" in front 0–10, "b" 10–20, nothing 20–30 (screen off).
        val events = listOf(
            AppEvent(0, "a", true), AppEvent(10 * M, "a", false),
            AppEvent(10 * M, "b", true), AppEvent(20 * M, "b", false),
        )
        val readings = listOf(r(0, 100), r(20, 96), r(30, 95))
        val d = Apps.drain(events, readings, 0, 30 * M)
        assertEquals(2.0, d["a"]!!, 0.001)
        assertEquals(2.0, d["b"]!!, 0.001)
        // The screen-off point isn't given to either app.
        assertEquals(4.0, d.values.sum(), 0.001)
    }

    @Test fun drainSkipsChargingAndGaps() {
        val events = listOf(AppEvent(0, "a", true))
        val readings = listOf(r(0, 50), r(10, 52, plugged = true), r(20, 51), r(200, 40))
        assertTrue(Apps.drain(events, readings, 0, 200 * M).isEmpty())
    }
}

class ReviewTest {
    private val run = ReviewRun(
        id = "1", started = 0, ended = 2 * H,
        conditions = Conditions(phone = "Pixel", protocol = "Video loop", refreshRate = "120 Hz"),
        samples = (0..8).map { r(it * 15L, 100 - it * 2, voltage = 4f, currentMa = -500, tempC = 30f + it) },
    )

    @Test fun summary() {
        val s = Review.summary(run, 2 * H)
        assertEquals(2 * H, s.elapsed)
        assertEquals(8.0, s.ratePerHour!!, 0.001)
        assertEquals(38f, s.peakTempC!!, 0.01f)
        assertEquals(2f, s.avgPowerW!!, 0.01f)
    }

    @Test fun matchingIgnoresCaseAndNotes() {
        val other = run.copy(id = "2", conditions = run.conditions.copy(protocol = "video LOOP", notes = "different"))
        val third = run.copy(id = "3", conditions = run.conditions.copy(refreshRate = "60 Hz"))
        assertEquals(setOf("1", "2"), Review.matching(run, listOf(run, other, third)).map { it.id }.toSet())
    }

    @Test fun csvEscapes() {
        assertEquals("\"a,b\"", Csv.cell("a,b"))
        assertEquals("'=SUM(A1)", Csv.cell("=SUM(A1)"))
        val csv = Review.csv(run)
        assertTrue(csv.contains("# phone,Pixel"))
        assertTrue(csv.lines().any { it.startsWith("0,0.00,100,0,30.0,4.000,-500,-2.00") })
    }
}

class AlertsTest {
    private val o = AlertOptions(lowEnabled = true, lowLevel = 20, chargeEnabled = true, chargeLevel = 80, warmEnabled = true, warmC = 40f)

    @Test fun firesOncePerCrossing() {
        var latch = AlertLatch()
        val (a1, l1) = Alerts.check(r(0, 20), o, latch); latch = l1
        assertEquals(listOf(Alert.LOW), a1)
        val (a2, l2) = Alerts.check(r(1, 19), o, latch); latch = l2
        assertTrue(a2.isEmpty())
        val (_, l3) = Alerts.check(r(2, 30, plugged = true), o, latch); latch = l3
        assertFalse(latch.low)
        val (a4, _) = Alerts.check(r(3, 80, plugged = true), o, latch)
        assertEquals(listOf(Alert.CHARGE), a4)
    }

    @Test fun warm() {
        val (a, l) = Alerts.check(r(0, 50, tempC = 41f), o, AlertLatch())
        assertEquals(listOf(Alert.WARM), a)
        assertTrue(Alerts.check(r(1, 50, tempC = 39.5f), o, l).first.isEmpty())
        assertFalse(Alerts.check(r(2, 50, tempC = 37f), o, l).second.warm)
    }
}

class FormatTest {
    @Test fun durations() {
        assertEquals("<1 min", Format.duration(30 * Time.SECOND))
        assertEquals("41 min", Format.duration(41 * M))
        assertEquals("3 h 12 m", Format.duration(3 * H + 12 * M))
        assertEquals("1:42:18", Format.clock(H + 42 * M + 18 * Time.SECOND))
    }
}

class AxisTest {
    @Test fun niceSteps() {
        assertEquals(5.0, Axis.niceStep(4.2), 1e-9)
        assertEquals(2.5, Axis.niceStep(2.3), 1e-9)
        assertEquals(10.0, Axis.niceStep(7.0), 1e-9)
        assertEquals(0.2, Axis.niceStep(0.17), 1e-9)
    }

    @Test fun powerScaleStartsAtZero() {
        val s = Axis.scale(3f, 20f, fromZero = true)
        assertEquals(0f, s.min, 0f)
        assertEquals(5f, s.step, 0f)
        assertEquals(20f, s.max, 0f)
        assertEquals(listOf(0f, 5f, 10f, 15f, 20f), s.ticks)
    }

    @Test fun pairedScalesShareGridlines() {
        val (p, t) = Axis.paired(Axis.scale(0f, 20f, fromZero = true), Axis.scale(29.4f, 33.1f))
        assertEquals(p.intervals, t.intervals)
        assertTrue(t.min <= 29.4f && t.max >= 33.1f)
    }

    @Test fun flatRangeStillHasSpan() {
        val s = Axis.scale(31f, 31f)
        assertTrue(s.max > s.min)
    }
}

class BenchmarkTest {
    private val steps = listOf(BenchStep("a", "A"), BenchStep("b", "B"), BenchStep("c", "C", minutes = 30))

    @Test fun positionWalksStepsThenRepeats() {
        val p0 = Benchmark.position(steps, 0)!!
        assertEquals("a", p0.step.id)
        assertEquals(0, p0.index)
        assertEquals(H, p0.end)

        val p1 = Benchmark.position(steps, 90 * M)!!
        assertEquals("b", p1.step.id)
        assertEquals(1, p1.index)
        assertEquals(H, p1.start)

        val p2 = Benchmark.position(steps, 2 * H + 29 * M)!!
        assertEquals("c", p2.step.id)
        assertEquals(2 * H + 30 * M, p2.end)

        // The loop is 2.5 h long, so 2.5 h in starts round two with the first app again.
        val again = Benchmark.position(steps, 2 * H + 30 * M)!!
        assertEquals("a", again.step.id)
        assertEquals(1, again.round)
        assertEquals(3, again.index)
        assertEquals(2 * H + 30 * M, again.start)
    }

    @Test fun videoAndMusicHaveDefaultLinks() {
        assertTrue(Benchmark.defaultLink("youtube").startsWith("https://www.youtube.com/watch?v="))
        assertTrue(Benchmark.defaultLink("spotify").startsWith("https://open.spotify.com/playlist/"))
        assertEquals("", Benchmark.defaultLink("chrome"))
    }

    @Test fun positionNeedsSteps() {
        assertNull(Benchmark.position(emptyList(), 0))
    }

    @Test fun reportSplitsDrainByApp() {
        // Ten minutes a step at 15-second-ish spacing is plenty; use one reading a minute.
        val samples = (0..120L).map { m ->
            val level = if (m <= 60) 100 - (m / 6).toInt() else 90 - ((m - 60) / 4).toInt()
            r(m, level, tempC = 30f + m / 60f, currentMa = if (m <= 60) -500 else -900, voltage = 4f)
        }
        val run = ReviewRun("x", 0, 120 * M, Conditions(phone = "Test"), samples)
        val segs = listOf(BenchSegment(0, 0, "chrome", 0, 60 * M), BenchSegment(1, 0, "youtube", 60 * M, 120 * M, relaunches = 2))
        val rep = Benchmark.report(run, segs, 120 * M)

        assertEquals(2, rep.apps.size)
        val chrome = rep.apps.first { it.stepId == "chrome" }
        assertEquals(10, chrome.used)
        assertEquals(10.0, chrome.perHour!!, 0.01)
        assertEquals(500.0, chrome.mahPerHour!!, 1.0)
        assertEquals(2f, chrome.avgPowerW!!, 0.01f)

        val yt = rep.apps.first { it.stepId == "youtube" }
        assertEquals(15, yt.used)
        // The first minute averages the two currents at the boundary.
        assertEquals(900.0, yt.mahPerHour!!, 5.0)
        assertEquals(2, yt.relaunches)
        assertEquals(1, rep.rounds)
        assertEquals(100, rep.startLevel)
        assertEquals(75, rep.endLevel)
    }

    @Test fun reportPrefersTheChargeCounter() {
        val samples = (0..60L).map { m -> r(m, 100 - (m / 10).toInt(), currentMa = -100, counter = 4000f - m * 10) }
        val run = ReviewRun("x", 0, 60 * M, Conditions(), samples)
        val rep = Benchmark.report(run, listOf(BenchSegment(0, 0, "chrome", 0, 60 * M)), 60 * M)
        assertEquals(600f, rep.apps.single().mah!!, 0.1f)
    }

    @Test fun shortOrGappySlotsHaveNoRate() {
        val samples = listOf(r(0, 100, currentMa = -500), r(5, 99, currentMa = -500), r(40, 95, currentMa = -500))
        val run = ReviewRun("x", 0, 40 * M, Conditions(), samples)
        val rep = Benchmark.report(run, listOf(BenchSegment(0, 0, "chrome", 0, 5 * M), BenchSegment(1, 0, "youtube", 5 * M, 40 * M)), 40 * M)
        assertNull(rep.segments[0].perHour)
        // 35 minutes with a 35-minute gap between readings: no current-based charge.
        assertNull(rep.segments[1].mah)
        assertEquals(4, rep.segments[1].used)
    }

    @Test fun csvHasOneRowPerSlot() {
        val samples = (0..60L).map { m -> r(m, 100 - (m / 10).toInt()) }
        val run = ReviewRun("x", 0, 60 * M, Conditions(phone = "Pixel, 9"), samples)
        val rep = Benchmark.report(run, listOf(BenchSegment(0, 0, "chrome", 0, 30 * M), BenchSegment(1, 0, "youtube", 30 * M, 60 * M)), 60 * M)
        val lines = Benchmark.csv(run, rep).lines().filter { it.isNotBlank() }
        assertTrue(lines.any { it == "# phone,\"Pixel, 9\"" })
        assertEquals(2, lines.count { !it.startsWith("#") && !it.startsWith("slot,") })
        assertTrue(Benchmark.text(run, rep, false).contains("Chrome: 6.0 pts/h"))
    }
}
