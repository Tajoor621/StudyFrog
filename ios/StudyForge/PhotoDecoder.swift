import Foundation
import UIKit
import ImageIO

struct IOSPhotoDecoder {
    static func decode(_ data: Data) throws -> UIImage {
        guard data.count <= 32_000_000, let source = CGImageSourceCreateWithData(data as CFData,[kCGImageSourceShouldCache: false] as CFDictionary), let properties = CGImageSourceCopyPropertiesAtIndex(source,0,nil) as? [CFString:Any], let width = properties[kCGImagePropertyPixelWidth] as? NSNumber, let height = properties[kCGImagePropertyPixelHeight] as? NSNumber, width.int64Value > 0, height.int64Value > 0, width.int64Value*height.int64Value <= 100_000_000 else { throw LocalFailure(message: "Unsupported or oversized photo") }
        let options: [CFString:Any] = [kCGImageSourceCreateThumbnailFromImageAlways:true,kCGImageSourceCreateThumbnailWithTransform:true,kCGImageSourceThumbnailMaxPixelSize:2048,kCGImageSourceShouldCacheImmediately:true]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source,0,options as CFDictionary) else { throw LocalFailure(message: "Photo cannot be decoded") }
        return UIImage(cgImage: image)
    }
}
