package com.scenio.mth.family

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.channels.BufferOverflow

/** Private local access control; it is not an OS parental-control or age-verification service. */
object FamilyPolicy {
    private val changes=MutableSharedFlow<Unit>(extraBufferCapacity=1,onBufferOverflow=BufferOverflow.DROP_OLDEST)
    fun observe(context:Context)=changes.onStart { emit(Unit) }.map { restricted(context) }.distinctUntilChanged()
    data class Status(val configured:Boolean,val restricted:Boolean,val blockedUntil:Long=0)
    private fun file(context:Context)=File(context.noBackupFilesDir,"family-policy.json")
    private fun read(context:Context):JSONObject? {
        val source=file(context)
        if(!source.exists() && !File(source.path+".bak").exists() && !File(source.path+".new").exists()) return null
        return AtomicFile(source).openRead().bufferedReader().use { JSONObject(it.readText()) }.also {
            require(it.getInt("version")==1 && it.getString("salt").length<=64 && it.getString("hash").length<=128)
            require(it.getString("algorithm") in listOf("PBKDF2WithHmacSHA256","PBKDF2WithHmacSHA1"))
            require(it.getInt("failures") in 0..4 && it.getLong("blockedUntil")>=0)
            require(Base64.decode(it.getString("salt"),Base64.NO_WRAP).size==16 && Base64.decode(it.getString("hash"),Base64.NO_WRAP).size==32)
            it.getBoolean("restricted")
        }
    }
    private fun write(context:Context,value:JSONObject) {
        val atomic=AtomicFile(file(context));val stream=atomic.startWrite()
        try { stream.write(value.toString().toByteArray(Charsets.UTF_8));atomic.finishWrite(stream);changes.tryEmit(Unit) }
        catch(e:Exception) { atomic.failWrite(stream);throw e }
    }
    @Synchronized fun status(context:Context):Status = try {
        read(context)?.let { Status(true,it.getBoolean("restricted"),it.getLong("blockedUntil")) } ?: Status(false,false)
    } catch(_:Exception) { Status(true,true) }
    fun restricted(context:Context)=status(context).restricted
    @Synchronized fun configureAndLock(context:Context,pin:String) {
        check(!status(context).configured) { "A parent PIN already exists" };require(PinDigest.valid(pin)) { "Use exactly six digits" }
        val algorithm=if(Build.VERSION.SDK_INT>=26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val salt=PinDigest.salt();val hash=PinDigest.derive(pin,salt,algorithm)
        write(context,JSONObject().put("version",1).put("algorithm",algorithm).put("salt",Base64.encodeToString(salt,Base64.NO_WRAP)).put("hash",Base64.encodeToString(hash,Base64.NO_WRAP)).put("restricted",true).put("failures",0).put("blockedUntil",0))
    }
    private fun authorize(context:Context,pin:String):JSONObject {
        val current=read(context) ?: error("Set a parent PIN first")
        check(System.currentTimeMillis()>=current.getLong("blockedUntil")) { "Too many attempts. Try again after the 15-minute cooldown" }
        val okay=PinDigest.matches(pin,Base64.decode(current.getString("salt"),Base64.NO_WRAP),Base64.decode(current.getString("hash"),Base64.NO_WRAP),current.getString("algorithm"))
        if(!okay) {
            val failures=current.getInt("failures")+1
            current.put("failures",if(failures>=5) 0 else failures).put("blockedUntil",if(failures>=5) System.currentTimeMillis()+900_000 else 0L)
            write(context,current);error("Incorrect PIN"+(if(failures>=5) "; wait 15 minutes" else ""))
        }
        return current.put("failures",0).put("blockedUntil",0)
    }
    @Synchronized fun setRestricted(context:Context,pin:String,restricted:Boolean) { write(context,authorize(context,pin).put("restricted",restricted)) }
    @Synchronized fun changePin(context:Context,old:String,new:String) {
        require(PinDigest.valid(new)) { "Use exactly six digits" }
        val current=authorize(context,old);val salt=PinDigest.salt();val hash=PinDigest.derive(new,salt,current.getString("algorithm"))
        write(context,current.put("salt",Base64.encodeToString(salt,Base64.NO_WRAP)).put("hash",Base64.encodeToString(hash,Base64.NO_WRAP)))
    }
}
