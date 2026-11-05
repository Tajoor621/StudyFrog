import SwiftUI
import UniformTypeIdentifiers

struct PhoneVideoRemixView: View {
    @ObservedObject var store: ForgeStore
    @State private var person = true
    @State private var start = "0"
    @State private var seconds = "15"
    @State private var rights = false
    @State private var videoPicker = false
    @State private var modelPicker = false
    @State private var clearHistory = false
    @State private var reportURL: URL?
    var body: some View {
        DisclosureGroup("Offline phone video remix") {
            Text("Everything stays on this phone. Current canvas becomes the background. Preserve original people and AAC sound. Up to 15 seconds / 480p / 15fps, SDR source up to 1080p and 64 MB. Keep the app open. Other source audio formats and larger clips use the computer engine.").font(.footnote)
            Button("Import person-removal ONNX · 6.16 MB") { modelPicker = true }.disabled(store.working)
            Text("Use the PPHumanSeg FP32 model linked in Help; its published checksum is verified. This is separate from the SD GGUF image model. Green-screen mode needs no model.").font(.footnote)
            Picker("Removal mode",selection: $person) { Text("Person · imported ONNX").tag(true);Text("Green screen · no model").tag(false) }
            TextField("Source start in seconds",text: $start).keyboardType(.decimalPad)
            TextField("Duration · 0.5 to 15 seconds",text: $seconds).keyboardType(.decimalPad)
            Toggle("I have permission to edit the people, video and soundtrack",isOn: $rights)
            Button("Choose video and render on phone") {
                guard validTimes else { store.message = "Use a start from 0–3600 and duration 0.5–15 seconds";return }
                videoPicker = true
            }.disabled(!rights || store.working || store.image == nil)
            Button("Recover last completed phone video") {
                do { store.videoURL = try PhoneRenderRecords.shared.latest();store.message = store.videoURL == nil ? "No retained completed phone result" : "Saved phone video recovered; use Share video below" }
                catch { store.message = error.localizedDescription }
            }
            Button("Prepare latest-attempt report for export") {
                do { try PhoneRenderRecords.shared.recover();let report = PhoneRenderRecords.shared.report;guard FileManager.default.fileExists(atPath: report.path) else { throw LocalFailure(message: "Render a phone video first") };reportURL = report }
                catch { store.message = error.localizedDescription }
            }
            if let reportURL { ShareLink("Export phone-render report",item: reportURL) }
            Button("Clear saved phone video and render reports",role: .destructive) { clearHistory = true }
            Text("One completed phone result is retained until replaced or cleared. A failed/interrupted attempt keeps the previous completed result. Reports include timing and before/after process/thermal snapshots, not peak-memory or independent physical certification.").font(.footnote)
        }.disabled(store.working)
        .alert("Clear phone-video history?",isPresented: $clearHistory) {
            Button("Clear history",role: .destructive) {
                do {
                    try PhoneRenderRecords.shared.clear()
                    if store.videoURL?.deletingLastPathComponent() == PhoneRenderRecords.shared.directory { store.videoURL = nil }
                    reportURL = nil;store.message = "Saved phone result and reports cleared"
                } catch { store.message = error.localizedDescription }
            }
            Button("Cancel",role: .cancel) {}
        } message: { Text("Deletes the retained phone result and reports in this app. Your original video, model and already exported copies remain.") }
        .fileImporter(isPresented: $modelPicker,allowedContentTypes: [.data]) { result in
            do {
                guard !FamilyAccess.shared.restricted,!store.working else { throw LocalFailure(message: "Workspace is locked or busy") }
                let source = try result.get(),scoped = source.startAccessingSecurityScopedResource()
                store.working = true;store.message = "Verifying person-removal model…"
                store.job = Task {
                    defer { store.working = false;if scoped { source.stopAccessingSecurityScopedResource() } }
                    let install = Task.detached { try PhonePersonModel.install(source) }
                    do {
                        try await withTaskCancellationHandler(operation: { try await install.value },onCancel: { install.cancel() })
                        store.message = "Person-removal model verified and installed locally"
                    } catch { store.message = error.localizedDescription }
                }
            } catch { store.message = error.localizedDescription }
        }
        .fileImporter(isPresented: $videoPicker,allowedContentTypes: [.movie]) { result in
            do {
                guard rights,validTimes,let from = Double(start),let duration = Double(seconds) else { throw LocalFailure(message: "Check permission and clip times") }
                store.phoneRemix(source: try result.get(),person: person,start: from,seconds: duration)
            } catch { store.message = error.localizedDescription }
        }
    }
    private var validTimes: Bool {
        guard let from = Double(start),let duration = Double(seconds) else { return false }
        return from.isFinite && (0...3600).contains(from) && duration.isFinite && (0.5...15).contains(duration)
    }
}
