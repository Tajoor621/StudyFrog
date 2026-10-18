package com.scenio.mth.generation

/** User-visible prompt directions, not a model-quality or scientific-accuracy guarantee. */
data class GenerationProfile(val enabled:Boolean=false,val style:String="natural",val detail:Int=50,val realism:Int=50,val scientific:Boolean=false) {
    init { require(style in STYLES && detail in 0..100 && realism in 0..100) }
    fun apply(prompt:String):String {
        if(!enabled) return prompt
        val detailText=if(detail<34) "simple composition, essential details" else if(detail<67) "balanced detail, clear composition" else "fine detail, carefully resolved small structures"
        val realismText=if(realism<34) "flat illustrative rendering" else if(realism<67) "semi-realistic rendering" else "photographic lighting, realistic materials"
        val science=if(scientific) "; educational scientific intent, omit uncertain details, do not invent labels; human review required" else ""
        return (prompt+"\nVisual direction: "+STYLES.getValue(style)+"; "+detailText+"; "+realismText+science).also { require(it.length<=2000) { "Prompt plus visual directions exceeds 2000 characters; shorten the prompt or disable visual directions" } }
    }
    companion object { val STYLES=linkedMapOf("natural" to "natural composition","scientific" to "clean scientific illustration","diagram" to "clear technical diagram","photo" to "photorealistic image","graphic" to "graphic design concept","watercolor" to "watercolor illustration") }
}
