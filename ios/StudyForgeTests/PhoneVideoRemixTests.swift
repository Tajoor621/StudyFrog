import XCTest
import UIKit
import AVFoundation
import CoreImage
@testable import StudyForge

final class PhoneVideoRemixTests: XCTestCase {
    private func fixture(_ name: String,_ ext: String) throws -> URL {
        try XCTUnwrap(Bundle(for: Self.self).url(forResource: name,withExtension: ext))
    }
    func testActualOrtModelAndMaskContract() throws {
        let model = try fixture("phone-person","onnx")
        XCTAssertTrue(try PhonePersonModel.verified(model))
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root,withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let target = root.appendingPathComponent("installed.onnx"),invalid = root.appendingPathComponent("invalid.onnx")
        try FileManager.default.copyItem(at: model,to: target);try Data([1,2,3]).write(to: invalid)
        XCTAssertThrowsError(try PhonePersonModel.install(invalid,to: target))
        XCTAssertTrue(try PhonePersonModel.verified(target))
        let format = UIGraphicsImageRendererFormat();format.scale = 1
        let image = UIGraphicsImageRenderer(size: CGSize(width: 160,height: 96),format: format).image { ctx in UIColor.red.setFill();ctx.fill(CGRect(x: 0,y: 0,width: 160,height: 96)) }
        let mask = try PhonePersonMask(model: model).mask(try XCTUnwrap(image.cgImage))
        XCTAssertEqual(mask.width,192);XCTAssertEqual(mask.height,192)
        XCTAssertThrowsError(try PhoneVideoRemix.dimensions(width: 3840,height: 2160))
        XCTAssertEqual(try PhoneVideoRemix.dimensions(width: 1080,height: 1920),CGSize(width: 480,height: 852))
    }
    func testActualPersonVideoPreservesSilentSource() throws {
        let source = try fixture("phone-silent","mp4"),model = try fixture("phone-person","onnx")
        let output = FileManager.default.temporaryDirectory.appendingPathComponent("person-\(UUID().uuidString).mp4")
        defer { try? FileManager.default.removeItem(at: output) }
        let format = UIGraphicsImageRendererFormat();format.scale = 1
        let background = UIGraphicsImageRenderer(size: CGSize(width: 160,height: 96),format: format).image { ctx in UIColor.blue.setFill();ctx.fill(CGRect(x: 0,y: 0,width: 160,height: 96)) }
        try PhoneVideoRemix.render(source: source,background: background,output: output,personMode: true,start: 0,seconds: 1,model: model,control: PhoneRenderControl(),progress: { _ in })
        let asset = AVURLAsset(url: output)
        XCTAssertTrue(asset.tracks(withMediaType: .audio).isEmpty);XCTAssertEqual(asset.tracks(withMediaType: .video).count,1)
        XCTAssertEqual(CMTimeGetSeconds(asset.duration),1,accuracy: 0.1)
    }
    func testOfflineGreenVideoKeepsAudioAndCancellationRemovesOutput() throws {
        let source = try fixture("phone-source","mp4")
        let output = FileManager.default.temporaryDirectory.appendingPathComponent("test-\(UUID().uuidString).mp4")
        defer { try? FileManager.default.removeItem(at: output) }
        let format = UIGraphicsImageRendererFormat();format.scale = 1
        let background = UIGraphicsImageRenderer(size: CGSize(width: 160,height: 96),format: format).image { ctx in UIColor.blue.setFill();ctx.fill(CGRect(x: 0,y: 0,width: 160,height: 96)) }
        try PhoneVideoRemix.render(source: source,background: background,output: output,personMode: false,start: 0,seconds: 1,control: PhoneRenderControl(),progress: { _ in })
        let asset = AVURLAsset(url: output)
        XCTAssertEqual(asset.tracks(withMediaType: .audio).count,1)
        XCTAssertEqual(asset.tracks(withMediaType: .video).count,1)
        XCTAssertEqual(CMTimeGetSeconds(asset.duration),1,accuracy: 0.1)
        let generator = AVAssetImageGenerator(asset: asset)
        let frame = try generator.copyCGImage(at: CMTime(seconds: 0.1,preferredTimescale: 600),actualTime: nil)
        var pixels = [UInt8](repeating: 0,count: frame.width*frame.height*4)
        let drawn = pixels.withUnsafeMutableBytes { data -> Bool in
            guard let context = CGContext(data: data.baseAddress,width: frame.width,height: frame.height,bitsPerComponent: 8,bytesPerRow: frame.width*4,space: CGColorSpaceCreateDeviceRGB(),bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue|CGBitmapInfo.byteOrder32Big.rawValue) else { return false }
            context.draw(frame,in: CGRect(x: 0,y: 0,width: frame.width,height: frame.height));return true
        }
        XCTAssertTrue(drawn);XCTAssertGreaterThan(pixels[(5*frame.width+5)*4+2],170)
        XCTAssertGreaterThan(pixels[(48*frame.width+80)*4],170)
        try FileManager.default.removeItem(at: output)
        try PhoneVideoRemix.render(source: fixture("phone-portrait","mp4"),background: background,output: output,personMode: false,start: 0,seconds: 1,control: PhoneRenderControl(),progress: { _ in })
        let portrait = try XCTUnwrap(AVURLAsset(url: output).tracks(withMediaType: .video).first)
        XCTAssertEqual(portrait.naturalSize,CGSize(width: 96,height: 160))
        try FileManager.default.removeItem(at: output)
        let control = PhoneRenderControl();control.stop("cancelled")
        XCTAssertThrowsError(try PhoneVideoRemix.render(source: source,background: background,output: output,personMode: false,start: 0,seconds: 1,control: control,progress: { _ in }))
        XCTAssertFalse(FileManager.default.fileExists(atPath: output.path))
    }
}
