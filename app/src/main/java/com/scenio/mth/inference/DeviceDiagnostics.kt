package com.scenio.mth.inference

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import org.json.JSONObject

object DeviceDiagnostics {
    fun snapshot(context:Context):JSONObject {
        val memory=ActivityManager.MemoryInfo();(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val process=Debug.MemoryInfo();Debug.getMemoryInfo(process)
        val power=context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return JSONObject().put("sample_time_ms",System.currentTimeMillis()).put("total_ram_bytes",memory.totalMem).put("available_ram_bytes",memory.availMem)
            .put("os_low_memory",memory.lowMemory).put("process_pss_bytes",process.totalPss.toLong()*1024)
            .put("native_heap_bytes",Debug.getNativeHeapAllocatedSize()).put("java_heap_bytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())
            .put("thermal_status",if(Build.VERSION.SDK_INT>=29) power.currentThermalStatus else JSONObject.NULL)
            .put("power_save",power.isPowerSaveMode).put("free_storage_bytes",context.filesDir.usableSpace)
    }
}
