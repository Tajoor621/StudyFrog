package com.scenio.mth.features.telemetry

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.scenio.mth.BuildConfig

/** No Firebase initialization until at least one explicit opt-in is present. */
class Telemetry(private val context: Context) {
    private var initialized = false
    private var analyticsAllowed = false
    @Synchronized
    fun applyConsent(analytics: Boolean, crashes: Boolean) {
        if (!BuildConfig.FIREBASE_ENABLED) return
        if (!initialized && !analytics && !crashes) return
        if (!initialized) {
            checkNotNull(FirebaseApp.initializeApp(context)) { "Firebase configuration missing" }
            initialized = true
        }
        val stats = FirebaseAnalytics.getInstance(context)
        stats.setConsent(mapOf(
            FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to
                if (analytics) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED,
            FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
            FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
            FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED
        ))
        stats.setAnalyticsCollectionEnabled(analytics)
        if (!analytics && analyticsAllowed) stats.resetAnalyticsData()
        val reports = FirebaseCrashlytics.getInstance()
        reports.setCrashlyticsCollectionEnabled(crashes)
        if (!crashes) reports.deleteUnsentReports()
        analyticsAllowed = analytics
    }
    @Synchronized
    fun disableAnalyticsNow() {
        if (initialized) {
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(false)
            FirebaseAnalytics.getInstance(context).resetAnalyticsData()
        }
        analyticsAllowed = false
    }
    @Synchronized
    fun disableCrashesNow() {
        if (initialized) {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
            FirebaseCrashlytics.getInstance().deleteUnsentReports()
        }
    }
    @Synchronized
    fun studioOpened() {
        if (initialized && analyticsAllowed) FirebaseAnalytics.getInstance(context)
            .logEvent("studio_opened", null)
    }
}
