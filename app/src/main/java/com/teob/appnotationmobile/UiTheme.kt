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
        backgroundTop = Color.rgb(246, 250, 251),
        backgroundMid = Color.rgb(242, 248, 249),
        backgroundBottom = Color.rgb(237, 245, 247),
        glassTop = Color.rgb(255, 255, 255),
        glassBottom = Color.rgb(250, 253, 253),
        glassStroke = Color.rgb(224, 235, 237),
        header = Color.rgb(22, 51, 58),
        headerText = Color.rgb(248, 250, 252),
        primary = Color.rgb(63, 175, 157),
        accent = Color.rgb(91, 135, 168),
        text = Color.rgb(24, 34, 38),
        muted = Color.rgb(99, 115, 122),
        iconOnAccent = Color.WHITE,
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
