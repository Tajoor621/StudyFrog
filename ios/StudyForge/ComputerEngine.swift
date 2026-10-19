import Foundation
import UIKit

final class LocalOnlyRedirectDelegate: NSObject,URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession,task: URLSessionTask,willPerformHTTPRedirection response: HTTPURLResponse,newRequest request: URLRequest,completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}
@MainActor struct ComputerEngine {
    private static let session: URLSession = {
        let config = URLSessionConfiguration.ephemeral;config.httpShouldSetCookies = false;config.urlCache = nil;config.urlCredentialStorage = nil
        return URLSession(configuration: config,delegate: LocalOnlyRedirectDelegate(),delegateQueue: nil)
    }()
    let host: String
    let token: String
    private func endpoint(_ path: String) throws -> URL {
        guard !FamilyAccess.shared.restricted else { throw LocalFailure(message: "Computer transfers are locked by family controls") }
        guard host == "localhost" || (host.hasSuffix(".local") && host.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "." || $0 == "-") })), !token.isEmpty, let url = URL(string: "http://\(host):8765\(path)") else { throw LocalFailure(message: "Enter your computer's .local hostname and engine token") }
        return url
    }
    private func request(_ path: String,method: String = "GET",body: Data? = nil) throws -> URLRequest {
        var request = URLRequest(url: try endpoint(path));request.timeoutInterval = 30;request.httpMethod = method;request.httpBody = body
        request.setValue("Bearer \(token)",forHTTPHeaderField: "Authorization");request.setValue("application/json",forHTTPHeaderField: "Content-Type");return request
    }
    func submit(image: UIImage?,prompt: String,negative: String,seed: Int64,kind: String,control: String = "none",controlScale: Double = 0.7,steps: Int = 20,strength: Double = 0.65) async throws -> String {
        var fields: [String:Any] = ["kind":kind,"prompt":prompt,"negative":negative,"seed":seed,"steps":steps,"strength":strength,"control":control,"control_scale":controlScale]
        if let image {
            let scale = min(1,1024/max(image.size.width,image.size.height))
            let format = UIGraphicsImageRendererFormat();format.scale = 1
            let upload = UIGraphicsImageRenderer(size: CGSize(width: image.size.width*scale,height: image.size.height*scale),format: format).image { _ in image.draw(in: CGRect(x: 0,y: 0,width: image.size.width*scale,height: image.size.height*scale)) }
            guard let png = upload.pngData() else { throw LocalFailure(message: "Photo could not be encoded") }
            fields["image"] = png.base64EncodedString()
        }
        let body = try JSONSerialization.data(withJSONObject: fields)
        let data = try await json(try request("/jobs",method: "POST",body: body))
        guard let id = data["id"] as? String, id.count == 32, id.allSatisfy({ $0.isHexDigit && !$0.isUppercase }) else { throw LocalFailure(message: "Invalid engine job identifier") }
        return id
    }
    func recognize(image: UIImage,language: String) async throws -> String {
        let format = UIGraphicsImageRendererFormat();format.scale = 1
        let scale = min(1,2400/max(image.size.width,image.size.height))
        let size = CGSize(width: image.size.width*scale,height: image.size.height*scale)
        let upload = UIGraphicsImageRenderer(size: size,format: format).image { _ in image.draw(in: CGRect(origin: .zero,size: size)) }
        guard let png = upload.pngData() else { throw LocalFailure(message: "Photo could not be encoded") }
        let body = try JSONSerialization.data(withJSONObject: ["image":png.base64EncodedString(),"language":language])
        let result = try await json(try request("/ocr",method: "POST",body: body))
        guard let text = result["text"] as? String,text.count <= 20000 else { throw LocalFailure(message: "Invalid OCR response") }
        return text
    }
    func status(_ id: String) async throws -> [String:Any] { try await json(try request("/jobs/\(id)")) }
    func cancel(_ id: String) async throws { _ = try await json(try request("/jobs/\(id)/cancel",method: "POST",body: Data("{}".utf8))) }
    func result(_ id: String,video: Bool = true) async throws -> URL {
        let (file,response) = try await Self.session.download(for: request("/results/\(id)"))
        guard let response = response as? HTTPURLResponse, response.statusCode == 200 else { throw LocalFailure(message: "Result download failed") }
        let size = try FileManager.default.attributesOfItem(atPath: file.path)[.size] as? NSNumber
        guard let size, size.int64Value <= 256_000_000 else { throw LocalFailure(message: "Result exceeds 256 MB") }
        let output = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString+(video ? ".mp4" : ".png"))
        try FileManager.default.moveItem(at: file,to: output);return output
    }
    private func json(_ request: URLRequest) async throws -> [String:Any] {
        let (data,response) = try await Self.session.data(for: request)
        guard data.count <= 1_000_000,let response = response as? HTTPURLResponse,let value = try JSONSerialization.jsonObject(with: data) as? [String:Any] else { throw LocalFailure(message: "Invalid engine response") }
        guard (200..<300).contains(response.statusCode) else { throw LocalFailure(message: value["error"] as? String ?? "Engine request failed") }
        return value
    }
}
