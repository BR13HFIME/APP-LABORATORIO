package com.example.applaboratorio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MensajeAdapter(
    private val listaMensajes: List<Mensaje>,
    private val uidActual: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TIPO_MIO = 1
        private const val TIPO_OTRO = 2
        private val formatoHora = SimpleDateFormat("HH:mm", Locale.getDefault())
    }

    class MensajeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessageText: TextView = view.findViewById(R.id.tvMessageText)
        val tvMessageTime: TextView = view.findViewById(R.id.tvMessageTime)
        val tvCheck: TextView? = view.findViewById(R.id.tvCheck)   // solo en mis mensajes
    }

    override fun getItemViewType(position: Int): Int {
        val mensaje = listaMensajes[position]
        return if (mensaje.emisorId == uidActual) TIPO_MIO else TIPO_OTRO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if (viewType == TIPO_MIO) {
            R.layout.item_message_mine
        } else {
            R.layout.item_message_other
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return MensajeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val mensaje = listaMensajes[position]
        holder as MensajeViewHolder
        holder.tvMessageText.text = mensaje.texto
        if (mensaje.timestamp > 0) {
            holder.tvMessageTime.text = formatoHora.format(Date(mensaje.timestamp))
            holder.tvMessageTime.visibility = View.VISIBLE
        } else {
            holder.tvMessageTime.visibility = View.GONE
        }

        holder.tvCheck?.let { check ->
            val contexto = check.context
            if (mensaje.leido) {
                check.text = "✓✓"
                check.setTextColor(ContextCompat.getColor(contexto, R.color.check_read))
                check.contentDescription = contexto.getString(R.string.chat_seen)
            } else {
                check.text = "✓"
                check.setTextColor(ContextCompat.getColor(contexto, R.color.text_secondary))
                check.contentDescription = null
            }
        }
    }

    override fun getItemCount(): Int = listaMensajes.size
}