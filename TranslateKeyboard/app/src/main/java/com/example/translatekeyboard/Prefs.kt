package com.example.translatekeyboard

import android.content.Context
import android.content.SharedPreferences

/**
 * Thin wrapper around SharedPreferences for the settings both the
 * Activity (writer) and the IME service (reader) need to share.
 */
object Prefs {
    private const val PREFS_NAME = "translate_keyboard_prefs"
    private const val KEY_API_KEY = "groq_api_key"
    private const val KEY_SOURCE_LANG = "source_lang"
    private const val KEY_TARGET_LANG = "target_lang"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getApiKey(context: Context): String =
        prefs(context).getString(KEY_API_KEY, "") ?: ""

    fun setApiKey(context: Context, value: String) {
        prefs(context).edit().putString(KEY_API_KEY, value).apply()
    }

    fun getSourceLang(context: Context): String =
        prefs(context).getString(KEY_SOURCE_LANG, "English") ?: "English"

    fun getTargetLang(context: Context): String =
        prefs(context).getString(KEY_TARGET_LANG, "Spanish") ?: "Spanish"

    fun setLanguages(context: Context, source: String, target: String) {
        prefs(context).edit()
            .putString(KEY_SOURCE_LANG, source.ifBlank { "English" })
            .putString(KEY_TARGET_LANG, target.ifBlank { "Spanish" })
            .apply()
    }

    fun swapLanguages(context: Context) {
        val source = getSourceLang(context)
        val target = getTargetLang(context)
        setLanguages(context, target, source)
    }
}
