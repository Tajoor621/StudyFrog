import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import PencilKit
import UserNotifications
import sf_engine

@main
struct StudyForgeApp: App {
    @AppStorage("appearance") private var appearance = "system"
    var body: some Scene { WindowGroup { ForgeView().preferredColorScheme(appearance == "dark" ? .dark : appearance == "light" ? .light : nil) } }
}
struct ExportDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.png,.movie] }
    var data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}
final class CanvasProxy: ObservableObject { weak var canvas: PKCanvasView? }
struct DrawingCanvas: UIViewRepresentable {
    @Binding var drawing: PKDrawing
    let proxy: CanvasProxy
    let erasing: Bool
    let color: UIColor
    func makeUIView(context: Context) -> PKCanvasView {
        let view = PKCanvasView();view.backgroundColor = .clear;view.isOpaque = false;view.drawingPolicy = .anyInput;view.isScrollEnabled = false;view.delegate = context.coordinator;proxy.canvas = view;return view
    }
    func updateUIView(_ view: PKCanvasView, context: Context) {
        context.coordinator.parent = self
        if view.drawing != drawing { view.drawing = drawing }
        view.tool = erasing ? PKEraserTool(.bitmap) : PKInkingTool(.pen,color: color,width: 5)
        proxy.canvas = view
    }
    func makeCoordinator() -> Coordinator { Coordinator(self) }
    final class Coordinator: NSObject, PKCanvasViewDelegate {
        var parent: DrawingCanvas
        init(_ parent: DrawingCanvas) { self.parent = parent }
        func canvasViewDrawingDidChange(_ canvasView: PKCanvasView) { parent.drawing = canvasView.drawing }
    }
}
@MainActor final class ForgeStore: ObservableObject {
    @Published var prompt = "A scientific botanical illustration of a fern"
    @Published var negative = "blurry, distorted"
    @Published var image: UIImage?
    @Published var drawings = [PKDrawing()]
    @Published var layerNames = ["Ink"]
    @Published var visible = [true]
    @Published var opacity = [1.0]
    @Published var blends = ["normal"]
    @Published var selected = 0
    @Published var records = [ProjectRecord]()
    @Published var folder = ""
    @Published var message = "Import a compatible local SD 1.x GGUF to generate offline."
    @Published var working = false
    @Published var percent = 0
    @Published var videoURL: URL?
    @Published var seed: Int64 = 42
    @Published var resolution = 256
    var job: Task<Void,Never>?
    var videoTask: Task<Void,Error>?
    var computerJob: (ComputerEngine,String)?
    func refresh() { do { records = try IOSProjectStore().list() } catch { message = error.localizedDescription } }
    func resetLayers() { drawings = [PKDrawing()];layerNames = ["Ink"];visible = [true];opacity = [1];blends = ["normal"];selected = 0 }
    func addLayer() { guard drawings.count < 16 else { message = "Maximum 16 layers";return };drawings.append(PKDrawing());layerNames.append("Layer \(drawings.count)");visible.append(true);opacity.append(1);blends.append("normal");selected = drawings.count-1 }
    func composite() -> UIImage? {
        guard let image else { return nil }
        let format = UIGraphicsImageRendererFormat();format.scale = 1
        return UIGraphicsImageRenderer(size: image.size,format: format).image { _ in
            image.draw(at: .zero)
            let canonical = CGRect(x: 0,y: 0,width: 1024,height: 1024*image.size.height/image.size.width)
            for i in drawings.indices where visible[i] {
                drawings[i].image(from: canonical,scale: image.size.width/1024).draw(in: CGRect(origin: .zero,size: image.size),blendMode: blends[i] == "multiply" ? .multiply : blends[i] == "screen" ? .screen : .normal,alpha: opacity[i])
            }
        }
    }
    func save(draft: Bool = false) {
        guard let image else { return }
        do { try IOSProjectStore().save(title: String(prompt.prefix(80)),prompt: prompt,negative: negative,image: image,drawings: drawings,names: layerNames,visible: visible,opacity: opacity,blends: blends,draft: draft,folder: folder);refresh();message = "Editable project saved on this device" }
        catch { message = error.localizedDescription }
    }
    func open(_ record: ProjectRecord) {
        do {
            let loaded = try IOSProjectStore().load(record);image = loaded.0;drawings = loaded.1;layerNames = record.layerNames
            if drawings.isEmpty { resetLayers() } else { visible = record.visible?.count == drawings.count ? record.visible! : Array(repeating: true,count: drawings.count);opacity = record.opacity?.count == drawings.count ? record.opacity! : Array(repeating: 1,count: drawings.count);blends = record.blends?.count == drawings.count ? record.blends! : Array(repeating: "normal",count: drawings.count);selected = 0 }
            prompt = record.prompt;negative = record.negative;folder = record.folder ?? "";message = "Editable project opened"
        } catch { message = error.localizedDescription }
    }
    func install(_ url: URL) {
        working = true
        job = Task { do { let hash = try await Task.detached { try IOSModelStore.install(url) }.value;message = "Model imported · SHA-256 \(hash.prefix(16))" } catch { message = error.localizedDescription };working = false }
    }
    func generate(photo: Bool) {
        guard !working else { return }
        guard FileManager.default.fileExists(atPath: IOSModelStore.model.path) else { message = "Import a compatible SD 1.x GGUF model first";return }
        let text = prompt, excluded = negative, number = seed, dimension = resolution, source = photo ? composite() : nil
        if photo && source == nil { message = "Import a photo first";return }
        working = true;percent = 0;UIApplication.shared.isIdleTimerDisabled = true
        job = Task {
            let monitor = Task { while !Task.isCancelled { percent = Int(sf_progress());try? await Task.sleep(nanoseconds: 500_000_000) } }
            defer { monitor.cancel();working = false;UIApplication.shared.isIdleTimerDisabled = false }
            do {
                let result = try await Task.detached { try NativeDiffusion.generate(model: IOSModelStore.model,prompt: text,negative: excluded,size: dimension,steps: 20,seed: number,initial: source) }.value
                image = result;resetLayers();percent = 100;message = "Generated locally · seed \(number)"
            } catch { message = error.localizedDescription }
        }
    }
    func video() {
        guard !working, let source = composite() else { return }
        let output = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString+".mp4")
        working = true;percent = 0;UIApplication.shared.isIdleTimerDisabled = true
        job = Task {
            defer { working = false;UIApplication.shared.isIdleTimerDisabled = false }
            do {
                videoTask = Task.detached { try RevealEncoder.encode(image: source,output: output) { value in Task { @MainActor in self.percent = value } } }
                try await videoTask!.value
                videoURL = output;message = "30-second 1080p reveal exported. This is not AI subject motion."
            } catch { message = error.localizedDescription }
        }
    }
    func motion(host: String,token: String) {
        guard !working,let image = composite() else { return }
        let engine = ComputerEngine(host: host,token: token),text = prompt,excluded = negative,number = seed
        working = true;percent = 0;UIApplication.shared.isIdleTimerDisabled = true
        job = Task {
            defer { working = false;computerJob = nil;UIApplication.shared.isIdleTimerDisabled = false }
            do {
                let id = try await engine.submit(image: image,prompt: text,negative: excluded,seed: number,kind: "motion")
                computerJob = (engine,id)
                while true {
                    try Task.checkCancellation();let state = try await engine.status(id)
                    percent = state["progress"] as? Int ?? 0
                    if state["status"] as? String == "done" { videoURL = try await engine.result(id);message = "AI motion video downloaded from your local computer";break }
                    if ["failed","cancelled"].contains(state["status"] as? String ?? "") { throw LocalFailure(message: state["error"] as? String ?? "Job cancelled") }
                    try await Task.sleep(nanoseconds: 5_000_000_000)
                }
            } catch { message = error.localizedDescription }
        }
    }
    func cancel() { sf_cancel();videoTask?.cancel();if let (engine,id) = computerJob { Task { try? await engine.cancel(id) } };job?.cancel();message = "Cancellation requested" }
}
struct ForgeView: View {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var store = ForgeStore()
    @StateObject private var proxy = CanvasProxy()
    @AppStorage("onboarded") private var onboarded = false
    @AppStorage("appearance") private var appearance = "system"
    @AppStorage("studyReminders") private var reminders = false
    @State private var photo: PhotosPickerItem?
    @State private var showModelPicker = false
    @State private var showExport = false
    @State private var exportData = Data()
    @State private var showSettings = false
    @State private var showLibrary = false
    @State private var showScience = false
    @State private var erasing = false
    @State private var color = Color.mint
    @State private var query = ""
    @AppStorage("computerHost") private var computerHost = ""
    @State private var computerToken = ""
    @State private var motionConsent = false
    @State private var deleteRecord: ProjectRecord?
    @State private var folderFilter = "\u{0}"
    @State private var moveRecord: ProjectRecord?
    @State private var folderDraft = ""
    private var folders: [String] { Array(Set(store.records.compactMap { $0.folder }.filter { !$0.isEmpty })).sorted() }
    private var filteredRecords: [ProjectRecord] { store.records.filter {
        (folderFilter == "\u{0}" || ($0.folder ?? "") == folderFilter) && (query.isEmpty || $0.title.localizedCaseInsensitiveContains(query) || $0.prompt.localizedCaseInsensitiveContains(query))
    } }
    private func ink(_ i: Int) -> Binding<PKDrawing> { Binding(get: { i < store.drawings.count ? store.drawings[i] : PKDrawing() },set: { if i < store.drawings.count { store.drawings[i] = $0 } }) }
    var body: some View {
        NavigationStack {
            if !onboarded {
                ScrollView { VStack(alignment: .leading,spacing: 24) {
                    Image("StudyFrogBrand").resizable().scaledToFit().accessibilityLabel("StudyFrog brand artwork")
                    Text("Create and study privately").font(.largeTitle).accessibilityAddTraits(.isHeader)
                    Text("Your projects and imported models stay on this device. No account, subscription or paid API is required. Generated science visuals require review. This app does not claim a certified child-safe mode.")
                    Button("Get started") { onboarded = true }.buttonStyle(.borderedProminent)
                }.padding() }
            } else {
                ScrollView { VStack(alignment: .leading,spacing: 16) {
                    TextEditor(text: $store.prompt).frame(minHeight: 120).overlay(RoundedRectangle(cornerRadius: 10).stroke(.secondary)).accessibilityLabel("Generation prompt").disabled(store.working)
                    TextField("Negative prompt",text: $store.negative).textFieldStyle(.roundedBorder).disabled(store.working)
                    Picker("Generation resolution",selection: $store.resolution) { Text("256px preview · 6 GB RAM").tag(256);Text("512px standard · 8 GB RAM").tag(512) }.disabled(store.working)
                    HStack { Button("Generate on device") { store.generate(photo: false) };Button("Photo conversion") { store.generate(photo: true) } }.buttonStyle(.borderedProminent).disabled(store.working)
                    if let image = store.image {
                        GeometryReader { geometry in
                            ZStack(alignment: .topLeading) {
                                Image(uiImage: image).resizable().frame(width: 1024,height: 1024*image.size.height/image.size.width)
                                ForEach(store.drawings.indices,id: \.self) { i in
                                    if store.visible[i] {
                                        if i == store.selected {
                                            DrawingCanvas(drawing: ink(i),proxy: proxy,erasing: erasing,color: UIColor(color)).frame(width: 1024,height: 1024*image.size.height/image.size.width).opacity(store.opacity[i]).blendMode(store.blends[i] == "multiply" ? .multiply : store.blends[i] == "screen" ? .screen : .normal)
                                        } else {
                                            Image(uiImage: store.drawings[i].image(from: CGRect(x: 0,y: 0,width: 1024,height: 1024*image.size.height/image.size.width),scale: 1)).frame(width: 1024,height: 1024*image.size.height/image.size.width).opacity(store.opacity[i]).blendMode(store.blends[i] == "multiply" ? .multiply : store.blends[i] == "screen" ? .screen : .normal).allowsHitTesting(false)
                                        }
                                    }
                                }
                            }.scaleEffect(geometry.size.width/1024,anchor: .topLeading)
                        }.aspectRatio(image.size,contentMode: .fit).clipped().accessibilityLabel("Editable image and ink canvas")
                        HStack { Button("Undo") { proxy.canvas?.undoManager?.undo() };Button("Redo") { proxy.canvas?.undoManager?.redo() };Toggle("Eraser",isOn: $erasing);ColorPicker("Ink",selection: $color) }
                        Picker("Ink layer",selection: $store.selected) { ForEach(store.layerNames.indices,id: \.self) { i in Text(store.layerNames[i]).tag(i) } }
                        HStack { Button("Add layer") { store.addLayer() };Toggle("Visible",isOn: $store.visible[store.selected]) }
                        Slider(value: $store.opacity[store.selected],in: 0...1) { Text("Layer opacity") }
                        TextField("Layer name",text: $store.layerNames[store.selected]).textFieldStyle(.roundedBorder)
                        Picker("Blend",selection: $store.blends[store.selected]) { Text("Normal").tag("normal");Text("Multiply").tag("multiply");Text("Screen").tag("screen") }
                        Button("Delete selected ink layer",role: .destructive) { if store.drawings.count > 1 { let index = store.selected;store.selected = 0;store.drawings.remove(at: index);store.layerNames.remove(at: index);store.visible.remove(at: index);store.opacity.remove(at: index);store.blends.remove(at: index) } }.disabled(store.drawings.count <= 1)
                    }
                    Button("Explore science · linked levels and labeled diagrams") { showScience = true }.disabled(store.working)
                    PhotosPicker("Import photo",selection: $photo,matching: .images).disabled(store.working)
                    TextField("Folder for new saves · blank means Unfiled",text: $store.folder).textFieldStyle(.roundedBorder).accessibilityLabel("Project folder")
                    HStack { Button("Save project") { store.save() };Button("Export PNG") { exportData = store.composite()?.pngData() ?? Data();showExport = true } }.disabled(store.image == nil || store.working)
                    Button("Create 30-second reveal video") { store.video() }.disabled(store.image == nil || store.working)
                    Button("Generate AI motion video on local computer") { motionConsent = true }.disabled(store.image == nil || store.working)
                    if let video = store.videoURL { ShareLink("Share video",item: video) }
                    if store.working { ProgressView(value: Double(store.percent),total: 100);Button("Cancel") { store.cancel() } }
                    Text(store.message).font(.footnote).accessibilityAddTraits(.updatesFrequently)
                    Text("On-device CPU inference uses a memory-saving 256px preview and needs a compatible GGUF and a sufficiently capable device. Keep the app open. Realistic AI motion video requires the optional local computer engine.").font(.footnote).foregroundStyle(.secondary)
                }.padding() }
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) { Button("Projects") { showLibrary = true } }
                    ToolbarItem(placement: .topBarTrailing) { Button("Settings") { showSettings = true } }
                }
            }
        }.navigationTitle("StudyForge").task { store.refresh();if store.image == nil { do { if let draft = try IOSProjectStore().draft() { store.open(draft);store.message = "Recovered editable draft" } } catch { store.message = error.localizedDescription } } }
        .onChange(of: scenePhase) { _,phase in if phase == .background && store.image != nil { store.save(draft: true) } }
        .onChange(of: photo) { _,item in Task {
            do { guard let data = try await item?.loadTransferable(type: Data.self) else { return };store.image = try IOSPhotoDecoder.decode(data);store.resetLayers();store.message = "Photo imported locally" }
            catch { store.message = error.localizedDescription }
        } }
        .alert("Send photo to your local computer?",isPresented: $motionConsent) {
            Button("Generate") { store.motion(host: computerHost,token: computerToken) }
            Button("Cancel",role: .cancel) {}
        } message: { Text("This sends your prompt and image over your local network to the engine configured in Settings. Use your own computer and a trusted network. It requires a local LTX model and CUDA GPU; no cloud or paid API is used.") }
        .fileImporter(isPresented: $showModelPicker,allowedContentTypes: [.data]) { result in do { store.install(try result.get()) } catch { store.message = error.localizedDescription } }
        .fileExporter(isPresented: $showExport,document: ExportDocument(data: exportData),contentType: .png,defaultFilename: "StudyForge") { result in if case .failure(let error) = result { store.message = error.localizedDescription } }
        .sheet(isPresented: $showScience) { StudyExplorerView(hasExistingImage: store.image != nil) { image,prompt in store.prompt = prompt;if let image { store.image = image;store.resetLayers() };store.message = "Study view selected; model-generated refinements require review" } }
        .sheet(isPresented: $showSettings) { NavigationStack { Form {
            Picker("Appearance",selection: $appearance) { Text("System").tag("system");Text("Light").tag("light");Text("Dark").tag("dark") }
            Button("Import local GGUF model") { showSettings = false;showModelPicker = true }.disabled(store.working)
            Text("No model weights are bundled. Use a complete SD 1.x GGUF and retain its model license.")
            Toggle("Daily study reminder",isOn: $reminders).onChange(of: reminders) { _,enabled in
                let center = UNUserNotificationCenter.current();center.removePendingNotificationRequests(withIdentifiers: ["study-reminder"])
                if enabled { center.requestAuthorization(options: [.alert,.sound]) { granted,_ in
                    if granted { let content = UNMutableNotificationContent();content.title = "A little time for learning";content.body = "Open StudyForge when you are ready.";center.add(UNNotificationRequest(identifier: "study-reminder",content: content,trigger: UNTimeIntervalNotificationTrigger(timeInterval: 86400,repeats: true))) }
                    else { Task { @MainActor in reminders = false } }
                } }
            }
            TextField("Computer hostname, e.g. my-mac.local",text: $computerHost).textInputAutocapitalization(.never).autocorrectionDisabled()
            SecureField("Local engine token (memory only)",text: $computerToken)
            Text("Computer motion generation is optional. On the computer, bind the engine to its private LAN address and set STUDYFORGE_VIDEO_MODEL. The token is never saved.")
            NavigationLink("Open-source licenses") { ScrollView { Text((Bundle.main.url(forResource: "third-party-licenses",withExtension: "txt").flatMap { try? String(contentsOf: $0,encoding: .utf8) }) ?? "Source licenses are included in the repository.").font(.footnote).padding() } }
            Text("No cloud analytics SDK is included in this iOS build. Projects and models are excluded from automatic backups; export important results yourself.")
        }.navigationTitle("Settings").toolbar { Button("Done") { showSettings = false } } } }
        .sheet(isPresented: $showLibrary) { NavigationStack {
            List {
                Picker("Folder",selection: $folderFilter) { Text("All folders").tag("\u{0}");Text("Unfiled").tag("");ForEach(folders,id: \.self) { Text($0).tag($0) } }
                ForEach(filteredRecords) { record in
                Button(record.title.isEmpty ? "Untitled project" : record.title) { store.open(record);showLibrary = false }
                    .swipeActions { Button("Delete",role: .destructive) { deleteRecord = record } }
                    .contextMenu { Button("Move to folder") { folderDraft = record.folder ?? "";moveRecord = record } }
                }
            }.searchable(text: $query).navigationTitle("Local projects").toolbar { Button("Done") { showLibrary = false } }
            .sheet(item: $moveRecord) { record in NavigationStack { Form {
                TextField("Folder · blank means Unfiled",text: $folderDraft)
                Button("Move project") { do { try IOSProjectStore().setFolder(record,folder: folderDraft);store.refresh();folderFilter = "\u{0}";moveRecord = nil } catch { store.message = error.localizedDescription } }
                Text(store.message).font(.footnote)
            }.navigationTitle("Project folder").toolbar { Button("Cancel") { moveRecord = nil } } } }
            .alert("Delete this project?",isPresented: Binding(get: { deleteRecord != nil },set: { if !$0 { deleteRecord = nil } })) {
                Button("Delete",role: .destructive) { if let record = deleteRecord { do { try IOSProjectStore().delete(record.id);store.refresh() } catch { store.message = error.localizedDescription } };deleteRecord = nil }
                Button("Cancel",role: .cancel) { deleteRecord = nil }
            }
        } }
    }
}
