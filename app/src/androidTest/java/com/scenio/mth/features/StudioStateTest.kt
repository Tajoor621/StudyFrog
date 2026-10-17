package com.scenio.mth.features

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scenio.mth.ForgeCanvas
import com.scenio.mth.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioStateTest {
    private fun canvas(view:View):ForgeCanvas? {
        if(view is ForgeCanvas) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) canvas(view.getChildAt(i))?.let { return it }
        return null
    }
    @Test fun nativeStudioPreservesPortraitImageAndEditableInkOnRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.nativeStatus().contains("native library loaded"))
                val image=Bitmap.createBitmap(64,128,Bitmap.Config.ARGB_8888)
                val ink=ForgeCanvas.Stroke(mutableListOf(ForgeCanvas.Point(.5f,.5f)),0xff00ff00.toInt(),.01f)
                requireNotNull(canvas(activity.findViewById(android.R.id.content))).restore(ForgeCanvas.Snapshot(image,listOf(ink)))
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val restored=requireNotNull(canvas(activity.findViewById(android.R.id.content))).snapshot()
                assertEquals(64,restored.base!!.width);assertEquals(128,restored.base!!.height)
                assertEquals(1,restored.strokes.size)
                assertEquals(.5f,restored.strokes.first().points.first().x,.0001f)
            }
        }
    }
}
