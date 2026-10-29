package com.alexmaxham.volta.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs on every platform, so the iPhone's results and CSV match the Android app's. */
class BenchmarkCommonTest {
    private val m = Time.MINUTE

    @Test fun positionLoopsThroughSteps() {
        val steps = listOf(BenchStep("a", "A", 60), BenchStep("b", "B", 30))
        val p = Benchmark.position(steps, 70 * m)!!
        assertEquals("b", p.step.id)
        assertEquals(0, p.round)
        assertEquals(60 * m, p.start)
        val q = Benchmark.position(steps, 95 * m)!!
        assertEquals("a", q.step.id)
        assertEquals(1, q.round)
        assertEquals(2, q.index)
    }

    @Test fun reportAndCsvUseDotDecimals() {
        val samples = listOf(Reading(0, 100, false), Reading(30 * m, 95, false), Reading(60 * m, 89, false))
        val run = ReviewRun("r", 0, 60 * m, Conditions(phone = "iPhone, test"), samples)
        val seg = BenchSegment(0, 0, "chrome", 0, 60 * m)
        val report = Benchmark.report(run, listOf(seg), 60 * m)
        assertEquals(11, report.apps.single().used)
        assertEquals(11.0, report.apps.single().perHour)
        val csv = Benchmark.csv(run, report)
        assertTrue("# phone,\"iPhone, test\"" in csv, csv)
        assertTrue("1,1,Chrome,0,60.0,100,89,11,11.00," in csv, csv)
    }

    @Test fun formatsWithoutJava() {
        assertEquals("1 h 05 m", Format.duration(65 * m))
        assertEquals("1:02:03", Format.clock(Time.HOUR + 2 * m + 3_000))
        assertEquals("0.13", fixed(0.125, 2))
        assertEquals("2.5", fixed(2.45f, 1))
        assertEquals("12", fixed(11.6, 0))
    }
}
