import SwiftUI
import UniformTypeIdentifiers

/// Copy file-provider input in bounded chunks off the main actor.
enum VideoRemixSource {
    static func copy(_ source: URL,to output: URL,limit: Int) throws {
        let input = try FileHandle(forReadingFrom: source);defer { try? input.close() }
        guard FileManager.default.createFile(atPath: output.path,contents: nil) else { throw LocalFailure(message: "Cannot create temporary video") }
        let writer = try FileHandle(forWritingTo: output);defer { try? writer.close() }
        var completed = false
        defer { if !completed { try? FileManager.default.removeItem(at: output) } }
        var size = 0
        while true {
            try Task.checkCancellation()
            guard let block = try input.read(upToCount: 65536),!block.isEmpty else { break }
            size += block.count
            guard size <= limit else { throw LocalFailure(message: "Video exceeds this profile's input limit") }
            try writer.write(contentsOf: block)
        }
        guard size > 0 else { throw LocalFailure(message: "Video is empty") }
        try writer.synchronize();completed = true
    }
}

struct VideoRemixView: View {
    @ObservedObject var store: ForgeStore
    let host: String
    let token: String
    @State private var profile = "mobile"
    @State private var mode = "person"
    @State private var start = "0"
    @State private var seconds = "15"
    @State private var rights = false
    @State private var picker = false
    private var maximum: Double { profile == "mobile" ? 15 : profile == "laptop" ? 30 : 60 }
    var body: some View {
        DisclosureGroup("Video remix · keep people and original sound") {
            Text("Import or generate the new background on the canvas first. Your own computer replaces the video background; original faces, performance and the first audio track remain. Audio is re-encoded AAC. No voice cloning or face reenactment.").font(.footnote)
            Picker("Output budget",selection: $profile) {
                Text("Mobile · 15s / 480p / 15fps / 64 MB").tag("mobile")
                Text("Laptop · 30s / 720p / 24fps / 128 MB").tag("laptop")
                Text("Desktop · 60s / 1080p / 30fps / 128 MB").tag("desktop")
            }
            Picker("Background removal",selection: $mode) {
                Text("Person · local PPHumanSeg ONNX").tag("person")
                Text("Green screen · no model").tag("green")
            }
            TextField("Source start in seconds",text: $start).keyboardType(.decimalPad)
            TextField("Clip duration in seconds",text: $seconds).keyboardType(.decimalPad)
            Toggle("I have permission to edit the video, people and soundtrack",isOn: $rights)
            Button("Choose video and send to my computer") {
                guard let from = Double(start),from.isFinite,from >= 0,from <= 3600,
                      let duration = Double(seconds),duration.isFinite,duration >= 0.5,duration <= maximum else {
                    store.message = "Use a start from 0–3600 seconds and duration 0.5–\(Int(maximum)) seconds";return
                }
                picker = true
            }.disabled(store.image == nil || !rights || store.working)
            Text("Portrait framing is preserved; small videos are not upscaled. Green clothing disappears in green-screen mode. Person edges may be imperfect. These are conservative limits, not speed guarantees. Processing uses the configured computer, including from a phone. Source copies are deleted at job completion/failure; unused uploads expire after 30 minutes. No phone-only AI remix is claimed.").font(.footnote)
        }.disabled(store.working)
        .fileImporter(isPresented: $picker,allowedContentTypes: [.movie,UTType(filenameExtension: "webm") ?? .movie]) { result in
            do {
                let source = try result.get()
                guard rights,let from = Double(start),let duration = Double(seconds),from.isFinite,duration.isFinite,
                      from >= 0,from <= 3600,duration >= 0.5,duration <= maximum else { throw LocalFailure(message: "Check video permission and clip times") }
                store.remix(source: source,host: host,token: token,profile: profile,mode: mode,start: from,seconds: duration)
            } catch { store.message = error.localizedDescription }
        }
    }
}
