import Foundation
import UIKit
import sf_engine

struct LocalFailure: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}
struct NativeDiffusion {
    static func generate(model: URL, prompt: String, negative: String, size: Int, steps: Int, seed: Int64, initial: UIImage?,strength: Double = 0.65) throws -> UIImage {
        guard strength.isFinite,(0.1...1).contains(strength),!prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, prompt.count <= 2000, negative.count <= 2000, !prompt.contains("\0"), !negative.contains("\0"), [256,512].contains(size), (1...40).contains(steps) else { throw LocalFailure(message: "Invalid prompt or inference parameters") }
        guard ProcessInfo.processInfo.physicalMemory >= (size == 512 ? 7_500_000_000 : 5_500_000_000) else { throw LocalFailure(message: "This resolution requires 6 GB (256px) or 8 GB (512px) device memory") }
        var input = [UInt8]()
        if let initial {
            let format = UIGraphicsImageRendererFormat(); format.scale = 1; format.opaque = true
            let fitted = UIGraphicsImageRenderer(size: CGSize(width: CGFloat(size), height: CGFloat(size)), format: format).image { ctx in
                UIColor.black.setFill(); ctx.fill(CGRect(x: 0,y: 0,width: CGFloat(size),height: CGFloat(size)))
                let scale = min(CGFloat(size)/initial.size.width, CGFloat(size)/initial.size.height)
                let w = initial.size.width*scale, h = initial.size.height*scale
                initial.draw(in: CGRect(x: (CGFloat(size)-w)/2,y: (CGFloat(size)-h)/2,width: w,height: h))
            }
            var rgba = [UInt8](repeating: 0, count: size*size*4)
            guard let cg = fitted.cgImage else { throw LocalFailure(message: "Photo cannot be decoded") }
            let drawn = rgba.withUnsafeMutableBytes { bytes -> Bool in
                guard let ctx = CGContext(data: bytes.baseAddress,width: size,height: size,bitsPerComponent: 8,bytesPerRow: size*4,space: CGColorSpaceCreateDeviceRGB(),bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
                ctx.draw(cg,in: CGRect(x: 0,y: 0,width: CGFloat(size),height: CGFloat(size))); return true
            }
            guard drawn else { throw LocalFailure(message: "Photo conversion failed") }
            input.reserveCapacity(size*size*3)
            for i in 0..<(size*size) { input.append(contentsOf: rgba[(i*4)..<(i*4+3)]) }
        }
        var output: UnsafeMutablePointer<UInt8>?, error: UnsafeMutablePointer<CChar>?
        let ok = model.path.withCString { modelPath in prompt.withCString { text in negative.withCString { excluded in
            input.withUnsafeBufferPointer { pixels in
                sf_generate(modelPath,text,excluded,Int32(size),Int32(size),Int32(steps),seed,Float(strength),initial == nil ? nil : pixels.baseAddress,&output,&error)
            }
        } } }
        defer { sf_free(output); sf_free(error) }
        guard ok != 0, let output else { throw LocalFailure(message: error.map { String(cString: $0) } ?? "Local model failed") }
        let data = Data(bytes: output,count: size*size*3)
        guard let provider = CGDataProvider(data: data as CFData), let image = CGImage(width: size,height: size,bitsPerComponent: 8,bitsPerPixel: 24,bytesPerRow: size*3,space: CGColorSpaceCreateDeviceRGB(),bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.none.rawValue),provider: provider,decode: nil,shouldInterpolate: true,intent: .defaultIntent) else { throw LocalFailure(message: "Invalid generated image") }
        return UIImage(cgImage: image)
    }
}
