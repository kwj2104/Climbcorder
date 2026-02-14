package com.example.climbcorder.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.decode.VideoFrameDecoder
import coil.load
import coil.request.videoFrameMillis
import com.example.climbcorder.R
import com.example.climbcorder.data.VideoItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActivityFeedAdapter(
    private val onClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<ActivityFeedAdapter.ViewHolder>() {

    private val items = mutableListOf<VideoItem>()
    private val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.feed_thumbnail)
        val date: TextView = view.findViewById(R.id.feed_date)
        val duration: TextView = view.findViewById(R.id.feed_duration)
    }

    fun updateItems(newItems: List<VideoItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_recording, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.thumbnail.load(item.uri) {
            crossfade(true)
            decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
            videoFrameMillis(item.duration / 2)
        }

        // dateAdded is in seconds
        holder.date.text = dateFormat.format(Date(item.dateAdded * 1000))

        val totalSeconds = item.duration / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        holder.duration.text = String.format("%02d:%02d", minutes, seconds)

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
