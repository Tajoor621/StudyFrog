import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

@main
struct StudyForgeApp: App {
    var body: some Scene { WindowGroup { ForgeView() } }
}
struct PNGDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.png] }
    var data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}
struct ForgeView: View {
    @State private var prompt = "Show a plant cell"
    @State private var photo: PhotosPickerItem?
    @State private var image: UIImage?
    @State private var showExport = false
    @State private var message = "Offline schematic and photo import ready. No model runtime is bundled."
    @State private var projects: [URL] = []
    private var directory: URL { FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0] }
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text("CREATE · LEARN · REFINE").font(.caption).foregroundStyle(.mint)
                    TextEditor(text: $prompt).frame(height: 120).padding(8).background(.quaternary).clipShape(RoundedRectangle(cornerRadius: 16))
                    if let image { Image(uiImage: image).resizable().scaledToFit().clipShape(RoundedRectangle(cornerRadius: 16)) }
                    Button("Create offline schematic") { diagram() }.buttonStyle(.borderedProminent).tint(.teal)
                    PhotosPicker("Import photo", selection: $photo, matching: .images)
                    HStack {
                        Button("Save project") { save() }.disabled(image == nil)
                        Button("Export PNG") { showExport = true }.disabled(image == nil)
                    }.buttonStyle(.bordered)
                    Text(message).font(.footnote)
                    Text("Local projects").font(.headline)
                    ForEach(projects, id: \.self) { file in
                        Button(file.deletingPathExtension().lastPathComponent) {
                            image = UIImage(contentsOfFile: file.path)
                            let meta = file.deletingPathExtension().appendingPathExtension("txt")
                            prompt = (try? String(contentsOf: meta, encoding: .utf8)) ?? ""
                        }
                    }
                }.padding()
            }.navigationTitle("StudyForge")
        }.preferredColorScheme(.dark)
        .task { refresh() }
        .onChange(of: photo) { _,newValue in
            Task {
                if let data = try? await newValue?.loadTransferable(type: Data.self), let loaded = UIImage(data: data) {
                    image = loaded; message = "Photo imported locally."
                }
            }
        }
        .fileExporter(isPresented: $showExport, document: PNGDocument(data: image?.pngData() ?? Data()), contentType: .png, defaultFilename: "StudyForge") { result in
            message = (try? result.get()) == nil ? "Export cancelled or failed" : "PNG exported"
        }
    }
    private func refresh() { projects = ((try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)) ?? []).filter { $0.pathExtension == "png" }.sorted { $0.lastPathComponent > $1.lastPathComponent } }
    private func save() {
        guard let data = image?.pngData() else { return }
        let url = directory.appendingPathComponent("Project-\(Int(Date().timeIntervalSince1970))-(UUID().uuidString.prefix(4)).png")
        do { try data.write(to: url, options: .atomic); try prompt.write(to: url.deletingPathExtension().appendingPathExtension("txt"), atomically: true, encoding: .utf8); refresh();message = "Image and prompt saved on device." }
        catch { message = error.localizedDescription }
    }
    private func diagram() {
        image = UIGraphicsImageRenderer(size: CGSize(width: 1024, height: 576)).image { context in
            let c = context.cgContext
            UIColor(red: 0.06, green: 0.09, blue: 0.16, alpha: 1).setFill();c.fill(CGRect(x: 0,y: 0,width: 1024,height: 576))
            let attrs: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 26), .foregroundColor: UIColor.white]
            ("StudyForge / " + String(prompt.prefix(60))).draw(in: CGRect(x: 30,y: 30,width: 950,height: 90), withAttributes: attrs)
            for (i,label) in ["Whole", "System", "Organ", "Cell"].enumerated() {
                let x = CGFloat(40 + i * 246)
                UIColor.systemMint.setStroke();c.setLineWidth(5);c.strokeEllipse(in: CGRect(x: x,y: 190,width: 190,height: 190))
                UIColor.systemPurple.setFill();c.fillEllipse(in: CGRect(x: x + 65,y: 255,width: 60,height: 60))
                label.draw(at: CGPoint(x: x + 25,y: 410), withAttributes: attrs)
            }
            "Schematic template — review before academic use".draw(at: CGPoint(x: 30,y: 520),withAttributes: attrs)
        }
        message = "Template generated on device; this is not AI inference."
    }
}
