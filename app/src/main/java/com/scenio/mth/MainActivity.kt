package com.scenio.mth

import android.app.Activity
import androidx.activity.ComponentActivity
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
    private lateinit var canvas: ForgeCanvas
    private lateinit var prompt: EditText
    private lateinit var negative: EditText
    private lateinit var status: TextView
    private lateinit var token: EditText
    private lateinit var mode: Spinner
    private lateinit var level: Spinner
    private lateinit var steps: SeekBar
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("forge",MODE_PRIVATE) }
    private var exporting: ByteArray? = null
    private var latestJob: String? = null
    private var busy = false
    private fun text(s:String) = TextView(this).apply { text=s;setTextColor(Color.WHITE);textSize=16f;setPadding(8,12,8,8) }
    private fun button(s:String, block:()->Unit) = Button(this).apply { text=s;setOnClickListener { block() } }
    private fun choices(items:List<String>) = Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,items) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        latestJob=prefs.getString("latestJob",null)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,24,24,32);setBackgroundColor(Color.rgb(15,23,42)) }
        val scroll=ScrollView(this).apply { addView(root) };setContentView(scroll)
        root.addView(ImageView(this).apply { setImageResource(com.scenio.mth.R.drawable.ic_xxhdpi);contentDescription="StudyForge logo" },LinearLayout.LayoutParams(84,84))
        root.addView(text("StudyForge  /  create • learn • refine").apply { textSize=25f })
        root.addView(text(nativeStatus()))
        mode=choices(listOf("Free Generate","Science Lab","Art Studio","Medical Explorer","Photo Converter","Video Creator"));root.addView(mode)
        prompt=EditText(this).apply { hint="Describe your idea or photo transformation…";minLines=3;setText(prefs.getString("prompt","Show mitosis step by step"));setTextColor(Color.WHITE) };root.addView(prompt)
        negative=EditText(this).apply { hint="Negative prompt";setTextColor(Color.WHITE) };root.addView(negative)
        level=choices(listOf("Whole body","System","Organ","Tissue","Cellular"));root.addView(level)
        root.addView(text("Model inference steps (10–40)"));steps=SeekBar(this).apply { max=30;progress=10 };root.addView(steps)
        canvas=ForgeCanvas(this);root.addView(canvas,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,600))
        root.addView(button("Create offline schematic") { canvas.image=canvas.diagram(prompt.text.toString(),level.selectedItem.toString());say("Template generated on this device. This is not AI inference.") })
        root.addView(button("Import photo") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="image/*";addCategory(Intent.CATEGORY_OPENABLE) },10) })
        root.addView(Switch(this).apply { text="Draw / annotate on ink layer";setTextColor(Color.WHITE);setOnCheckedChangeListener { _,on -> canvas.drawing=on } })
        root.addView(button("Undo ink") { canvas.undo() })
        root.addView(button("Save project locally") { saveProject() })
        root.addView(button("Open local library") { library() })
        root.addView(button("Create 30-second video on this phone") { localVideo() })
        root.addView(button("Export PNG") { export(canvas.flatten().let { b -> java.io.ByteArrayOutputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray() } },"image/png","studyforge.png") })
        root.addView(text("Optional local computer engine • USB connection • no cloud"))
        token=EditText(this).apply { hint="Engine token (kept in memory only)";inputType=129;setTextColor(Color.WHITE) };root.addView(token)
        root.addView(button("Generate with local model") { submit("image") })
        root.addView(button("30-second 1080p reveal video") { submit("video") })
        root.addView(button("Check current engine job / download result") { poll() })
        root.addView(text("Video exports a progressive reveal; subject-motion generation is not implemented. Downloaded models run on your own computer. No phone-local diffusion runtime is bundled."))
        status=text("Ready • all projects stay local");root.addView(status)
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
    private fun saveProject() {
        val id=UUID.randomUUID().toString();val dir=File(filesDir,"projects/$id");dir.mkdirs()
        File(dir,"image.png").outputStream().use { canvas.flatten().compress(Bitmap.CompressFormat.PNG,100,it) }
        File(dir,"project.json").writeText(JSONObject().put("prompt",prompt.text.toString()).put("negative",negative.text.toString()).put("mode",mode.selectedItem.toString()).put("level",level.selectedItem.toString()).put("created",System.currentTimeMillis()).put("flattened",true).toString())
        progress("project:$id",1,1)
        prefs.edit().putString("prompt",prompt.text.toString()).apply();say("Saved image and prompt. Ink is flattened in saved projects.")
    }
    private fun library() {
        val dirs=File(filesDir,"projects").listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        if(dirs.isEmpty()) { say("No saved projects yet");return }
        val titles=dirs.map { runCatching { JSONObject(File(it,"project.json").readText()).getString("prompt").take(60) }.getOrDefault(it.name) }
        AlertDialog.Builder(this).setTitle("Local projects").setItems(titles.toTypedArray()) { _,i ->
            canvas.image=BitmapFactory.decodeFile(File(dirs[i],"image.png").path)
            val meta=JSONObject(File(dirs[i],"project.json").readText());prompt.setText(meta.optString("prompt"));negative.setText(meta.optString("negative"));say("Project loaded")
        }.show()
    }
    private fun export(data:ByteArray,mime:String,name:String) {
        exporting=data;startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type=mime;addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,name) },11)
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK) { exporting=null;return };val uri=data?.data ?: return
        try {
            if(requestCode==10) {
                val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,bounds) }
                val options=BitmapFactory.Options().apply { inSampleSize=1 };while(bounds.outWidth/options.inSampleSize>2048 || bounds.outHeight/options.inSampleSize>2048) options.inSampleSize*=2
                canvas.image=contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,options) };say("Photo imported locally")
            } else if(requestCode==11) { exporting?.let { bytes -> contentResolver.openOutputStream(uri)?.use { it.write(bytes) } };exporting=null;say("Export saved") }
        } catch(e:Exception) { say(e.message ?: "File operation failed") }
    }
    private fun localVideo() {
        if(busy) { say("Please wait for the current request");return }
        if(canvas.image==null) { say("Import or generate an image first");return }
        val bitmap=canvas.flatten();say("Rendering 30-second reveal on this device. Keep StudyForge open…")
        background {
            val file=File(cacheDir,"reveal-${UUID.randomUUID()}.mp4")
            try { RevealVideo.render(bitmap,file);val bytes=file.readBytes();runOnUiThread { if(!isFinishing && !isDestroyed) export(bytes,"video/mp4","studyforge-30s.mp4") } }
            finally { file.delete();bitmap.recycle() }
        }
    }
    private fun api(path:String,auth:String,body:JSONObject?=null):ByteArray {
        val c=URL("http://127.0.0.1:8765$path").openConnection() as HttpURLConnection
        try {
            c.connectTimeout=10000;c.readTimeout=30000;c.setRequestProperty("Authorization","Bearer $auth")
            if(body!=null) { c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use { it.write(body.toString().toByteArray()) } }
            if(c.responseCode !in 200..299) throw IllegalStateException(c.errorStream?.bufferedReader()?.readText()?.take(300) ?: "Engine error")
            return c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }
    private fun background(block:()->Unit) {
        if(busy) { say("Please wait for the current request");return };busy=true
        worker.execute { try { block() } catch(e:Exception) { runOnUiThread { say(e.message ?: "Engine unavailable") } } finally { runOnUiThread { busy=false } } }
    }
    private fun submit(kind:String) {
        val auth=token.text.toString();if(auth.isBlank()) { say("Start the local engine and enter its token first");return }
        val body=JSONObject().put("kind",kind).put("prompt",prompt.text.toString()).put("negative",negative.text.toString()).put("steps",steps.progress+10).put("width",768).put("height",432)
        if(kind=="video" || mode.selectedItem.toString()=="Photo Converter") {
            if(canvas.image==null) { say("Import or generate an image first");return }
            val bytes=java.io.ByteArrayOutputStream().use { out -> canvas.flatten().compress(Bitmap.CompressFormat.PNG,100,out);out.toByteArray() };body.put("image",Base64.encodeToString(bytes,Base64.NO_WRAP))
        }
        say("Submitting to your local engine…")
        background { val job=JSONObject(String(api("/jobs",auth,body)));runOnUiThread { latestJob=job.getString("id");progress("generation:${job.getString("id")}",1,2);prefs.edit().putString("latestJob",latestJob).apply();say("Job queued. Check job to see progress.") } }
    }
    private fun poll() {
        val id=latestJob ?: run { say("Submit a job first");return };val auth=token.text.toString()
        background {
            val job=JSONObject(String(api("/jobs/$id",auth)))
            if(job.getString("status")=="done") {
                val bytes=api("/results/$id",auth)
                runOnUiThread { progress("generation:$id",2,2);if(job.getString("kind")=="video") export(bytes,"video/mp4","studyforge-30s.mp4") else { canvas.image=BitmapFactory.decodeByteArray(bytes,0,bytes.size);say("Generation complete • review before academic use") } }
            } else runOnUiThread { say(job.optString("error",job.getString("status"))) }
        }
    }
    override fun onDestroy() { worker.shutdownNow();super.onDestroy() }
}
