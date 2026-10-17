package com.scenio.mth

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
    private var darkPalette = true
    private lateinit var canvas: ForgeCanvas
    private lateinit var prompt: EditText
    private lateinit var negative: EditText
    private lateinit var status: TextView
    private lateinit var token: EditText
    private lateinit var mode: Spinner
    private lateinit var level: Spinner
    private lateinit var seed: EditText
    private lateinit var strength: SeekBar
    private lateinit var steps: SeekBar
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("forge",MODE_PRIVATE) }
    private var latestJob: String? = null
    private var busy = false
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
        latestJob=prefs.getString("latestJob",null)
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
        mode=choices(listOf("Free Generate","Science Lab","Art Studio","Medical Explorer","Photo Converter","Video Creator"));root.addView(mode)
        prompt=EditText(this).apply { hint="Describe your idea or photo transformation…";minLines=3;setText(session.prompt.ifBlank { prefs.getString("prompt","Show mitosis step by step") });setTextColor(Color.WHITE) };root.addView(prompt)
        negative=EditText(this).apply { hint="Negative prompt";setText(session.negative);setTextColor(Color.WHITE) };root.addView(negative)
        seed=EditText(this).apply { hint="Seed (blank gives a new variation)";inputType=android.text.InputType.TYPE_CLASS_NUMBER;setTextColor(Color.WHITE);setText(session.seed) };root.addView(seed)
        root.addView(text("Photo transformation strength"))
        strength=SeekBar(this).apply { max=90;progress=55;contentDescription="Photo transformation strength from 10 to 100 percent" };root.addView(strength)
        level=choices(listOf("Whole body","System","Organ","Tissue","Cellular"));root.addView(level)
        root.addView(text("Model inference steps (10–40)"));steps=SeekBar(this).apply { max=30;progress=session.steps };root.addView(steps)
        canvas=ForgeCanvas(this).apply { onEdited={ dirty=true } };root.addView(canvas,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,(260*resources.displayMetrics.density).toInt()))
        session.snapshot?.let { canvas.restore(it);dirty=false }
        mode.setSelection(session.mode);level.setSelection(session.level)
        root.addView(button("Create offline schematic") { canvas.image=canvas.diagram(prompt.text.toString(),level.selectedItem.toString());say("Template generated on this device. This is not AI inference.") })
        root.addView(button("Import photo") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="image/*";addCategory(Intent.CATEGORY_OPENABLE) },10) })
        root.addView(Switch(this).apply { text="Draw / annotate on ink layer";setTextColor(Color.WHITE);setOnCheckedChangeListener { _,on -> canvas.drawing=on } })
        root.addView(button("Undo ink") { canvas.undo() })
        root.addView(button("Redo ink") { canvas.redo() })
        root.addView(text("Brush size"))
        root.addView(SeekBar(this).apply { max=19;progress=3;contentDescription="Brush size";setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar:SeekBar?,value:Int,fromUser:Boolean) { canvas.inkSize=(value+1)/1000f }
            override fun onStartTrackingTouch(bar:SeekBar?) {}
            override fun onStopTrackingTouch(bar:SeekBar?) {}
        }) })
        root.addView(button("Ink color") { AlertDialog.Builder(this).setTitle("Ink color").setItems(arrayOf("Mint","White","Gold","Blue","Red","Black")) { _,index ->
            canvas.inkColor=intArrayOf(0xff5eead4.toInt(),Color.WHITE,0xffffcc55.toInt(),0xff55aaff.toInt(),0xffff5555.toInt(),Color.BLACK)[index]
        }.show() })
        root.addView(button("Save project locally") { saveProject() })
        root.addView(button("Open local library") { library() })
        root.addView(button("Create 30-second video on this phone") { localVideo() })
        root.addView(button("Export PNG") { exportPng() })
        root.addView(button("Import project backup") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/zip";addCategory(Intent.CATEGORY_OPENABLE) },12) })
        root.addView(text("Optional local computer engine • USB connection • no cloud"))
        token=EditText(this).apply { hint="Engine token (kept in memory only)";inputType=129;setTextColor(Color.WHITE) };root.addView(token)
        root.addView(button("Generate with local model") { submit("image") })
        root.addView(button("30-second 1080p reveal video") { submit("video") })
        root.addView(button("Check current engine job / download result") { poll() })
        root.addView(button("Manage engine jobs") { engineHistory() })
        root.addView(button("Check engine connection") { val auth=token.text.toString();background {
            val health=JSONObject(String(api("/health",auth)))
            ui { say(if(health.optBoolean("model_configured")) "Connected • local model folder configured" else "Connected • no local AI model configured") }
        } })
        root.addView(text("Video exports a progressive reveal; subject-motion generation is not implemented. Downloaded models run on your own computer. No phone-local diffusion runtime is bundled."))
        status=text("Ready • all projects stay local").apply { accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE };root.addView(status)
        prompt.addTextChangedListener { dirty=true };negative.addTextChangedListener { dirty=true }
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
        if(session.snapshot==null) background {
            val draft=projects.loadDraft()
            if(draft!=null) ui { if(canvas.image==null) { applyProject(draft);say("Recovered editable draft from this device") } }
        }
        if (!prefs.getBoolean("notice",false)) AlertDialog.Builder(this).setTitle("Educational creative tools").setMessage("Generated visuals may contain scientific errors. This build has no Adult Mode and is not a certified child-safe system. Medical anatomy remains available.").setPositiveButton("Continue") { _,_ -> prefs.edit().putBoolean("notice",true).apply() }.show()
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
        .put("seed",seed.text.toString()).put("strength",strength.progress+10).put("mode",mode.selectedItem.toString()).put("level",level.selectedItem.toString()).put("steps",steps.progress+10)
    private fun ui(block:()->Unit) { runOnUiThread { if(!isFinishing && !isDestroyed) block() } }
    private fun saveProject() {
        if(canvas.image==null) { say("Import or generate an image first");return }
        val snapshot=canvas.snapshot();val meta=metadata()
        background { val id=projects.save(snapshot,meta);ui { progress("project:$id",1,1);dirty=false;say("Saved editable image, ink layer and prompt") } }
    }
    private fun applyProject(loaded:com.scenio.mth.editor.LoadedProject) {
        canvas.restore(loaded.snapshot);val meta=loaded.info.meta
        prompt.setText(meta.optString("prompt"));negative.setText(meta.optString("negative"))
        (0 until mode.adapter.count).firstOrNull { mode.adapter.getItem(it).toString()==meta.optString("mode") }?.let { mode.setSelection(it) }
        (0 until level.adapter.count).firstOrNull { level.adapter.getItem(it).toString()==meta.optString("level") }?.let { level.setSelection(it) }
        steps.progress=(meta.optInt("steps",20)-10).coerceIn(0,30);seed.setText(meta.optString("seed"));strength.progress=(meta.optInt("strength",65)-10).coerceIn(0,90);dirty=false
    }
    private fun library() { background {
        val items=projects.list();ui {
            if(items.isEmpty()) { say("No saved projects yet");return@ui }
            val search=EditText(this).apply { hint="Search project titles";setSingleLine() }
            AlertDialog.Builder(this).setTitle("Local projects").setView(search).setPositiveButton("Search") { _,_ ->
                val query=search.text.toString();val found=items.filter { it.title.contains(query,true) || it.meta.optString("prompt").contains(query,true) }
                if(found.isEmpty()) say("No matching projects") else AlertDialog.Builder(this).setTitle("Projects").setItems(found.map { it.title }.toTypedArray()) { _,index ->
                    val item=found[index]
                    AlertDialog.Builder(this).setTitle(item.title).setItems(arrayOf("Open","Rename","Export editable backup","Delete")) { _,action ->
                        when(action) {
                            0 -> background { val loaded=projects.load(item.id);ui { applyProject(loaded);say("Editable project loaded") } }
                            1 -> { val title=EditText(this).apply { setText(item.title) };AlertDialog.Builder(this).setTitle("Rename project").setView(title).setPositiveButton("Save") { _,_ ->
                                val name=title.text.toString();background { projects.rename(item.id,name);ui { say("Project renamed") } }
                            }.setNegativeButton("Cancel",null).show() }
                            2 -> background { val file=File(cacheDir,"project-${item.id}.zip");projects.archive(item.id,file);ui { export(file,"application/zip","studyforge-project.zip") } }
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
        if(resultCode!=RESULT_OK) { if(requestCode==11) { pendingExport?.delete();pendingExport=null };return }
        val uri=data?.data ?: return
        when(requestCode) {
            10 -> background { val bitmap=PhotoDecoder.read(contentResolver,uri);ui { canvas.image=bitmap;say("Photo imported with its original orientation and aspect ratio") } }
            11 -> { val file=pendingExport;pendingExport=null
                if(file==null) { say("Export data is no longer available; export again");return }
                background { try {
                    contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: throw IOException("Cannot open export destination")
                    ui { say("Export saved") }
                } finally { file.delete() } }
            }
            12 -> background { val id=contentResolver.openInputStream(uri)?.use { projects.importArchive(it) } ?: throw IOException("Cannot open backup")
                val loaded=projects.load(id);ui { applyProject(loaded);say("Editable backup imported") }
            }
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
    private fun submit(kind:String) {
        val auth=token.text.toString();if(auth.isBlank()) { say("Start the local engine and enter its token first");return }
        val value=if(seed.text.isBlank()) java.security.SecureRandom().nextInt(Int.MAX_VALUE) else seed.text.toString().toIntOrNull()
        if(value==null || value<0) { say("Seed must be between 0 and 2147483647");return }
        if(prompt.text.isBlank() || prompt.length()>2000 || negative.length()>2000) { say("Write a prompt of 1–2000 characters; negative prompt maximum 2000");return }
        val body=JSONObject().put("kind",kind).put("prompt",prompt.text.toString()).put("negative",negative.text.toString())
            .put("steps",steps.progress+10).put("seed",value).put("strength",(strength.progress+10)/100.0).put("width",768).put("height",432)
        val photo=kind=="video" || mode.selectedItem.toString()=="Photo Converter"
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
        background {
            val job=JSONObject(String(api("/jobs/$id",auth)))
            if(job.getString("status")=="done") {
                val video=job.getString("kind")=="video"
                val file=File(cacheDir,"result-$id.${if(video) "mp4" else "png"}")
                download("/results/$id",auth,file)
                if(video) ui { progress("generation:$id",2,2);export(file,"video/mp4","studyforge-30s.mp4") }
                else {
                    val bitmap=try { PhotoDecoder.read(contentResolver,android.net.Uri.fromFile(file)) } finally { file.delete() }
                    ui { progress("generation:$id",2,2);canvas.image=bitmap;say("Generation complete • review before academic use") }
                }
            } else ui { say(job.optString("error",job.getString("status"))) }
        }
    }
    private fun download(path:String,auth:String,file:File) {
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
    private fun rememberEditor() {
        session.snapshot=canvas.snapshot();session.prompt=prompt.text.toString();session.negative=negative.text.toString()
        session.mode=mode.selectedItemPosition;session.level=level.selectedItemPosition;session.steps=steps.progress;session.seed=seed.text.toString()
        prefs.edit().putString("prompt",session.prompt).apply()
    }
    override fun onSaveInstanceState(outState:Bundle) {
        rememberEditor();outState.putString("pendingExport",pendingExport?.path);super.onSaveInstanceState(outState)
    }
    override fun onStop() {
        rememberEditor()
        if(dirty && canvas.image!=null) { projects.autosave(canvas.snapshot(),metadata());dirty=false }
        super.onStop()
    }
    override fun onDestroy() { worker.shutdownNow();super.onDestroy() }
}
