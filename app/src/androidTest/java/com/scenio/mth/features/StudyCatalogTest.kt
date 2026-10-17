package com.scenio.mth.features

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scenio.mth.ForgeCanvas
import com.scenio.mth.science.StudyCatalog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyCatalogTest {
    @Test fun deepViewProducesAnEditablePersistentLabelLayer() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>();val catalog=StudyCatalog(context)
        assertEquals(listOf("Organism","System","Organ","Tissue","Cellular"),catalog.path("cardiomyocyte").map { it.level })
        assertTrue(catalog.generationPrompt("cardiomyocyte").contains("Human body > Cardiovascular system > Heart"))
        val snapshot=catalog.snapshot("cardiomyocyte");val canvas=ForgeCanvas(context);canvas.restore(snapshot)
        canvas.updateTextLabel(0,"Cell membrane",.2f,.3f)
        val roundTrip=ForgeCanvas.decodeDocument(ForgeCanvas.encodeInk(canvas.snapshot()))
        assertEquals("Cell membrane",roundTrip.strokes[0].text);assertEquals(.2f,roundTrip.strokes[0].points[0].x,0f)
        assertEquals("study-labels",roundTrip.strokes[0].layerId);snapshot.base?.recycle()
    }
    @Test fun everyBundledViewRendersWithAndWithoutLabels() {
        val catalog=StudyCatalog(ApplicationProvider.getApplicationContext())
        for(node in catalog.nodes) {
            val snapshot=catalog.snapshot(node.id);val rendered=ForgeCanvas.render(snapshot)
            assertEquals(1200,rendered.width);assertEquals(900,rendered.height);rendered.recycle();snapshot.base?.recycle()
            val hidden=catalog.snapshot(node.id,false);assertTrue(hidden.strokes.isEmpty());hidden.base?.recycle()
        }
    }
}
