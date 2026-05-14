package com.teob.appnotationmobile

import android.app.Activity
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

data class CachedImport(
    val name: String,
    val contentBase64: String,
)

enum class ImportCacheKind(
    val prefKey: String,
) {
    STUDENTS("cached-student-lists"),
    GRIDS("cached-grids"),
}

object ImportCacheStore {
    private const val PREFS = "tp-import-cache"
    private const val MAX_ITEMS = 5

    fun load(activity: Activity, kind: ImportCacheKind): List<CachedImport> {
        val raw = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .getString(kind.prefKey, null)
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                CachedImport(
                    name = item.getString("name"),
                    contentBase64 = item.getString("contentBase64"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(activity: Activity, kind: ImportCacheKind, name: String, bytes: ByteArray) {
        val entry = CachedImport(name, Base64.encodeToString(bytes, Base64.NO_WRAP))
        val items = (listOf(entry) + load(activity, kind).filterNot { it.name == name })
            .take(MAX_ITEMS)
        val array = JSONArray(items.map { item ->
            JSONObject()
                .put("name", item.name)
                .put("contentBase64", item.contentBase64)
        })
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .edit()
            .putString(kind.prefKey, array.toString())
            .apply()
    }
}
