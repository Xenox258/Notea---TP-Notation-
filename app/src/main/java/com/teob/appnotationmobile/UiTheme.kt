package com.teob.appnotationmobile

import android.app.Activity
import android.graphics.Color

data class UiPalette(
    val backgroundTop: Int,
    val backgroundMid: Int,
    val backgroundBottom: Int,
    val glassTop: Int,
    val glassBottom: Int,
    val glassStroke: Int,
    val header: Int,
    val headerText: Int,
    val primary: Int,
    val accent: Int,
    val text: Int,
    val muted: Int,
    val iconOnAccent: Int,
)

object UiTheme {
    val light = UiPalette(
        backgroundTop = Color.rgb(249, 252, 251),
        backgroundMid = Color.rgb(243, 249, 247),
        backgroundBottom = Color.rgb(236, 245, 243),
        glassTop = Color.argb(232, 255, 255, 255),
        glassBottom = Color.argb(205, 235, 247, 242),
        glassStroke = Color.argb(170, 194, 224, 214),
        header = Color.rgb(255, 255, 255),
        headerText = Color.rgb(24, 34, 38),
        primary = Color.rgb(116, 185, 165),
        accent = Color.rgb(158, 208, 193),
        text = Color.rgb(24, 34, 38),
        muted = Color.rgb(99, 115, 122),
        iconOnAccent = Color.rgb(19, 53, 47),
    )

    val dark = UiPalette(
        backgroundTop = Color.rgb(13, 22, 25),
        backgroundMid = Color.rgb(15, 27, 31),
        backgroundBottom = Color.rgb(18, 32, 38),
        glassTop = Color.rgb(31, 45, 50),
        glassBottom = Color.rgb(25, 37, 43),
        glassStroke = Color.rgb(55, 76, 83),
        header = Color.rgb(6, 16, 20),
        headerText = Color.rgb(245, 247, 250),
        primary = Color.rgb(83, 196, 177),
        accent = Color.rgb(104, 151, 190),
        text = Color.rgb(236, 240, 244),
        muted = Color.rgb(165, 181, 187),
        iconOnAccent = Color.rgb(8, 20, 24),
    )
}

object ThemePrefs {
    private const val PREFS = "ui-theme"
    private const val KEY_DARK = "dark-mode"

    fun loadDarkMode(activity: Activity): Boolean {
        return activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).getBoolean(KEY_DARK, false)
    }

    fun saveDarkMode(activity: Activity, isDarkMode: Boolean) {
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK, isDarkMode)
            .apply()
    }
}
