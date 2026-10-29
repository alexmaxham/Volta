import Foundation
import VoltaCore

/// The shared Kotlin benchmark logic (VoltaCore, built from core/) applied to the iOS run files, so
/// the schedule, per-app results and CSV match the Android app's.
extension StepConfig {
    var core: BenchStep { BenchStep(id: id, name: name, minutes: Int32(minutes), link: link) }

    static var defaults: [StepConfig] {
        Benchmark.shared.DEFAULT.map { StepConfig(id: $0.id, name: $0.name, minutes: Int($0.minutes), link: $0.link) }
    }
}

extension RunFile {
    var coreRun: ReviewRun {
        let c = plan.conditions
        let readings = samples.compactMap { s in s.level.map { IosBench.shared.reading(time: s.time, level: Int32($0), plugged: s.plugged) } }
        return IosBench.shared.reviewRun(
            id: plan.runId,
            started: started,
            ended: ended ?? -1,
            conditions: IosBench.shared.conditions(phone: c.phone, software: c.software, brightness: c.brightness, network: c.network, notes: c.notes),
            samples: readings
        )
    }

    var coreSegments: [BenchSegment] {
        slots.map {
            IosBench.shared.segment(
                index: Int32($0.index), round: Int32($0.round), stepId: $0.stepId,
                start: $0.start, end: $0.end ?? -1, relaunches: Int32($0.relaunches)
            )
        }
    }

    func report(now: Int64 = nowMillis()) -> BenchReport {
        // A run the runner lost hold of ends at its last reading, not now.
        Benchmark.shared.report(run: coreRun, segments: coreSegments, now: active ? lastTime : now)
    }

    func csv() -> String { Benchmark.shared.csv(run: coreRun, report: report()) }

    func text() -> String { Benchmark.shared.text(run: coreRun, report: report(), fahrenheit: false) }

    /// The hottest thermal state seen while [stepId] ran, or nil without readings.
    func peakThermal(stepId: String) -> Int? {
        let ranges = slots.filter { $0.stepId == stepId }.map { $0.start...($0.end ?? lastTime) }
        return samples.filter { s in ranges.contains { $0.contains(s.time) } }.map(\.thermal).max()
    }
}

enum Thermal {
    static func name(_ state: Int) -> String {
        switch state {
        case 0: return "Nominal"
        case 1: return "Fair"
        case 2: return "Serious"
        default: return "Critical"
        }
    }
}
