package com.scenio.mth

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES20
import android.opengl.GLUtils
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Hardware H.264 still reveal, 30s/1080p/24fps. No AI subject motion. */
object RevealVideo {
    fun render(image: Bitmap, output: File, onProgress: (Int) -> Unit = {}) {
        var releaseCodec:MediaCodec?=null
        var releaseSurface:android.view.Surface?=null
        var releaseMuxer:MediaMuxer?=null
        var releaseDisplay=EGL14.EGL_NO_DISPLAY
        var releaseContext=EGL14.EGL_NO_CONTEXT
        var releaseWindow=EGL14.EGL_NO_SURFACE
        var started=false
        var muxing=false
        var completed=false
        try {
        val codec=MediaCodec.createEncoderByType("video/avc").also { releaseCodec=it }
        val format=MediaFormat.createVideoFormat("video/avc",1920,1080).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE,6_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE,24)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,2)
        }
        codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface=codec.createInputSurface().also { releaseSurface=it }
        val display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY).also { releaseDisplay=it }
        check(EGL14.eglInitialize(display,IntArray(2),0,IntArray(2),0)) { "EGL initialization failed" }
        val configs=arrayOfNulls<android.opengl.EGLConfig>(1);val count=IntArray(1)
        val attributes=intArrayOf(EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,0x3142,1,EGL14.EGL_NONE)
        check(EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0) && count[0]>0) { "No recordable EGL config" }
        val context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0).also { releaseContext=it }
        val window=EGL14.eglCreateWindowSurface(display,configs[0],surface,intArrayOf(EGL14.EGL_NONE),0).also { releaseWindow=it }
        check(EGL14.eglMakeCurrent(display,window,window,context)) { "Cannot bind encoder surface" }
        val muxer=MediaMuxer(output.path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { releaseMuxer=it }
        var track=-1
        val info=MediaCodec.BufferInfo()
        fun drain(final:Boolean) {
            val deadline=System.nanoTime()+30_000_000_000L
            while(true) {
                val index=codec.dequeueOutputBuffer(info,if(final) 10000L else 0L)
                if(index==MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if(!final) return
                    check(System.nanoTime()<deadline) { "Video encoder timed out" }
                } else if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    check(!muxing);track=muxer.addTrack(codec.outputFormat);muxer.start();muxing=true
                } else if(index>=0) {
                    try {
                        val buffer=codec.getOutputBuffer(index) ?: error("Missing encoder buffer")
                        if(info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size=0
                        if(info.size>0) { check(muxing);buffer.position(info.offset);buffer.limit(info.offset+info.size);muxer.writeSampleData(track,buffer,info) }
                    } finally { codec.releaseOutputBuffer(index,false) }
                    if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
        fun shader(type:Int,source:String):Int {
            val id=GLES20.glCreateShader(type);GLES20.glShaderSource(id,source);GLES20.glCompileShader(id)
            val ok=IntArray(1);GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,ok,0);check(ok[0]!=0) { GLES20.glGetShaderInfoLog(id) };return id
        }
        run {
            val vs=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 pos; attribute vec2 uv; varying vec2 coord; void main(){gl_Position=vec4(pos,0.,1.);coord=uv;}")
            val fs=shader(GLES20.GL_FRAGMENT_SHADER,"precision mediump float; varying vec2 coord; uniform sampler2D tex; uniform float progress; uniform vec2 fit; void main(){vec2 p=(coord-.5)/fit+.5;vec4 bg=vec4(.06,.09,.16,1.);vec4 col=(p.x<0.||p.x>1.||p.y<0.||p.y>1.)?bg:texture2D(tex,p);float reveal=1.-smoothstep(progress-.002,progress+.002,coord.x);gl_FragColor=mix(bg,col,reveal);}")
            val program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program)
            val linked=IntArray(1);GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);check(linked[0]!=0) { "Shader program link failed" }
            GLES20.glUseProgram(program)
            val vertices=floatArrayOf(-1f,-1f,0f,1f, 1f,-1f,1f,1f, -1f,1f,0f,0f, 1f,1f,1f,0f)
            val data=ByteBuffer.allocateDirect(vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(vertices);position(0) }
            val pos=GLES20.glGetAttribLocation(program,"pos");val uv=GLES20.glGetAttribLocation(program,"uv")
            GLES20.glEnableVertexAttribArray(pos);GLES20.glEnableVertexAttribArray(uv)
            data.position(0);GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,16,data)
            data.position(2);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,16,data)
            val textures=IntArray(1);GLES20.glGenTextures(1,textures,0);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,image,0)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"tex"),0)
            val progress=GLES20.glGetUniformLocation(program,"progress")
            val fit=com.scenio.mth.editor.fitImage(1920f,1080f,image.width,image.height)
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"fit"),fit.width/1920f,fit.height/1080f)
            GLES20.glViewport(0,0,1920,1080);codec.start();started=true
            for(frame in 0 until 720) {
                check(!Thread.currentThread().isInterrupted) { "Video export cancelled" }
                drain(false)
                if(frame%24==0) onProgress(frame*100/720)
                GLES20.glUniform1f(progress,(frame/24f/28f).coerceAtMost(1.01f))
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
                EGLExt.eglPresentationTimeANDROID(display,window,frame*1_000_000_000L/24)
                check(EGL14.eglSwapBuffers(display,window)) { "Encoder swap failed" }
            }
            codec.signalEndOfInputStream();drain(true)
            GLES20.glDeleteTextures(1,textures,0);GLES20.glDeleteProgram(program);GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs)
        }
        completed=true
        onProgress(100)
        } finally {
            runCatching { if(started) releaseCodec?.stop() }
            runCatching { releaseCodec?.release() }
            runCatching { if(muxing) releaseMuxer?.stop() }
            runCatching { releaseMuxer?.release() }
            runCatching {
                if(releaseDisplay!=EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(releaseDisplay,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
                    if(releaseWindow!=EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(releaseDisplay,releaseWindow)
                    if(releaseContext!=EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(releaseDisplay,releaseContext)
                    EGL14.eglReleaseThread();EGL14.eglTerminate(releaseDisplay)
                }
            }
            runCatching { releaseSurface?.release() }
            if(!completed) output.delete()
        }
    }
}
