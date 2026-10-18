package com.scenio.mth.science

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import com.scenio.mth.ForgeCanvas

class StudyExplorerActivity:ComponentActivity() {
    private lateinit var catalog:StudyCatalog
    private var selected="human-body"
    private var showLabels=true
    private var preview:Bitmap?=null
    private lateinit var notes:EditText
    private lateinit var body:LinearLayout
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        catalog=StudyCatalog(this);selected=savedInstanceState?.getString("selected") ?: selected;showLabels=savedInstanceState?.getBoolean("labels",true) ?: true
        notes=EditText(this).apply { hint="Additional generation instructions (optional)";setText(savedInstanceState?.getString("notes", ""));maxLines=5;filters=arrayOf(android.text.InputFilter.LengthFilter(500)) }
        val scroll=ScrollView(this);body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,24,24,32) };scroll.addView(body);setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { v,insets ->
            if(android.os.Build.VERSION.SDK_INT>=30) { val b=insets.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom) }
            else v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            insets
        }
        render()
    }
    private fun text(value:String,size:Float=17f) = TextView(this).apply { text=value;textSize=size;setPadding(8,12,8,12);setTextColor(0xff16324f.toInt()) }
    private fun action(value:String,click:()->Unit) { body.addView(Button(this).apply { text=value;setOnClickListener { click() } }) }
    private fun select(id:String) { selected=id;render() }
    private fun render() {
        (notes.parent as? ViewGroup)?.removeView(notes);body.removeAllViews();body.setBackgroundColor(0xfff8fafc.toInt())
        val node=catalog.node(selected)
        body.addView(text("Offline Science Explorer",25f).apply { androidx.core.view.ViewCompat.setAccessibilityHeading(this,true) })
        action("Choose study topic") { AlertDialog.Builder(this).setTitle("Study topics").setItems(arrayOf("Body → system → organ → cell","Mitosis sequence")) { _,i -> select(catalog.roots[i]) }.show() }
        body.addView(text(catalog.path(selected).joinToString(" › ") { it.title },15f))
        body.addView(text(node.title,23f).apply { androidx.core.view.ViewCompat.setAccessibilityHeading(this,true) })
        val snapshot=catalog.snapshot(selected,showLabels);val next=ForgeCanvas.render(snapshot);snapshot.base?.recycle()
        body.addView(ImageView(this).apply { setImageBitmap(next);adjustViewBounds=true;contentDescription=node.alt;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
        preview?.recycle();preview=next
        body.addView(text(node.summary))
        body.addView(Switch(this).apply { text="Show diagram labels";setTextColor(0xff16324f.toInt());isChecked=showLabels;setOnCheckedChangeListener { _,on -> showLabels=on;render() } })
        body.addView(text("Diagram description",19f).apply { androidx.core.view.ViewCompat.setAccessibilityHeading(this,true) });body.addView(text(node.alt))
        node.children.forEach { id -> action("Explore deeper: "+catalog.node(id).title) { select(id) } }
        node.next?.let { id -> action("Next phase: "+catalog.node(id).title) { select(id) } }
        val previous=node.parent ?: catalog.nodes.firstOrNull { it.next==selected }?.id
        previous?.let { id -> action("Previous view: "+catalog.node(id).title) { select(id) } }
        if(!com.scenio.mth.family.FamilyPolicy.restricted(this)) {
            body.addView(notes)
            action("Open diagram in editor") { finishWith("diagram") }
            action("Use this view as generation prompt") { finishWith("prompt") }
        }
        body.addView(text(catalog.notice,15f))
        body.addView(text("Sources",19f).apply { androidx.core.view.ViewCompat.setAccessibilityHeading(this,true) })
        catalog.sources.filter { it.id in node.sourceIds }.forEach { source -> if(com.scenio.mth.family.FamilyPolicy.restricted(this)) body.addView(text(source.title)) else action(source.title+" · opens browser") { runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(source.url))) }.onFailure { Toast.makeText(this,"No browser is available; open the source URL from the documentation",Toast.LENGTH_LONG).show() } } }
        action(if(com.scenio.mth.family.FamilyPolicy.restricted(this)) "Return to study-only home" else "Return to studio") { finish() }
    }
    private fun finishWith(action:String) {
        if(com.scenio.mth.family.FamilyPolicy.restricted(this)) return
        setResult(RESULT_OK,Intent().putExtra("study-id",selected).putExtra("study-action",action).putExtra("study-labels",showLabels).putExtra("study-notes",notes.text.toString()));finish()
    }
    override fun onSaveInstanceState(outState:Bundle) { outState.putString("selected",selected);outState.putBoolean("labels",showLabels);outState.putString("notes",notes.text.toString());super.onSaveInstanceState(outState) }
    override fun onDestroy() { body.removeAllViews();preview?.recycle();preview=null;super.onDestroy() }
}
