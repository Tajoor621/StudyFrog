package com.scenio.mth.generation

import org.json.JSONObject

object ProfileCodec {
    fun encode(value:GenerationProfile)=JSONObject().put("enabled",value.enabled).put("style",value.style).put("detail",value.detail).put("realism",value.realism).put("scientific",value.scientific)
    fun decode(value:JSONObject?)=if(value==null) GenerationProfile() else GenerationProfile(value.optBoolean("enabled",false),value.optString("style","natural"),value.optInt("detail",50),value.optInt("realism",50),value.optBoolean("scientific",false))
}
