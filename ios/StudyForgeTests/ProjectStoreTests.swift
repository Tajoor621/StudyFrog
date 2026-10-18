import XCTest
import PencilKit
import UIKit
@testable import StudyForge

final class ProjectStoreTests: XCTestCase {
    func testEditableProjectSurvivesStoreReopeningAndDeletion() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString,isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let store = try IOSProjectStore(root: root)
        let format = UIGraphicsImageRendererFormat();format.scale = 1
        let image = UIGraphicsImageRenderer(size: CGSize(width: 64,height: 128),format: format).image { ctx in UIColor.green.setFill();ctx.fill(CGRect(x: 0,y: 0,width: 64,height: 128)) }
        let saved = try store.save(title: "Botany",prompt: "Plant",negative: "blur",image: image,drawings: [PKDrawing()],names: ["Labels"])
        let reopened = try IOSProjectStore(root: root)
        XCTAssertEqual(try reopened.list().first?.id,saved.id)
        let loaded = try reopened.load(saved)
        XCTAssertEqual(loaded.0.size.width,64);XCTAssertEqual(loaded.0.size.height,128);XCTAssertEqual(loaded.1.count,1)
        try reopened.rename(saved,title: "Fern");XCTAssertEqual(try reopened.list().first?.title,"Fern")
        try reopened.setFolder(saved,folder: " Biology ")
        XCTAssertEqual(try IOSProjectStore(root: root).list().first?.folder,"Biology")
        XCTAssertThrowsError(try reopened.setFolder(saved,folder: "Bad\nfolder"))
        try reopened.delete(saved.id);XCTAssertTrue(try reopened.list().isEmpty)
    }
    func testLegacyRecordsDecodeWithoutFolder() throws {
        let record = ProjectRecord(id: UUID(),title: "Legacy",prompt: "",negative: "",created: Date(),layerNames: ["Ink"])
        var fields = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(record)) as? [String:Any])
        fields.removeValue(forKey: "folder")
        let old = try JSONDecoder().decode(ProjectRecord.self,from: JSONSerialization.data(withJSONObject: fields))
        XCTAssertNil(old.folder)
    }
    func testMissingModelFailsWithoutReturningAnImage() throws {
        XCTAssertThrowsError(try NativeDiffusion.generate(model: URL(fileURLWithPath: "/missing.gguf"),prompt: "",negative: "",size: 256,steps: 20,seed: 42,initial: nil))
    }
}
