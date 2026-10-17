import Foundation
import UIKit

struct StudySource: Codable, Identifiable { let id: String;let title: String;let url: URL }
struct StudyLabel: Codable { let text: String;let x: Double;let y: Double }
struct StudyShape: Codable { let kind: String;let points: [Double];let color: String;let fill: Bool }
struct StudyNode: Codable, Identifiable {
    let id: String;let title: String;let level: String;let summary: String;let prompt: String;let alt: String
    let sourceIds: [String];let children: [String];let parent: String?;let next: String?
    let shapes: [StudyShape];let labels: [StudyLabel]
}
struct StudyCatalog: Codable {
    let version: Int;let notice: String;let roots: [String];let sources: [StudySource];let nodes: [StudyNode]
    static func load() throws -> StudyCatalog {
        guard let url = Bundle.main.url(forResource: "study-catalog",withExtension: "json") else { throw LocalFailure(message: "Study catalog is missing") }
        let catalog = try JSONDecoder().decode(StudyCatalog.self,from: Data(contentsOf: url))
        guard catalog.version == 1, Set(catalog.nodes.map(\.id)).count == catalog.nodes.count else { throw LocalFailure(message: "Invalid study catalog") }
        for node in catalog.nodes { _ = try catalog.path(node.id) }
        return catalog
    }
    func node(_ id: String) throws -> StudyNode {
        guard let result = nodes.first(where: { $0.id == id }) else { throw LocalFailure(message: "Unknown study view") };return result
    }
    func path(_ id: String) throws -> [StudyNode] {
        var result = [StudyNode](), current: StudyNode? = try node(id)
        while let item = current {
            guard !result.contains(where: { $0.id == item.id }) else { throw LocalFailure(message: "Cyclic study path") }
            result.append(item);current = try item.parent.map { try node($0) }
        }
        return result.reversed()
    }
    func generationPrompt(_ id: String) throws -> String {
        "Study path: " + (try path(id)).map(\.title).joined(separator: " > ") + ". " + (try node(id)).prompt + " Educational illustration; verify all generated labels against the references."
    }
    func render(_ id: String,labels: Bool) throws -> UIImage {
        let node = try node(id), format = UIGraphicsImageRendererFormat();format.scale = 1;format.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: 1200,height: 900),format: format).image { renderer in
            let c = renderer.cgContext
            UIColor(red: 248/255,green: 250/255,blue: 252/255,alpha: 1).setFill();c.fill(CGRect(x: 0,y: 0,width: 1200,height: 900))
            let ink = UIColor(red: 22/255,green: 50/255,blue: 79/255,alpha: 1)
            func text(_ value: String,_ x: CGFloat,_ y: CGFloat,_ size: CGFloat,_ bold: Bool = false) {
                (value as NSString).draw(at: CGPoint(x: x,y: y-size),withAttributes: [.font:bold ? UIFont.boldSystemFont(ofSize: size) : UIFont.systemFont(ofSize: size),.foregroundColor:ink])
            }
            text(node.title,60,66,38,true);text(node.level+" / simplified schematic",60,106,23)
            for shape in node.shapes {
                guard shape.points.count >= 4,shape.points.count % 2 == 0 else { continue }
                let hex = UInt32(shape.color.dropFirst(),radix: 16) ?? 0x16324F
                let color = UIColor(red: CGFloat((hex>>16)&255)/255,green: CGFloat((hex>>8)&255)/255,blue: CGFloat(hex&255)/255,alpha: 1)
                c.setStrokeColor(color.cgColor);c.setFillColor(color.cgColor);c.setLineWidth(5);c.setLineCap(.round)
                func point(_ i: Int) -> CGPoint { CGPoint(x: 60+shape.points[i]*1080,y: 150+shape.points[i+1]*600) }
                let a = point(0), b = point(2), rect = CGRect(x: a.x,y: a.y,width: b.x-a.x,height: b.y-a.y)
                c.beginPath()
                switch shape.kind {
                case "ellipse": c.addEllipse(in: rect)
                case "rect": c.addPath(UIBezierPath(roundedRect: rect,cornerRadius: 18).cgPath)
                default: c.move(to: a);for i in stride(from: 2,to: shape.points.count,by: 2) { c.addLine(to: point(i)) }
                }
                c.drawPath(using: shape.fill ? .fillStroke : .stroke)
            }
            if labels { for label in node.labels { text(label.text,60+label.x*1080,150+label.y*600,29) } }
            text("References: "+node.sourceIds.joined(separator: ", ")+" / OpenStax",60,823,22)
            text("Original conceptual diagram / not to scale / educational use",60,862,22)
        }
    }
}
