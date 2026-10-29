import SwiftUI
import VoltaCore

/// A benchmark run's results: drain per app, each slot as it ran, and sharing as text or CSV.
struct RunView: View {
    let id: String
    @State private var run: RunFile?
    private let refresh = Timer.publish(every: 15, on: .main, in: .common).autoconnect()

    var body: some View {
        Group {
            if let run {
                RunResults(run: run, onEnd: { end(run) })
            } else {
                ContentUnavailableView("No such run", systemImage: "questionmark.circle")
            }
        }
        .navigationTitle("Results")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear(perform: load)
        .onReceive(refresh) { _ in if run?.active == true { load() } }
    }

    private func load() { run = BenchFiles.run(id) }

    /// Ends a run the runner lost hold of, at its last reading.
    private func end(_ r: RunFile) {
        var r = r
        r.ended = r.lastTime
        r.reason = "Ended in Volta"
        if let i = r.slots.lastIndex(where: { $0.end == nil }) { r.slots[i].end = max(r.slots[i].start, r.lastTime) }
        BenchFiles.save(r)
        load()
    }
}

private struct RunResults: View {
    let run: RunFile
    let onEnd: () -> Void

    var body: some View {
        let report = run.report()
        let stale = run.active && nowMillis() - run.lastTime > 10 * 60_000
        List {
            Section {
                VStack(alignment: .leading, spacing: 6) {
                    Text(run.plan.conditions.phone).font(.headline)
                    Text((run.active ? "Running for " : "Lasted ") + Format.shared.duration(millis: report.elapsed))
                        .font(.system(.title, design: .rounded).bold())
                    if let start = report.startLevel, let end = report.endLevel {
                        Text("\(start.intValue)% → \(end.intValue)%").foregroundStyle(.secondary)
                    }
                    if let reason = run.reason { Text(reason).font(.footnote).foregroundStyle(.secondary) }
                    if let now = run.now, run.active, !stale { Text("Now: \(now)").font(.footnote).foregroundStyle(.secondary) }
                }
                if stale {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("No readings for over 10 minutes. The Mac may have lost the iPhone: run ios/bench.sh again to carry on, or end the run here.")
                            .font(.footnote)
                        Button("End run", role: .destructive, action: onEnd)
                    }
                }
            }
            Section {
                ForEach(report.apps, id: \.stepId) { a in
                    AppRow(app: a, thermal: run.peakThermal(stepId: a.stepId), capacity: run.plan.capacityMah)
                }
            } header: {
                Text("Drain per app")
            } footer: {
                Text("Battery points per hour, from iOS's battery level. mAh per hour is estimated from the design capacity you entered. Thermal state is iOS's, not a temperature.")
            }
            Section("Slots") {
                ForEach(report.segments, id: \.segment.index) { s in
                    let used = IosBench.shared.slotUsed(result: s)
                    LabeledContent {
                        Text(used >= 0 ? "\(used) pts" : "–")
                    } label: {
                        Text("\(s.name) · round \(s.round + 1)")
                        Text(Format.shared.duration(millis: s.duration) + (s.segment.relaunches > 0 ? " · \(s.segment.relaunches) relaunches" : ""))
                    }
                }
            }
        }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    ShareLink("Share as text", item: run.text())
                    if let csv = csvFile() { ShareLink("Share CSV", item: csv) }
                } label: {
                    Image(systemName: "square.and.arrow.up")
                }
            }
        }
    }

    private func csvFile() -> URL? {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("volta-benchmark-\(run.id.prefix(8)).csv")
        return (try? run.csv().write(to: url, atomically: true, encoding: .utf8)) != nil ? url : nil
    }
}

private struct AppRow: View {
    let app: AppResult
    let thermal: Int?
    let capacity: Int?

    var body: some View {
        let perHour = IosBench.shared.appPerHour(result: app)
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(app.name).font(.headline)
                Spacer()
                Text(perHour >= 0 ? Format.shared.rate(perHour: perHour) : "under 10 min").monospacedDigit()
            }
            let details = [
                perHour >= 0 ? capacity.map { "≈ " + Format.shared.mah(mah: Float(perHour * Double($0) / 100)) + "/h" } : nil,
                Format.shared.duration(millis: app.duration),
                thermal.map { "thermal " + Thermal.name($0).lowercased() },
                app.relaunches > 0 ? "\(app.relaunches) relaunches" : nil,
            ].compactMap { $0 }
            Text(details.joined(separator: " · ")).font(.footnote).foregroundStyle(.secondary)
        }
    }
}
