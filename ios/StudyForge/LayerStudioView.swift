import SwiftUI
import PhotosUI
import PencilKit

private struct FrozenLayerPreview: View,Equatable {
    let layer: IOSInkLayer
    let size: CGSize
    static func ==(left: Self,right: Self) -> Bool { left.size == right.size && left.layer.drawing == right.layer.drawing && left.layer.mask == right.layer.mask && left.layer.maskEnabled == right.layer.maskEnabled && left.layer.raster === right.layer.raster }
    var body: some View {
        if let image = try? IOSLayerRenderer.foreground(layer,size: size) { Image(uiImage: image).resizable().frame(width: size.width,height: size.height) }
        else { Text("Layer preview unavailable") }
    }
}
private struct LayerMaskPreview: View,Equatable {
    let drawing: PKDrawing
    let enabled: Bool
    let size: CGSize
    static func ==(left: Self,right: Self) -> Bool { left.enabled == right.enabled && left.size == right.size && left.drawing == right.drawing }
    var body: some View {
        if enabled && !drawing.strokes.isEmpty {
            if let image = try? IOSLayerRenderer.maskImage(drawing,size: size,alpha: true) { Image(uiImage: image).resizable().frame(width: size.width,height: size.height) }
            else { Rectangle().fill(.clear).frame(width: size.width,height: size.height) }
        }
        else { Rectangle().fill(.white).frame(width: size.width,height: size.height) }
    }
}
struct LayerStudioView: View {
    @ObservedObject var store: ForgeStore
    @ObservedObject var proxy: CanvasProxy
    @State private var erasing = false
    @State private var color = Color.mint
    @State private var width = 5.0
    @State private var inkStyle = "pen"
    @State private var maskEditing = false
    @State private var restoring = false
    @State private var sliderEditing = false
    @State private var rasterPhoto: PhotosPickerItem?
    private var active: IOSInkLayer { store.layers[store.layers.indices.contains(store.selected) ? store.selected : 0] }
    private var inkType: PKInkingTool.InkType { inkStyle == "pencil" ? .pencil : inkStyle == "marker" ? .marker : .pen }
    private func drawing(_ id: UUID,mask: Bool) -> Binding<PKDrawing> {
        Binding(get: { guard let layer = store.layers.first(where: { $0.id == id }) else { return PKDrawing() };return mask ? layer.mask : layer.drawing },set: { store.updateDrawing(id,mask: mask,value: $0) })
    }
    private func field<T>(_ key: WritableKeyPath<IOSInkLayer,T>) -> Binding<T> { let id = active.id;return Binding(get: { (store.layers.first { $0.id == id } ?? active)[keyPath:key] },set: { value in store.editLayer(id) { $0[keyPath:key] = value } }) }
    private func transform(_ key: WritableKeyPath<LayerTransform,Double>) -> Binding<Double> { let id = active.id;return Binding(get: { (store.layers.first { $0.id == id } ?? active).transform[keyPath:key] },set: { value in store.editLayer(id,checkpoint: !sliderEditing) { $0.transform[keyPath:key] = value } }) }
    private func sliderChanged(_ editing: Bool) { if editing && !sliderEditing { store.checkpoint() };sliderEditing = editing }
    var body: some View {
        VStack(alignment: .leading,spacing: 12) {
            if let image = store.image {
                let size = IOSLayerRenderer.canonical(image.size).size
                GeometryReader { geometry in
                    Group {
                        if maskEditing {
                            ZStack {
                                Rectangle().fill(.white)
                                DrawingCanvas(drawing: drawing(active.id,mask: true),proxy: proxy,erasing: false,color: restoring ? .white : .black,width: CGFloat(width),begin: { store.checkpoint() })
                            }.frame(width: size.width,height: size.height)
                        } else {
                            ZStack(alignment: .topLeading) {
                                Image(uiImage: image).resizable().frame(width: size.width,height: size.height)
                                ForEach(Array(store.layers.enumerated()),id: \.element.id) { index,layer in
                                    if layer.visible {
                                        layerContent(layer,selected: index == store.selected,size: size)
                                            .opacity(layer.opacity).blendMode(layer.blend == "multiply" ? .multiply : layer.blend == "screen" ? .screen : .normal)
                                            .scaleEffect(CGFloat(layer.transform.scale)).rotationEffect(.degrees(layer.transform.rotation))
                                            .offset(x: size.width*CGFloat(layer.transform.x),y: size.height*CGFloat(layer.transform.y))
                                    }
                                }
                            }.frame(width: size.width,height: size.height)
                        }
                    }.scaleEffect(geometry.size.width/size.width,anchor: .topLeading)
                }.aspectRatio(image.size,contentMode: .fit).clipped().accessibilityLabel(maskEditing ? "Layer mask editor in local coordinates; black hides, white restores" : "Editable image and layer canvas")
                HStack { Button("Undo edit") { store.undo() };Button("Redo edit") { store.redo() } }
                Picker("Active layer",selection: $store.selected) { ForEach(store.layers.indices,id: \.self) { Text(store.layers[$0].name).tag($0) } }
                HStack { Button("Add ink layer") { store.addLayer() };PhotosPicker("Add photo layer",selection: $rasterPhoto,matching: .images) }
                Toggle("Edit mask · black hides / white restores",isOn: $maskEditing)
                if maskEditing { Toggle("Restore mask with white ink",isOn: $restoring);Text("Mask preview uses local layer coordinates. Turn off Edit mask to see the composite.").font(.caption) }
                else { Toggle("Ink eraser",isOn: $erasing);ColorPicker("Ink color",selection: $color);Picker("Brush",selection: $inkStyle) { Text("Pen").tag("pen");Text("Pencil").tag("pencil");Text("Marker").tag("marker") } }
                Slider(value: $width,in: 1...50) { Text("Brush width") }
                TextField("Layer name",text: field(\.name)).textFieldStyle(.roundedBorder)
                Toggle("Layer visible",isOn: field(\.visible))
                Slider(value: Binding(get: { active.opacity },set: { value in store.editLayer(active.id,checkpoint: !sliderEditing) { $0.opacity = value } }),in: 0...1,onEditingChanged: sliderChanged) { Text("Layer opacity") }
                Picker("Blend",selection: field(\.blend)) { Text("Normal").tag("normal");Text("Multiply").tag("multiply");Text("Screen").tag("screen") }
                Toggle("Mask enabled",isOn: field(\.maskEnabled))
                Button("Clear selected layer mask") { store.editLayer(active.id) { $0.mask = PKDrawing() } }
                DisclosureGroup("Move, scale and rotate") {
                    Slider(value: transform(\.x),in: -1...1,onEditingChanged: sliderChanged) { Text("Horizontal shift") }
                    Slider(value: transform(\.y),in: -1...1,onEditingChanged: sliderChanged) { Text("Vertical shift") }
                    Slider(value: transform(\.scale),in: 0.1...4,onEditingChanged: sliderChanged) { Text("Layer scale") }
                    Slider(value: transform(\.rotation),in: -180...180,onEditingChanged: sliderChanged) { Text("Layer rotation") }
                    Button("Reset transform") { store.editLayer(active.id) { $0.transform = LayerTransform() } }
                }
                HStack { Button("Move backward") { store.moveLayer(-1) };Button("Move forward") { store.moveLayer(1) } }
                Button("Delete selected layer",role: .destructive) { store.deleteLayer() }.disabled(store.layers.count <= 1)
            }
        }.onChange(of: rasterPhoto) { _,item in Task {
            let base = store.image
            do {
                guard let data = try await item?.loadTransferable(type: Data.self) else { return }
                let image = try IOSPhotoDecoder.decode(data,maxSide: 1024)
                guard !store.working,store.image === base,!FamilyAccess.shared.restricted else { throw LocalFailure(message: "Workspace changed during photo import; please retry") }
                store.addLayer(raster: image)
            }
            catch { store.message = error.localizedDescription }
            rasterPhoto = nil
        } }
    }
    @ViewBuilder private func layerContent(_ layer: IOSInkLayer,selected: Bool,size: CGSize) -> some View {
        if selected {
            ZStack {
                if let raster = layer.raster { Image(uiImage: raster).resizable().scaledToFit().frame(width: size.width,height: size.height) }
                DrawingCanvas(drawing: drawing(layer.id,mask: false),proxy: proxy,erasing: erasing,color: UIColor(color),width: CGFloat(width),inkType: inkType,begin: { store.checkpoint() }).frame(width: size.width,height: size.height)
            }.compositingGroup().mask { LayerMaskPreview(drawing: layer.mask,enabled: layer.maskEnabled,size: size).equatable() }
        } else { FrozenLayerPreview(layer: layer,size: size).equatable().allowsHitTesting(false) }
    }
}
