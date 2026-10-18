import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import PencilKit
import UserNotifications
import sf_engine

@main
@MainActor struct StudyForgeApp: App {
    @StateObject private var family = FamilyAccess.shared
    @AppStorage("creativeAgeAcknowledged") private var acknowledged = false
    @AppStorage("appearance") private var appearance = "system"
    var body: some Scene { WindowGroup {
        Group { if family.restricted { FamilyLearningHomeView() } else if !acknowledged { CreativeAgeGateView() } else { ForgeView() } }
            .environmentObject(family).preferredColorScheme(appearance == "dark" ? .dark : appearance == "light" ? .light : nil)
    } }
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
    var width: CGFloat = 5
    var inkType: PKInkingTool.InkType = .pen
    var begin: ()->Void = {}
    func makeUIView(context: Context) -> PKCanvasView {
        let view = PKCanvasView();view.backgroundColor = .clear;view.isOpaque = false;view.drawingPolicy = .anyInput;view.isScrollEnabled = false;view.delegate = context.coordinator;proxy.canvas = view;return view
    }
    func updateUIView(_ view: PKCanvasView, context: Context) {
        context.coordinator.parent = self
        if view.drawing != drawing { view.drawing = drawing }
        view.tool = erasing ? PKEraserTool(.bitmap) : PKInkingTool(inkType,color: color,width: width)
        proxy.canvas = view
    }
    func makeCoordinator() -> Coordinator { Coordinator(self) }
    final class Coordinator: NSObject, PKCanvasViewDelegate {
        var parent: DrawingCanvas
        init(_ parent: DrawingCanvas) { self.parent = parent }
        func canvasViewDidBeginUsingTool(_ canvasView: PKCanvasView) { parent.begin() }
        func canvasViewDrawingDidChange(_ canvasView: PKCanvasView) { let value = canvasView.drawing;parent.drawing = value;if parent.drawing != value { canvasView.drawing = parent.drawing } }
    }
}
@MainActor final class ForgeStore: ObservableObject {
    @Published var prompt = "A scientific botanical illustration of a fern"
    @Published var negative = "blurry, distorted"
    @Published var image: UIImage?
    @Published var layers = [IOSInkLayer()]
    private var history = [LayerHistoryState]()
    private var future = [LayerHistoryState]()
    @Published var selected = 0
    @Published var records = [ProjectRecord]()
    @Published var folder = ""
    @Published var message = "Import a compatible local SD 1.x GGUF to generate offline."
    @Published var working = false
    @Published var percent = 0
    @Published var videoURL: URL?
    @Published var seed: Int64 = 42
    @Published var resolution = 256
    @Published var inferenceSteps = 20
    @Published var photoStrength = 0.65
    @Published var profile = GenerationProfile()
    var job: Task<Void,Never>?
    var videoTask: Task<Void,Error>?
    var computerJob: (ComputerEngine,String)?
    func refresh() { do { records = try IOSProjectStore().list() } catch { message = error.localizedDescription } }
    func resetLayers() { selected = 0;layers = [IOSInkLayer()];history.removeAll();future.removeAll() }
    private func trimHistory() {
        while history.count > 32 || history.reduce(0,{ $0+$1.pointCount }) > 250_000 { history.removeFirst() }
        func rasterPixels() -> Int {
            var images = [ObjectIdentifier:UIImage]()
            for state in history+future+[LayerHistoryState(layers: layers,selected: selected)] { for layer in state.layers { if let image = layer.raster { images[ObjectIdentifier(image)] = image } } }
            return images.values.reduce(0) { $0+($1.cgImage.map { $0.width*$0.height } ?? 0) }
        }
        while (!history.isEmpty || !future.isEmpty) && rasterPixels() > 16_000_000 { if !history.isEmpty { history.removeFirst() } else { future.removeFirst() } }
    }
    func checkpoint() { history.append(LayerHistoryState(layers: layers,selected: selected));future.removeAll();trimHistory() }
    func undo() { guard let state = history.popLast() else { return };future.append(LayerHistoryState(layers: layers,selected: selected));selected = 0;layers = state.layers;selected = max(0,min(state.selected,layers.count-1));trimHistory() }
    func redo() { guard let state = future.popLast() else { return };history.append(LayerHistoryState(layers: layers,selected: selected));selected = 0;layers = state.layers;selected = max(0,min(state.selected,layers.count-1));trimHistory() }
    func addLayer(raster: UIImage? = nil) {
        guard image != nil,layers.count < 16 else { message = "Create a base image first; maximum 16 layers";return }
        let layer = IOSInkLayer(name: raster == nil ? "Layer \(layers.count+1)" : "Photo \(layers.count+1)",raster: raster)
        do { try IOSLayerRenderer.validate(layers+[layer]);checkpoint();layers.append(layer);selected = layers.count-1;trimHistory() } catch { message = error.localizedDescription }
    }
    func editLayer(_ id: UUID,checkpoint save: Bool = true,_ change: (inout IOSInkLayer)->Void) {
        guard let index = layers.firstIndex(where: { $0.id == id }) else { return }
        var next = layers;change(&next[index])
        do { try IOSLayerRenderer.validate(next);if save { checkpoint() };layers = next;trimHistory() } catch { message = error.localizedDescription }
    }
    func updateDrawing(_ id: UUID,mask: Bool,value: PKDrawing) { editLayer(id,checkpoint: false) { if mask { $0.mask = value } else { $0.drawing = value } } }
    func deleteLayer() { guard layers.count > 1 else { return };checkpoint();var next = layers;next.remove(at: selected);selected = 0;layers = next;trimHistory() }
    func moveLayer(_ delta: Int) { let destination = max(0,min(layers.count-1,selected+delta));guard destination != selected else { return };checkpoint();var next = layers;let layer = next.remove(at: selected);next.insert(layer,at: destination);layers = next;selected = destination }
    func composite() -> UIImage? { guard let image else { return nil };do { return try IOSLayerRenderer.composite(base: image,layers: layers) } catch { message = error.localizedDescription;return nil } }
    func save(draft: Bool = false) {
        guard let image else { return }
        let allowance = EditorSaveAllowance();allowance.begin()
        let snapshot = layers,title = String(prompt.prefix(80)),text = prompt,excluded = negative,folder = folder,profile = profile,seed = seed,steps = inferenceSteps,resolution = resolution,strength = photoStrength
        Task {
            defer { allowance.end() }
            do {
                _ = try await Task.detached {
                    try IOSProjectStore().save(title: title,prompt: text,negative: excluded,image: image,drawings: snapshot.map(\.drawing),names: snapshot.map(\.name),visible: snapshot.map(\.visible),opacity: snapshot.map(\.opacity),blends: snapshot.map(\.blend),draft: draft,folder: folder,profile: profile,seed: seed,steps: steps,resolution: resolution,strength: strength,layerAssets: snapshot)
                }.value
                refresh();message = "Editable project snapshot saved on this device"
            } catch { message = error.localizedDescription }
        }
    }

    func open(_ record: ProjectRecord) {
        do {
            let projectStore = try IOSProjectStore();let loaded = try projectStore.load(record);let assets = try projectStore.loadLayerAssets(record)
            let count = loaded.1.count
            let restored = (0..<count).map { i in IOSInkLayer(name: String(record.layerNames[i].prefix(80)).trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Layer \(i+1)" : String(record.layerNames[i].prefix(80)),drawing: loaded.1[i],raster: assets.rasters[i],mask: assets.masks[i],maskEnabled: record.maskEnabled?.count == count ? record.maskEnabled![i] : true,visible: record.visible?.count == count ? record.visible![i] : true,opacity: record.opacity?.count == count ? record.opacity![i] : 1,blend: record.blends?.count == count ? record.blends![i] : "normal",transform: record.transforms?.count == count ? record.transforms![i] : LayerTransform()) }
            try IOSLayerRenderer.validate(restored);image = loaded.0;selected = 0;layers = restored;history.removeAll();future.removeAll()
            prompt = record.prompt;negative = record.negative;folder = record.folder ?? "";profile = record.profile ?? GenerationProfile()
            inferenceSteps = (10...40).contains(record.steps ?? 20) ? (record.steps ?? 20) : 20
            photoStrength = (0.1...1).contains(record.strength ?? 0.65) ? (record.strength ?? 0.65) : 0.65
            seed = (0...2_147_483_647).contains(record.seed ?? 42) ? (record.seed ?? 42) : 42
            resolution = [256,512].contains(record.resolution ?? 256) ? (record.resolution ?? 256) : 256;message = "Editable project opened"
        } catch { message = error.localizedDescription }
    }
    func install(_ url: URL) {
        working = true
        job = Task { do { let hash = try await Task.detached { try IOSModelStore.install(url) }.value;message = "Model imported · SHA-256 \(hash.prefix(16))" } catch { message = error.localizedDescription };working = false }
    }
    func generate(photo: Bool) {
        guard !FamilyAccess.shared.restricted else { message = "Generation is locked by family controls";return }
        guard !working else { return }
        guard FileManager.default.fileExists(atPath: IOSModelStore.model.path) else { message = "Import a compatible SD 1.x GGUF model first";return }
        let text: String
        do { text = try profile.apply(prompt) } catch { message = error.localizedDescription;return }
        let excluded = negative, number = seed, dimension = resolution, source = photo ? composite() : nil, steps = inferenceSteps, strength = photoStrength
        if photo && source == nil { message = "Import a photo first";return }
        working = true;percent = 0;UIApplication.shared.isIdleTimerDisabled = true
        job = Task {
            let monitor = Task { while !Task.isCancelled { percent = Int(sf_progress());try? await Task.sleep(nanoseconds: 500_000_000) } }
            defer { monitor.cancel();working = false;UIApplication.shared.isIdleTimerDisabled = false }
            do {
                let result = try await Task.detached { try NativeDiffusion.generate(model: IOSModelStore.model,prompt: text,negative: excluded,size: dimension,steps: steps,seed: number,initial: source,strength: strength) }.value
                try Task.checkCancellation();guard !FamilyAccess.shared.restricted else { throw CancellationError() };image = result;resetLayers();percent = 100;message = "Generated locally · seed \(number)"
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
    func computerImage(host: String,token: String,control: String,scale: Double,photo: Bool) {
        guard !working,!FamilyAccess.shared.restricted else { return }
        let source = photo || control != "none" ? composite() : nil
        if (photo || control != "none") && source == nil { message = "Import a photo or prepared guidance map first";return }
        let text: String
        do { text = try profile.apply(prompt) } catch { message = error.localizedDescription;return }
        let engine = ComputerEngine(host: host,token: token),excluded = negative,number = seed,steps = inferenceSteps,strength = photoStrength
        working = true;percent = 0;UIApplication.shared.isIdleTimerDisabled = true
        job = Task {
            defer { working = false;UIApplication.shared.isIdleTimerDisabled = false;computerJob = nil }
            do {
                let id = try await engine.submit(image: source,prompt: text,negative: excluded,seed: number,kind: "image",control: control,controlScale: scale,steps: steps,strength: strength)
                computerJob = (engine,id)
                while true {
                    try Task.checkCancellation()
                    guard !FamilyAccess.shared.restricted else { throw CancellationError() }
                    let status = try await engine.status(id)
                    switch status["status"] as? String {
                    case "done":
                        let file = try await engine.result(id,video: false);defer { try? FileManager.default.removeItem(at: file) }
                        let result = try IOSPhotoDecoder.decode(Data(contentsOf: file))
                        try Task.checkCancellation();guard !FamilyAccess.shared.restricted else { throw CancellationError() }
                        image = result;resetLayers();percent = 100;message = "Computer image generated · seed \(number). Review model output before study use";return
                    case "failed","cancelled": throw LocalFailure(message: status["error"] as? String ?? "Computer generation stopped")
                    default: message = "Computer image job: \(status["status"] as? String ?? "queued")";try await Task.sleep(nanoseconds: 2_000_000_000)
                    }
                }
            } catch { if let (engine,id) = computerJob { try? await engine.cancel(id) };message = error.localizedDescription }
        }
    }
    func motion(host: String,token: String) {
        guard !FamilyAccess.shared.restricted else { return }
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
    @EnvironmentObject private var family: FamilyAccess
    @State private var showFamilyControls = false
    @State private var showLibrary = false
    @State private var showScience = false
    @State private var erasing = false
    @State private var color = Color.mint
    @State private var query = ""
    @AppStorage("computerHost") private var computerHost = ""
    @State private var computerToken = ""
    @State private var motionConsent = false
    @State private var computerImageConsent = false
    @State private var imageGuidance = "none"
    @State private var imageGuidanceScale = 0.7
    @State private var computerUsesPhoto = false
    @State private var deleteRecord: ProjectRecord?
    @State private var folderFilter = "\u{0}"
    @State private var moveRecord: ProjectRecord?
    @State private var folderDraft = ""
    private var folders: [String] { Array(Set(store.records.compactMap { $0.folder }.filter { !$0.isEmpty })).sorted() }
    private var filteredRecords: [ProjectRecord] { store.records.filter {
        (folderFilter == "\u{0}" || ($0.folder ?? "") == folderFilter) && (query.isEmpty || $0.title.localizedCaseInsensitiveContains(query) || $0.prompt.localizedCaseInsensitiveContains(query))
    } }
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
                    GenerationControlsView(profile: $store.profile,steps: $store.inferenceSteps,strength: $store.photoStrength,prompt: store.prompt).disabled(store.working)
                    Picker("Generation resolution",selection: $store.resolution) { Text("256px preview · 6 GB RAM").tag(256);Text("512px standard · 8 GB RAM").tag(512) }.disabled(store.working)
                    HStack { Button("Generate on device") { store.generate(photo: false) };Button("Photo conversion") { store.generate(photo: true) } }.buttonStyle(.borderedProminent).disabled(store.working)
                    if store.image != nil { LayerStudioView(store: store,proxy: proxy).disabled(store.working) }
                    Button("Explore science · linked levels and labeled diagrams") { showScience = true }.disabled(store.working)
                    PhotosPicker("Import photo",selection: $photo,matching: .images).disabled(store.working)
                    TextField("Folder for new saves · blank means Unfiled",text: $store.folder).textFieldStyle(.roundedBorder).accessibilityLabel("Project folder")
                    HStack { Button("Save project") { store.save() };Button("Export PNG") { if let data = store.composite()?.pngData() { exportData = data;showExport = true } else { store.message = "Could not render PNG; check the layer settings" } } }.disabled(store.image == nil || store.working)
                    Button("Create 30-second reveal video") { store.video() }.disabled(store.image == nil || store.working)
                    DisclosureGroup("Local computer image generation") {
                        Toggle("Use current image for photo conversion",isOn: $computerUsesPhoto)
                        Picker("Image guidance",selection: $imageGuidance) { Text("None").tag("none");Text("ControlNet Canny edges from photo").tag("canny");Text("ControlNet prepared depth map").tag("depth");Text("ControlNet prepared OpenPose map").tag("pose");Text("IP-Adapter reference photo").tag("reference") }
                        Slider(value: $imageGuidanceScale,in: 0...1) { Text("Image guidance strength") }
                        Text("Requires your own computer and separately prepared local SD 1.x guidance weights. Depth and pose modes expect prepared maps.").font(.footnote)
                        Button("Generate image with local computer") { computerImageConsent = true }.disabled(store.working)
                    }
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
        .onDisappear { if family.restricted { store.cancel() } }
        .onChange(of: scenePhase) { _,phase in if phase == .background && store.image != nil { store.save(draft: true) } }
        .onChange(of: photo) { _,item in Task {
            do { guard let data = try await item?.loadTransferable(type: Data.self) else { return };guard !store.working,!family.restricted else { throw LocalFailure(message: "Workspace changed during photo import; please retry") };store.image = try IOSPhotoDecoder.decode(data);store.resetLayers();store.message = "Photo imported locally" }
            catch { store.message = error.localizedDescription }
        } }
        .alert("Send an image job to your local computer?",isPresented: $computerImageConsent) {
            Button("Generate") { store.computerImage(host: computerHost,token: computerToken,control: imageGuidance,scale: imageGuidanceScale,photo: computerUsesPhoto) }
            Button("Cancel",role: .cancel) {}
        } message: { Text("Your prompt and selected photo/map are sent to the computer host in Settings over your local network. Use your own computer and a trusted network. No cloud or paid API is used.") }
        .alert("Send photo to your local computer?",isPresented: $motionConsent) {
            Button("Generate") { store.motion(host: computerHost,token: computerToken) }
            Button("Cancel",role: .cancel) {}
        } message: { Text("This sends your prompt and image over your local network to the engine configured in Settings. Use your own computer and a trusted network. It requires a local LTX model and CUDA GPU; no cloud or paid API is used.") }
        .fileImporter(isPresented: $showModelPicker,allowedContentTypes: [.data]) { result in do { store.install(try result.get()) } catch { store.message = error.localizedDescription } }
        .fileExporter(isPresented: $showExport,document: ExportDocument(data: exportData),contentType: .png,defaultFilename: "StudyForge") { result in if case .failure(let error) = result { store.message = error.localizedDescription } }
        .sheet(isPresented: $showScience) { StudyExplorerView(hasExistingImage: store.image != nil) { image,prompt in store.prompt = prompt;if let image { store.image = image;store.resetLayers() };store.message = "Study view selected; model-generated refinements require review" } }
        .sheet(isPresented: $showFamilyControls) { FamilyControlsView() }
        .sheet(isPresented: $showSettings) { NavigationStack { Form {
            Button("Family access · parent PIN") { showSettings = false;showFamilyControls = true }
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
