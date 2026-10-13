package com.example.applaboratorio

import android.graphics.Typeface
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Un contacto junto con el resumen de su conversación con nosotros
data class Conversacion(
    val usuario: Usuario,
    val ultimoMensaje: String = "",
    val ultimoTimestamp: Long = 0,
    val ultimoEmisor: String = "",
    val ultimoLeido: Boolean = true
)

class UsuarioAdapter(
    private val uidActual: String,
    private val onUsuarioClick: (Usuario) -> Unit
) : RecyclerView.Adapter<UsuarioAdapter.UsuarioViewHolder>() {

    private var conversaciones: List<Conversacion> = emptyList()

    class UsuarioViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAvatar: TextView = view.findViewById(R.id.tvAvatar)
        val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
        val tvNombre: TextView = view.findViewById(R.id.tvNombre)
        val tvUltimoMensaje: TextView = view.findViewById(R.id.tvUltimoMensaje)
        val tvHora: TextView = view.findViewById(R.id.tvHora)
        val vNoLeido: View = view.findViewById(R.id.vNoLeido)
        val iconoChat: View = view.findViewById(R.id.iconoChat)
    }

    fun actualizar(nuevas: List<Conversacion>) {
        conversaciones = nuevas
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsuarioViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_usuario, parent, false)
        return UsuarioViewHolder(view)
    }

    override fun onBindViewHolder(holder: UsuarioViewHolder, position: Int) {
        val conversacion = conversaciones[position]
        val usuario = conversacion.usuario
        val contexto = holder.itemView.context

        holder.tvNombre.text = usuario.nombreVisible
        Avatar.mostrar(holder.tvAvatar, holder.ivAvatar, usuario.nombreVisible, usuario.foto)

        val yaHablaron = conversacion.ultimoTimestamp > 0
        val esMio = conversacion.ultimoEmisor == uidActual
        val sinLeer = yaHablaron && !esMio && !conversacion.ultimoLeido

        holder.tvUltimoMensaje.text = when {
            !yaHablaron -> contexto.getString(R.string.user_item_hint)
            esMio -> contexto.getString(R.string.you_prefix, conversacion.ultimoMensaje)
            else -> conversacion.ultimoMensaje
        }
        // Los chats con mensajes nuevos resaltan en negrita y con el color de la marca
        holder.tvUltimoMensaje.setTypeface(null, if (sinLeer) Typeface.BOLD else Typeface.NORMAL)
        holder.tvUltimoMensaje.setTextColor(
            ContextCompat.getColor(contexto, if (sinLeer) R.color.text_primary else R.color.text_secondary)
        )
        holder.tvHora.setTextColor(
            ContextCompat.getColor(contexto, if (sinLeer) R.color.brand_primary else R.color.text_secondary)
        )

        holder.tvHora.text = if (yaHablaron) formatearHora(conversacion.ultimoTimestamp, contexto.getString(R.string.yesterday)) else ""
        holder.tvHora.visibility = if (yaHablaron) View.VISIBLE else View.GONE
        holder.vNoLeido.visibility = if (sinLeer) View.VISIBLE else View.GONE
        holder.iconoChat.visibility = if (yaHablaron) View.GONE else View.VISIBLE

        holder.itemView.setOnClickListener {
            onUsuarioClick(usuario)
        }
    }

    override fun getItemCount(): Int = conversaciones.size

    // Hoy: "14:05" · ayer: "Ayer" · antes: "12/10"
    private fun formatearHora(timestamp: Long, ayer: String): String = when {
        DateUtils.isToday(timestamp) -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        DateUtils.isToday(timestamp + DateUtils.DAY_IN_MILLIS) -> ayer
        else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(timestamp))
    }
}
