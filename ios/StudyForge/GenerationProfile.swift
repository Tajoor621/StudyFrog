import Foundation
import SwiftUI

struct GenerationProfile: Codable, Equatable {
    var enabled = false
    var style = "natural"
    var detail = 50.0
    var realism = 50.0
    var scientific = false
    static let styles = [("natural","Natural composition"),("scientific","Clean scientific illustration"),("diagram","Clear technical diagram"),("photo","Photorealistic image"),("graphic","Graphic design concept"),("watercolor","Watercolor illustration")]
    func apply(_ prompt: String) throws -> String {
        guard enabled else { return prompt }
        guard let direction = Self.styles.first(where: { $0.0 == style }),detail.isFinite,realism.isFinite,(0...100).contains(detail),(0...100).contains(realism) else { throw LocalFailure(message: "Invalid visual directions") }
        let details = detail < 34 ? "simple composition, essential details" : detail < 67 ? "balanced detail, clear composition" : "fine detail, carefully resolved small structures"
        let rendering = realism < 34 ? "flat illustrative rendering" : realism < 67 ? "semi-realistic rendering" : "photographic lighting, realistic materials"
        let science = scientific ? "; educational scientific intent, omit uncertain details, do not invent labels; human review required" : ""
        let result = prompt+"\nVisual direction: "+direction.1.lowercased()+"; "+details+"; "+rendering+science
        guard result.count <= 2000 else { throw LocalFailure(message: "Prompt plus visual directions exceeds 2000 characters; shorten it or disable visual directions") }
        return result
    }
}
struct GenerationControlsView: View {
    @Binding var profile: GenerationProfile
    @Binding var steps: Int
    @Binding var strength: Double
    let prompt: String
    var body: some View {
        DisclosureGroup("Visual directions and inference settings") {
            Toggle("Append visual directions to my prompt",isOn: $profile.enabled)
            Picker("Style",selection: $profile.style) { ForEach(GenerationProfile.styles,id: \.0) { style in Text(style.1).tag(style.0) } }
            Slider(value: $profile.detail,in: 0...100,step: 1) { Text("Detail · simple to fine") }
            Slider(value: $profile.realism,in: 0...100,step: 1) { Text("Realism · illustrative to photographic") }
            Toggle("Scientific intent · requires human review",isOn: $profile.scientific)
            Stepper("Inference steps: \(steps)",value: $steps,in: 10...40)
            Slider(value: $strength,in: 0.1...1) { Text("Photo transformation strength") }
            Text("Directions are added to the prompt. They do not verify scientific accuracy or guarantee model adherence.").font(.footnote)
            Text((try? profile.apply(prompt)) ?? "Prompt plus directions is too long").font(.caption).textSelection(.enabled)
        }
    }
}
