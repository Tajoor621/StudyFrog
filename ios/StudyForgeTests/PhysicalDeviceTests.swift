import XCTest
import UIKit
import PencilKit
import AVFoundation
import CryptoKit
@testable import StudyForge

final class PhysicalDeviceTests: XCTestCase {
    private func requirePhysical(_ flag: String) throws {
        guard ProcessInfo.processInfo.environment[flag] == "1" else { throw XCTSkip("Opt-in physical device test") }
        #if targetEnvironment(simulator)
        XCTFail("Use a physical iPhone or iPad");throw LocalFailure(message: "Simulator is not physical-device evidence")
        #endif
    }
    private func attach(_ value: [String:Any],name: String) throws {
        let data = try JSONSerialization.data(withJSONObject: value,options: [.sortedKeys])
        let attachment = XCTAttachment(data: data,uniformTypeIdentifier: "public.json");attachment.name = name;attachment.lifetime = .keepAlways;add(attachment)
    }
    func testRealModelBenchmark() throws {
        try requirePhysical("STUDYFORGE_RUN_REAL_MODEL")
        let expected = ProcessInfo.processInfo.environment["STUDYFORGE_MODEL_SHA256"] ?? ""
        guard expected.count == 64,expected.allSatisfy({ "0123456789abcdef".contains($0) }) else { throw LocalFailure(message: "Set the actual imported model SHA-256") }
        let handle = try FileHandle(forReadingFrom: IOSModelStore.model);defer { try? handle.close() }
        var hash = SHA256();while let block = try handle.read(upToCount: 1_048_576),!block.isEmpty { hash.update(data: block) }
        guard expected == hash.finalize().map({ String(format: "%02x",$0) }).joined() else { throw LocalFailure(message: "Imported model checksum does not match") }
        let size = Int(ProcessInfo.processInfo.environment["STUDYFORGE_BENCHMARK_SIZE"] ?? "256") ?? 0
        guard [256,512].contains(size) else { throw LocalFailure(message: "Choose 256 or 512") }
        let options = XCTMeasureOptions();options.iterationCount = 1
        var attempts = [[String:Any]]()
        let prompts = [("botany","A scientific botanical illustration of a green fern on white paper"),("product","A realistic red ceramic mug on a neutral table, soft daylight")]
        measure(metrics: [XCTClockMetric(),XCTMemoryMetric()],options: options) {
            for (caseID,prompt) in prompts {
            let before = DeviceDiagnostics.snapshot(),started = ProcessInfo.processInfo.systemUptime
            do {
                guard ProcessInfo.processInfo.thermalState.rawValue < 2 else { throw LocalFailure(message: "Cool the device first") }
                let image = try NativeDiffusion.generate(model: IOSModelStore.model,prompt: prompt,negative: "blurry, distorted",size: size,steps: 10,seed: 42,initial: nil)
                guard image.cgImage?.width == size,image.cgImage?.height == size else { throw LocalFailure(message: "Unexpected generated dimensions") }
                attempts.append(["case":caseID,"status":"passed","elapsed_ms":(ProcessInfo.processInfo.systemUptime-started)*1000,"before":before,"after":DeviceDiagnostics.snapshot()])
            } catch { attempts.append(["case":caseID,"status":"failed","error_type":String(describing: type(of: error)),"before":before,"after":DeviceDiagnostics.snapshot()]);XCTFail(error.localizedDescription) }
            }
        }
        try attach(["schema":1,"status":attempts.count == 2 && attempts.allSatisfy({ $0["status"] as? String == "passed" }) ? "completed" : "failed","platform":"ios","physical_device":true,"build_version":Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "unknown","model_sha256":expected,"size":size,"steps":10,"seed":42,"call_includes_model_load":true,"quality_assessed":false,"attempts":attempts],name: "real-model-benchmark")
    }
    func testRepeatedEncoderAndLayerStress() throws {
        try requirePhysical("STUDYFORGE_RUN_DEVICE_STRESS")
        let format = UIGraphicsImageRendererFormat();format.scale = 1
        let image = UIGraphicsImageRenderer(size: CGSize(width: 512,height: 512),format: format).image { context in UIColor.green.setFill();context.fill(CGRect(x: 0,y: 0,width: 512,height: 512)) }
        var attempts = [[String:Any]]()
        for index in 0..<2 {
            let output = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString+".mp4")
            defer { try? FileManager.default.removeItem(at: output) }
            let before = DeviceDiagnostics.snapshot(),started = ProcessInfo.processInfo.systemUptime
            do {
                guard ProcessInfo.processInfo.thermalState.rawValue < 2 else { throw LocalFailure(message: "Cool the device first") }
                try RevealEncoder.encode(image: image,output: output) { _ in }
                let asset = AVURLAsset(url: output);let duration = CMTimeGetSeconds(asset.duration)
                XCTAssertEqual(duration,30,accuracy: 0.1)
                let track = try XCTUnwrap(asset.tracks(withMediaType: .video).first)
                XCTAssertEqual(track.naturalSize,CGSize(width: 1920,height: 1080));XCTAssertEqual(track.nominalFrameRate,24,accuracy: 0.1)
                attempts.append(["case":"encoder","iteration":index,"status":"passed","elapsed_ms":(ProcessInfo.processInfo.systemUptime-started)*1000,"before":before,"after":DeviceDiagnostics.snapshot()])
            } catch { attempts.append(["case":"encoder","status":"failed","error_type":String(describing: type(of: error))]);try attach(["cases":attempts],name: "device-stress-failed");throw error }
        }
        for _ in 0..<5 {
            let layers = (0..<16).map { IOSInkLayer(name: "Layer \($0)") }
            let data = try PortableProject.encode(base: image,layers: layers,selected: 0,title: "Stress",prompt: "",negative: "",folder: "")
            XCTAssertEqual(try PortableProject.decode(data).layers.count,16)
        }
        try attach(["schema":1,"platform":"ios","cases":attempts,"bounded_layer_roundtrips":5,"manual_accessibility_verified":false,"os_pressure_simulated":false],name: "device-stress")
    }
}
