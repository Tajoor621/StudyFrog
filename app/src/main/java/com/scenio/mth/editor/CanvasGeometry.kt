package com.scenio.mth.editor

/** Image bounds in a viewport. All ink coordinates are relative to these bounds. */
data class ImageBounds(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun contains(x: Float, y: Float) = x >= left && x <= left + width && y >= top && y <= top + height
}
fun fitImage(viewWidth: Float, viewHeight: Float, imageWidth: Int, imageHeight: Int): ImageBounds {
    require(viewWidth > 0 && viewHeight > 0 && imageWidth > 0 && imageHeight > 0)
    val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
    val width = imageWidth * scale
    val height = imageHeight * scale
    return ImageBounds((viewWidth-width)/2, (viewHeight-height)/2, width, height)
}
