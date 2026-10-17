import SwiftUI

struct StudyExplorerView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var catalog: StudyCatalog?
    @State private var selected = "human-body"
    @State private var labels = true
    @State private var notes = ""
    @State private var preview: UIImage?
    @State private var error = ""
    @State private var replace = false
    @State private var pendingDiagram = false
    let hasExistingImage: Bool
    let use: (UIImage?,String) -> Void
    var body: some View {
        NavigationStack {
            ScrollView { VStack(alignment: .leading,spacing: 18) {
                if let catalog, let node = try? catalog.node(selected) {
                    Menu("Choose study topic") { Button("Body → system → organ → cell") { selected = "human-body" };Button("Mitosis sequence") { selected = "prophase" } }
                    Text((try? catalog.path(selected).map(\.title).joined(separator: " › ")) ?? "").font(.caption)
                    Text(node.title).font(.title2).accessibilityAddTraits(.isHeader)
                    if let preview { Image(uiImage: preview).resizable().scaledToFit().accessibilityHidden(true) }
                    Text(node.summary)
                    Toggle("Show diagram labels",isOn: $labels)
                    Text("Diagram description").font(.headline).accessibilityAddTraits(.isHeader);Text(node.alt)
                    ForEach(node.children,id: \.self) { id in Button("Explore deeper: "+((try? catalog.node(id).title) ?? id)) { selected = id } }
                    if let next = node.next { Button("Next phase: "+((try? catalog.node(next).title) ?? next)) { selected = next } }
                    if let previous = node.parent ?? catalog.nodes.first(where: { $0.next == selected })?.id { Button("Previous view: "+((try? catalog.node(previous).title) ?? previous)) { selected = previous } }
                    TextField("Additional generation instructions",text: $notes,axis: .vertical).textFieldStyle(.roundedBorder).onChange(of: notes) { _,value in if value.count > 500 { notes = String(value.prefix(500)) } }
                    Button("Open diagram in editor") { request(true) }.buttonStyle(.borderedProminent)
                    Button("Use this view as generation prompt") { request(false) }
                    Text(catalog.notice).font(.footnote)
                    Text("Sources").font(.headline).accessibilityAddTraits(.isHeader)
                    ForEach(catalog.sources.filter { node.sourceIds.contains($0.id) }) { source in Link(source.title+" · opens browser",destination: source.url) }
                }
                if !error.isEmpty { Text(error).foregroundStyle(.red) }
            }.padding() }
            .navigationTitle("Science Explorer")
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Close") { dismiss() } } }
            .task { do { catalog = try StudyCatalog.load();refresh() } catch { self.error = error.localizedDescription } }
            .onChange(of: selected) { _,_ in refresh() }.onChange(of: labels) { _,_ in refresh() }
            .alert("Replace the current study view?",isPresented: $replace) { Button("Continue") { finish() };Button("Cancel",role: .cancel) {} } message: { Text("Save your current project first if you want to keep unsaved edits and its prompt.") }
        }
    }
    private func refresh() { do { preview = try catalog?.render(selected,labels: labels);error = "" } catch { self.error = error.localizedDescription } }
    private func request(_ diagram: Bool) { pendingDiagram = diagram;if hasExistingImage { replace = true } else { finish() } }
    private func finish() {
        guard let catalog else { return }
        do { let prompt = try catalog.generationPrompt(selected)+(notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "" : " Additional instructions: "+notes);use(pendingDiagram ? try catalog.render(selected,labels: labels) : nil,prompt);dismiss() }
        catch { self.error = error.localizedDescription }
    }
}
