import XCTest
@testable import StudyForge

final class VideoRemixSourceTests: XCTestCase {
    func testBoundedVideoCopyPreservesSourceAndRejectsExcessOrEmptyInput() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root,withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let source = root.appendingPathComponent("source.mp4"),output = root.appendingPathComponent("copy.bin")
        let bytes = Data(repeating: 37,count: 131073)
        try bytes.write(to: source)
        try VideoRemixSource.copy(source,to: output,limit: bytes.count)
        XCTAssertEqual(try Data(contentsOf: output),bytes)
        try FileManager.default.removeItem(at: output)
        XCTAssertThrowsError(try VideoRemixSource.copy(source,to: output,limit: 65536))
        XCTAssertFalse(FileManager.default.fileExists(atPath: output.path))
        XCTAssertEqual(try Data(contentsOf: source),bytes)
        try Data().write(to: source)
        XCTAssertThrowsError(try VideoRemixSource.copy(source,to: output,limit: 65536))
        XCTAssertFalse(FileManager.default.fileExists(atPath: output.path))
    }
}
