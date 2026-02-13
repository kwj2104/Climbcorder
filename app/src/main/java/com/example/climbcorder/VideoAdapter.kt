package com.example.climbcorder

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.decode.VideoFrameDecoder
import coil.load
import coil.request.videoFrameMillis

data class VideoItem(val uri: Uri, val duration: Long, val dateAdded: Long)

class VideoAdapter(
    private val items: List<VideoItem>,
    private val onClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.thumbnail)
        val duration: TextView = view.findViewById(R.id.duration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video_thumbnail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // Make items square
        val width = holder.itemView.resources.displayMetrics.widthPixels / 4
        holder.itemView.layoutParams.height = width

        holder.thumbnail.load(item.uri) {
            crossfade(true)
            decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
            videoFrameMillis(item.duration / 2)
        }

        val totalSeconds = item.duration / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        holder.duration.text = String.format("%02d:%02d", minutes, seconds)

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
