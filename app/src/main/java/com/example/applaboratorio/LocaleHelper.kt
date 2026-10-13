package com.example.applaboratorio

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

// El idioma se aplica a TODA la app con AppCompatDelegate: al cambiarlo, Android recrea
// todas las pantallas abiertas. La elección se guarda en SharedPreferences y se vuelve
// a aplicar cada vez que arranca la app (ver AppLaboratorio).
object LocaleHelper {    //Instancia compartida por toda la app

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    fun getSavedLanguage(context: Context): String {  // por defecto "es" si nunca ha elegido ninguno
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, "es") ?: "es"
    }

    fun aplicarIdiomaGuardado(context: Context) {
        aplicar(getSavedLanguage(context))
    }

    // En Android 13+ el idioma lo guarda el sistema y AppCompat solo puede aplicarlo con
    // una pantalla abierta: las pantallas de entrada lo sincronizan con el guardado.
    fun sincronizar(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val guardado = getSavedLanguage(context)
        val actual = AppCompatDelegate.getApplicationLocales()
        if (actual.isEmpty || actual[0]?.language != guardado) aplicar(guardado)
    }

    fun alternarIdioma(context: Context) {
        val nuevo = if (getSavedLanguage(context) == "es") "en" else "es"
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, nuevo).apply()
        aplicar(nuevo)
    }

    private fun aplicar(idioma: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(idioma))
    }
}
