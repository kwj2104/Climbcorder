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

class VideoAdapter(
    private val items: List<VideoItem>,
    private val onClick: (VideoItem) -> Unit,
    private val onLongClick: (VideoItem) -> Unit = {},
    private val onSelectionChanged: (Int) -> Unit = {}
) : RecyclerView.Adapter<VideoAdapter.ViewHolder>() {

    var selectMode: Boolean = false
        set(value) {
            field = value
            if (!value) selectedIds.clear()
            notifyDataSetChanged()
        }

    private val selectedIds = mutableSetOf<Long>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.thumbnail)
        val duration: TextView = view.findViewById(R.id.duration)
        val selectOverlay: View = view.findViewById(R.id.select_overlay)
        val checkIcon: ImageView = view.findViewById(R.id.check_icon)
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

        val isSelected = selectedIds.contains(item.mediaStoreId)
        holder.selectOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            if (selectMode) {
                toggleSelection(item.mediaStoreId)
                notifyItemChanged(position)
            } else {
                onClick(item)
            }
        }
        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }

    override fun getItemCount() = items.size

    private fun toggleSelection(id: Long) {
        if (!selectedIds.remove(id)) selectedIds.add(id)
        onSelectionChanged(selectedIds.size)
    }

    fun clearSelection() {
        selectedIds.clear()
        onSelectionChanged(0)
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<VideoItem> =
        items.filter { it.mediaStoreId in selectedIds }
}
