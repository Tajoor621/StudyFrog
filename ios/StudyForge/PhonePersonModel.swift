import Foundation
import UIKit
import CryptoKit
import OnnxRuntimeBindings

/// The same checksum-pinned Apache-2.0 PPHumanSeg FP32 model as the computer engine.
enum PhonePersonModel {
    static let digest = "552d8a984054e59b5d773d24b9b12022b22046ceb2bbc4c9aaeaceb36a9ddf24"
    static let bytes = 6_163_938
    static var file: URL { FileManager.default.urls(for: .applicationSupportDirectory,in: .userDomainMask)[0].appendingPathComponent("video-person.onnx") }
    static func verified(_ url: URL) throws -> Bool {
        guard (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) == bytes else { return false }
        let input = try FileHandle(forReadingFrom: url);defer { try? input.close() }
        var hash = SHA256()
        while let block = try input.read(upToCount: 65536),!block.isEmpty { hash.update(data: block) }
        return hash.finalize().map { String(format: "%02x",$0) }.joined() == digest
    }
    static func install(_ source: URL,to target: URL = PhonePersonModel.file) throws {
        try FileManager.default.createDirectory(at: target.deletingLastPathComponent(),withIntermediateDirectories: true)
        let stage = target.deletingLastPathComponent().appendingPathComponent("person-\(UUID().uuidString).part")
        defer { try? FileManager.default.removeItem(at: stage) }
        try VideoRemixSource.copy(source,to: stage,limit: bytes)
        guard try verified(stage) else { throw LocalFailure(message: "Person model checksum mismatch; choose the PPHumanSeg FP32 ONNX linked in Help") }
        try Task.checkCancellation()
        if FileManager.default.fileExists(atPath: target.path) { _ = try FileManager.default.replaceItemAt(target,withItemAt: stage) }
        else { try FileManager.default.moveItem(at: stage,to: target) }
        var url = target;var values = URLResourceValues();values.isExcludedFromBackup = true;try url.setResourceValues(values)
    }
}

final class PhonePersonMask {
    private let environment: ORTEnv
    private let session: ORTSession
    private let input: String
    private let outputs: Set<String>
    init(model: URL = PhonePersonModel.file) throws {
        guard try PhonePersonModel.verified(model) else { throw LocalFailure(message: "Import the verified PPHumanSeg ONNX first") }
        environment = try ORTEnv(loggingLevel: .warning)
        let options = try ORTSessionOptions();try options.setIntraOpNumThreads(2)
        session = try ORTSession(env: environment,modelPath: model.path,sessionOptions: options)
        let names = try session.inputNames();guard names.count == 1 else { throw LocalFailure(message: "Incompatible segmentation input") }
        input = names[0];outputs = Set(try session.outputNames())
    }
    func mask(_ image: CGImage) throws -> CGImage {
        let plane = 192*192
        var pixels = [UInt8](repeating: 0,count: plane*4)
        let rendered = pixels.withUnsafeMutableBytes { bytes -> Bool in
            guard let context = CGContext(data: bytes.baseAddress,width: 192,height: 192,bitsPerComponent: 8,bytesPerRow: 192*4,space: CGColorSpaceCreateDeviceRGB(),bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue|CGBitmapInfo.byteOrder32Big.rawValue) else { return false }
            context.interpolationQuality = .high;context.draw(image,in: CGRect(x: 0,y: 0,width: 192,height: 192));return true
        }
        guard rendered else { throw LocalFailure(message: "Cannot prepare person mask") }
        var rgb = [Float](repeating: 0,count: plane*3)
        for i in 0..<plane { for c in 0..<3 { rgb[c*plane+i] = Float(pixels[i*4+c])/127.5-1 } }
        let data = rgb.withUnsafeBufferPointer { Data(buffer: $0) }
        let value = try ORTValue(tensorData: NSMutableData(data: data),elementType: .float,shape: [1,3,192,192])
        let result = try session.run(withInputs: [input:value],outputNames: outputs,runOptions: nil)
        guard let tensor = result.values.first,try tensor.tensorTypeAndShapeInfo().shape.map({ $0.intValue }) == [1,2,192,192] else { throw LocalFailure(message: "Invalid segmentation output") }
        let raw = try tensor.tensorData() as Data
        let scores = raw.withUnsafeBytes { Array($0.bindMemory(to: Float.self)) }
        guard scores.count == plane*2,scores.allSatisfy({ $0.isFinite }) else { throw LocalFailure(message: "Invalid mask values") }
        let mask = Data((0..<plane).map { scores[$0+plane]>scores[$0] ? UInt8(255) : UInt8(0) })
        guard let provider = CGDataProvider(data: mask as CFData),let cg = CGImage(width: 192,height: 192,bitsPerComponent: 8,bitsPerPixel: 8,bytesPerRow: 192,space: CGColorSpaceCreateDeviceGray(),bitmapInfo: CGBitmapInfo(rawValue: 0),provider: provider,decode: nil,shouldInterpolate: true,intent: .defaultIntent) else { throw LocalFailure(message: "Cannot create mask") }
        return cg
    }
}
