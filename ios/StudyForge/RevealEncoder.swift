import AVFoundation
import UIKit

struct RevealEncoder {
    static func encode(image: UIImage, output: URL, progress: @escaping @Sendable (Int) -> Void) throws {
        let writer = try AVAssetWriter(outputURL: output,fileType: .mp4)
        let input = AVAssetWriterInput(mediaType: .video,outputSettings: [AVVideoCodecKey: AVVideoCodecType.h264,AVVideoWidthKey: 1920,AVVideoHeightKey: 1080,AVVideoCompressionPropertiesKey: [AVVideoAverageBitRateKey: 6_000_000]])
        let attributes: [String: Any] = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB,kCVPixelBufferWidthKey as String: 1920,kCVPixelBufferHeightKey as String: 1080,kCVPixelBufferCGImageCompatibilityKey as String: true,kCVPixelBufferCGBitmapContextCompatibilityKey as String: true]
        let adapter = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input,sourcePixelBufferAttributes: attributes)
        guard writer.canAdd(input) else { throw LocalFailure(message: "Video encoder unavailable") }
        writer.add(input); guard writer.startWriting() else { throw writer.error ?? LocalFailure(message: "Video encoder cannot start") }
        writer.startSession(atSourceTime: .zero)
        var completed = false
        defer { if !completed { writer.cancelWriting(); try? FileManager.default.removeItem(at: output) } }
        let format = UIGraphicsImageRendererFormat();format.scale = 1;format.opaque = true
        let still = UIGraphicsImageRenderer(size: CGSize(width: 1920,height: 1080),format: format).image { ctx in
            UIColor.black.setFill();ctx.fill(CGRect(x: 0,y: 0,width: 1920,height: 1080))
            let scale = min(1920/image.size.width,1080/image.size.height)
            let w = image.size.width*scale,h = image.size.height*scale
            image.draw(in: CGRect(x: (1920-w)/2,y: (1080-h)/2,width: w,height: h))
        }
        guard let cgImage = still.cgImage else { throw LocalFailure(message: "Cannot render source image") }
        for frame in 0..<720 {
            try Task.checkCancellation()
            let deadline = Date().addingTimeInterval(30)
            while !input.isReadyForMoreMediaData {
                guard Date() < deadline, writer.status == .writing else { throw writer.error ?? LocalFailure(message: "Video encoder timed out") }
                Thread.sleep(forTimeInterval: 0.005)
            }
            try autoreleasepool {
                guard let pool = adapter.pixelBufferPool else { throw LocalFailure(message: "Encoder buffer unavailable") }
                var buffer: CVPixelBuffer?
                guard CVPixelBufferPoolCreatePixelBuffer(nil,pool,&buffer) == kCVReturnSuccess, let buffer else { throw LocalFailure(message: "Video buffer allocation failed") }
                CVPixelBufferLockBaseAddress(buffer,[]);defer { CVPixelBufferUnlockBaseAddress(buffer,[]) }
                guard let ctx = CGContext(data: CVPixelBufferGetBaseAddress(buffer),width: 1920,height: 1080,bitsPerComponent: 8,bytesPerRow: CVPixelBufferGetBytesPerRow(buffer),space: CGColorSpaceCreateDeviceRGB(),bitmapInfo: CGImageAlphaInfo.noneSkipFirst.rawValue) else { throw LocalFailure(message: "Video drawing failed") }
                ctx.setFillColor(UIColor.black.cgColor);ctx.fill(CGRect(x: 0,y: 0,width: 1920,height: 1080))
                ctx.saveGState();ctx.clip(to: CGRect(x: 0,y: 0,width: min(1920,CGFloat(frame)/672*1920),height: 1080));ctx.draw(cgImage,in: CGRect(x: 0,y: 0,width: 1920,height: 1080));ctx.restoreGState()
                guard adapter.append(buffer,withPresentationTime: CMTime(value: Int64(frame),timescale: 24)) else { throw writer.error ?? LocalFailure(message: "Video frame rejected") }
            }
            if frame%24 == 0 { progress(frame*100/720) }
        }
        input.markAsFinished();let finish = DispatchSemaphore(value: 0)
        writer.finishWriting { finish.signal() }
        guard finish.wait(timeout: .now()+60) == .success, writer.status == .completed else { throw writer.error ?? LocalFailure(message: "Could not finalize video") }
        completed = true;progress(100)
    }
}
