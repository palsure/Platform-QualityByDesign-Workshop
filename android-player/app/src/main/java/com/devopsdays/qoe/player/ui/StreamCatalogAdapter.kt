package com.devopsdays.qoe.player.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.devopsdays.qoe.player.R
import com.devopsdays.qoe.player.data.StreamVideo

class StreamCatalogAdapter(
    private val items: List<StreamVideo>,
    private val onClick: (StreamVideo) -> Unit,
) : RecyclerView.Adapter<StreamCatalogAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_stream, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.stream_title)
        private val subtitle: TextView = itemView.findViewById(R.id.stream_subtitle)

        fun bind(video: StreamVideo, onClick: (StreamVideo) -> Unit) {
            title.text = video.title
            subtitle.text = video.subtitle
            itemView.contentDescription = video.title
            itemView.setOnClickListener { onClick(video) }
        }
    }
}
