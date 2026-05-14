package com.teob.appnotationmobile

import android.app.Activity
import android.graphics.Color

data class UiPalette(
    val backgroundTop: Int,
    val backgroundMid: Int,
    val backgroundBottom: Int,
    val surface: Int,
    val surfaceAlt: Int,
    val glassTop: Int,
    val glassBottom: Int,
    val glassStroke: Int,
    val header: Int,
    val headerText: Int,
    val primary: Int,
    val accent: Int,
    val text: Int,
    val muted: Int,
    val success: Int,
    val warning: Int,
    val danger: Int,
    val iconOnAccent: Int,
)

object UiTheme {
    val light = UiPalette(
        backgroundTop = Color.rgb(249, 251, 252),
        backgroundMid = Color.rgb(249, 251, 252),
        backgroundBottom = Color.rgb(245, 249, 248),
        surface = Color.rgb(255, 255, 255),
        surfaceAlt = Color.rgb(240, 247, 245),
        glassTop = Color.rgb(255, 255, 255),
        glassBottom = Color.rgb(255, 255, 255),
        glassStroke = Color.rgb(219, 230, 227),
        header = Color.rgb(255, 255, 255),
        headerText = Color.rgb(28, 40, 47),
        primary = Color.rgb(45, 126, 105),
        accent = Color.rgb(91, 177, 151),
        text = Color.rgb(28, 40, 47),
        muted = Color.rgb(92, 111, 118),
        success = Color.rgb(47, 142, 93),
        warning = Color.rgb(182, 117, 34),
        danger = Color.rgb(190, 68, 68),
        iconOnAccent = Color.rgb(255, 255, 255),
    )

    val dark = UiPalette(
        backgroundTop = Color.rgb(12, 20, 24),
        backgroundMid = Color.rgb(12, 20, 24),
        backgroundBottom = Color.rgb(15, 27, 30),
        surface = Color.rgb(22, 34, 39),
        surfaceAlt = Color.rgb(28, 48, 51),
        glassTop = Color.rgb(22, 34, 39),
        glassBottom = Color.rgb(22, 34, 39),
        glassStroke = Color.rgb(52, 72, 78),
        header = Color.rgb(18, 29, 34),
        headerText = Color.rgb(245, 247, 250),
        primary = Color.rgb(95, 198, 170),
        accent = Color.rgb(129, 214, 189),
        text = Color.rgb(236, 240, 244),
        muted = Color.rgb(167, 184, 190),
        success = Color.rgb(92, 198, 132),
        warning = Color.rgb(230, 170, 83),
        danger = Color.rgb(235, 108, 108),
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
