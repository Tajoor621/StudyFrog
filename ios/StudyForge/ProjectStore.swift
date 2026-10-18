import Foundation
import UIKit
import PencilKit
import CryptoKit

struct ProjectRecord: Codable, Identifiable {
    var id: UUID
    var title: String
    var prompt: String
    var negative: String
    var created: Date
    var layerNames: [String]
    var visible: [Bool]?
    var opacity: [Double]?
    var blends: [String]?
    var draft: Bool?
    var folder: String?
    var profile: GenerationProfile?
    var seed: Int64?
    var steps: Int?
    var resolution: Int?
    var strength: Double?
}
struct IOSProjectStore {
    let root: URL
    init(root: URL? = nil) throws {
        self.root = root ?? FileManager.default.urls(for: .applicationSupportDirectory,in: .userDomainMask)[0].appendingPathComponent("Projects",isDirectory: true)
        try FileManager.default.createDirectory(at: self.root,withIntermediateDirectories: true)
        var dir = self.root; var values = URLResourceValues(); values.isExcludedFromBackup = true; try dir.setResourceValues(values)
    }
    func list() throws -> [ProjectRecord] {
        try FileManager.default.contentsOfDirectory(at: root,includingPropertiesForKeys: nil).compactMap { directory in
            try? JSONDecoder().decode(ProjectRecord.self,from: Data(contentsOf: directory.appendingPathComponent("project.json")))
        }.filter { $0.draft != true }.sorted { $0.created > $1.created }
    }
    @discardableResult func save(title: String,prompt: String,negative: String,image: UIImage,drawings: [PKDrawing],names: [String],visible: [Bool] = [],opacity: [Double] = [],blends: [String] = [],draft: Bool = false,folder: String = "",profile: GenerationProfile? = nil,seed: Int64? = nil,steps: Int? = nil,resolution: Int? = nil,strength: Double? = nil) throws -> ProjectRecord {
        let folder = try validatedFolder(folder)
        guard drawings.count == names.count, drawings.count <= 16, let png = image.pngData() else { throw LocalFailure(message: "Invalid project layers") }
        let record = ProjectRecord(id: UUID(),title: String(title.prefix(160)),prompt: prompt,negative: negative,created: Date(),layerNames: names,visible: visible,opacity: opacity,blends: blends,draft: draft,folder: folder,profile: profile,seed: seed,steps: steps,resolution: resolution,strength: strength)
        let temporary = root.appendingPathComponent("pending-\(record.id.uuidString)",isDirectory: true)
        try FileManager.default.createDirectory(at: temporary,withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: temporary) }
        try png.write(to: temporary.appendingPathComponent("base.png"),options: .atomic)
        for (index,drawing) in drawings.enumerated() { try drawing.dataRepresentation().write(to: temporary.appendingPathComponent("ink-\(index).pkdrawing"),options: .atomic) }
        try JSONEncoder().encode(record).write(to: temporary.appendingPathComponent("project.json"),options: .atomic)
        try FileManager.default.moveItem(at: temporary,to: root.appendingPathComponent(record.id.uuidString,isDirectory: true))
        if draft {
            let pointer = root.appendingPathComponent("draft.json")
            let previous = (try? Data(contentsOf: pointer)).flatMap { try? JSONDecoder().decode(ProjectRecord.self,from: $0) }
            try JSONEncoder().encode(record).write(to: pointer,options: .atomic)
            if let previous { try? delete(previous.id) }
        }
        return record
    }
    func draft() throws -> ProjectRecord? {
        let pointer = root.appendingPathComponent("draft.json")
        guard FileManager.default.fileExists(atPath: pointer.path) else { return nil }
        return try JSONDecoder().decode(ProjectRecord.self,from: Data(contentsOf: pointer))
    }
    func load(_ record: ProjectRecord) throws -> (UIImage,[PKDrawing]) {
        let directory = root.appendingPathComponent(record.id.uuidString,isDirectory: true)
        guard let image = UIImage(contentsOfFile: directory.appendingPathComponent("base.png").path), record.layerNames.count <= 16 else { throw LocalFailure(message: "Project image is missing") }
        return (image,try record.layerNames.indices.map { try PKDrawing(data: Data(contentsOf: directory.appendingPathComponent("ink-\($0).pkdrawing"))) })
    }
    private func validatedFolder(_ value: String) throws -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count <= 80, !value.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }) else { throw LocalFailure(message: "Use a folder name of up to 80 characters without control characters") }
        return trimmed
    }
    func setFolder(_ record: ProjectRecord,folder: String) throws {
        let file = root.appendingPathComponent(record.id.uuidString).appendingPathComponent("project.json")
        var updated = try JSONDecoder().decode(ProjectRecord.self,from: Data(contentsOf: file))
        updated.folder = try validatedFolder(folder)
        try JSONEncoder().encode(updated).write(to: file,options: .atomic)
    }
    func delete(_ id: UUID) throws { try FileManager.default.removeItem(at: root.appendingPathComponent(id.uuidString)) }
    func rename(_ record: ProjectRecord,title: String) throws {
        guard !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, title.count <= 160 else { throw LocalFailure(message: "Use a title of 1–160 characters") }
        var updated = record; updated.title = title
        try JSONEncoder().encode(updated).write(to: root.appendingPathComponent(record.id.uuidString).appendingPathComponent("project.json"),options: .atomic)
    }
}
struct IOSModelStore {
    static var directory: URL { FileManager.default.urls(for: .applicationSupportDirectory,in: .userDomainMask)[0].appendingPathComponent("Models",isDirectory: true) }
    static var model: URL { directory.appendingPathComponent("image.gguf") }
    static func install(_ url: URL) throws -> String {
        let access = url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }
        try FileManager.default.createDirectory(at: directory,withIntermediateDirectories: true)
        let source = try FileHandle(forReadingFrom: url); defer { try? source.close() }
        let header = try source.read(upToCount: 4) ?? Data()
        guard header == Data("GGUF".utf8) else { throw LocalFailure(message: "Choose a complete SD 1.x GGUF model") }
        let temporary = directory.appendingPathComponent(UUID().uuidString+".part")
        FileManager.default.createFile(atPath: temporary.path,contents: nil)
        defer { try? FileManager.default.removeItem(at: temporary) }
        let destination = try FileHandle(forWritingTo: temporary); defer { try? destination.close() }
        var hash = SHA256(); hash.update(data: header); try destination.write(contentsOf: header); var total = header.count
        while let block = try source.read(upToCount: 1_048_576), !block.isEmpty {
            total += block.count; guard total <= 4_294_967_296 else { throw LocalFailure(message: "Model exceeds 4 GB") }
            hash.update(data: block); try destination.write(contentsOf: block)
        }
        guard total >= 32_000_000 else { throw LocalFailure(message: "Incomplete model file") }
        try destination.synchronize()
        if FileManager.default.fileExists(atPath: model.path) { _ = try FileManager.default.replaceItemAt(model,withItemAt: temporary) }
        else { try FileManager.default.moveItem(at: temporary,to: model) }
        var folder = directory; var values = URLResourceValues(); values.isExcludedFromBackup = true; try folder.setResourceValues(values)
        let digest = hash.finalize().map { String(format: "%02x",$0) }.joined()
        try Data(digest.utf8).write(to: directory.appendingPathComponent("sha256.txt"),options: .atomic)
        return digest
    }
}
