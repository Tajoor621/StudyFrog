import Foundation
import SwiftUI
import Security
import CommonCrypto

struct FamilyRecord: Codable {
    var salt: Data
    var hash: Data
    var restricted: Bool
    var failures: Int
    var blockedUntil: Date
}
protocol FamilyRecordStorage {
    func load() throws -> Data?
    func save(_ data: Data) throws
}
struct FamilyKeychain: FamilyRecordStorage {
    private var query: [String:Any] { [kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:"com.scenio.mth.family",kSecAttrAccount as String:"parent-pin"] }
    func load() throws -> Data? {
        var request = query;request[kSecReturnData as String] = true;request[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?;let status = SecItemCopyMatching(request as CFDictionary,&result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess,let data = result as? Data else { throw LocalFailure(message: "Parent PIN storage is unavailable") }
        return data
    }
    func save(_ data: Data) throws {
        let result = SecItemUpdate(query as CFDictionary,[kSecValueData as String:data] as CFDictionary)
        if result == errSecItemNotFound {
            var item = query;item[kSecValueData as String] = data;item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            guard SecItemAdd(item as CFDictionary,nil) == errSecSuccess else { throw LocalFailure(message: "Could not save parent PIN") }
        } else if result != errSecSuccess { throw LocalFailure(message: "Could not save parent PIN") }
    }
}
final class FamilyVault: @unchecked Sendable {
    private let storage: FamilyRecordStorage
    private let lock = NSLock()
    init(storage: FamilyRecordStorage = FamilyKeychain()) { self.storage = storage }
    static func valid(_ pin: String) -> Bool { pin.utf8.count == 6 && pin.utf8.allSatisfy { $0 >= 48 && $0 <= 57 } }
    private func read() throws -> FamilyRecord? {
        guard let data = try storage.load() else { return nil }
        let record = try JSONDecoder().decode(FamilyRecord.self,from: data)
        guard record.salt.count == 16,record.hash.count == 32,(0...4).contains(record.failures) else { throw LocalFailure(message: "Parent PIN record is damaged") }
        return record
    }
    func status() throws -> FamilyRecord? { lock.lock();defer { lock.unlock() };return try read() }
    private func digest(_ pin: String,salt: Data) throws -> Data {
        guard Self.valid(pin) else { throw LocalFailure(message: "Use exactly six digits") }
        let password = Array(pin.utf8);var output = Data(count: 32)
        let result = output.withUnsafeMutableBytes { target in salt.withUnsafeBytes { saltBytes in password.withUnsafeBytes { passwordBytes in
            CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2),passwordBytes.bindMemory(to: Int8.self).baseAddress,password.count,saltBytes.bindMemory(to: UInt8.self).baseAddress,salt.count,CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),210_000,target.bindMemory(to: UInt8.self).baseAddress,32)
        } } }
        guard result == kCCSuccess else { throw LocalFailure(message: "PIN derivation failed") };return output
    }
    private func salt() throws -> Data {
        var value = Data(count: 16)
        let result = value.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault,16,$0.baseAddress!) }
        guard result == errSecSuccess else { throw LocalFailure(message: "Secure random generation failed") };return value
    }
    private func save(_ record: FamilyRecord) throws { try storage.save(JSONEncoder().encode(record)) }
    func configure(_ pin: String) throws {
        lock.lock();defer { lock.unlock() }
        guard try read() == nil else { throw LocalFailure(message: "A parent PIN already exists") }
        let salt = try salt();try save(FamilyRecord(salt: salt,hash: digest(pin,salt: salt),restricted: true,failures: 0,blockedUntil: .distantPast))
    }
    private func authorize(_ pin: String) throws -> FamilyRecord {
        guard var record = try read() else { throw LocalFailure(message: "Set a parent PIN first") }
        guard Date() >= record.blockedUntil else { throw LocalFailure(message: "Too many attempts. Wait for the 15-minute cooldown") }
        let value = Self.valid(pin) ? try digest(pin,salt: record.salt) : Data(repeating: 0,count: 32)
        let difference = zip(value,record.hash).reduce(UInt8(0)) { $0 | ($1.0 ^ $1.1) }
        guard Self.valid(pin) && difference == 0 else {
            record.failures += 1
            if record.failures >= 5 { record.failures = 0;record.blockedUntil = Date().addingTimeInterval(900) }
            try save(record);throw LocalFailure(message: "Incorrect parent PIN")
        }
        record.failures = 0;record.blockedUntil = .distantPast;return record
    }
    func setRestricted(_ restricted: Bool,pin: String) throws {
        lock.lock();defer { lock.unlock() };var record = try authorize(pin);record.restricted = restricted;try save(record)
    }
    func changePin(_ old: String,new: String) throws {
        lock.lock();defer { lock.unlock() }
        guard Self.valid(new) else { throw LocalFailure(message: "Use exactly six digits") }
        var record = try authorize(old);record.salt = try salt();record.hash = try digest(new,salt: record.salt);try save(record)
    }
}
@MainActor final class FamilyAccess: ObservableObject {
    static let shared = FamilyAccess()
    @Published private(set) var restricted = true
    @Published private(set) var configured = true
    private let vault = FamilyVault()
    init() { refresh() }
    func refresh() {
        do { let record = try vault.status();configured = record != nil;restricted = record?.restricted ?? false }
        catch { configured = true;restricted = true }
    }
    func update(pin: String,new: String,change: Bool) async throws {
        let vault = vault,configured = configured,target = !restricted
        do {
            try await Task.detached {
                if !configured { try vault.configure(new) }
                else if change { try vault.changePin(pin,new: new) }
                else { try vault.setRestricted(target,pin: pin) }
            }.value
            refresh()
        } catch { refresh();throw error }
    }
}
struct FamilyControlsView: View {
    @EnvironmentObject private var family: FamilyAccess
    @Environment(\.dismiss) private var dismiss
    @State private var pin = ""
    @State private var newPin = ""
    @State private var confirm = ""
    @State private var busy = false
    @State private var message = ""
    var body: some View {
        NavigationStack { Form {
            Text("Study-only mode locks generation, photos, projects, external browsing and computer transfers. Bundled diagrams and medical anatomy stay available. This is not verified age detection or certified child safety.")
            Text("Keep the six-digit PIN safe. There is no account or remote PIN recovery. Back up important projects first. Device parental controls provide stronger protection; changing the device clock can affect local cooldowns.")
            if family.configured { SecureField("Current parent PIN",text: $pin).keyboardType(.numberPad).privacySensitive() }
            SecureField(family.configured ? "New PIN · only to change PIN" : "Choose six-digit parent PIN",text: $newPin).keyboardType(.numberPad).privacySensitive()
            SecureField("Confirm new PIN",text: $confirm).keyboardType(.numberPad).privacySensitive()
            Button(!family.configured ? "Set PIN and enable study-only mode" : family.restricted ? "Unlock creative workspace" : "Enable study-only mode") { perform(false) }.disabled(busy)
            if family.configured { Button("Change parent PIN") { perform(true) }.disabled(busy) }
            Text(message).accessibilityLabel(message)
            if busy { ProgressView() }
        }.navigationTitle("Family access").toolbar { Button("Done") { dismiss() }.disabled(busy) }.interactiveDismissDisabled(busy) }
    }
    private func perform(_ change: Bool) {
        guard !busy else { return }
        if (!family.configured || change) && newPin != confirm { message = "New PINs do not match";return }
        busy = true
        Task {
            do { try await family.update(pin: pin,new: newPin,change: change);pin = "";newPin = "";confirm = "";message = "Family access updated";if !change { dismiss() } }
            catch { message = error.localizedDescription }
            busy = false
        }
    }
}
struct FamilyLearningHomeView: View {
    @State private var controls = false
    @State private var science = false
    var body: some View {
        NavigationStack { VStack(alignment: .leading,spacing: 24) {
            Text("Study-only workspace").font(.largeTitle)
            Text("Explore bundled science and anatomy. Private projects, prompts, photos and generation are locked. Educational diagrams still need teacher review.")
            Button("Explore offline science") { science = true }.buttonStyle(.borderedProminent)
            Button("Parent controls · PIN required") { controls = true }
            Spacer()
        }.padding().navigationTitle("StudyForge") }
        .sheet(isPresented: $science) { StudyExplorerView(hasExistingImage: false,readOnly: true) { _,_ in } }
        .sheet(isPresented: $controls) { FamilyControlsView() }
    }
}
struct CreativeAgeGateView: View {
    @AppStorage("creativeAgeAcknowledged") private var acknowledged = false
    @State private var controls = false
    var body: some View {
        VStack(alignment: .leading,spacing: 24) {
            Text("Choose your workspace").font(.largeTitle)
            Text("The creative workspace is intended for adults. Age confirmation is self-declared. A parent can instead configure PIN-protected access to bundled educational diagrams. Generated medical visuals require review.")
            Button("I am 18 or older · open creative workspace") { acknowledged = true }.buttonStyle(.borderedProminent)
            Button("Set up study-only family access") { controls = true }
        }.padding().sheet(isPresented: $controls) { FamilyControlsView() }
    }
}
