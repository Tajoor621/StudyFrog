import XCTest
import CryptoKit
@testable import StudyForge

final class ModelImportTests: XCTestCase {
    private func workspace(_ run:(URL,URL)throws->Void) throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root,withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root) }
        try run(root,root.appendingPathComponent("models"))
    }
    func testInvalidAndCancelledImportsPreserveExistingModel() throws {
        try workspace { root,directory in
            try FileManager.default.createDirectory(at: directory,withIntermediateDirectories: true)
            let old = directory.appendingPathComponent("image.gguf");try Data("existing".utf8).write(to: old)
            let invalid = root.appendingPathComponent("bad.gguf");try Data("invalid".utf8).write(to: invalid)
            XCTAssertThrowsError(try IOSModelStore.install(invalid,directory: directory))
            XCTAssertThrowsError(try IOSModelStore.install(invalid,directory: directory,cancelled: { true }))
            XCTAssertEqual(try String(contentsOf: old),"existing")
        }
    }
    func testChecksumVerificationPreservesExistingModelAndInstallsTransportFixture() throws {
        try workspace { root,directory in
            try FileManager.default.createDirectory(at: directory,withIntermediateDirectories: true)
            let old = directory.appendingPathComponent("image.gguf");try Data("existing".utf8).write(to: old)
            // Transport-only bytes; no claim that this synthetic file supports inference.
            let source = root.appendingPathComponent("transport-fixture.gguf")
            var bytes = Data([71,71,85,70,3,0,0,0]);bytes.append(Data(repeating: 0,count: 32_000_000-8));try bytes.write(to: source)
            let hash = SHA256.hash(data: bytes).map { String(format: "%02x",$0) }.joined()
            XCTAssertThrowsError(try IOSModelStore.install(source,directory: directory,expectedSHA256: String(repeating: "0",count: 64)))
            XCTAssertEqual(try String(contentsOf: old),"existing")
            let copying = Progress(totalUnitCount: 32_000_000)
            XCTAssertThrowsError(try IOSModelStore.install(source,directory: directory,expectedSHA256: hash,cancelled: { copying.completedUnitCount > 2_000_000 },progress: { copied,_ in copying.completedUnitCount = copied }))
            XCTAssertEqual(try String(contentsOf: old),"existing")
            XCTAssertEqual(try IOSModelStore.install(source,directory: directory,expectedSHA256: hash),hash)
            XCTAssertEqual(try old.resourceValues(forKeys: [.fileSizeKey]).fileSize,32_000_000)
            XCTAssertFalse(try FileManager.default.contentsOfDirectory(atPath: directory.path).contains { $0.hasSuffix(".part") })
        }
    }
    func testDiagnosticsExposeResidentMemoryAndEnvironment() {
        let snapshot = DeviceDiagnostics.snapshot()
        XCTAssertNotNil(snapshot["total_ram_bytes"]);XCTAssertNotNil(snapshot["resident_bytes"]);XCTAssertNotNil(snapshot["simulator"])
    }
}
