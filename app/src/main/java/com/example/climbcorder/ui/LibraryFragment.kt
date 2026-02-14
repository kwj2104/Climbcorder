package com.example.climbcorder.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.climbcorder.R
import com.example.climbcorder.data.VideoRepository

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

        val videos = VideoRepository.loadVideos(requireContext())

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
}
