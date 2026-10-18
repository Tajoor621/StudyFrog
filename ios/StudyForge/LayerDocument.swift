import Foundation
import UIKit
import PencilKit
import CoreImage

struct LayerTransform: Codable, Equatable {
    var x = 0.0
    var y = 0.0
    var scale = 1.0
    var rotation = 0.0
    var valid: Bool { x.isFinite && y.isFinite && scale.isFinite && rotation.isFinite && (-1...1).contains(x) && (-1...1).contains(y) && (0.1...4).contains(scale) && (-180...180).contains(rotation) }
}
struct IOSInkLayer: Identifiable {
    var id = UUID()
    var name = "Ink"
    var drawing = PKDrawing()
    var raster: UIImage?
    var mask = PKDrawing()
    var maskEnabled = true
    var visible = true
    var opacity = 1.0
    var blend = "normal"
    var transform = LayerTransform()
    var pointCount: Int { drawing.strokes.reduce(0) { $0+$1.path.count }+mask.strokes.reduce(0) { $0+$1.path.count } }
    var strokeCount: Int { drawing.strokes.count+mask.strokes.count }
}
struct LayerHistoryState {
    let layers: [IOSInkLayer]
    let selected: Int
    var pointCount: Int { layers.reduce(0) { $0+$1.pointCount } }
}
struct IOSLayerRenderer {
    private static let context = CIContext(options: [.cacheIntermediates:false])
    static func canonical(_ size: CGSize) -> CGRect { CGRect(x: 0,y: 0,width: 1024,height: 1024*size.height/size.width) }
    static func rasterPixels(_ layers: [IOSInkLayer]) -> Int { layers.reduce(0) { $0+($1.raster?.cgImage.map { $0.width*$0.height } ?? 0) } }
    static func validate(_ layers: [IOSInkLayer]) throws {
        guard (1...16).contains(layers.count),Set(layers.map(\.id)).count == layers.count,
              layers.reduce(0, { $0+$1.pointCount }) <= 100_000,layers.reduce(0, { $0+$1.strokeCount }) <= 512,
              rasterPixels(layers) <= 8_000_000,
              layers.allSatisfy({ !$0.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && $0.name.count <= 80 && $0.transform.valid && $0.opacity.isFinite && (0...1).contains($0.opacity) && ["normal","multiply","screen"].contains($0.blend) && ($0.raster == nil || ($0.raster!.cgImage != nil && $0.raster!.cgImage!.width <= 1024 && $0.raster!.cgImage!.height <= 1024)) }) else { throw LocalFailure(message: "Layer limits exceeded or layer settings are invalid") }
    }
    static func maskImage(_ drawing: PKDrawing,size: CGSize,alpha: Bool = false) throws -> UIImage {
        let format = UIGraphicsImageRendererFormat();format.scale = 1;format.opaque = true
        let image = UIGraphicsImageRenderer(size: size,format: format).image { ctx in
            UIColor.white.setFill();ctx.fill(CGRect(origin: .zero,size: size))
            drawing.image(from: canonical(size),scale: size.width/1024).draw(in: CGRect(origin: .zero,size: size))
        }
        if !alpha { return image }
        guard let source = CIImage(image: image),let cg = context.createCGImage(source.applyingFilter("CIMaskToAlpha"),from: CGRect(origin: .zero,size: size)) else { throw LocalFailure(message: "Could not render layer mask") }
        return UIImage(cgImage: cg)
    }
    static func foreground(_ layer: IOSInkLayer,size: CGSize) throws -> UIImage {
        let format = UIGraphicsImageRendererFormat();format.scale = 1
        let image = UIGraphicsImageRenderer(size: size,format: format).image { _ in
            if let raster = layer.raster {
                let scale = min(size.width/raster.size.width,size.height/raster.size.height)
                let fitted = CGSize(width: raster.size.width*scale,height: raster.size.height*scale)
                raster.draw(in: CGRect(x: (size.width-fitted.width)/2,y: (size.height-fitted.height)/2,width: fitted.width,height: fitted.height))
            }
            layer.drawing.image(from: canonical(size),scale: size.width/1024).draw(in: CGRect(origin: .zero,size: size))
        }
        if !layer.maskEnabled || layer.mask.strokes.isEmpty { return image }
        guard let input = CIImage(image: image),let mask = CIImage(image: try maskImage(layer.mask,size: size)) else { throw LocalFailure(message: "Could not render layer mask") }
        let bounds = CGRect(origin: .zero,size: size)
        let clear = CIImage(color: CIColor(red: 0,green: 0,blue: 0,alpha: 0)).cropped(to: bounds)
        let output = input.applyingFilter("CIBlendWithMask",parameters: [kCIInputBackgroundImageKey:clear,kCIInputMaskImageKey:mask])
        guard let cg = context.createCGImage(output,from: bounds) else { throw LocalFailure(message: "Could not apply layer mask") };return UIImage(cgImage: cg)
    }
    static func composite(base: UIImage,layers: [IOSInkLayer]) throws -> UIImage {
        let format = UIGraphicsImageRendererFormat();format.scale = 1
        var failure: Error?
        let image = UIGraphicsImageRenderer(size: base.size,format: format).image { context in
            base.draw(at: .zero)
            for layer in layers where layer.visible && layer.opacity > 0 {
                let cg = context.cgContext;cg.saveGState();cg.clip(to: CGRect(origin: .zero,size: base.size))
                cg.translateBy(x: base.size.width*CGFloat(0.5+layer.transform.x),y: base.size.height*CGFloat(0.5+layer.transform.y))
                cg.rotate(by: CGFloat(layer.transform.rotation * .pi/180));cg.scaleBy(x: CGFloat(layer.transform.scale),y: CGFloat(layer.transform.scale))
                guard let foreground = try? foreground(layer,size: base.size) else { failure = LocalFailure(message: "Could not composite a layer mask");cg.restoreGState();continue }
                foreground.draw(in: CGRect(x: -base.size.width/2,y: -base.size.height/2,width: base.size.width,height: base.size.height),blendMode: layer.blend == "multiply" ? .multiply : layer.blend == "screen" ? .screen : .normal,alpha: CGFloat(layer.opacity))
                cg.restoreGState()
            }
        }
        if let failure { throw failure };return image
    }
}


@MainActor final class EditorSaveAllowance {
    private var identifier = UIBackgroundTaskIdentifier.invalid
    func begin() { identifier = UIApplication.shared.beginBackgroundTask(withName: "Save StudyForge project") { [weak self] in Task { @MainActor in self?.end() } } }
    func end() { if identifier != .invalid { UIApplication.shared.endBackgroundTask(identifier);identifier = .invalid } }
}
