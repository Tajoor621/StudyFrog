import Foundation
import Darwin

struct DeviceDiagnostics {
    static var report: URL { FileManager.default.urls(for: .applicationSupportDirectory,in: .userDomainMask)[0].appendingPathComponent("device-benchmark.json") }
    static func snapshot() -> [String:Any] {
        var info = mach_task_basic_info();var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size/MemoryLayout<integer_t>.size)
        let status = withUnsafeMutablePointer(to: &info) { pointer in
            pointer.withMemoryRebound(to: integer_t.self,capacity: Int(count)) { task_info(mach_task_self_,task_flavor_t(MACH_TASK_BASIC_INFO),$0,&count) }
        }
        var system = utsname();uname(&system)
        let machine = withUnsafeBytes(of: system.machine) { String(decoding: $0.prefix { $0 != 0 },as: UTF8.self) }
        var result: [String:Any] = ["machine":machine,"sample_time_ms":Date().timeIntervalSince1970*1000,"total_ram_bytes":ProcessInfo.processInfo.physicalMemory,"thermal_state":ProcessInfo.processInfo.thermalState.rawValue,"low_power":ProcessInfo.processInfo.isLowPowerModeEnabled,"os":ProcessInfo.processInfo.operatingSystemVersionString]
        if status == KERN_SUCCESS { result["resident_bytes"] = info.resident_size }
        #if targetEnvironment(simulator)
        result["simulator"] = true
        #else
        result["simulator"] = false
        #endif
        return result
    }
    static func record(started: TimeInterval,before: [String:Any],size: Int,steps: Int,seed: Int64,photo: Bool,success: Bool) {
        let hash = (try? String(contentsOf: IOSModelStore.directory.appendingPathComponent("sha256.txt"),encoding: .utf8)) ?? "unknown"
        let value: [String:Any] = ["schema":1,"platform":"ios","build_version":Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "unknown","elapsed_ms":(ProcessInfo.processInfo.systemUptime-started)*1000,"before":before,"after":snapshot(),"model_sha256":hash,"model_sha256_source":"cached import receipt, not rehashed for this call","size":size,"steps":steps,"seed":seed,"photo":photo,"success":success,"quality_assessed":false]
        guard let data = try? JSONSerialization.data(withJSONObject: value,options: [.sortedKeys]) else { return }
        try? data.write(to: report,options: .atomic)
        var file = report;var values = URLResourceValues();values.isExcludedFromBackup = true;try? file.setResourceValues(values)
    }
}
