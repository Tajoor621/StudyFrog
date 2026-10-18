import XCTest
import UIKit
import PencilKit
@testable import StudyForge

final class LayerDocumentTests: XCTestCase {
    private func image(_ color: UIColor,size: Int = 200) -> UIImage {
        let format = UIGraphicsImageRendererFormat();format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: CGFloat(size),height: CGFloat(size)),format: format).image { ctx in color.setFill();ctx.fill(CGRect(x: 0,y: 0,width: CGFloat(size),height: CGFloat(size))) }
    }
    private func mask(_ color: UIColor) -> PKStroke {
        let points = [CGPoint(x: 400,y: 512),CGPoint(x: 624,y: 512)].enumerated().map { index,point in PKStrokePoint(location: point,timeOffset: Double(index)*0.1,size: CGSize(width: 100,height: 100),opacity: 1,force: 1,azimuth: 0,altitude: .pi/2) }
        return PKStroke(ink: PKInk(.pen,color: color),path: PKStrokePath(controlPoints: points,creationDate: Date()))
    }
    private func pixel(_ image: UIImage,_ x: Int = 100,_ y: Int = 100) throws -> [UInt8] {
        let cg = try XCTUnwrap(image.cgImage);var bytes = [UInt8](repeating: 0,count: cg.width*cg.height*4)
        let drawn = bytes.withUnsafeMutableBytes { data -> Bool in
            guard let context = CGContext(data: data.baseAddress,width: cg.width,height: cg.height,bitsPerComponent: 8,bytesPerRow: cg.width*4,space: CGColorSpaceCreateDeviceRGB(),bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue) else { return false }
            context.draw(cg,in: CGRect(x: 0,y: 0,width: CGFloat(cg.width),height: CGFloat(cg.height)));return true
        }
        XCTAssertTrue(drawn);let offset = (y*cg.width+x)*4;return Array(bytes[offset..<(offset+4)])
    }
    func testMaskHidesRestoresAndDisablesWithoutDestroyingPhoto() throws {
        let base = image(.white),raster = image(.red)
        var layer = IOSInkLayer(name: "Photo",raster: raster,mask: PKDrawing(strokes: [mask(.black)]))
        XCTAssertEqual(try pixel(IOSLayerRenderer.composite(base: base,layers: [layer])),[255,255,255,255])
        layer.maskEnabled = false
        XCTAssertEqual(try pixel(IOSLayerRenderer.composite(base: base,layers: [layer])),[255,0,0,255])
        layer.maskEnabled = true;layer.mask = PKDrawing(strokes: [mask(.black),mask(.white)])
        XCTAssertEqual(try pixel(IOSLayerRenderer.composite(base: base,layers: [layer])),[255,0,0,255])
        XCTAssertEqual(try pixel(raster),[255,0,0,255])
    }
    func testTransformsMultiplyAndBoundsPreserveTheBase() throws {
        let base = image(.white)
        let layer = IOSInkLayer(name: "Photo",raster: image(.red),opacity: 0.5,blend: "multiply",transform: LayerTransform(x: 0.25,scale: 0.5,rotation: 90))
        let result = try IOSLayerRenderer.composite(base: base,layers: [layer])
        XCTAssertEqual(try pixel(result,25,100),[255,255,255,255])
        let center = try pixel(result,150,100);XCTAssertEqual(center[0],255);XCTAssertTrue((126...129).contains(center[1]));XCTAssertEqual(center[3],255)
        var invalid = layer;invalid.transform.scale = 0;XCTAssertThrowsError(try IOSLayerRenderer.validate([invalid]))
        invalid.transform.scale = 1;invalid.transform.x = .nan;XCTAssertThrowsError(try IOSLayerRenderer.validate([invalid]))
    }
    func testRasterMaskTransformAndGenerationSettingsSurviveReopening() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let store = try IOSProjectStore(root: root)
        let layer = IOSInkLayer(name: "Photo",raster: image(.red),mask: PKDrawing(strokes: [mask(.black)]),maskEnabled: false,visible: false,opacity: 0.5,blend: "multiply",transform: LayerTransform(x: 0.2,scale: 0.8))
        let profile = GenerationProfile(enabled: true,style: "scientific")
        let record = try store.save(title: "Layer test",prompt: "Fern",negative: "blur",image: image(.white),drawings: [layer.drawing],names: [layer.name],folder: "Biology",profile: profile,seed: 7,steps: 30,resolution: 512,strength: 0.4,layerAssets: [layer])
        let reopened = try IOSProjectStore(root: root);let saved = try XCTUnwrap(reopened.list().first)
        let assets = try reopened.loadLayerAssets(saved)
        XCTAssertEqual(try pixel(XCTUnwrap(assets.rasters[0])),[255,0,0,255]);XCTAssertEqual(assets.masks[0].strokes.count,1)
        XCTAssertEqual(saved.visible,[false]);XCTAssertEqual(saved.opacity,[0.5]);XCTAssertEqual(saved.blends,["multiply"]);XCTAssertEqual(saved.transforms,[layer.transform]);XCTAssertEqual(saved.maskEnabled,[false]);XCTAssertEqual(saved.profile,profile);XCTAssertEqual(saved.steps,30)
        try reopened.setFolder(record,folder: "Moved");try reopened.rename(record,title: "Renamed")
        XCTAssertEqual(try reopened.list().first?.folder,"Moved")
        var legacy = record;legacy.assetVersion = nil;legacy.hasRasters = nil;legacy.maskEnabled = nil;legacy.transforms = nil
        let oldAssets = try reopened.loadLayerAssets(legacy);XCTAssertNil(oldAssets.rasters[0]);XCTAssertTrue(oldAssets.masks[0].strokes.isEmpty)
    }
    @MainActor func testDocumentUndoRestoresPhotoTransformsDeletionAndMask() throws {
        let store = ForgeStore();store.image = image(.white);store.resetLayers();store.addLayer(raster: image(.red))
        let id = try XCTUnwrap(store.layers.last?.id)
        store.editLayer(id) { $0.transform.x = 0.3 };store.undo();XCTAssertEqual(store.layers.last?.transform.x,0)
        store.redo();XCTAssertEqual(store.layers.last?.transform.x,0.3)
        store.deleteLayer();XCTAssertEqual(store.layers.count,1);store.undo();XCTAssertEqual(store.layers.count,2)
        store.editLayer(id) { $0.mask = PKDrawing(strokes: [mask(.black)]) }
        store.undo();XCTAssertTrue(store.layers.last!.mask.strokes.isEmpty);store.redo();XCTAssertEqual(store.layers.last?.mask.strokes.count,1)
    }
}
