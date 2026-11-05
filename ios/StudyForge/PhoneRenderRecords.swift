import Foundation
import AVFoundation

/// One live attempt and one retained success. Reports contain no source filename or media.
final class PhoneRenderRecords: @unchecked Sendable {
    struct Run: Sendable { let id: String;let source: URL;let output: URL;let started: TimeInterval }
    static let shared = PhoneRenderRecords(directory: FileManager.default.urls(for: .applicationSupportDirectory,in: .userDomainMask)[0].appendingPathComponent("phone-video-jobs"))
    let directory: URL
    var report: URL { directory.appendingPathComponent("report.json") }
    private var latestReceipt: URL { directory.appendingPathComponent("latest.json") }
    private var active: String?
    private let lock = NSRecursiveLock()
    init(directory: URL) { self.directory = directory }
    private func prepare() throws {
        try FileManager.default.createDirectory(at: directory,withIntermediateDirectories: true)
        var url = directory;var values = URLResourceValues();values.isExcludedFromBackup = true;try url.setResourceValues(values)
    }
    private func identity(_ value: String) throws -> String {
        guard UUID(uuidString: value)?.uuidString.lowercased() == value else { throw LocalFailure(message: "Invalid phone-job identity") };return value
    }
    private func output(_ value: String) throws -> URL { directory.appendingPathComponent("result-\(try identity(value)).mp4") }
    private func read(_ url: URL) throws -> [String:Any]? {
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        let data = try Data(contentsOf: url)
        guard data.count<=100000,let record = try JSONSerialization.jsonObject(with: data) as? [String:Any],record["schema"] as? Int == 1,let id = record["run_id"] as? String else { throw LocalFailure(message: "Damaged phone-job record") }
        _ = try identity(id);return record
    }
    private func write(_ value: [String:Any],to url: URL) throws { try prepare();try JSONSerialization.data(withJSONObject: value,options: [.sortedKeys]).write(to: url,options: .atomic) }
    private func validate(_ url: URL) throws -> [String:Any] {
        let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        let asset = AVURLAsset(url: url)
        guard size>0,size<=64_000_000,let video = asset.tracks(withMediaType: .video).first else { throw LocalFailure(message: "Saved phone video is unavailable") }
        let duration = CMTimeGetSeconds(asset.duration),w = abs(video.naturalSize.width),h = abs(video.naturalSize.height)
        guard duration.isFinite,(0.4...15.2).contains(duration),(16...854).contains(w),(16...854).contains(h),min(w,h)<=480 else { throw LocalFailure(message: "Invalid saved phone video") }
        return ["width":Int(w),"height":Int(h),"duration_us":duration*1_000_000,"bytes":size,"audio_present":!asset.tracks(withMediaType: .audio).isEmpty]
    }
    private func latestRecord() throws -> [String:Any]? {
        guard let record = try read(latestReceipt),record["status"] as? String == "done",let id = record["run_id"] as? String else { return nil }
        _ = try validate(output(id));return record
    }
    func recover() throws {
        lock.lock();defer { lock.unlock() };try prepare();guard active == nil else { return }
        let latest = try latestRecord()
        if var current = try read(report),current["status"] as? String == "running",let id = current["run_id"] as? String {
            if latest?["run_id"] as? String == id,let latest { try write(latest,to: report) }
            else { current["status"] = "interrupted";current["elapsed_ms"] = NSNull();current["recovered_after_process_end"] = true;try write(current,to: report) }
        }
        let retained = latest?["run_id"] as? String
        for file in try FileManager.default.contentsOfDirectory(at: directory,includingPropertiesForKeys: nil) {
            let name = file.deletingPathExtension().lastPathComponent
            guard file.pathExtension == "mp4",name.hasPrefix("source-") || name.hasPrefix("result-") else { continue }
            let prefix = name.hasPrefix("source-") ? "source-" : "result-"
            let id = String(name.dropFirst(prefix.count));guard (try? identity(id)) != nil else { continue }
            if file.lastPathComponent != "result-\(retained ?? "").mp4" { try FileManager.default.removeItem(at: file) }
        }
    }
    func begin(mode: String,start: Double,seconds: Double) throws -> Run {
        lock.lock();defer { lock.unlock() }
        guard active == nil else { throw LocalFailure(message: "A phone job is still running or cancelling") }
        try recover()
        guard ["person","green"].contains(mode),start.isFinite,(0...3600).contains(start),seconds.isFinite,(0.5...15).contains(seconds) else { throw LocalFailure(message: "Invalid phone clip settings") }
        guard (try directory.resourceValues(forKeys: [.volumeAvailableCapacityKey]).volumeAvailableCapacity ?? 0)>512_000_000 else { throw LocalFailure(message: "Free at least 512 MB before phone remix") }
        let id = UUID().uuidString.lowercased()
        let run = Run(id: id,source: directory.appendingPathComponent("source-\(id).mp4"),output: try output(id),started: ProcessInfo.processInfo.systemUptime)
        let value: [String:Any] = ["schema":1,"kind":"phone_remix_attempt","run_id":id,"status":"running","platform":"ios","build_version":Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "unknown","started_at_ms":Date().timeIntervalSince1970*1000,"mode":mode,"start_seconds":start,"requested_seconds":seconds,"profile":"mobile","before":DeviceDiagnostics.snapshot(),"quality_assessed":false,"physical_execution_verified":false,"elapsed_scope":"source copy, segmentation, encode and audio mux; before/after snapshots are not peak memory","requested_model_sha256":mode == "person" ? PhonePersonModel.digest as Any : NSNull()]
        try write(value,to: report);active = id;return run
    }
    func complete(_ run: Run) throws {
        lock.lock();defer { lock.unlock() }
        guard active == run.id,var record = try read(report),record["run_id"] as? String == run.id else { throw LocalFailure(message: "Phone run journal changed") }
        let previous = try latestRecord()?["run_id"] as? String
        let media = try validate(run.output)
        record["status"] = "done";record["elapsed_ms"] = (ProcessInfo.processInfo.systemUptime-run.started)*1000;record["after"] = DeviceDiagnostics.snapshot();record["output"] = media
        defer { active = nil;try? FileManager.default.removeItem(at: run.source) }
        try write(record,to: latestReceipt);try write(record,to: report)
        if let previous,previous != run.id { try? FileManager.default.removeItem(at: output(previous)) }
    }
    func fail(_ run: Run,cancelled: Bool,errorType: String) throws {
        lock.lock();defer { lock.unlock() };guard active == run.id else { return }
        defer { active = nil;try? FileManager.default.removeItem(at: run.source);try? FileManager.default.removeItem(at: run.output) }
        guard var record = try read(report) else { return }
        record["status"] = cancelled ? "cancelled" : "failed";record["elapsed_ms"] = (ProcessInfo.processInfo.systemUptime-run.started)*1000;record["after"] = DeviceDiagnostics.snapshot();record["error_type"] = String(errorType.prefix(100))
        try write(record,to: report)
    }
    func latest() throws -> URL? {
        lock.lock();defer { lock.unlock() };try recover()
        guard let id = try latestRecord()?["run_id"] as? String else { return nil };return try output(id)
    }
    func deleteLatest() throws {
        lock.lock();defer { lock.unlock() };guard active == nil else { throw LocalFailure(message: "Finish the phone job before deleting its saved result") }
        guard let id = try latestRecord()?["run_id"] as? String else { return }
        try FileManager.default.removeItem(at: output(id));try FileManager.default.removeItem(at: latestReceipt)
        if var current = try read(report),current["run_id"] as? String == id { current["result_deleted_by_user"] = true;try write(current,to: report) }
    }
    func clear() throws {
        lock.lock();defer { lock.unlock() };guard active == nil else { throw LocalFailure(message: "Finish the phone job before clearing its history") }
        try prepare()
        for file in try FileManager.default.contentsOfDirectory(at: directory,includingPropertiesForKeys: [.isDirectoryKey]) {
            if try file.resourceValues(forKeys: [.isDirectoryKey]).isDirectory != true { try FileManager.default.removeItem(at: file) }
        }
    }

}
