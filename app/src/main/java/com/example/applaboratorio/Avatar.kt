package com.example.applaboratorio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import java.io.ByteArrayOutputStream

// Las fotos de perfil se guardan en Firestore como un JPEG pequeño en Base64.
// Así no hace falta Firebase Storage (que ya exige el plan de pago).
object Avatar {

    private const val LADO_FOTO = 256
    private val cache = LruCache<String, Bitmap>(40)

    /**
     * Muestra la foto si existe; si no, la inicial del nombre.
     * @param colorear true para pintar el círculo con el color propio del contacto
     */
    fun mostrar(
        tvInicial: TextView,
        ivFoto: ImageView,
        nombre: String,
        foto: String,
        colorear: Boolean = true
    ) {
        tvInicial.text = nombre.take(1).uppercase().ifEmpty { "U" }
        if (colorear) {
            tvInicial.background?.mutate()?.setTint(AvatarColores.para(nombre))
        }

        val bitmap = decodificar(foto)
        if (bitmap != null) {
            ivFoto.setImageBitmap(bitmap)
            ivFoto.visibility = View.VISIBLE
        } else {
            ivFoto.setImageDrawable(null)
            ivFoto.visibility = View.GONE
        }
    }

    fun decodificar(foto: String): Bitmap? {
        if (foto.isBlank()) return null
        val clave = foto.hashCode().toString() + foto.length
        cache.get(clave)?.let { return it }
        return try {
            val bytes = Base64.decode(foto, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { cache.put(clave, it) }
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** Lee la imagen elegida, la recorta en cuadrado de 256 px y la devuelve en Base64. */
    fun comprimir(context: Context, uri: Uri): String? {
        return try {
            // Primero solo medimos, para no cargar fotos enormes de la cámara en memoria
            val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, medidas)
            }
            var escala = 1
            while (medidas.outWidth / (escala * 2) >= LADO_FOTO &&
                medidas.outHeight / (escala * 2) >= LADO_FOTO
            ) {
                escala *= 2
            }

            val opciones = BitmapFactory.Options().apply { inSampleSize = escala }
            val original = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opciones)
            } ?: return null

            val lado = minOf(original.width, original.height)
            val recorte = Bitmap.createBitmap(
                original,
                (original.width - lado) / 2,
                (original.height - lado) / 2,
                lado,
                lado
            )
            val escalada = Bitmap.createScaledBitmap(recorte, LADO_FOTO, LADO_FOTO, true)

            val salida = ByteArrayOutputStream()
            escalada.compress(Bitmap.CompressFormat.JPEG, 80, salida)
            Base64.encodeToString(salida.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}
