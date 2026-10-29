import UIKit
import VoltaCore
import XCTest

/// Runs the app benchmark on an iPhone. iOS doesn't let an app open, swipe through and watch other
/// apps, but a UI test can: started from a Mac over Wi-Fi by ios/bench.sh, it opens each app on
/// schedule, scrolls, swipes and taps in it, brings it back when something else takes over and
/// records the battery every 15 seconds until it reaches the stop level.
final class BenchRunner: XCTestCase {
    override func setUp() {
        // A missed tap or a failed relaunch is noted, but mustn't end a 15-hour run.
        continueAfterFailure = true
        Quiescence.skip()
    }

    func testBenchmark() throws {
        let env = ProcessInfo.processInfo.environment
        let smoke = env["VOLTA_SMOKE"] == "1"
        guard let plan = smoke ? Self.smokePlan() : BenchFiles.plan() else {
            XCTFail("No benchmark is ready. Open Volta on the iPhone, set up the app benchmark and tap Ready.")
            return
        }
        let maxMinutes = env["VOLTA_MAX_MINUTES"].flatMap(Double.init) ?? (smoke ? 2.5 : nil)
        let driver = Driver(plan: plan, maxMillis: maxMinutes.map { Int64($0 * 60_000) })
        guard let run = driver.drive() else { return }

        if smoke {
            XCTAssertNotNil(run.ended)
            XCTAssertGreaterThanOrEqual(run.slots.count, 2, "The smoke run should switch apps")
            XCTAssertFalse(run.samples.isEmpty)
            XCTAssertFalse(run.csv().isEmpty)
        }
        // Show the results in Volta.
        let volta = XCUIApplication()
        volta.launchEnvironment["VOLTA_SHOW_RUN"] = run.id
        volta.launch()
        XCTAssertTrue(volta.wait(for: .runningForeground, timeout: 30))
    }

    /// A short run in Safari, which every iPhone and simulator has, to check the runner end to end.
    static func smokePlan() -> PlanFile {
        PlanFile(
            runId: "smoke-\(nowMillis())",
            created: nowMillis(),
            steps: [StepConfig(id: "safari", name: "Safari", minutes: 1), StepConfig(id: "safari", name: "Safari", minutes: 1)],
            stopLevel: Int(Benchmark.shared.STOP_LEVEL),
            conditions: RunConditions(phone: UIDevice.current.model, software: "iOS " + UIDevice.current.systemVersion, notes: "Smoke test"),
            capacityMah: nil
        )
    }
}

/// The benchmark loop, a port of the Android BenchService's.
private final class Driver {
    private static let tick = 0.5
    /// Time for an app to open before the first move.
    private static let openWait: Int64 = 5_000
    private static let startGap: Int64 = 2_500
    /// How long another app may stay in front before the step's app is opened again.
    private static let awayLimit: Int64 = 15_000
    private static let batteryEvery: Int64 = 15_000
    /// Opens in a row that never brought the app to the front before the slot gives up on it.
    private static let maxFailedOpens = 3

    private var run: RunFile
    private let maxMillis: Int64?
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
    private let screen = UIScreen.main.bounds.size

    init(plan: PlanFile, maxMillis: Int64?) {
        UIDevice.current.isBatteryMonitoringEnabled = true
        self.maxMillis = maxMillis
        if var existing = BenchFiles.run(plan.runId) {
            existing.plan = plan
            run = existing
        } else {
            run = RunFile(plan: plan, started: nowMillis())
        }
    }

    /// Runs until the battery reaches the stop level, a charger is connected or the time limit
    /// passes. Returns the run, or nil when there was nothing to run.
    func drive() -> RunFile? {
        if !run.active {
            XCTFail("This benchmark already ran. Set up a new one in Volta.")
            return nil
        }
        // No readings for a while means the phone ran out, or the Mac lost it for too long: the run ended then.
        if let last = run.samples.last?.time, nowMillis() - last > 10 * 60_000 {
            if let i = run.slots.lastIndex(where: { $0.end == nil }) { run.slots[i].end = max(run.slots[i].start, last) }
            run.ended = last
            run.reason = "The runner stopped for over 10 minutes"
            BenchFiles.save(run)
            return run
        }
        BenchFiles.save(run)

        let steps = run.plan.steps.map(\.core)
        var slot = run.slots.last { $0.end == nil }
        var work: Workload?
        var app: XCUIApplication?
        var opened = -1
        var queue: [Move] = []
        var loopAt = 0
        var nextMove: Int64 = 0
        var awaySince: Int64 = 0
        var failedOpens = 0
        var nextBattery: Int64 = 0

        func save(_ s: SlotRecord) {
            if let i = run.slots.lastIndex(where: { $0.index == s.index }) { run.slots[i] = s } else { run.slots.append(s) }
            BenchFiles.save(run)
        }

        func finish(_ reason: String, at now: Int64) {
            if let w = work, let a = app, a.state == .runningForeground { perform(w.leave, in: a) }
            if var s = slot { s.end = now; save(s) }
            run.ended = now
            run.reason = reason
            run.now = nil
            BenchFiles.save(run)
            XCUIDevice.shared.press(.home)
        }

        while true {
            let now = nowMillis()
            if let maxMillis, now - run.started >= maxMillis {
                finish("The time limit was reached", at: now)
                return run
            }
            guard let pos = Benchmark.shared.position(steps: steps, elapsed: now - run.started) else { return run }
            let index = Int(pos.index)

            if opened != index {
                let previous = slot
                if var p = previous, p.index != index {
                    if let w = work, let a = app, a.state == .runningForeground { perform(w.leave, in: a) }
                    p.end = now
                    save(p)
                    slot = nil
                }
                if slot == nil {
                    slot = SlotRecord(index: index, round: Int(pos.round), stepId: pos.step.id, start: now, end: nil)
                    save(slot!)
                }
                run.now = "\(pos.step.name) · round \(pos.round + 1)"
                BenchFiles.save(run)
                if let p = previous, p.index != index {
                    XCUIDevice.shared.press(.home)
                    pause(1.5)
                }
                work = Workloads.of(run.plan.steps[index % run.plan.steps.count])
                app = work.map { XCUIApplication(bundleIdentifier: $0.bundleId) }
                if let w = work, let a = app { open(w, a) }
                opened = index
                queue = work?.start ?? []
                loopAt = 0
                nextMove = nowMillis() + Self.openWait
                awaySince = 0
                failedOpens = 0
            }

            if let w = work, let a = app {
                let front = a.state == .runningForeground
                if front {
                    awaySince = 0
                    failedOpens = 0
                } else if awaySince == 0 {
                    awaySince = now
                } else if now - awaySince > Self.awayLimit {
                    failedOpens += 1
                    if failedOpens > Self.maxFailedOpens {
                        // Most likely not installed: sit out the rest of the slot on the home screen.
                        XCTFail("\(w.bundleId) didn't open \(Self.maxFailedOpens) times. Is it installed?")
                        work = nil
                        app = nil
                    } else {
                        open(w, a)
                        queue = w.start
                        nextMove = nowMillis() + Self.openWait
                        awaySince = 0
                        if var s = slot { s.relaunches += 1; slot = s; save(s) }
                    }
                }
                if let w = work, let a = app, front, now >= nextMove {
                    if !queue.isEmpty {
                        perform([queue.removeFirst()], in: a)
                        nextMove = nowMillis() + Self.startGap
                    } else if !w.loop.isEmpty {
                        perform([w.loop[loopAt % w.loop.count]], in: a)
                        loopAt += 1
                        nextMove = nowMillis() + Int64(w.every * 1000)
                    }
                }
            }

            if now >= nextBattery {
                nextBattery = now + Self.batteryEvery
                let s = Self.reading(now)
                run.samples.append(s)
                BenchFiles.save(run)
                if s.plugged {
                    finish("The charger was connected", at: now)
                    return run
                }
                if let level = s.level, level <= run.plan.stopLevel {
                    finish("The battery reached \(level)%", at: now)
                    return run
                }
            }
            pause(Self.tick)
        }
    }

    private static func reading(_ now: Int64) -> SampleRecord {
        let d = UIDevice.current
        let level = d.batteryLevel < 0 ? nil : Int((d.batteryLevel * 100).rounded())
        return SampleRecord(
            time: now,
            level: level,
            plugged: d.batteryState == .charging || d.batteryState == .full,
            thermal: ProcessInfo.processInfo.thermalState.rawValue
        )
    }

    private func pause(_ seconds: Double) {
        RunLoop.current.run(until: Date().addingTimeInterval(seconds))
    }

    private func open(_ w: Workload, _ app: XCUIApplication) {
        if let url = w.url.flatMap(URL.init(string:)) {
            openURL(url)
        } else {
            app.activate()
        }
    }

    /// Opens [url] in the app its scheme names, confirming if iOS asks first.
    private func openURL(_ url: URL) {
        XCUIDevice.shared.system.open(url)
        let confirm = springboard.buttons["Open"]
        if confirm.waitForExistence(timeout: 2) { confirm.tap() }
    }

    private func perform(_ moves: [Move], in app: XCUIApplication) {
        for (i, m) in moves.enumerated() {
            if i > 0 { pause(Double(Self.startGap) / 1000) }
            switch m {
            case let .swipe(x1, y1, x2, y2, seconds):
                let from = point(app, x1, y1)
                let distance = hypot((x2 - x1) * screen.width, (y2 - y1) * screen.height)
                from.press(
                    forDuration: 0.02,
                    thenDragTo: point(app, x2, y2),
                    withVelocity: XCUIGestureVelocity(distance / CGFloat(max(seconds, 0.05))),
                    thenHoldForDuration: 0
                )
            case let .tap(x, y):
                point(app, x, y).tap()
            case let .tapLabel(words, exact, avoid, fallback):
                if let e = find(app, words, exact: exact, avoid: avoid) {
                    e.tap()
                } else if let f = fallback {
                    point(app, f.x, f.y).tap()
                }
            case let .open(url):
                if let u = URL(string: url) { openURL(u) }
            }
        }
    }

    /// A point given as fractions of the screen, from the top left.
    private func point(_ app: XCUIApplication, _ x: CGFloat, _ y: CGFloat) -> XCUICoordinate {
        app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: x * screen.width, dy: y * screen.height))
    }

    /// The first hittable element labelled with one of [words], trying each word in turn.
    private func find(_ app: XCUIApplication, _ words: [String], exact: Bool, avoid: [String]) -> XCUIElement? {
        for w in words {
            let format = exact ? "label ==[c] %@ OR identifier ==[c] %@" : "label CONTAINS[c] %@ OR identifier CONTAINS[c] %@"
            let matches = app.descendants(matching: .any).matching(NSPredicate(format: format, w, w))
            for e in matches.allElementsBoundByIndex.prefix(10) where e.isHittable {
                let label = (e.label + " " + e.identifier).lowercased()
                if !avoid.contains(where: { label.contains($0) }) { return e }
            }
        }
        return nil
    }
}

/// XCUITest waits for an app to go idle before each tap or swipe. Video and game apps never do, so
/// each move would stall for a minute. The runner moves on its own schedule instead.
enum Quiescence {
    private static var done = false

    static func skip() {
        guard !done, let cls = NSClassFromString("XCUIApplicationProcess") else { return }
        done = true
        let noWait: @convention(block) (AnyObject, Bool) -> Void = { _, _ in }
        let noWaitPreEvent: @convention(block) (AnyObject, Bool, Bool) -> Void = { _, _, _ in }
        if let m = class_getInstanceMethod(cls, NSSelectorFromString("waitForQuiescenceIncludingAnimationsIdle:")) {
            method_setImplementation(m, imp_implementationWithBlock(noWait))
        }
        if let m = class_getInstanceMethod(cls, NSSelectorFromString("waitForQuiescenceIncludingAnimationsIdle:isPreEvent:")) {
            method_setImplementation(m, imp_implementationWithBlock(noWaitPreEvent))
        }
    }
}
