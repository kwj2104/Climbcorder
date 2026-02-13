package com.example.climbcorder

import android.content.ContentUris
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class LibraryFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        val emptyState = view.findViewById<TextView>(R.id.empty_state)

        val videos = loadVideos()

        if (videos.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            recyclerView.layoutManager = GridLayoutManager(requireContext(), 4)
            recyclerView.adapter = VideoAdapter(videos) { video ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.container, PlayerFragment.newInstance(video.uri))
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    private fun loadVideos(): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%HelloWorld%")
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        requireContext().contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val duration = cursor.getLong(durationCol)
                val dateAdded = cursor.getLong(dateCol)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                )
                videos.add(VideoItem(uri, duration, dateAdded))
            }
        }
        return videos
    }
}
