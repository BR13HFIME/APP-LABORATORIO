package com.example.applaboratorio

import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

// Con targetSdk 35+ Android dibuja la app de borde a borde, así que cada pantalla
// reserva espacio para la barra de estado, la de navegación y el teclado.

fun ComponentActivity.activarBordeABorde() {
    // Iconos claros en la barra de estado porque las cabeceras usan el degradado de marca
    enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
}

fun View.dejarEspacioArriba() {
    val paddingInicial = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { vista, insets ->
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        vista.updatePadding(top = paddingInicial + barras.top)
        insets
    }
}

fun View.dejarEspacioAbajo() {
    val paddingInicial = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { vista, insets ->
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
        vista.updatePadding(bottom = paddingInicial + barras.bottom)
        insets
    }
}
