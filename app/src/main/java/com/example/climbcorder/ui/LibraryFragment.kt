package com.example.climbcorder.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.climbcorder.R
import com.example.climbcorder.data.AppDatabase
import com.example.climbcorder.data.HiddenVideo
import com.example.climbcorder.data.VideoItem
import com.example.climbcorder.data.VideoRepository
import java.util.concurrent.Executors

class LibraryFragment : Fragment() {

    private val executor = Executors.newSingleThreadExecutor()
    private var isSelectMode = false
    private var adapter: VideoAdapter? = null

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        val ctx = requireContext().applicationContext
        executor.execute {
            var count = 0
            for (uri in uris) {
                if (VideoRepository.importVideo(ctx, uri) != null) count++
            }
            val msg = if (count > 0) "Imported $count video(s)" else "Import failed"
            view?.post {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                refreshGrid()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val header = view.findViewById<LinearLayout>(R.id.header)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)

        // Set RecyclerView top padding to match header height (including status bar inset)
        header.post {
            recyclerView.setPadding(
                recyclerView.paddingLeft, header.height,
                recyclerView.paddingRight, recyclerView.paddingBottom
            )
        }

        view.findViewById<TextView>(R.id.btn_import).setOnClickListener {
            importLauncher.launch("video/*")
        }

        view.findViewById<TextView>(R.id.btn_select).setOnClickListener {
            toggleSelectMode()
        }

        view.findViewById<TextView>(R.id.btn_delete).setOnClickListener {
            val selected = adapter?.getSelectedItems() ?: return@setOnClickListener
            if (selected.isEmpty()) return@setOnClickListener
            showBulkDeleteDialog(selected)
        }

        refreshGrid()
    }

    private fun toggleSelectMode() {
        val view = view ?: return
        isSelectMode = !isSelectMode
        val btnSelect = view.findViewById<TextView>(R.id.btn_select)
        val deleteBar = view.findViewById<LinearLayout>(R.id.delete_bar)
        val btnDelete = view.findViewById<TextView>(R.id.btn_delete)

        if (isSelectMode) {
            btnSelect.text = "Cancel"
            deleteBar.visibility = View.VISIBLE
            btnDelete.text = "Delete"
            adapter?.selectMode = true
        } else {
            btnSelect.text = "Select"
            deleteBar.visibility = View.GONE
            adapter?.selectMode = false
        }
    }

    private fun exitSelectMode() {
        if (isSelectMode) toggleSelectMode()
    }

    private fun updateDeleteCount(count: Int) {
        val btnDelete = view?.findViewById<TextView>(R.id.btn_delete) ?: return
        btnDelete.text = if (count > 0) "Delete ($count)" else "Delete"
    }

    private fun refreshGrid() {
        val view = view ?: return
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        val emptyState = view.findViewById<TextView>(R.id.empty_state)
        val ctx = requireContext()

        executor.execute {
            val hiddenIds = AppDatabase.getInstance(ctx).hiddenVideoDao().getAllHiddenIds().toSet()
            val videos = VideoRepository.loadVideos(ctx, excludeIds = hiddenIds)

            recyclerView.post {
                if (!isAdded) return@post
                if (videos.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE
                    recyclerView.layoutManager = GridLayoutManager(requireContext(), 4)
                    val newAdapter = VideoAdapter(
                        videos,
                        onClick = { video ->
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.container, PlayerFragment.newInstance(video.uri))
                                .addToBackStack(null)
                                .commit()
                        },
                        onLongClick = { video ->
                            showVideoOptionsDialog(video)
                        },
                        onSelectionChanged = { count ->
                            updateDeleteCount(count)
                        }
                    )
                    recyclerView.adapter = newAdapter
                    adapter = newAdapter
                    if (isSelectMode) newAdapter.selectMode = true
                }
            }
        }
    }

    private fun showVideoOptionsDialog(video: VideoItem) {
        val options = arrayOf("Remove from library", "Delete from device")
        AlertDialog.Builder(requireContext())
            .setTitle("Video options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> hideVideo(video)
                    1 -> confirmDeleteVideo(video)
                }
            }
            .show()
    }

    private fun showBulkDeleteDialog(selected: List<VideoItem>) {
        val options = arrayOf("Remove from library", "Delete from device")
        AlertDialog.Builder(requireContext())
            .setTitle("Delete ${selected.size} video(s)")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> bulkHideVideos(selected)
                    1 -> confirmBulkDelete(selected)
                }
            }
            .show()
    }

    private fun bulkHideVideos(videos: List<VideoItem>) {
        val ctx = requireContext().applicationContext
        executor.execute {
            val dao = AppDatabase.getInstance(ctx).hiddenVideoDao()
            for (video in videos) {
                dao.insert(HiddenVideo(mediaStoreId = video.mediaStoreId))
            }
            view?.post {
                exitSelectMode()
                refreshGrid()
            }
        }
    }

    private fun confirmBulkDelete(videos: List<VideoItem>) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete ${videos.size} video(s)")
            .setMessage("This will permanently delete the selected videos from your device.")
            .setPositiveButton("Delete") { _, _ ->
                val ctx = requireContext().applicationContext
                executor.execute {
                    for (video in videos) {
                        VideoRepository.deleteVideo(ctx, video.uri)
                    }
                    view?.post {
                        Toast.makeText(requireContext(), "${videos.size} video(s) deleted", Toast.LENGTH_SHORT).show()
                        exitSelectMode()
                        refreshGrid()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun hideVideo(video: VideoItem) {
        val ctx = requireContext().applicationContext
        executor.execute {
            AppDatabase.getInstance(ctx).hiddenVideoDao()
                .insert(HiddenVideo(mediaStoreId = video.mediaStoreId))
            view?.post { refreshGrid() }
        }
    }

    private fun confirmDeleteVideo(video: VideoItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete video")
            .setMessage("This will permanently delete the video from your device.")
            .setPositiveButton("Delete") { _, _ ->
                val ctx = requireContext().applicationContext
                executor.execute {
                    VideoRepository.deleteVideo(ctx, video.uri)
                    view?.post {
                        Toast.makeText(requireContext(), "Video deleted", Toast.LENGTH_SHORT).show()
                        refreshGrid()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
