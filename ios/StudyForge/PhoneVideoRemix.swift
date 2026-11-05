import AVFoundation
import UIKit
import CoreImage
import AudioToolbox

final class PhoneRenderControl: @unchecked Sendable {
    private let lock = NSLock()
    private var failure: String?
    func stop(_ reason: String) { lock.lock();failure = reason;lock.unlock() }
    func check() throws {
        lock.lock();let message = failure;lock.unlock()
        if let message { throw LocalFailure(message: message) }
        guard ProcessInfo.processInfo.thermalState != .serious,ProcessInfo.processInfo.thermalState != .critical else { throw LocalFailure(message: "Stopped because the phone is too hot") }
        try Task.checkCancellation()
    }
}

struct PhoneVideoRemix {
    static func dimensions(width: CGFloat,height: CGFloat) throws -> CGSize {
        guard width >= 16,height >= 16,width <= 1920,height <= 1920,width*height <= 2_073_600 else { throw LocalFailure(message: "Phone remix supports SDR source video up to 1080p") }
        let scale = min(1,min((height>width ? 480 : 854)/width,(height>width ? 854 : 480)/height))
        let w = Int(width*scale)/2*2,h = Int(height*scale)/2*2
        guard min(w,h) >= 16 else { throw LocalFailure(message: "Source aspect ratio is too narrow") }
        return CGSize(width: w,height: h)
    }
    static func composite(_ frame: CGImage,background: CGImage,person: PhonePersonMask?,context: CIContext) throws -> CGImage {
        let foreground = CIImage(cgImage: frame),extent = foreground.extent
        let bg = CIImage(cgImage: background)
        let scale = max(extent.width/bg.extent.width,extent.height/bg.extent.height)
        let scaled = bg.transformed(by: CGAffineTransform(scaleX: scale,y: scale))
        let fitted = scaled.transformed(by: CGAffineTransform(translationX: (extent.width-scaled.extent.width)/2,y: (extent.height-scaled.extent.height)/2)).cropped(to: extent)
        let mask: CIImage
        if let person {
            let cg = try person.mask(frame)
            mask = CIImage(cgImage: cg).transformed(by: CGAffineTransform(scaleX: extent.width/192,y: extent.height/192))
        } else {
            // Saturated green key; source green clothing is removed too.
            var rgba = [UInt8](repeating: 0,count: frame.width*frame.height*4)
            let ok = rgba.withUnsafeMutableBytes { bytes -> Bool in
                guard let drawing = CGContext(data: bytes.baseAddress,width: frame.width,height: frame.height,bitsPerComponent: 8,bytesPerRow: frame.width*4,space: CGColorSpaceCreateDeviceRGB(),bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue|CGBitmapInfo.byteOrder32Big.rawValue) else { return false }
                drawing.draw(frame,in: extent);return true
            }
            guard ok else { throw LocalFailure(message: "Cannot prepare green-screen mask") }
            let values = Data((0..<(frame.width*frame.height)).map { i -> UInt8 in
                let r = Float(rgba[i*4]),g = Float(rgba[i*4+1]),b = Float(rgba[i*4+2])
                return g>45 && g>r*1.25 && g>b*1.25 ? 0 : 255
            })
            mask = CIImage(bitmapData: values,bytesPerRow: frame.width,size: extent.size,format: .L8,colorSpace: CGColorSpaceCreateDeviceGray())
        }
        let result = foreground.applyingFilter("CIBlendWithMask",parameters: [kCIInputBackgroundImageKey:fitted,kCIInputMaskImageKey:mask])
        guard let image = context.createCGImage(result,from: extent) else { throw LocalFailure(message: "Cannot composite video frame") }
        return image
    }
    static func render(source: URL,background: UIImage,output: URL,personMode: Bool,start: Double,seconds: Double,model: URL = PhonePersonModel.file,control: PhoneRenderControl,progress: @escaping @Sendable (Int)->Void) throws {
        guard start.isFinite,(0...3600).contains(start),seconds.isFinite,(0.5...15).contains(seconds) else { throw LocalFailure(message: "Phone clip must be 0.5–15 seconds") }
        let size = try source.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard size>0,size<=64_000_000 else { throw LocalFailure(message: "Phone video input limit is 64 MB") }
        guard ProcessInfo.processInfo.physicalMemory >= 2_000_000_000,
              (try output.deletingLastPathComponent().resourceValues(forKeys: [.volumeAvailableCapacityKey]).volumeAvailableCapacity ?? 0)>512_000_000 else { throw LocalFailure(message: "At least 2 GB device RAM and 512 MB free storage are required") }
        let warning = NotificationCenter.default.addObserver(forName: UIApplication.didReceiveMemoryWarningNotification,object: nil,queue: nil) { _ in control.stop("Stopped for low memory") }
        defer { NotificationCenter.default.removeObserver(warning) }
        try control.check()
        let asset = AVURLAsset(url: source)
        guard let video = asset.tracks(withMediaType: .video).first else { throw LocalFailure(message: "No video track") }
        let duration = CMTimeGetSeconds(asset.duration)
        guard duration.isFinite,duration<=3600,start<duration else { throw LocalFailure(message: "Invalid source duration or clip start") }
        let count = Int(ceil(min(seconds,duration-start)*15));guard count>=8 else { throw LocalFailure(message: "Clip is too short") }
        let transformed = video.naturalSize.applying(video.preferredTransform)
        let outputSize = try dimensions(width: abs(transformed.width),height: abs(transformed.height))
        let w = Int(outputSize.width),h = Int(outputSize.height)
        let frameDuration = CMTime(value: Int64(count),timescale: 15)
        if let audio = asset.tracks(withMediaType: .audio).first {
            guard let rawFormat = audio.formatDescriptions.first,
                  CMAudioFormatDescriptionGetStreamBasicDescription(rawFormat as! CMAudioFormatDescription)?.pointee.mFormatID == kAudioFormatMPEG4AAC else { throw LocalFailure(message: "Phone remix requires AAC source audio; use the computer engine for other sound formats") }
        }
        for description in video.formatDescriptions {
            let format = description as! CMFormatDescription
            let transfer = CMFormatDescriptionGetExtension(format,extensionKey: kCMFormatDescriptionExtension_TransferFunction) as? String
            guard transfer != (kCMFormatDescriptionTransferFunction_SMPTE_ST_2084_PQ as String),transfer != (kCMFormatDescriptionTransferFunction_ITU_R_2100_HLG as String) else { throw LocalFailure(message: "Export HDR as SDR before phone remix") }
        }
        let mask = personMode ? try PhonePersonMask(model: model) : nil
        let generator = AVAssetImageGenerator(asset: asset);generator.appliesPreferredTrackTransform = true;generator.maximumSize = outputSize
        generator.requestedTimeToleranceBefore = .zero;generator.requestedTimeToleranceAfter = .zero
        let format = UIGraphicsImageRendererFormat();format.scale = 1
        let bg = UIGraphicsImageRenderer(size: outputSize,format: format).image { _ in
            let scale = max(outputSize.width/background.size.width,outputSize.height/background.size.height)
            let size = CGSize(width: background.size.width*scale,height: background.size.height*scale)
            background.draw(in: CGRect(x: (outputSize.width-size.width)/2,y: (outputSize.height-size.height)/2,width: size.width,height: size.height))
        }
        guard let bgImage = bg.cgImage else { throw LocalFailure(message: "Cannot prepare background") }
        let silent = output.deletingLastPathComponent().appendingPathComponent("silent-\(UUID().uuidString).mp4")
        var complete = false
        defer { try? FileManager.default.removeItem(at: silent);if !complete { try? FileManager.default.removeItem(at: output) } }
        let writer = try AVAssetWriter(outputURL: silent,fileType: .mp4)
        defer { if writer.status == .writing { writer.cancelWriting() } }
        let input = AVAssetWriterInput(mediaType: .video,outputSettings: [AVVideoCodecKey:AVVideoCodecType.h264,AVVideoWidthKey:w,AVVideoHeightKey:h,AVVideoCompressionPropertiesKey:[AVVideoAverageBitRateKey:2_000_000,AVVideoExpectedSourceFrameRateKey:15]])
        let attributes: [String:Any] = [kCVPixelBufferPixelFormatTypeKey as String:kCVPixelFormatType_32BGRA,kCVPixelBufferWidthKey as String:w,kCVPixelBufferHeightKey as String:h,kCVPixelBufferCGImageCompatibilityKey as String:true,kCVPixelBufferCGBitmapContextCompatibilityKey as String:true]
        let adapter = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input,sourcePixelBufferAttributes: attributes)
        guard writer.canAdd(input) else { throw LocalFailure(message: "H.264 encoder unavailable") };writer.add(input)
        guard writer.startWriting() else { throw writer.error ?? LocalFailure(message: "Encoder could not start") };writer.startSession(atSourceTime: .zero)
        let context = CIContext(options: [.cacheIntermediates:false]);let deadline = Date().addingTimeInterval(1800)
        for frame in 0..<count {
            try control.check();guard Date()<deadline else { throw LocalFailure(message: "Phone remix exceeded 30 minutes") }
            try autoreleasepool {
                let time = CMTime(seconds: start+Double(frame)/15,preferredTimescale: 60000)
                let decoded = try generator.copyCGImage(at: time,actualTime: nil)
                let exact = UIGraphicsImageRenderer(size: outputSize,format: format).image { _ in UIImage(cgImage: decoded).draw(in: CGRect(origin: .zero,size: outputSize)) }
                guard let image = exact.cgImage else { throw LocalFailure(message: "Cannot resize source frame") }
                let result = try composite(image,background: bgImage,person: mask,context: context)
                let wait = Date().addingTimeInterval(30)
                while !input.isReadyForMoreMediaData { try control.check();guard writer.status == .writing,Date()<wait else { throw writer.error ?? LocalFailure(message: "Video encoder timed out") };Thread.sleep(forTimeInterval: 0.005) }
                guard let pool = adapter.pixelBufferPool else { throw LocalFailure(message: "Encoder pool unavailable") }
                var buffer: CVPixelBuffer?;guard CVPixelBufferPoolCreatePixelBuffer(nil,pool,&buffer)==kCVReturnSuccess,let buffer else { throw LocalFailure(message: "Video memory allocation failed") }
                context.render(CIImage(cgImage: result),to: buffer)
                guard adapter.append(buffer,withPresentationTime: CMTime(value: Int64(frame),timescale: 15)) else { throw writer.error ?? LocalFailure(message: "Frame was rejected") }
            }
            progress((frame+1)*90/count)
        }
        writer.endSession(atSourceTime: frameDuration);input.markAsFinished()
        let done = DispatchSemaphore(value: 0);writer.finishWriting { done.signal() }
        while done.wait(timeout: .now()+0.1) != .success { try control.check();guard Date()<deadline else { throw LocalFailure(message: "Encoder finalize timed out") } }
        guard writer.status == .completed else { throw writer.error ?? LocalFailure(message: "Could not finalize video") }
        try control.check()
        let composition = AVMutableComposition(),encoded = AVURLAsset(url: silent)
        guard let encodedVideo = encoded.tracks(withMediaType: .video).first,let target = composition.addMutableTrack(withMediaType: .video,preferredTrackID: kCMPersistentTrackID_Invalid) else { throw LocalFailure(message: "Cannot prepare output video") }
        try target.insertTimeRange(CMTimeRange(start: .zero,duration: frameDuration),of: encodedVideo,at: .zero)
        if let audio = asset.tracks(withMediaType: .audio).first,let targetAudio = composition.addMutableTrack(withMediaType: .audio,preferredTrackID: kCMPersistentTrackID_Invalid) {
            let from = CMTime(seconds: start,preferredTimescale: 60000)
            let range = CMTimeRange(start: from,duration: frameDuration).intersection(audio.timeRange)
            if range.duration > .zero { try targetAudio.insertTimeRange(range,of: audio,at: CMTimeSubtract(range.start,from)) }
        }
        guard let export = AVAssetExportSession(asset: composition,presetName: AVAssetExportPresetPassthrough) else { throw LocalFailure(message: "Cannot prepare MP4 export") }
        export.outputURL = output;export.outputFileType = .mp4;export.shouldOptimizeForNetworkUse = true
        export.exportAsynchronously {}
        while export.status == .waiting || export.status == .exporting {
            do { try control.check();guard Date()<deadline else { throw LocalFailure(message: "MP4 export timed out") } }
            catch { export.cancelExport();throw error }
            Thread.sleep(forTimeInterval: 0.01)
        }
        guard export.status == .completed else { throw export.error ?? LocalFailure(message: "Cannot preserve source AAC audio") }
        try control.check();complete = true;progress(100)
    }
}
