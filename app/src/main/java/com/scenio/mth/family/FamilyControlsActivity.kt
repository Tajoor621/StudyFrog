package com.scenio.mth.family

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.scenio.mth.features.FeaturesActivity
import com.scenio.mth.inference.LocalGenerationWorker
import com.scenio.mth.inference.NativeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FamilyControlsActivity:ComponentActivity() {
    private var working=false
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onBackPressedDispatcher.addCallback(this,object:androidx.activity.OnBackPressedCallback(true) { override fun handleOnBackPressed() { if(!working) finish() } })
        val state=FamilyPolicy.status(this)
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(32,32,32,32) }
        setContentView(ScrollView(this).apply {
            addView(body)
            setOnApplyWindowInsetsListener { view,insets ->
                if(android.os.Build.VERSION.SDK_INT>=30) { val b=insets.getInsets(android.view.WindowInsets.Type.systemBars());view.setPadding(b.left,b.top,b.right,b.bottom) }
                else view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
                insets
            }
        })
        fun text(value:String) { body.addView(TextView(this).apply { text=value;textSize=17f;setPadding(0,16,0,16) }) }
        fun pin(label:String)=EditText(this).apply { hint=label;contentDescription=label;inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD;filters=arrayOf(android.text.InputFilter.LengthFilter(6));setSaveEnabled(false);body.addView(this) }
        text("Family access controls")
        text("Study-only mode opens bundled diagrams and anatomy, and locks prompts, photos, projects, model generation and computer transfers. It is an app access boundary, not verified age detection or certified child safety.")
        text("Keep your PIN safe. There is no account or remote PIN recovery. Back up important projects before enabling the lock. Device owners can remove app data; this does not replace device parental controls.")
        val current=if(state.configured) pin("Current six-digit parent PIN") else null
        val first=pin(if(state.configured) "New PIN · only for changing PIN" else "Choose six-digit parent PIN")
        val confirmation=pin("Confirm new PIN")
        val status=TextView(this).apply { accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE };body.addView(status)
        fun perform(change:Boolean) {
            if(working) return;working=true
            val old=current?.text?.toString().orEmpty();val next=first.text.toString();val confirm=confirmation.text.toString()
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        if(change || !state.configured) require(next==confirm) { "New PINs do not match" }
                        when { !state.configured -> FamilyPolicy.configureAndLock(applicationContext,next);change -> FamilyPolicy.changePin(applicationContext,old,next);else -> FamilyPolicy.setRestricted(applicationContext,old,!state.restricted) }
                    }
                    if(FamilyPolicy.restricted(this@FamilyControlsActivity)) {
                        NativeEngine.cancel();WorkManager.getInstance(applicationContext).cancelUniqueWork(LocalGenerationWorker.NAME)
                        openHome(this@FamilyControlsActivity)
                    } else if(!change) startActivity(Intent(this@FamilyControlsActivity,FeaturesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    else { status.text="PIN changed";current?.text?.clear();first.text.clear();confirmation.text.clear() }
                } catch(e:kotlinx.coroutines.CancellationException) { throw e } catch(e:Exception) { status.text=e.message ?: "Could not change family access" }
                finally { working=false }
            }
        }
        body.addView(Button(this).apply { text=if(!state.configured) "Set PIN and enable study-only mode" else if(state.restricted) "Unlock creative workspace" else "Enable study-only mode";setOnClickListener { perform(false) } })
        if(state.configured) body.addView(Button(this).apply { text="Change parent PIN";setOnClickListener { perform(true) } })
        body.addView(Button(this).apply { text="Back";setOnClickListener { if(!working) finish() } })
    }
    companion object {
        fun openHome(context:android.content.Context) { context.startActivity(Intent(context,FamilyHomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) }
    }
}
