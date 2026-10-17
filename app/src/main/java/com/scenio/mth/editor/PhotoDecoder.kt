package com.scenio.mth.editor

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object PhotoDecoder {
    fun read(resolver: ContentResolver, uri: Uri): Bitmap {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        resolver.openInputStream(uri).use { requireNotNull(it);BitmapFactory.decodeStream(it,null,bounds) }
        require(bounds.outWidth>0 && bounds.outHeight>0) { "Unsupported or damaged image" }
        require(bounds.outWidth.toLong()*bounds.outHeight<=100_000_000) { "Image dimensions exceed the import limit" }
        val options=BitmapFactory.Options().apply { inSampleSize=1 }
        while(bounds.outWidth/options.inSampleSize>2048 || bounds.outHeight/options.inSampleSize>2048) options.inSampleSize*=2
        val decoded=resolver.openInputStream(uri).use { requireNotNull(it);requireNotNull(BitmapFactory.decodeStream(it,null,options)) }
        val orientation=resolver.openInputStream(uri).use { input ->
            if(input==null) ExifInterface.ORIENTATION_NORMAL else runCatching { ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        }
        val matrix=Matrix()
        when(orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f,1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f,-1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f);matrix.postScale(-1f,1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f);matrix.postScale(-1f,1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        }
        return if(matrix.isIdentity) decoded else Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true).also { if(it!==decoded) decoded.recycle() }
    }
}
