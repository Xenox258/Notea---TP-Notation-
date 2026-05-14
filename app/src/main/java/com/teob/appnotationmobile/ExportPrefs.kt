package com.teob.appnotationmobile

import android.app.Activity

object ActiveProjectPrefs {
    private const val PREFS = "tp-project"
    private const val KEY_ACTIVE_PROJECT_ID = "active-project-id"

    fun save(activity: Activity, projectId: String) {
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_PROJECT_ID, projectId)
            .apply()
    }

    fun load(activity: Activity): String? {
        return activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .getString(KEY_ACTIVE_PROJECT_ID, null)
    }
}

data class PendingExport(
    val projectId: String,
    val ownerId: String,
    val label: String,
)

object PendingExportPrefs {
    private const val PREFS = "tp-project"
    private const val KEY_PROJECT_ID = "export-project-id"
    private const val KEY_OWNER_ID = "export-owner-id"
    private const val KEY_LABEL = "export-label"

    fun save(activity: Activity, projectId: String, ownerId: String, label: String) {
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROJECT_ID, projectId)
            .putString(KEY_OWNER_ID, ownerId)
            .putString(KEY_LABEL, label)
            .apply()
    }

    fun load(activity: Activity): PendingExport? {
        val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        val projectId = prefs.getString(KEY_PROJECT_ID, null) ?: return null
        val ownerId = prefs.getString(KEY_OWNER_ID, null) ?: return null
        val label = prefs.getString(KEY_LABEL, null) ?: return null
        return PendingExport(projectId, ownerId, label)
    }
}

object PendingBulkExportPrefs {
    private const val PREFS = "tp-project"
    private const val KEY_PROJECT_ID = "bulk-export-project-id"

    fun save(activity: Activity, projectId: String) {
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROJECT_ID, projectId)
            .apply()
    }

    fun load(activity: Activity): String? {
        return activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .getString(KEY_PROJECT_ID, null)
    }
}
