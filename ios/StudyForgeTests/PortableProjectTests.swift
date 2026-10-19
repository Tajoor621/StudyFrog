import XCTest
import UIKit
import PencilKit
@testable import StudyForge

final class PortableProjectTests: XCTestCase {
    private func fixture() throws -> Data { try Data(contentsOf: XCTUnwrap(Bundle(for: Self.self).url(forResource: "portable-project-v1",withExtension: "json"))) }
    private func pixel(_ image: UIImage) throws -> [UInt8] {
        let cg = try XCTUnwrap(image.cgImage);var bytes = [UInt8](repeating: 0,count: 4)
        try bytes.withUnsafeMutableBytes { buffer in
            let context = try XCTUnwrap(CGContext(data: buffer.baseAddress,width: 1,height: 1,bitsPerComponent: 8,bytesPerRow: 4,space: CGColorSpaceCreateDeviceRGB(),bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue))
            context.draw(cg,in: CGRect(x: 0,y: 0,width: 1,height: 1))
        };return bytes
    }
    func testSharedFixturePreservesLayersPixelsTransformsAndNativeSave() throws {
        let document = try PortableProject.decode(fixture())
        XCTAssertEqual(document.folder,"Biology");XCTAssertEqual(document.selected,1);XCTAssertEqual(document.layers.count,2)
        XCTAssertFalse(document.layers[0].visible);XCTAssertEqual(document.layers[0].transform,LayerTransform(x: 0.2,y: -0.1,scale: 0.8,rotation: 90))
        XCTAssertEqual(document.layers[0].opacity,0.4);XCTAssertEqual(document.layers[0].blend,"screen")
        XCTAssertEqual(document.layers[1].raster?.cgImage?.width,2)
        let encoded = try PortableProject.encode(base: document.base,layers: document.layers,selected: document.selected,title: document.title,prompt: document.prompt,negative: document.negative,folder: document.folder)
        let copy = try PortableProject.decode(encoded)
        XCTAssertEqual(copy.layers.map(\.name),document.layers.map(\.name));XCTAssertEqual(copy.layers.map(\.transform),document.layers.map(\.transform));XCTAssertEqual(copy.selected,1)
        XCTAssertEqual(try pixel(XCTUnwrap(copy.layers[1].raster)),[0,0,255,255])
        let composite = try pixel(IOSLayerRenderer.composite(base: copy.base,layers: copy.layers))
        XCTAssertTrue((126...129).contains(composite[0]));XCTAssertEqual(composite[2],255)
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let store = try IOSProjectStore(root: root)
        let record = try store.save(title: copy.title,prompt: copy.prompt,negative: copy.negative,image: copy.base,drawings: copy.layers.map(\.drawing),names: copy.layers.map(\.name),folder: copy.folder,layerAssets: copy.layers)
        XCTAssertEqual(record.blends,["screen","multiply"]);XCTAssertEqual(try store.loadLayerAssets(record).rasters.count,2)
    }
    func testDamagedAssetsVersionsDimensionsAndSettingsAreRejected() throws {
        let data = try fixture()
        let changes: [(inout [String:Any])->Void] = [
            { $0["version"] = 2 },{ $0["selected"] = 2 },{ $0["folder"] = "bad\nfolder" },{ $0["layers"] = [] },
            { var base = $0["base"] as! [String:Any];base["sha256"] = String(repeating: "0",count: 64);$0["base"] = base },
            { var base = $0["base"] as! [String:Any];base["width"] = 2049;$0["base"] = base },
            { var base = $0["base"] as! [String:Any];base["width"] = 1;$0["base"] = base },
            { var layers = $0["layers"] as! [[String:Any]];layers[0]["blend"] = "unsupported";$0["layers"] = layers },
            { var layers = $0["layers"] as! [[String:Any]];layers[0]["opacity"] = 2;$0["layers"] = layers },
            { var layers = $0["layers"] as! [[String:Any]];var transform = layers[0]["transform"] as! [String:Any];transform["scale"] = 0;layers[0]["transform"] = transform;$0["layers"] = layers }
        ]
        for change in changes { var json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String:Any]);change(&json);XCTAssertThrowsError(try PortableProject.decode(JSONSerialization.data(withJSONObject: json))) }
        XCTAssertThrowsError(try PortableProject.decode(Data(repeating: 32,count: PortableProject.limit+1)))
        XCTAssertThrowsError(try PortableProject.decode(Data((String(repeating: "[",count: 17)+String(repeating: "]",count: 17)).utf8)))
    }
    func testExportCreatesRasterCopyAndRetainsOriginalPencilKitInk() throws {
        let document = try PortableProject.decode(fixture())
        let points = [PKStrokePoint(location: CGPoint(x: 512,y: 512),timeOffset: 0,size: CGSize(width: 40,height: 40),opacity: 1,force: 1,azimuth: 0,altitude: .pi/2)]
        var layers = document.layers
        layers[1].drawing = PKDrawing(strokes: [PKStroke(ink: PKInk(.pen,color: .red),path: PKStrokePath(controlPoints: points,creationDate: Date()))])
        let original = layers[1].drawing.dataRepresentation()
        let data = try PortableProject.encode(base: document.base,layers: layers,selected: 1,title: document.title,prompt: document.prompt,negative: document.negative,folder: document.folder)
        let copy = try PortableProject.decode(data)
        XCTAssertTrue(copy.layers[1].drawing.strokes.isEmpty);XCTAssertNotNil(copy.layers[1].raster)
        XCTAssertEqual(original,layers[1].drawing.dataRepresentation())
    }
}
