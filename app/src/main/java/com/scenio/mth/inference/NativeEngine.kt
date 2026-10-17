package com.scenio.mth.inference

object NativeEngine {
    init { System.loadLibrary("native-lib") }
    external fun available():Boolean
    external fun progress():Int
    external fun cancel()
    external fun generate(model:ByteArray,prompt:ByteArray,negative:ByteArray,size:Int,steps:Int,seed:Long,strength:Float,initial:IntArray?):IntArray
}
