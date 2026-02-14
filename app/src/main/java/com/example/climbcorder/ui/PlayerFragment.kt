package com.example.climbcorder.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.climbcorder.R
class PlayerFragment : Fragment() {

    private var player: ExoPlayer? = null

    companion object {
        private const val ARG_URI = "video_uri"

        fun newInstance(uri: Uri): PlayerFragment {
            return PlayerFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_URI, uri)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_player, container, false)
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val playerView = view.findViewById<PlayerView>(R.id.player_view)
        val btnBack = view.findViewById<ImageButton>(R.id.btn_back)

        @Suppress("DEPRECATION")
        val videoUri = arguments?.getParcelable<Uri>(ARG_URI) ?: return

        player = ExoPlayer.Builder(requireContext()).build().also { exo ->
            playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(videoUri))
            exo.prepare()
            exo.playWhenReady = true
        }

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
    }
}
