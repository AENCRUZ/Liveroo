package com.group4.liveroo.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext

/**
 * In-app dark mode choice (addendum 01, part A). UI only: kept in its own SharedPreferences file,
 * never in the identity store or the database, and never synced. Default is light.
 */
object ThemePreference {
    private const val FILE = "liveroo_ui_prefs"
    private const val KEY = "dark_mode"
    private var state: MutableState<Boolean>? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Reads the saved value on first use, so the first frame already has the right theme. */
    fun isDark(context: Context): Boolean {
        val s = state ?: mutableStateOf(prefs(context).getBoolean(KEY, false)).also { state = it }
        return s.value
    }

    fun setDark(context: Context, dark: Boolean) {
        prefs(context).edit().putBoolean(KEY, dark).apply()
        val s = state
        if (s == null) state = mutableStateOf(dark) else s.value = dark
    }
}

/** Reads the snapshot state, so toggling the switch recomposes everything that uses the theme. */
@Composable
fun rememberDarkMode(): Boolean = ThemePreference.isDark(LocalContext.current)
