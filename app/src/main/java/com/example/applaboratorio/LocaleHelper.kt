package com.example.applaboratorio

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

object LocaleHelper {    //Instancia compartida por toda la app

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    fun setLocale(context: Context, languageCode: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()

        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = context.resources.configuration
        config.setLocale(locale) // "a partir de ahora, usa este idioma para resolver los recursos @string"
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    fun getSavedLanguage(context: Context): String {  // función para leer qué idioma había elegido el usuario anteriormente (por defecto "es" si nunca ha elegido ninguno)
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, "es") ?: "es"
    }
}

