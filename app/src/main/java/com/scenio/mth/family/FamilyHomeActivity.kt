package com.scenio.mth.family

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import com.scenio.mth.science.StudyExplorerActivity

class FamilyHomeActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(32,32,32,32) }
        setContentView(ScrollView(this).apply {
            addView(body)
            setOnApplyWindowInsetsListener { view,insets ->
                if(android.os.Build.VERSION.SDK_INT>=30) { val b=insets.getInsets(android.view.WindowInsets.Type.systemBars());view.setPadding(b.left,b.top,b.right,b.bottom) }
                else view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
                insets
            }
        })
        body.addView(TextView(this).apply { text="StudyForge · study-only workspace";textSize=25f;androidx.core.view.ViewCompat.setAccessibilityHeading(this,true) })
        body.addView(TextView(this).apply { text="Explore bundled educational diagrams, including medical anatomy. Prompts, imported photos, private projects, external browsing and generation are locked. Educational diagrams still need teacher review.";textSize=18f })
        body.addView(Button(this).apply { text="Explore offline science";setOnClickListener { startActivity(Intent(this@FamilyHomeActivity,StudyExplorerActivity::class.java)) } })
        body.addView(Button(this).apply { text="Parent controls · PIN required";setOnClickListener { startActivity(Intent(this@FamilyHomeActivity,FamilyControlsActivity::class.java)) } })
    }
}
