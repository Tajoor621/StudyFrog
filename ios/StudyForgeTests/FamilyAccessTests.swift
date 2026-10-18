import XCTest
@testable import StudyForge

final class MemoryFamilyStorage: FamilyRecordStorage {
    var data: Data?
    func load() throws -> Data? { data }
    func save(_ data: Data) throws { self.data = data }
}
final class FamilyAccessTests: XCTestCase {
    func testPinLockChangeAndPersistence() throws {
        let storage = MemoryFamilyStorage(),vault = FamilyVault(storage: MemoryFamilyStorage())
        XCTAssertNil(try vault.status())
        let first = FamilyVault(storage: storage);try first.configure("003621")
        XCTAssertEqual(try first.status()?.restricted,true)
        XCTAssertFalse(String(decoding: try XCTUnwrap(storage.data),as: UTF8.self).contains("003621"))
        try first.changePin("003621",new: "009821")
        let reopened = FamilyVault(storage: storage)
        XCTAssertThrowsError(try reopened.setRestricted(false,pin: "003621"))
        try reopened.setRestricted(false,pin: "009821");XCTAssertEqual(try reopened.status()?.restricted,false)
    }
    func testCooldownAndCorruptRecordsDoNotUnlock() throws {
        let storage = MemoryFamilyStorage(),vault = FamilyVault(storage: MemoryFamilyStorage())
        XCTAssertThrowsError(try vault.configure("１２３４５６"))
        let locked = FamilyVault(storage: storage);try locked.configure("003621")
        for _ in 0..<5 { XCTAssertThrowsError(try locked.setRestricted(false,pin: "000000")) }
        XCTAssertGreaterThan(try XCTUnwrap(locked.status()?.blockedUntil),Date())
        XCTAssertThrowsError(try locked.setRestricted(false,pin: "003621"))
        storage.data = Data("damaged".utf8);XCTAssertThrowsError(try locked.status())
    }
    func testVisualDirectionsPreservePromptAndEnforceBounds() throws {
        let prompt = "  A fern with clear venation  "
        XCTAssertEqual(try GenerationProfile().apply(prompt),prompt)
        let profile = GenerationProfile(enabled: true,style: "scientific",detail: 90,realism: 20,scientific: true)
        let result = try profile.apply(prompt)
        XCTAssertTrue(result.hasPrefix(prompt));XCTAssertTrue(result.contains("human review required"));XCTAssertTrue(result.contains("flat illustrative"))
        XCTAssertThrowsError(try profile.apply(String(repeating: "x",count: 2000)))
    }
}
