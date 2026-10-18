package com.scenio.mth

import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import androidx.work.WorkInfo
import androidx.work.ExistingWorkPolicy
import com.scenio.mth.inference.ModelStore
import com.scenio.mth.inference.NativeEngine
import com.scenio.mth.inference.LocalGenerationWorker
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.widget.addTextChangedListener
import kotlinx.coroutines.flow.collect
import com.scenio.mth.features.data.ThemeMode
import android.content.res.ColorStateList
import android.view.ViewGroup as AndroidViewGroup
import com.scenio.mth.editor.EditorSession
import com.scenio.mth.editor.ProjectStore
import com.scenio.mth.editor.PhotoDecoder
import android.view.View
import android.view.WindowInsets
import java.io.IOException
import androidx.lifecycle.lifecycleScope
import com.scenio.mth.features.FeatureRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.view.ViewGroup
import android.widget.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    external fun nativeStatus(): String
    companion object { init { System.loadLibrary("native-lib") } }
    private val session: EditorSession by viewModels()
    private val projects by lazy { ProjectStore(applicationContext) }
    private var pendingExport: File? = null
    private var dirty = false
    private var projectFolder = ""
    private var generationProfile=com.scenio.mth.generation.GenerationProfile()
    private lateinit var controlMode:Spinner
    private lateinit var controlStrength:SeekBar
    private var editRevision = 0L
    private var darkPalette = true
    private lateinit var canvas: ForgeCanvas
    private lateinit var prompt: EditText
    private lateinit var negative: EditText
    private lateinit var status: TextView
    private lateinit var token: EditText
    private lateinit var mode: Spinner
    private lateinit var level: Spinner
    private lateinit var seed: EditText
    private lateinit var nativeResolution: Spinner
    private lateinit var strength: SeekBar
    private lateinit var steps: SeekBar
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("forge",MODE_PRIVATE) }
    private var latestJob: String? = null
    private var busy = false
    private var localDialog:AlertDialog? = null
    private var localActive = false
    private var loadedLocalResult:String? = null
    private fun text(s:String) = TextView(this).apply { text=s;setTextColor(Color.WHITE);textSize=16f;setPadding(8,12,8,8) }
    private fun button(s:String, block:()->Unit) = Button(this).apply { text=s;setOnClickListener { block() } }
    private fun choices(items:List<String>) = Spinner(this).apply {
        adapter=object:ArrayAdapter<String>(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,items) {
            override fun getView(position:Int,convertView:View?,parent:ViewGroup):View = super.getView(position,convertView,parent).apply {
                (this as? TextView)?.setTextColor(if(darkPalette) Color.WHITE else 0xff102033.toInt())
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(com.scenio.mth.family.FamilyPolicy.restricted(this)) { com.scenio.mth.family.FamilyControlsActivity.openHome(this);finish();return }
        latestJob=prefs.getString("latestJob",null)
        projectFolder=session.folder
        generationProfile=if(session.snapshot!=null || session.prompt.isNotEmpty()) session.profile else runCatching { com.scenio.mth.generation.ProfileCodec.decode(JSONObject(prefs.getString("generation-profile","{}")!!)) }.getOrDefault(com.scenio.mth.generation.GenerationProfile())
        pendingExport=savedInstanceState?.getString("pendingExport")?.let { path ->
            File(path).takeIf { it.isFile && it.canonicalFile.parentFile==cacheDir.canonicalFile }
        }
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,24,24,32);setBackgroundColor(Color.rgb(15,23,42)) }
        val scroll=ScrollView(this).apply { addView(root) };setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { view,insets ->
            if(android.os.Build.VERSION.SDK_INT>=30) {
                val bars=insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            } else view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            insets
        }
        root.addView(ImageView(this).apply { setImageResource(R.drawable.ic_xxhdpi);contentDescription="StudyFrog app icon";scaleType=ImageView.ScaleType.FIT_CENTER },LinearLayout.LayoutParams((64*resources.displayMetrics.density).toInt(),(64*resources.displayMetrics.density).toInt()))
        root.addView(text("StudyForge  /  create • learn • refine").apply { textSize=25f })
        root.addView(text(nativeStatus()))
        root.addView(button("Open-source licenses") { val license=resources.openRawResource(R.raw.third_party_licenses).bufferedReader().use { it.readText() };AlertDialog.Builder(this).setTitle("Open-source licenses").setMessage(license).setPositiveButton("Close",null).show() })
        mode=choices(listOf("Free Generate","Science Lab","Art Studio","Medical Explorer","Photo Converter","Video Creator"));root.addView(mode)
        prompt=EditText(this).apply { hint="Describe your idea or photo transformation…";minLines=3;setText(session.prompt.ifBlank { prefs.getString("prompt","Show mitosis step by step") });setTextColor(Color.WHITE) };root.addView(prompt)
        negative=EditText(this).apply { hint="Negative prompt";setText(session.negative);setTextColor(Color.WHITE) };root.addView(negative)
        root.addView(button("Visual directions · style, detail and realism") { profileControls() })
        seed=EditText(this).apply { hint="Seed (blank gives a new variation)";inputType=android.text.InputType.TYPE_CLASS_NUMBER;setTextColor(Color.WHITE);setText(session.seed) };root.addView(seed)
        root.addView(text("Photo transformation strength"))
        strength=SeekBar(this).apply { max=90;progress=55;contentDescription="Photo transformation strength from 10 to 100 percent" };root.addView(strength)
        level=choices(listOf("Whole body","System","Organ","Tissue","Cellular"));root.addView(level)
        root.addView(text("Model inference steps (10–40)"));steps=SeekBar(this).apply { max=30;progress=session.steps };root.addView(steps)
        canvas=ForgeCanvas(this).apply { onEdited={ markEdited() } };root.addView(canvas,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,(260*resources.displayMetrics.density).toInt()))
        session.snapshot?.let { canvas.restore(it);dirty=false }
        mode.setSelection(session.mode);level.setSelection(session.level)
        root.addView(button("Import on-device image model (GGUF)") {
            if(localActive) say("Cancel local generation before changing the model") else startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="*/*";addCategory(Intent.CATEGORY_OPENABLE) },13)
        })
        root.addView(button("On-device model information / remove") {
            AlertDialog.Builder(this).setTitle("Offline SD 1.x model").setMessage(ModelStore(applicationContext).description()+"\nImport a complete SD 1.x GGUF. Keep its license. Models are not bundled and may require several GB of memory.")
                .setPositiveButton("Close",null).setNegativeButton("Remove model") { _,_ -> if(localActive) say("Cancel generation first") else background { ModelStore(applicationContext).remove();ui { say("Local model removed") } } }.show()
        })
        root.addView(button("Export device generation benchmark") {
            val report=File(filesDir,"generated/benchmark-latest.json")
            if(!report.isFile) say("Generate on this device once to record a real benchmark") else background { val copy=File(cacheDir,"studyforge-benchmark.json");report.copyTo(copy,overwrite=true);ui { export(copy,"application/json","studyforge-benchmark.json") } }
        })
        nativeResolution=choices(listOf("256px preview · 6 GB RAM","512px standard · 8 GB RAM"));root.addView(nativeResolution)
        root.addView(button("Generate on this device") { localGenerate() })
        root.addView(button("Cancel on-device generation") { NativeEngine.cancel();WorkManager.getInstance(this).cancelUniqueWork(LocalGenerationWorker.NAME) })
        root.addView(button("Explore science · linked levels and labeled diagrams") { if(!localActive && !busy) startActivityForResult(Intent(this,com.scenio.mth.science.StudyExplorerActivity::class.java),14) else say("Wait for generation to finish") })
        root.addView(button("Create offline schematic") { canvas.image=canvas.diagram(prompt.text.toString(),level.selectedItem.toString());say("Template generated on this device. This is not AI inference.") })
        root.addView(button("Import photo") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="image/*";addCategory(Intent.CATEGORY_OPENABLE) },10) })
        root.addView(Switch(this).apply { text="Draw / annotate on ink layer";setTextColor(Color.WHITE);setOnCheckedChangeListener { _,on -> canvas.drawing=on } })
        root.addView(button("Undo edit") { canvas.undo() })
        root.addView(button("Redo edit") { canvas.redo() })
        root.addView(text("Brush size"))
        root.addView(SeekBar(this).apply { max=19;progress=3;contentDescription="Brush size";setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar:SeekBar?,value:Int,fromUser:Boolean) { canvas.inkSize=(value+1)/1000f }
            override fun onStartTrackingTouch(bar:SeekBar?) {}
            override fun onStopTrackingTouch(bar:SeekBar?) {}
        }) })
        val tools=choices(listOf("Brush","Erase prior marks","Mask: hide","Mask: restore","Line","Rectangle","Ellipse","Perspective ray"));root.addView(tools)
        tools.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) { canvas.tool=listOf("brush","erase","mask-hide","mask-show","line","rectangle","ellipse","perspective")[position] }
            override fun onNothingSelected(parent:AdapterView<*>?) {}
        }
        root.addView(button("Import photo as editable layer") { if(canvas.image==null) say("Create or import a base image first") else startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="image/*";addCategory(Intent.CATEGORY_OPENABLE) },15) })
        root.addView(button("Guides, symmetry and stabilization") { guideControls() })
        root.addView(Switch(this).apply { text="Stylus tilt affects brush width";setOnCheckedChangeListener { _,on -> canvas.pencilTilt=on } })
        val fonts=choices(listOf("Sans serif","Serif","Monospace"));root.addView(text("Font for new text labels"));root.addView(fonts)
        fonts.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) { canvas.font=listOf("sans-serif","serif","monospace")[position] }
            override fun onNothingSelected(parent:AdapterView<*>?) {}
        }
        root.addView(button("Combine last two closed shapes") { AlertDialog.Builder(this).setTitle("Filled vector result · active layer").setItems(arrayOf("Union","Intersect","Subtract second shape","Exclusive difference")) { _,which -> runCatching { canvas.combineShapes(listOf(android.graphics.Path.Op.UNION,android.graphics.Path.Op.INTERSECT,android.graphics.Path.Op.DIFFERENCE,android.graphics.Path.Op.XOR)[which]) }.onFailure { say(it.message ?: "Cannot combine these shapes") } }.setNegativeButton("Cancel",null).show() })
        root.addView(button("Current project folder") { folderDialog(projectFolder) { projectFolder=it;markEdited();say("New saves use ${it.ifBlank { "Unfiled" }}") } })
        root.addView(button("Layers · visibility, opacity, blend and order") { layerControls() })
        root.addView(button("Edit text labels / positions") { editLabels() })
        root.addView(button("Add editable text label") { val field=EditText(this).apply { hint="Label text" };AlertDialog.Builder(this).setTitle("Text label").setView(field).setPositiveButton("Add") { _,_ -> runCatching { canvas.label(field.text.toString()) }.onFailure { say(it.message ?: "Cannot add label") } }.setNegativeButton("Cancel",null).show() })
        root.addView(button("Ink color") { AlertDialog.Builder(this).setTitle("Ink color").setItems(arrayOf("Mint","White","Gold","Blue","Red","Black")) { _,index ->
            canvas.inkColor=intArrayOf(0xff5eead4.toInt(),Color.WHITE,0xffffcc55.toInt(),0xff55aaff.toInt(),0xffff5555.toInt(),Color.BLACK)[index]
        }.show() })
        root.addView(button("Save project locally") { saveProject() })
        root.addView(button("Open local library") { library() })
        root.addView(button("Create 30-second video on this phone") { localVideo() })
        root.addView(button("Export SVG · editable shapes and text") {
            if(canvas.image==null) say("Import or generate an image first") else {
                val snapshot=canvas.snapshot();background { val file=File(cacheDir,"vector-${UUID.randomUUID()}.svg");file.bufferedWriter().use { com.scenio.mth.editor.VectorExporter.write(snapshot,it) };ui { export(file,"image/svg+xml","studyforge.svg") } }
            }
        })
        root.addView(button("Export PNG") { exportPng() })
        root.addView(button("Import project backup") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/zip";addCategory(Intent.CATEGORY_OPENABLE) },12) })
        root.addView(text("Optional local computer engine • USB connection • no cloud"))
        token=EditText(this).apply { hint="Engine token (kept in memory only)";inputType=129;setTextColor(Color.WHITE) };root.addView(token)
        root.addView(text("Computer image guidance · imported local model required"))
        controlMode=choices(listOf("No extra guidance","ControlNet Canny edges from photo","ControlNet prepared depth map","ControlNet prepared OpenPose map","IP-Adapter reference photo"));root.addView(controlMode)
        root.addView(text("Image guidance strength · 0 to 100 percent"));controlStrength=SeekBar(this).apply { max=100;progress=70;contentDescription="Computer image guidance strength" };root.addView(controlStrength)
        root.addView(button("Generate with local model") { submit("image") })
        root.addView(button("Generate 30-second AI motion video (local computer)") { submit("motion") })
        root.addView(button("30-second 1080p reveal video") { submit("video") })
        root.addView(button("Refine to 4K / 8K on local computer") { AlertDialog.Builder(this).setTitle("Tiled model refinement · may take a long time").setItems(arrayOf("4K · 3840×2160","8K · 7680×4320")) { _,choice -> if(choice==0) submit("refine",3840,2160) else submit("refine",7680,4320) }.show() })
        root.addView(button("Export last unmodified generation at full resolution") {
            val name=prefs.getString("original-result",null);val source=name?.let { File(filesDir,"generated/$it") }
            if(source?.isFile!=true) say("Download or generate a result first") else background { val copy=File(cacheDir,"original-${UUID.randomUUID()}.png");source.copyTo(copy,overwrite=true);ui { export(copy,"image/png","studyforge-original.png") } }
        })
        root.addView(button("Check current engine job / download result") { poll() })
        root.addView(button("Manage engine jobs") { engineHistory() })
        root.addView(button("Check engine connection") { val auth=token.text.toString();background {
            val health=JSONObject(String(api("/health",auth)))
            ui { say(if(health.optBoolean("model_configured")) "Connected • local model folder configured" else "Connected • no local AI model configured") }
        } })
        root.addView(text("On-device image generation uses an imported SD 1.x GGUF. Still-reveal video runs on this phone; AI motion video uses the optional local computer engine."))
        status=text("Ready • all projects stay local").apply { accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE };root.addView(status)
        prompt.addTextChangedListener { markEdited() };negative.addTextChangedListener { markEdited() };seed.addTextChangedListener { markEdited() }
        lifecycleScope.launch { FeatureRuntime.graph(this@MainActivity).settings.data.collect { settings ->
            darkPalette=when(settings.theme) { ThemeMode.DARK -> true;ThemeMode.LIGHT -> false;ThemeMode.SYSTEM -> resources.configuration.uiMode and 0x30 == 0x20 }
            val bg=if(darkPalette) 0xff0f172a.toInt() else 0xfff7f8fc.toInt()
            val fg=if(darkPalette) Color.WHITE else 0xff102033.toInt()
            val accent=if(settings.dynamicColor && android.os.Build.VERSION.SDK_INT>=31) getColor(android.R.color.system_accent1_200) else 0xff5eead4.toInt()
            root.setBackgroundColor(bg)
            fun recolor(view:View) {
                if(view is Switch) { view.setTextColor(fg);view.thumbTintList=ColorStateList.valueOf(accent) }
                else if(view is Button) { view.setTextColor(0xff102033.toInt());view.backgroundTintList=ColorStateList.valueOf(accent) }
                else if(view is TextView) { view.setTextColor(fg);if(view is EditText) { view.setHintTextColor(if(darkPalette) Color.LTGRAY else Color.DKGRAY);view.backgroundTintList=ColorStateList.valueOf(accent) } }
                if(view is AndroidViewGroup) for(i in 0 until view.childCount) recolor(view.getChildAt(i))
            }
            recolor(root)
        } }
        lifecycleScope.launch {
            WorkManager.getInstance(this@MainActivity).getWorkInfosForUniqueWorkFlow(LocalGenerationWorker.NAME).collect { jobs ->
                val job=jobs.firstOrNull() ?: return@collect
                localActive=!job.state.isFinished
                if(localActive && localDialog==null) localDialog=AlertDialog.Builder(this@MainActivity).setTitle("Generating on this device").setMessage("Loading your model…").setCancelable(false).setNegativeButton("Cancel") { _,_ -> NativeEngine.cancel();WorkManager.getInstance(this@MainActivity).cancelUniqueWork(LocalGenerationWorker.NAME) }.show()
                if(localActive) localDialog?.setMessage("${job.progress.getInt("percent",0)}% · You can leave the app; Android manages this local job.")
                else { localDialog?.dismiss();localDialog=null }
                if(localActive) say("On-device generation: ${job.progress.getInt("percent",0)}% · loading the model can take time")
                else if(job.state==WorkInfo.State.SUCCEEDED) {
                    val path=job.outputData.getString("path")
                    if(path!=null && path!=loadedLocalResult) {
                        while(busy || pendingExport!=null) kotlinx.coroutines.delay(100)
                        background { val image=PhotoDecoder.read(contentResolver,android.net.Uri.fromFile(File(path)));ui { loadedLocalResult=path;prefs.edit().putString("original-result",File(path).name).apply();canvas.image=image;say("Generated on this device · result saved privately") } }
                    }
                } else if(job.state==WorkInfo.State.FAILED) say(job.outputData.getString("error") ?: "Local generation failed")
            }
        }
        if(session.snapshot==null) background {
            val draft=projects.loadDraft()
            if(draft!=null) ui { if(canvas.image==null) { applyProject(draft);say("Recovered editable draft from this device") } }
        }
        if (!prefs.getBoolean("age-ack-18-v1",false)) AlertDialog.Builder(this).setTitle("Educational creative tools").setMessage("Generated visuals may contain scientific errors. The creative workspace is for adults. Family controls can lock access to bundled educational diagrams with a parent PIN; this is not a certified child-safe content filter. Medical anatomy remains available.").setCancelable(false).setNegativeButton("Study-only setup") { _,_ -> startActivity(Intent(this,com.scenio.mth.family.FamilyControlsActivity::class.java));finish() }.setPositiveButton("I am 18 or older") { _,_ -> prefs.edit().putBoolean("age-ack-18-v1",true).apply() }.show()
    }
    private fun say(s:String) { if(!isFinishing && !isDestroyed) status.text=s }
    private fun progress(id: String, step: Int, total: Int) {
        lifecycleScope.launch {
            try { FeatureRuntime.graph(this@MainActivity).progress.recordStep(id,step,total) }
            catch(e:CancellationException) { throw e }
            catch(_:Exception) { say("The result is available, but its progress could not be saved") }
        }
    }
    private fun metadata() = JSONObject().put("prompt",prompt.text.toString()).put("negative",negative.text.toString())
        .put("profile",com.scenio.mth.generation.ProfileCodec.encode(generationProfile)).put("folder",projectFolder).put("seed",seed.text.toString()).put("strength",strength.progress+10).put("mode",mode.selectedItem.toString()).put("level",level.selectedItem.toString()).put("steps",steps.progress+10)
    private fun ui(block:()->Unit) { runOnUiThread { if(!isFinishing && !isDestroyed && !com.scenio.mth.family.FamilyPolicy.restricted(this)) block() } }
    private fun markEdited() { dirty=true;editRevision++ }
    private fun saveProject() {
        if(canvas.image==null) { say("Import or generate an image first");return }
        val snapshot=canvas.snapshot();val meta=metadata();val savedRevision=editRevision
        background { val id=projects.save(snapshot,meta);ui { progress("project:$id",1,1);if(editRevision==savedRevision) dirty=false;say("Saved editable image, ink layer and prompt") } }
    }
    private fun applyProject(loaded:com.scenio.mth.editor.LoadedProject) {
        canvas.restore(loaded.snapshot);val meta=loaded.info.meta;projectFolder=loaded.info.folder;generationProfile=runCatching { com.scenio.mth.generation.ProfileCodec.decode(meta.optJSONObject("profile")) }.getOrDefault(com.scenio.mth.generation.GenerationProfile())
        prompt.setText(meta.optString("prompt"));negative.setText(meta.optString("negative"))
        (0 until mode.adapter.count).firstOrNull { mode.adapter.getItem(it).toString()==meta.optString("mode") }?.let { mode.setSelection(it) }
        (0 until level.adapter.count).firstOrNull { level.adapter.getItem(it).toString()==meta.optString("level") }?.let { level.setSelection(it) }
        steps.progress=(meta.optInt("steps",20)-10).coerceIn(0,30);seed.setText(meta.optString("seed"));strength.progress=(meta.optInt("strength",65)-10).coerceIn(0,90);dirty=false
    }
    private fun layerControls() {
        val layers=canvas.layerList()
        AlertDialog.Builder(this).setTitle("Layers · top entry is the front layer").setItems((layers.reversed().map { "${if(it.id==canvas.activeLayer) "✓ " else ""}${it.name} · ${if(it.visible) "visible" else "hidden"}" }+"Add layer").toTypedArray()) { _,index ->
            if(index==layers.size) { runCatching { canvas.addLayer() }.onFailure { say(it.message ?: "Cannot add layer") };return@setItems }
            val layer=layers.reversed()[index];canvas.selectLayer(layer.id)
            AlertDialog.Builder(this).setTitle(layer.name).setItems(arrayOf("Select for editing","Rename","Show / hide","Opacity","Blend mode","Move forward","Move backward","Delete layer","Move, scale and rotate","Enable / disable mask","Clear mask")) { _,action ->
                when(action) {
                    1 -> { val name=EditText(this).apply { setText(layer.name) };AlertDialog.Builder(this).setTitle("Layer name").setView(name).setPositiveButton("Save") { _,_ -> runCatching { canvas.updateLayer(layer.copy(name=name.text.toString())) }.onFailure { say("Use a name of 1–80 characters") } }.setNegativeButton("Cancel",null).show() }
                    2 -> canvas.updateLayer(layer.copy(visible=!layer.visible))
                    3 -> { val opacity=SeekBar(this).apply { max=100;progress=(layer.opacity*100).toInt();contentDescription="Layer opacity" };AlertDialog.Builder(this).setTitle("Layer opacity").setView(opacity).setPositiveButton("Apply") { _,_ -> canvas.updateLayer(layer.copy(opacity=opacity.progress/100f)) }.show() }
                    4 -> AlertDialog.Builder(this).setTitle("Blend mode").setItems(arrayOf("Normal","Multiply","Screen")) { _,choice -> canvas.updateLayer(layer.copy(blend=listOf("normal","multiply","screen")[choice])) }.show()
                    5 -> canvas.moveLayer(layer.id,1)
                    6 -> canvas.moveLayer(layer.id,-1)
                    8 -> transformLayer(layer)
                    9 -> canvas.updateLayer(layer.copy(maskEnabled=!layer.maskEnabled))
                    10 -> canvas.clearMask()
                    7 -> AlertDialog.Builder(this).setTitle("Delete this ink layer?").setPositiveButton("Delete") { _,_ -> runCatching { canvas.deleteLayer(layer.id) }.onFailure { say("Keep at least one editable layer") } }.setNegativeButton("Cancel",null).show()
                }
            }.show()
        }.setNegativeButton("Close",null).show()
    }
    private fun profileControls() {
        val value=generationProfile;val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,8,24,8) }
        val enabled=Switch(this).apply { text="Append visual directions to my prompt";isChecked=value.enabled };form.addView(enabled)
        val styles=com.scenio.mth.generation.GenerationProfile.STYLES
        val style=choices(styles.values.toList()).apply { setSelection(styles.keys.indexOf(value.style)) };form.addView(style)
        form.addView(text("Detail · simple to fine"));val detail=SeekBar(this).apply { max=100;progress=value.detail;contentDescription="Detail direction" };form.addView(detail)
        form.addView(text("Realism · illustrative to photographic"));val realism=SeekBar(this).apply { max=100;progress=value.realism;contentDescription="Realism direction" };form.addView(realism)
        val science=Switch(this).apply { text="Scientific intent · requires human review";isChecked=value.scientific };form.addView(science)
        form.addView(text("These settings add visible instructions to the prompt. They do not verify scientific accuracy or guarantee model adherence."))
        AlertDialog.Builder(this).setTitle("Visual directions").setView(form).setPositiveButton("Apply") { _,_ ->
            generationProfile=com.scenio.mth.generation.GenerationProfile(enabled.isChecked,styles.keys.elementAt(style.selectedItemPosition),detail.progress,realism.progress,science.isChecked)
            prefs.edit().putString("generation-profile",com.scenio.mth.generation.ProfileCodec.encode(generationProfile).toString()).apply();markEdited()
            AlertDialog.Builder(this).setTitle("Prompt sent to image model").setMessage(runCatching { generationProfile.apply(prompt.text.toString()) }.getOrElse { it.message }).setPositiveButton("Close",null).show()
        }.setNegativeButton("Cancel",null).show()
    }
    private fun folderDialog(initial:String,apply:(String)->Unit) {
        val field=EditText(this).apply { hint="Folder name · blank means Unfiled";setText(initial);filters=arrayOf(android.text.InputFilter.LengthFilter(80));setSingleLine() }
        AlertDialog.Builder(this).setTitle("Project folder").setView(field).setPositiveButton("Apply") { _,_ ->
            val value=field.text.toString().trim();if(value.any { it.isISOControl() }) say("Use a folder name without control characters") else apply(value)
        }.setNegativeButton("Cancel",null).show()
    }
    private fun transformLayer(layer:ForgeCanvas.InkLayer) {
        val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,8,24,8) }
        fun slider(label:String,limit:Int,value:Int)=SeekBar(this).apply { max=limit;progress=value;contentDescription=label;form.addView(text(label));form.addView(this) }
        val x=slider("Horizontal shift: −100 to 100 percent",200,((layer.offsetX+1)*100).toInt())
        val y=slider("Vertical shift: −100 to 100 percent",200,((layer.offsetY+1)*100).toInt())
        val scale=slider("Scale: 10 to 400 percent",390,(layer.scale*100-10).toInt())
        val rotation=slider("Rotation: −180 to 180 degrees",360,(layer.rotation+180).toInt())
        AlertDialog.Builder(this).setTitle("Layer transform").setView(form).setPositiveButton("Apply") { _,_ -> canvas.updateLayer(layer.copy(offsetX=(x.progress-100)/100f,offsetY=(y.progress-100)/100f,scale=(scale.progress+10)/100f,rotation=(rotation.progress-180).toFloat())) }
            .setNeutralButton("Reset") { _,_ -> canvas.updateLayer(layer.copy(offsetX=0f,offsetY=0f,scale=1f,rotation=0f)) }.setNegativeButton("Cancel",null).show()
    }
    private fun guideControls() {
        val current=canvas.guides;val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,8,24,8) }
        form.addView(text("Grid subdivisions"));val grid=choices(listOf("Off","5","10","20")).apply { setSelection(listOf(0,5,10,20).indexOf(current.grid)) };form.addView(grid)
        form.addView(text("Mirror new marks"));val symmetry=choices(listOf("None","Vertical","Horizontal","Both")).apply { setSelection(listOf("none","vertical","horizontal","both").indexOf(current.symmetry)) };form.addView(symmetry)
        val rulers=Switch(this).apply { text="Percentage ruler";isChecked=current.rulers };form.addView(rulers)
        val perspective=Switch(this).apply { text="One-point perspective guide";isChecked=current.perspective };form.addView(perspective)
        form.addView(text("Stroke stabilization · 0 to 95 percent"));val smoothing=SeekBar(this).apply { max=95;progress=(current.stabilization*100).toInt();contentDescription="Stroke stabilization" };form.addView(smoothing)
        AlertDialog.Builder(this).setTitle("Drawing guides").setView(form).setPositiveButton("Apply") { _,_ -> canvas.configureGuides(ForgeCanvas.Guides(listOf(0,5,10,20)[grid.selectedItemPosition],rulers.isChecked,listOf("none","vertical","horizontal","both")[symmetry.selectedItemPosition],perspective.isChecked,smoothing.progress/100f)) }.setNegativeButton("Cancel",null).show()
    }
    private fun editLabels() {
        val labels=canvas.textLabels()
        if(labels.isEmpty()) { say("Add a label or open a labeled study diagram first");return }
        AlertDialog.Builder(this).setTitle("Edit labels").setItems(labels.map { it.second.text }.toTypedArray()) { _,which ->
            val (index,stroke)=labels[which];val point=stroke.points.first()
            val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,8,24,8) }
            val value=EditText(this).apply { setText(stroke.text);hint="Label text";filters=arrayOf(android.text.InputFilter.LengthFilter(200)) };form.addView(value)
            form.addView(text("Horizontal position"));val x=SeekBar(this).apply { max=100;progress=(point.x*100).toInt();contentDescription="Label horizontal position, percent of image width" };form.addView(x)
            form.addView(text("Vertical position"));val y=SeekBar(this).apply { max=100;progress=(point.y*100).toInt();contentDescription="Label vertical position, percent of image height" };form.addView(y)
            AlertDialog.Builder(this).setTitle("Label and position").setView(form).setPositiveButton("Apply") { _,_ -> runCatching { canvas.updateTextLabel(index,value.text.toString(),x.progress/100f,y.progress/100f) }.onFailure { say("Enter a nonempty label of up to 200 characters") } }.setNegativeButton("Cancel",null).show()
        }.setNegativeButton("Close",null).show()
    }
    private fun library() { background {
        val items=projects.list();ui {
            if(items.isEmpty()) { say("No saved projects yet");return@ui }
            val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
            val search=EditText(this).apply { hint="Search project titles or prompts";setSingleLine() };form.addView(search)
            val folderNames=items.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted()
            val folder=choices(listOf("All folders","Unfiled")+folderNames);form.addView(folder)
            AlertDialog.Builder(this).setTitle("Local projects").setView(form).setPositiveButton("Search") { _,_ ->
                val query=search.text.toString();val found=items.filter { item ->
                    (folder.selectedItemPosition==0 || item.folder==if(folder.selectedItemPosition==1) "" else folderNames[folder.selectedItemPosition-2]) && (item.title.contains(query,true) || item.meta.optString("prompt").contains(query,true))
                }
                if(found.isEmpty()) say("No matching projects") else AlertDialog.Builder(this).setTitle("Projects").setItems(found.map { it.title }.toTypedArray()) { _,index ->
                    val item=found[index]
                    AlertDialog.Builder(this).setTitle(item.title).setItems(arrayOf("Open","Rename","Export editable backup","Delete","Move to folder")) { _,action ->
                        when(action) {
                            0 -> background { val loaded=projects.load(item.id);ui { applyProject(loaded);say("Editable project loaded") } }
                            1 -> { val title=EditText(this).apply { setText(item.title) };AlertDialog.Builder(this).setTitle("Rename project").setView(title).setPositiveButton("Save") { _,_ ->
                                val name=title.text.toString();background { projects.rename(item.id,name);ui { say("Project renamed") } }
                            }.setNegativeButton("Cancel",null).show() }
                            2 -> background { val file=File(cacheDir,"project-${item.id}.zip");projects.archive(item.id,file);ui { export(file,"application/zip","studyforge-project.zip") } }
                            4 -> folderDialog(item.folder) { value -> background { projects.setFolder(item.id,value);ui { say("Project moved to ${value.ifBlank { "Unfiled" }}") } } }
                            3 -> AlertDialog.Builder(this).setTitle("Delete this project?").setMessage("The saved project will be removed from this device.").setPositiveButton("Delete") { _,_ ->
                                background { projects.delete(item.id);ui { say("Project deleted") } }
                            }.setNegativeButton("Cancel",null).show()
                        }
                    }.show()
                }.show()
            }.setNegativeButton("Cancel",null).show()
        }
    } }
    private fun exportPng() {
        if(canvas.image==null) { say("Import or generate an image first");return }
        val snapshot=canvas.snapshot()
        background {
            val bitmap=ForgeCanvas.render(snapshot);val file=File(cacheDir,"image-${UUID.randomUUID()}.png")
            try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) } } finally { bitmap.recycle() }
            ui { export(file,"image/png","studyforge.png") }
        }
    }
    private fun export(file:File,mime:String,name:String) {
        pendingExport=file
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type=mime;addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,name) },11)
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(com.scenio.mth.family.FamilyPolicy.restricted(this)) return
        if(resultCode!=RESULT_OK) { if(requestCode==11) { pendingExport?.delete();pendingExport=null };return }
        if(requestCode==14) {
            val id=data?.getStringExtra("study-id") ?: return
            val catalog=com.scenio.mth.science.StudyCatalog(this)
            val apply={
                val extra=data.getStringExtra("study-notes").orEmpty().take(500)
                prompt.setText(catalog.generationPrompt(id)+(if(extra.isBlank()) "" else " Additional instructions: $extra"))
                if(data.getStringExtra("study-action")=="diagram") { canvas.restore(catalog.snapshot(id,data.getBooleanExtra("study-labels",true)));say("Offline study diagram opened; its labels are editable on the Study labels layer") }
                else say("Study path added to the prompt. Choose on-device or computer generation.")
            }
            if(canvas.image!=null) AlertDialog.Builder(this).setTitle("Replace the current study view?").setMessage("Save the current project first if you want to keep its unsaved edits and prompt.").setPositiveButton("Continue") { _,_ -> apply() }.setNegativeButton("Cancel",null).show() else apply()
            return
        }
        val uri=data?.data ?: return
        when(requestCode) {
            15 -> background { val bitmap=PhotoDecoder.read(contentResolver,uri,1024);ui { runCatching { canvas.addRaster(bitmap) }.onSuccess { say("Photo added as an editable layer") }.onFailure { bitmap.recycle();say(it.message ?: "Cannot add photo layer") } } }
            10 -> background { val bitmap=PhotoDecoder.read(contentResolver,uri);ui { canvas.image=bitmap;say("Photo imported with its original orientation and aspect ratio") } }
            11 -> { val file=pendingExport;pendingExport=null
                if(file==null) { say("Export data is no longer available; export again");return }
                background { try {
                    contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: throw IOException("Cannot open export destination")
                    ui { say("Export saved") }
                } finally { file.delete() } }
            }
            13 -> background { val description=ModelStore(applicationContext).import(uri);ui { say("Model imported: $description") } }
            12 -> background { val id=contentResolver.openInputStream(uri)?.use { projects.importArchive(it) } ?: throw IOException("Cannot open backup")
                val loaded=projects.load(id);ui { applyProject(loaded);say("Editable backup imported") }
            }
        }
    }
    private fun localGenerate() {
        if(com.scenio.mth.family.FamilyPolicy.restricted(this)) return
        if(localActive || busy) { say("Wait for the current request or cancel generation");return }
        if(!NativeEngine.available()) { say("This build was compiled without local inference");return }
        val model=ModelStore(applicationContext).model
        if(!model.isFile) { say("Import an SD 1.x GGUF model first");return }
        val memory=android.app.ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(memory)
        val dimension=if(nativeResolution.selectedItemPosition==0) 256 else 512
        if(memory.totalMem<(if(dimension==512) 7_500_000_000L else 5_500_000_000L) || memory.availMem<(if(dimension==512) 4_000_000_000L else 2_000_000_000L) || memory.lowMemory) { say("Local inference needs a device with 6 GB RAM / 2 GB available for 256px, or 8 GB RAM / 4 GB available for 512px. Close other apps or use your local computer engine.");return }
        val text=runCatching { generationProfile.apply(prompt.text.toString()) }.getOrElse { say(it.message ?: "Invalid visual directions");return };val excluded=negative.text.toString()
        if(text.isBlank() || text.length>2000 || excluded.length>2000) { say("Enter a prompt of 1–2000 characters");return }
        val number=if(seed.text.isBlank()) java.security.SecureRandom().nextInt(Int.MAX_VALUE).toLong() else seed.text.toString().toLongOrNull()
        if(number==null || number !in 0L..2147483647L) { say("Invalid seed");return }
        val id=UUID.randomUUID();val input=JSONObject().put("prompt",text).put("negative",excluded).put("size",dimension).put("steps",steps.progress+10).put("seed",number).put("strength",(strength.progress+10)/100.0)
        val snapshot=if(mode.selectedItem.toString()=="Photo Converter") canvas.snapshot() else null
        if(snapshot!=null && snapshot.base==null) { say("Import a photo first");return }
        background {
            if(snapshot!=null) {
                val bitmap=ForgeCanvas.render(snapshot);val file=File(cacheDir,"$id-input.png")
                try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) } } finally { bitmap.recycle() }
                input.put("initial",file.name)
            }
            val requestFile=File(noBackupFilesDir,"local-jobs/$id.json").apply { parentFile?.mkdirs();writeText(input.toString()) }
            val request=OneTimeWorkRequestBuilder<LocalGenerationWorker>().setId(id).build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(LocalGenerationWorker.NAME,ExistingWorkPolicy.KEEP,request)
            ui { localActive=true;say("Queued local generation · seed $number · $dimension px on-device image") }
        }
    }
    private fun localVideo() {
        if(busy || pendingExport!=null) { say("Please finish the current operation");return }
        if(canvas.image==null) { say("Import or generate an image first");return }
        val snapshot=canvas.snapshot();say("Rendering 30-second reveal on this device. Keep StudyForge open…")
        background {
            val bitmap=ForgeCanvas.render(snapshot)
            val file=File(cacheDir,"reveal-${UUID.randomUUID()}.mp4")
            try { RevealVideo.render(bitmap,file) { percent -> ui { say("Rendering video: $percent% • keep the app open") } };ui { export(file,"video/mp4","studyforge-30s.mp4") } }
            catch(e:Exception) { file.delete();throw e }
            finally { bitmap.recycle() }
        }
    }
    private fun api(path:String,auth:String,body:JSONObject?=null,method:String=if(body==null) "GET" else "POST"):ByteArray {
        checkCreativeAccess()
        val c=URL("http://127.0.0.1:8765$path").openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects=false;c.connectTimeout=10000;c.readTimeout=30000;c.requestMethod=method;c.setRequestProperty("Authorization","Bearer $auth")
            if(body!=null) { c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use { it.write(body.toString().toByteArray()) } }
            if(c.responseCode !in 200..299) throw IllegalStateException(c.errorStream?.bufferedReader()?.readText()?.take(300) ?: "Engine error")
            return c.inputStream.use { input -> val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true) { val n=input.read(buffer);if(n<0) break;require(out.size()+n<=1_000_000) { "Unexpected engine response size" };out.write(buffer,0,n) };out.toByteArray() }
        } finally { c.disconnect() }
    }
    private fun background(block:()->Unit) {
        if(busy || pendingExport!=null) { say("Please finish the current operation");return };busy=true
        worker.execute { try { block() } catch(e:Exception) { ui { say(e.message ?: "Operation failed") } } finally { ui { busy=false } } }
    }
    private fun engineHistory() {
        val auth=token.text.toString();if(auth.isBlank()) { say("Enter the local engine token first");return }
        background {
            val jobs=JSONObject(String(api("/jobs",auth))).getJSONArray("jobs")
            val items=(0 until jobs.length()).map { jobs.getJSONObject(it) }
            ui {
                if(items.isEmpty()) { say("No engine jobs on this computer");return@ui }
                AlertDialog.Builder(this).setTitle("Local engine jobs").setItems(items.map { "${it.getString("kind")} · ${it.getString("status")} · ${it.getString("id").take(8)}" }.toTypedArray()) { _,index ->
                    val job=items[index];val id=job.getString("id");val active=job.getString("status") in listOf("queued","running","cancelling")
                    AlertDialog.Builder(this).setTitle(if(active) "Cancel this engine job?" else "Delete this engine job?")
                        .setMessage(if(active) "Generation stops at the next safe processing step." else "This removes its result and prompt history from your local computer. Saved phone projects remain available.")
                        .setPositiveButton(if(active) "Cancel job" else "Delete") { _,_ -> background {
                            val result=if(active) api("/jobs/$id/cancel",auth,JSONObject()) else api("/jobs/$id",auth,method="DELETE")
                            ui { say("Engine job: ${JSONObject(String(result)).getString("status")}") }
                        } }.setNegativeButton("Back",null).show()
                }.show()
            }
        }
    }
    private fun submit(kind:String,width:Int=768,height:Int=432) {
        if(com.scenio.mth.family.FamilyPolicy.restricted(this)) return
        val auth=token.text.toString();if(auth.isBlank()) { say("Start the local engine and enter its token first");return }
        val value=if(seed.text.isBlank()) java.security.SecureRandom().nextInt(Int.MAX_VALUE) else seed.text.toString().toIntOrNull()
        if(value==null || value<0) { say("Seed must be between 0 and 2147483647");return }
        if(prompt.text.isBlank() || prompt.length()>2000 || negative.length()>2000) { say("Write a prompt of 1–2000 characters; negative prompt maximum 2000");return }
        val effective=runCatching { generationProfile.apply(prompt.text.toString()) }.getOrElse { say(it.message ?: "Invalid visual directions");return }
        val control=if(kind=="image") listOf("none","canny","depth","pose","reference")[controlMode.selectedItemPosition] else "none"
        val body=JSONObject().put("control",control).put("control_scale",controlStrength.progress/100.0).put("kind",kind).put("prompt",effective).put("negative",negative.text.toString())
            .put("steps",steps.progress+10).put("seed",value).put("strength",(strength.progress+10)/100.0).put("width",width).put("height",height)
        val photo=control!="none" || kind in listOf("video","motion","refine") || mode.selectedItem.toString()=="Photo Converter"
        val snapshot=if(photo) canvas.snapshot() else null
        if(photo && snapshot?.base==null) { say("Import or generate an image first");return }
        say("Submitting to your local engine…")
        background {
            if(snapshot!=null) {
                val bitmap=ForgeCanvas.render(snapshot)
                val ratio=minOf(1f,1024f/maxOf(bitmap.width,bitmap.height))
                val upload=if(ratio<1f) Bitmap.createScaledBitmap(bitmap,(bitmap.width*ratio).toInt(),(bitmap.height*ratio).toInt(),true) else bitmap
                try {
                    val bytes=java.io.ByteArrayOutputStream().use { out -> check(upload.compress(Bitmap.CompressFormat.PNG,100,out));out.toByteArray() }
                    body.put("image",Base64.encodeToString(bytes,Base64.NO_WRAP))
                } finally { if(upload!==bitmap) upload.recycle();bitmap.recycle() }
            }
            val job=JSONObject(String(api("/jobs",auth,body)))
            ui { latestJob=job.getString("id");progress("generation:${job.getString("id")}",1,2);prefs.edit().putString("latestJob",latestJob).apply();say("Job queued • seed $value. Check the job to see progress.") }
        }
    }
    private fun poll() {
        val id=latestJob ?: run { say("Submit a job first");return };val auth=token.text.toString()
        if(!id.matches(Regex("[0-9a-f]{32}"))) { say("Invalid engine job identifier");return }
        background {
            val job=JSONObject(String(api("/jobs/$id",auth)))
            if(job.getString("status")=="done") {
                val video=job.getString("kind") in listOf("video","motion")
                val file=if(video) File(cacheDir,"result-$id.mp4") else File(filesDir,"generated/result-$id.png").apply { parentFile?.mkdirs() }
                download("/results/$id",auth,file)
                if(video) ui { progress("generation:$id",2,2);export(file,"video/mp4","studyforge-30s.mp4") }
                else {
                    val bitmap=PhotoDecoder.read(contentResolver,android.net.Uri.fromFile(file))
                    prefs.edit().putString("original-result",file.name).apply()
                    ui { progress("generation:$id",2,2);canvas.image=bitmap;say("Generation complete • review before academic use") }
                }
            } else ui { say(job.optString("error",job.getString("status"))) }
        }
    }
    private fun download(path:String,auth:String,file:File) {
        checkCreativeAccess()
        val c=URL("http://127.0.0.1:8765$path").openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects=false;c.connectTimeout=10000;c.readTimeout=30000;c.setRequestProperty("Authorization","Bearer $auth")
            check(c.responseCode in 200..299) { "Result download failed" }
            var total=0L
            file.outputStream().use { out -> c.inputStream.use { input ->
                val buffer=ByteArray(8192)
                while(true) { val n=input.read(buffer);if(n<0) break;total+=n;require(total<=256_000_000) { "Result exceeds 256 MB" };out.write(buffer,0,n) }
            } }
        } catch(e:Exception) { file.delete();throw e } finally { c.disconnect() }
    }
    private fun checkCreativeAccess() { check(!com.scenio.mth.family.FamilyPolicy.restricted(this)) { "Creative workspace is locked by family controls" } }
    override fun onResume() {
        super.onResume()
        if(com.scenio.mth.family.FamilyPolicy.restricted(this)) { com.scenio.mth.family.FamilyControlsActivity.openHome(this);finish() }
    }
    private fun rememberEditor() {
        if(!::canvas.isInitialized) return
        session.profile=generationProfile;session.folder=projectFolder;session.snapshot=canvas.snapshot();session.prompt=prompt.text.toString();session.negative=negative.text.toString()
        session.mode=mode.selectedItemPosition;session.level=level.selectedItemPosition;session.steps=steps.progress;session.seed=seed.text.toString()
        prefs.edit().putString("prompt",session.prompt).apply()
    }
    override fun onSaveInstanceState(outState:Bundle) {
        rememberEditor();outState.putString("pendingExport",pendingExport?.path);super.onSaveInstanceState(outState)
    }
    override fun onStop() {
        rememberEditor()
        if(::canvas.isInitialized && dirty && canvas.image!=null) { projects.autosave(canvas.snapshot(),metadata());dirty=false }
        super.onStop()
    }
    override fun onDestroy() { localDialog?.dismiss();worker.shutdownNow();super.onDestroy() }
}
