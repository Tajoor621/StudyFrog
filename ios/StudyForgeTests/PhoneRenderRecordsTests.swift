import XCTest
import UIKit
@testable import StudyForge

final class PhoneRenderRecordsTests: XCTestCase {
    func testCompletedResultSurvivesFailureAndProcessRestart() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let records = PhoneRenderRecords(directory: root)
        let source = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "phone-source",withExtension: "mp4"))
        let format = UIGraphicsImageRendererFormat();format.scale = 1
        let bg = UIGraphicsImageRenderer(size: CGSize(width: 160,height: 96),format: format).image { ctx in UIColor.blue.setFill();ctx.fill(CGRect(x: 0,y: 0,width: 160,height: 96)) }
        let success = try records.begin(mode: "green",start: 0,seconds: 1)
        try FileManager.default.copyItem(at: source,to: success.source)
        try PhoneVideoRemix.render(source: success.source,background: bg,output: success.output,personMode: false,start: 0,seconds: 1,control: PhoneRenderControl(),progress: { _ in })
        try records.complete(success)
        XCTAssertEqual(try records.latest(),success.output);XCTAssertFalse(FileManager.default.fileExists(atPath: success.source.path))
        let report = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: records.report)) as? [String:Any])
        XCTAssertEqual(report["status"] as? String,"done");XCTAssertEqual(report["physical_execution_verified"] as? Bool,false)
        XCTAssertEqual(report["quality_assessed"] as? Bool,false);XCTAssertNotNil(report["before"]);XCTAssertNotNil(report["after"])
        let failed = try records.begin(mode: "green",start: 0,seconds: 1)
        try Data("source".utf8).write(to: failed.source);try Data("partial".utf8).write(to: failed.output)
        try records.fail(failed,cancelled: false,errorType: "SyntheticFailure")
        XCTAssertEqual(try records.latest(),success.output);XCTAssertFalse(FileManager.default.fileExists(atPath: failed.output.path))
        let interrupted = try records.begin(mode: "green",start: 0,seconds: 1)
        try Data("source".utf8).write(to: interrupted.source);try Data("partial".utf8).write(to: interrupted.output)
        try records.recover();XCTAssertTrue(FileManager.default.fileExists(atPath: interrupted.source.path))
        let restarted = PhoneRenderRecords(directory: root);try restarted.recover()
        let recovered = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: restarted.report)) as? [String:Any])
        XCTAssertEqual(recovered["status"] as? String,"interrupted");XCTAssertTrue(recovered["elapsed_ms"] is NSNull)
        XCTAssertFalse(FileManager.default.fileExists(atPath: interrupted.source.path));XCTAssertFalse(FileManager.default.fileExists(atPath: interrupted.output.path))
        XCTAssertEqual(try restarted.latest(),success.output)
        try restarted.clear();XCTAssertNil(try restarted.latest());XCTAssertFalse(FileManager.default.fileExists(atPath: success.output.path))
    }
    func testCorruptReceiptCannotReadOrDeleteOutsideFile() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let outside = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString+".mp4")
        try FileManager.default.createDirectory(at: root,withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root);try? FileManager.default.removeItem(at: outside) }
        try Data("original".utf8).write(to: outside)
        let record: [String:Any] = ["schema":1,"run_id":"../"+outside.lastPathComponent,"status":"done"]
        try JSONSerialization.data(withJSONObject: record).write(to: root.appendingPathComponent("latest.json"))
        let records = PhoneRenderRecords(directory: root)
        XCTAssertThrowsError(try records.latest());try records.clear()
        XCTAssertEqual(try String(contentsOf: outside,encoding: .utf8),"original");XCTAssertNil(try records.latest())
    }
}
