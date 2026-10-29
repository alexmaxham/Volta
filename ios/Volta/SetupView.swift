import SwiftUI
import VoltaCore

/// The apps in the loop, the run's conditions and the checklist before it can start.
struct SetupView: View {
    var onReady: () -> Void

    @AppStorage("skipped") private var skipped = ""
    @AppStorage("link.youtube") private var youtubeLink = ""
    @AppStorage("link.ytmusic") private var musicLink = ""
    @AppStorage("cond.phone") private var phone = SetupView.model
    @AppStorage("cond.brightness") private var brightness = "200 nits"
    @AppStorage("cond.network") private var network = "Wi-Fi"
    @AppStorage("cond.notes") private var notes = ""
    @AppStorage("capacity") private var capacity = ""
    @State private var checklist = false
    @State private var ready = false
    @Environment(\.dismiss) private var dismiss

    private var steps: [StepConfig] {
        StepConfig.defaults.filter { !skippedIds.contains($0.id) }.map { s in
            var s = s
            if s.id == "youtube" { s.link = youtubeLink }
            if s.id == "ytmusic" { s.link = musicLink }
            return s
        }
    }

    private var skippedIds: Set<String> { Set(skipped.split(separator: ",").map(String.init)) }

    var body: some View {
        Form {
            Section {
                ForEach(StepConfig.defaults) { step in
                    StepRow(
                        step: step,
                        on: Binding(
                            get: { !skippedIds.contains(step.id) },
                            set: { on in
                                var ids = skippedIds
                                if on { ids.remove(step.id) } else { ids.insert(step.id) }
                                skipped = ids.sorted().joined(separator: ",")
                            }
                        ),
                        link: step.id == "youtube" ? $youtubeLink : step.id == "ytmusic" ? $musicLink : nil
                    )
                }
            } header: {
                Text("Apps, an hour each")
            } footer: {
                Text("The loop repeats until the battery reaches \(Benchmark.shared.STOP_LEVEL)%. Sign in to each app and dismiss its first-run screens before you start.")
            }
            Section("Conditions") {
                LabeledContent("Phone") { TextField("Phone", text: $phone).multilineTextAlignment(.trailing) }
                LabeledContent("Brightness") { TextField("Brightness", text: $brightness).multilineTextAlignment(.trailing) }
                LabeledContent("Network") { TextField("Network", text: $network).multilineTextAlignment(.trailing) }
                LabeledContent("Design capacity") {
                    TextField("mAh", text: $capacity).keyboardType(.numberPad).multilineTextAlignment(.trailing)
                }
                TextField("Notes", text: $notes, axis: .vertical)
            }
            Section {
                Button("Ready") { checklist = true }.disabled(steps.isEmpty)
            } footer: {
                Text("iOS reports only the battery level, so results are in points per hour, with mAh estimated from the design capacity.")
            }
        }
        .navigationTitle("App benchmark")
        .sheet(isPresented: $checklist) {
            ChecklistView(camera: steps.contains { $0.id == "camera" }) {
                save()
                checklist = false
                ready = true
            }
        }
        .alert("Ready to start", isPresented: $ready) {
            Button("OK") { dismiss() }
        } message: {
            Text("Unplug the iPhone, then run ios/bench.sh on your Mac. Keep the Mac awake and on the same Wi-Fi until the run ends.")
        }
    }

    private func save() {
        let plan = PlanFile(
            runId: UUID().uuidString,
            created: nowMillis(),
            steps: steps,
            stopLevel: Int(Benchmark.shared.STOP_LEVEL),
            conditions: RunConditions(
                phone: phone,
                software: "iOS " + UIDevice.current.systemVersion,
                brightness: brightness,
                network: network,
                notes: notes
            ),
            capacityMah: Int(capacity)
        )
        BenchFiles.save(plan)
        onReady()
    }

    /// The model identifier, such as iPhone16,2, until the user names the phone.
    static var model: String {
        var info = utsname()
        uname(&info)
        return withUnsafeBytes(of: &info.machine) { String(decoding: $0.prefix { $0 != 0 }, as: UTF8.self) }
    }
}

private struct StepRow: View {
    let step: StepConfig
    @Binding var on: Bool
    var link: Binding<String>?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Toggle(isOn: $on) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(step.name)
                    Text(Workloads.hint(step.id)).font(.footnote).foregroundStyle(.secondary)
                    if let note = installNote { Text(note).font(.footnote).foregroundStyle(.orange) }
                }
            }
            if on, let link {
                TextField("Link (optional)", text: link)
                    .textInputAutocapitalization(.never).keyboardType(.URL).autocorrectionDisabled()
                    .font(.footnote)
            }
        }
    }

    private var installNote: String? {
        guard let scheme = Workloads.scheme(step.id), let url = URL(string: scheme + "://") else { return nil }
        return UIApplication.shared.canOpenURL(url) ? nil : "Not installed"
    }
}

/// The checks to confirm before the run. The battery is checked live; iOS settings can't be read, so those are ticked by hand.
private struct ChecklistView: View {
    let camera: Bool
    let onStart: () -> Void

    @StateObject private var battery = BatteryMonitor()
    @State private var autoOff = false
    @State private var nits = false
    @State private var autoLock = false
    @State private var cameraSet = false
    @State private var paired = false
    @State private var white = false
    @Environment(\.dismiss) private var dismiss

    private var charged: Bool { (battery.level ?? 0) >= 100 }
    private var all: Bool { autoOff && nits && autoLock && charged && paired && (!camera || cameraSet) }

    var body: some View {
        NavigationStack {
            List {
                Check("Auto-Brightness is off", "Settings → Accessibility → Display & Text Size → Auto-Brightness.", $autoOff)
                Check(
                    "Brightness set to 200 nits with a lux meter",
                    "Show a white screen and adjust the brightness slider until your meter reads 200 nits. A lux meter held flat against the screen reads roughly 630 lux at 200 nits.",
                    $nits
                )
                Button("Show a white screen") { white = true }
                Check("Auto-Lock is set to Never", "Settings → Display & Brightness → Auto-Lock. Some apps don't keep the screen on themselves.", $autoLock)
                if camera {
                    Check(
                        "Camera records 4K at 60 fps",
                        "Settings → Camera → Record Video → 4K at 60 fps, and Preserve Settings → Camera Mode on.",
                        $cameraSet
                    )
                }
                Check(
                    "The Mac can reach this iPhone over Wi-Fi",
                    "In Xcode → Window → Devices and Simulators, tick Connect via network for this iPhone, then unplug it.",
                    $paired
                )
                HStack(alignment: .top) {
                    Image(systemName: charged ? "checkmark.circle.fill" : "circle").foregroundStyle(charged ? .green : .secondary)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Charged to 100%")
                        Text(charged ? "At 100%." : "At \(battery.level.map { "\($0)%" } ?? "an unknown level"). Charge to 100% first.")
                            .font(.footnote).foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Before you start")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Ready", action: onStart).disabled(!all) }
            }
            .fullScreenCover(isPresented: $white) {
                Color.white.ignoresSafeArea().statusBarHidden().onTapGesture { white = false }
            }
        }
    }
}

private struct Check: View {
    let title: String
    let detail: String
    @Binding var on: Bool

    init(_ title: String, _ detail: String, _ on: Binding<Bool>) {
        self.title = title
        self.detail = detail
        _on = on
    }

    var body: some View {
        Button { on.toggle() } label: {
            HStack(alignment: .top) {
                Image(systemName: on ? "checkmark.circle.fill" : "circle").foregroundStyle(on ? .green : .secondary)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).foregroundStyle(.primary)
                    Text(detail).font(.footnote).foregroundStyle(.secondary)
                }
            }
        }
    }
}
