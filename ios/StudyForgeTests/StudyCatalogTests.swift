import XCTest
@testable import StudyForge

final class StudyCatalogTests: XCTestCase {
    func testStudyPathPreservesContextInTheGenerationPrompt() throws {
        let catalog = try StudyCatalog.load()
        XCTAssertEqual(try catalog.path("cardiomyocyte").map(\.level),["Organism","System","Organ","Tissue","Cellular"])
        XCTAssertTrue(try catalog.generationPrompt("cardiomyocyte").contains("Human body > Cardiovascular system > Heart"))
        XCTAssertEqual(try catalog.node("prophase").next,"prometaphase")
    }
    func testEveryStudyDiagramRendersAtExplicitPixelDimensions() throws {
        let catalog = try StudyCatalog.load()
        for node in catalog.nodes {
            let image = try catalog.render(node.id,labels: true)
            XCTAssertEqual(image.cgImage?.width,1200);XCTAssertEqual(image.cgImage?.height,900)
            XCTAssertFalse(node.alt.isEmpty)
        }
    }
}
