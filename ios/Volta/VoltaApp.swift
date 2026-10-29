import SwiftUI
import VoltaCore

@main
struct VoltaApp: App {
    var body: some Scene {
        WindowGroup {
            HomeView()
        }
    }
}

/// The battery level and charging state, refreshed every few seconds.
final class BatteryMonitor: ObservableObject {
    @Published var level: Int?
    @Published var plugged = false
    private var timer: Timer?

    init() {
        UIDevice.current.isBatteryMonitoringEnabled = true
        read()
        timer = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in self?.read() }
    }

    deinit { timer?.invalidate() }

    private func read() {
        let d = UIDevice.current
        level = d.batteryLevel < 0 ? nil : Int((d.batteryLevel * 100).rounded())
        plugged = d.batteryState == .charging || d.batteryState == .full
    }
}

struct HomeView: View {
    @StateObject private var battery = BatteryMonitor()
    @State private var runs: [RunFile] = []
    @State private var plan: PlanFile?
    @State private var path: [String] = []
    private let refresh = Timer.publish(every: 15, on: .main, in: .common).autoconnect()

    var body: some View {
        NavigationStack(path: $path) {
            List {
                Section {
                    HStack(alignment: .firstTextBaseline) {
                        Text(battery.level.map { "\($0)%" } ?? "–").font(.system(size: 56, weight: .bold, design: .rounded))
                        Spacer()
                        Text(battery.plugged ? "Charging" : "On battery").foregroundStyle(.secondary)
                    }
                }
                Section {
                    NavigationLink("Set up a run") { SetupView(onReady: load) }
                    if let plan, BenchFiles.run(plan.runId) == nil {
                        VStack(alignment: .leading, spacing: 6) {
                            Label("Ready to start", systemImage: "checkmark.circle.fill").foregroundStyle(.green)
                            Text("Unplug, then run ios/bench.sh on your Mac. It starts the run over Wi-Fi.")
                                .font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                } header: {
                    Text("App benchmark")
                } footer: {
                    Text("Volta's runner opens each app in turn for an hour, scrolls, swipes and taps in it, and records the battery until it reaches \(Benchmark.shared.STOP_LEVEL)%.")
                }
                if !runs.isEmpty {
                    Section("Runs") {
                        ForEach(runs) { r in
                            NavigationLink(value: r.id) { RunRow(run: r) }
                        }
                        .onDelete { offsets in
                            for i in offsets { BenchFiles.delete(runs[i].id) }
                            load()
                        }
                    }
                }
            }
            .navigationTitle("Volta")
            .navigationDestination(for: String.self) { RunView(id: $0) }
            .refreshable { load() }
            .onReceive(refresh) { _ in load() }
            .onAppear {
                load()
                if let id = ProcessInfo.processInfo.environment["VOLTA_SHOW_RUN"], path.isEmpty { path = [id] }
            }
        }
    }

    private func load() {
        runs = BenchFiles.runs()
        plan = BenchFiles.plan()
    }
}

struct RunRow: View {
    let run: RunFile

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(Date(timeIntervalSince1970: Double(run.started) / 1000).formatted(date: .abbreviated, time: .shortened))
            Text(status).font(.footnote).foregroundStyle(.secondary)
        }
    }

    private var status: String {
        let length = Format.shared.duration(millis: (run.ended ?? run.lastTime) - run.started)
        if run.active { return "Running for \(length)" + (run.now.map { " · \($0)" } ?? "") }
        return "Lasted \(length)"
    }
}
