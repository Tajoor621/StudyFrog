import Foundation
import UIKit
import PencilKit
import ImageIO
import CryptoKit

/// Local layered raster exchange; native PencilKit projects retain their original strokes.
struct PortableProject {
    static let limit = 64_000_000
    struct Asset: Codable { var width: Int;var height: Int;var sha256: String;var png: String }
    struct Layer: Codable { var name: String;var visible: Bool;var opacity: Double;var blend: String;var transform: LayerTransform;var image: Asset }
    struct File: Codable {
        var format = "studyforge-layered-raster"
        var version = 1
        var title: String;var prompt: String;var negative: String;var folder: String;var selected: Int
        var base: Asset;var layers: [Layer]
    }
    struct Document { var title: String;var prompt: String;var negative: String;var folder: String;var selected: Int;var base: UIImage;var layers: [IOSInkLayer] }
    private static func fail() -> LocalFailure { LocalFailure(message: "Invalid, damaged or oversized portable project") }
    private static func text(_ value: String,_ length: Int) throws -> String {
        guard value.utf16.count <= length,!value.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) && $0.value != 10 && $0.value != 9 }) else { throw fail() };return value
    }
    private static func digest(_ data: Data) -> String { SHA256.hash(data: data).map { String(format: "%02x",$0) }.joined() }
    private static func asset(_ image: UIImage) throws -> Asset {
        guard let cg = image.cgImage,let data = image.pngData() else { throw fail() }
        return Asset(width: cg.width,height: cg.height,sha256: digest(data),png: data.base64EncodedString())
    }
    static func encode(base: UIImage,layers: [IOSInkLayer],selected: Int,title: String,prompt: String,negative: String,folder: String) throws -> Data {
        try IOSLayerRenderer.validate(layers)
        guard let cg = base.cgImage,cg.width <= 2048,cg.height <= 2048,(0..<layers.count).contains(selected),!folder.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }) else { throw fail() }
        let side = min(1024,Int(sqrt(8_000_000.0/Double(layers.count))))
        let factor = min(1,Double(side)/Double(max(cg.width,cg.height)))
        let size = CGSize(width: CGFloat(max(1,Int(Double(cg.width)*factor))),height: CGFloat(max(1,Int(Double(cg.height)*factor))))
        let images = try layers.map { layer in
            Layer(name: try text(layer.name,80),visible: layer.visible,opacity: layer.opacity,blend: layer.blend,transform: layer.transform,image: try asset(IOSLayerRenderer.foreground(layer,size: size)))
        }
        let document = File(title: try text(title,160),prompt: try text(prompt,2000),negative: try text(negative,2000),folder: try text(folder,80),selected: selected,base: try asset(base),layers: images)
        let data = try JSONEncoder().encode(document);guard data.count <= limit else { throw fail() };return data
    }
    static func decode(_ data: Data) throws -> Document {
        guard data.count <= limit else { throw fail() }
        var depth = 0,quoted = false,escaped = false
        for byte in data {
            if quoted { if escaped { escaped = false } else if byte == 92 { escaped = true } else if byte == 34 { quoted = false } }
            else if byte == 34 { quoted = true }
            else if byte == 123 || byte == 91 { depth += 1;guard depth <= 16 else { throw fail() } }
            else if byte == 125 || byte == 93 { depth -= 1 }
        }
        let file = try JSONDecoder().decode(File.self,from: data)
        guard file.format == "studyforge-layered-raster",file.version == 1,(1...16).contains(file.layers.count),(0..<file.layers.count).contains(file.selected),!file.folder.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }) else { throw fail() }
        _ = try text(file.title,160);_ = try text(file.prompt,2000);_ = try text(file.negative,2000);_ = try text(file.folder,80)
        func image(_ value: Asset,_ side: Int,_ bytes: Int) throws -> UIImage {
            guard (1...side).contains(value.width),(1...side).contains(value.height),value.png.utf8.count <= ((bytes+2)/3)*4,
                  let png = Data(base64Encoded: value.png),png.count <= bytes,png.starts(with: [137,80,78,71,13,10,26,10]),digest(png) == value.sha256,
                  let source = CGImageSourceCreateWithData(png as CFData,[kCGImageSourceShouldCache:false] as CFDictionary),
                  let properties = CGImageSourceCopyPropertiesAtIndex(source,0,nil) as? [CFString:Any],
                  let width = properties[kCGImagePropertyPixelWidth] as? Int,let height = properties[kCGImagePropertyPixelHeight] as? Int,width == value.width,height == value.height,
                  let cg = CGImageSourceCreateImageAtIndex(source,0,[kCGImageSourceShouldCacheImmediately:true] as CFDictionary) else { throw fail() }
            return UIImage(cgImage: cg)
        }
        let base = try image(file.base,2048,32_000_000)
        var pixels = 0
        let layers = try file.layers.map { layer -> IOSInkLayer in
            guard (1...1024).contains(layer.image.width),(1...1024).contains(layer.image.height) else { throw fail() }
            pixels += layer.image.width*layer.image.height;guard pixels <= 8_000_000 else { throw fail() }
            _ = try text(layer.name,80)
            guard !layer.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,layer.opacity.isFinite,(0...1).contains(layer.opacity),layer.transform.valid,["normal","multiply","screen"].contains(layer.blend) else { throw fail() }
            return IOSInkLayer(name: layer.name,raster: try image(layer.image,1024,5_000_000),visible: layer.visible,opacity: layer.opacity,blend: layer.blend,transform: layer.transform)
        }
        try IOSLayerRenderer.validate(layers)
        return Document(title: file.title,prompt: file.prompt,negative: file.negative,folder: file.folder,selected: file.selected,base: base,layers: layers)
    }
    static func read(_ url: URL) throws -> Document {
        let access = url.startAccessingSecurityScopedResource();defer { if access { url.stopAccessingSecurityScopedResource() } }
        let handle = try FileHandle(forReadingFrom: url);defer { try? handle.close() }
        var data = Data()
        while let block = try handle.read(upToCount: 65_536),!block.isEmpty { guard data.count+block.count <= limit else { throw fail() };data.append(block) }
        return try decode(data)
    }
}
