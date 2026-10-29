import Foundation

/// One app in the loop, as set up in Volta. [link] is optional content to open, such as a video or playlist.
struct StepConfig: Codable, Hashable, Identifiable {
    var id: String
    var name: String
    var minutes: Int = 60
    var link: String = ""
}

/// Conditions for the run, entered in Volta before it starts.
struct RunConditions: Codable, Hashable {
    var phone = ""
    var software = ""
    var brightness = ""
    var network = ""
    var notes = ""
}

/// The run Volta has ready for the runner on the Mac to start.
struct PlanFile: Codable, Hashable {
    var runId: String
    var created: Int64
    var steps: [StepConfig]
    var stopLevel: Int
    var conditions: RunConditions
    /// The battery's design capacity, for an mAh estimate. iOS doesn't report charge.
    var capacityMah: Int?
}

/// A battery reading. [level] is nil where iOS doesn't report it (the simulator). [thermal] is
/// ProcessInfo's thermal state: 0 nominal, 1 fair, 2 serious, 3 critical.
struct SampleRecord: Codable, Hashable {
    var time: Int64
    var level: Int?
    var plugged: Bool
    var thermal: Int
}

/// One app slot as it actually ran. [relaunches] counts the times the app was brought back.
struct SlotRecord: Codable, Hashable {
    var index: Int
    var round: Int
    var stepId: String
    var start: Int64
    var end: Int64?
    var relaunches = 0
}

/// A benchmark run, written by the runner as it goes and read by Volta for the results.
struct RunFile: Codable, Hashable, Identifiable {
    var plan: PlanFile
    var started: Int64
    var ended: Int64?
    var reason: String?
    var samples: [SampleRecord] = []
    var slots: [SlotRecord] = []
    /// The app the runner is in, for Volta's status line.
    var now: String?

    var id: String { plan.runId }
    var active: Bool { ended == nil }
    var lastTime: Int64 { samples.last?.time ?? started }
}

func nowMillis() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

/// The benchmark's files, in the app group's folder so both Volta and the runner see them. Falls
/// back to this app's own documents when the group isn't available, as on an unsigned build.
enum BenchFiles {
    private final class Marker {}

    private static let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.outputFormatting = [.sortedKeys]
        return e
    }()

    /// The app group from this target's Info.plist, set from VOLTA_APP_GROUP in the build settings.
    static var group: String? { Bundle(for: Marker.self).object(forInfoDictionaryKey: "VoltaAppGroup") as? String }

    static var folder: URL {
        let base = group.flatMap { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: $0) }
            ?? FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let dir = base.appendingPathComponent("Bench", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir.appendingPathComponent("runs"), withIntermediateDirectories: true)
        return dir
    }

    private static var planURL: URL { folder.appendingPathComponent("plan.json") }

    private static func runURL(_ id: String) -> URL { folder.appendingPathComponent("runs/\(id).json") }

    static func plan() -> PlanFile? { read(planURL) }

    static func save(_ plan: PlanFile) { write(plan, planURL) }

    static func run(_ id: String) -> RunFile? { read(runURL(id)) }

    static func save(_ run: RunFile) { write(run, runURL(run.id)) }

    static func delete(_ id: String) { try? FileManager.default.removeItem(at: runURL(id)) }

    /// Every run, newest first.
    static func runs() -> [RunFile] {
        let dir = folder.appendingPathComponent("runs")
        let files = (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? []
        return files.filter { $0.pathExtension == "json" }.compactMap { read($0) as RunFile? }.sorted { $0.started > $1.started }
    }

    private static func read<T: Decodable>(_ url: URL) -> T? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    private static func write<T: Encodable>(_ value: T, _ url: URL) {
        guard let data = try? encoder.encode(value) else { return }
        try? data.write(to: url, options: .atomic)
    }
}
