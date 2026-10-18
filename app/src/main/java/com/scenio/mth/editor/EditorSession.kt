package com.scenio.mth.editor

import androidx.lifecycle.ViewModel
import com.scenio.mth.ForgeCanvas

/** Retain editor content over configuration changes without retaining an Activity. */
class EditorSession : ViewModel() {
    var snapshot: ForgeCanvas.Snapshot? = null
    var folder = ""
    var profile = com.scenio.mth.generation.GenerationProfile()
    var prompt = ""
    var negative = ""
    var mode = 0
    var level = 0
    var steps = 10
    var seed = ""
}
