package com.scenio.mth.features.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.scenio.mth.R
import com.scenio.mth.features.FeatureRuntime
import com.scenio.mth.features.FeaturesActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.concurrent.TimeUnit

class ReminderScheduler(private val context: Context) {
    fun setEnabled(enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) { manager.cancelUniqueWork(NAME); return }
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(24, TimeUnit.HOURS).build()
        manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
    companion object { const val NAME = "studyforge-study-reminder-v1" }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = try { FeatureRuntime.graph(context).settings.data.first() }
        catch (e: CancellationException) { throw e }
        catch (_: IOException) { return if (runAttemptCount < 3) Result.retry() else Result.failure() }
        if (!settings.reminders) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return Result.success()
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL,
                context.getString(R.string.sf_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT))
            if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE)
                return Result.success()
        }
        val intent = PendingIntent.getActivity(context, 0,
            Intent(context, FeaturesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.sf_ic_notification)
            .setContentTitle(context.getString(R.string.sf_reminder_title))
            .setContentText(context.getString(R.string.sf_reminder_body))
            .setContentIntent(intent).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).build()
        try { manager.notify(7301, notification) }
        catch (_: SecurityException) { return Result.success() }
        return Result.success()
    }
    companion object { const val CHANNEL = "studyforge-study-v1" }
}
