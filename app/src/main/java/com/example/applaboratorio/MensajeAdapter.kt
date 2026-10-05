package com.example.applaboratorio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MensajeAdapter(
    private val listaMensajes: List<Mensaje>,
    private val uidActual: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TIPO_MIO = 1
        private const val TIPO_OTRO = 2
    }

    class MensajeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessageText: TextView = view.findViewById(R.id.tvMessageText)
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
        (holder as MensajeViewHolder).tvMessageText.text = mensaje.texto
    }

    override fun getItemCount(): Int = listaMensajes.size
}