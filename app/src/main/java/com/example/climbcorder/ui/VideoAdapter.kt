package com.example.climbcorder.ui

import android.graphics.Bitmap
import android.os.Build
import android.util.LruCache
import android.util.Size
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
import java.util.concurrent.Executors

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
    private val thumbExecutor = Executors.newFixedThreadPool(4)
    private val thumbCache = LruCache<Long, Bitmap>(50)

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

        // Load thumbnail using OS-cached MediaStore thumbnails (API 29+)
        // or fall back to Coil VideoFrameDecoder for older devices
        holder.thumbnail.tag = item.mediaStoreId
        val cached = thumbCache.get(item.mediaStoreId)
        if (cached != null) {
            holder.thumbnail.setImageBitmap(cached)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            holder.thumbnail.setImageDrawable(null)
            val id = item.mediaStoreId
            thumbExecutor.execute {
                try {
                    val bitmap = holder.thumbnail.context.contentResolver
                        .loadThumbnail(item.uri, Size(width, width), null)
                    thumbCache.put(id, bitmap)
                    holder.thumbnail.post {
                        if (holder.thumbnail.tag == id) {
                            holder.thumbnail.setImageBitmap(bitmap)
                        }
                    }
                } catch (_: Exception) {}
            }
        } else {
            holder.thumbnail.load(item.uri) {
                decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
                videoFrameMillis(item.duration / 2)
            }
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
