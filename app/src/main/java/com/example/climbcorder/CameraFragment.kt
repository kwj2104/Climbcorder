package com.example.climbcorder

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var viewFinder: PreviewView
    private lateinit var recordButton: Button
    private lateinit var statusText: TextView
    private lateinit var bluetoothIndicator: LinearLayout

    private var mediaSession: MediaSessionCompat? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var bluetoothConnected = false
    private var recordingViaHeadset = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    updateBluetoothState(true)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    updateBluetoothState(false)
                }
            }
        }
    }

    companion object {
        private const val TAG = "CameraFragment"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewFinder = view.findViewById(R.id.viewFinder)
        recordButton = view.findViewById(R.id.recordButton)
        statusText = view.findViewById(R.id.statusText)
        bluetoothIndicator = view.findViewById(R.id.bluetoothIndicator)

        // Check permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }

        // Set up record button
        recordButton.setOnClickListener {
            recordingViaHeadset = false
            if (recording != null) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupMediaSession()
        registerBluetoothReceiver()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(requireContext(), "ClimbcorderRecord").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                    Log.d(TAG, "onMediaButtonEvent: $mediaButtonEvent")
                    val keyEvent = mediaButtonEvent.getParcelableExtra(
                        Intent.EXTRA_KEY_EVENT,
                        android.view.KeyEvent::class.java
                    )
                    Log.d(TAG, "KeyEvent: keyCode=${keyEvent?.keyCode} action=${keyEvent?.action}")
                    if (keyEvent?.action == android.view.KeyEvent.ACTION_DOWN) {
                        when (keyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            android.view.KeyEvent.KEYCODE_HEADSETHOOK,
                            android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
                            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                toggleRecordingViaHeadset()
                                return true
                            }
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }

                override fun onPlay() {
                    Log.d(TAG, "onPlay")
                    toggleRecordingViaHeadset()
                }

                override fun onPause() {
                    Log.d(TAG, "onPause")
                    toggleRecordingViaHeadset()
                }

                override fun onStop() {
                    Log.d(TAG, "onStop")
                    toggleRecordingViaHeadset()
                }
            })

            // Set flags for media button and transport control handling
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            // Set playback state — STATE_PLAYING with declared actions so the
            // system routes media button events to this session
            val playbackState = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_STOP
                )
                .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)
                .build()
            setPlaybackState(playbackState)
        }
    }

    private fun toggleRecordingViaHeadset() {
        recordingViaHeadset = true
        if (recording != null) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun requestAudioFocus() {
        val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { focus ->
                Log.d(TAG, "Audio focus changed: $focus")
            }
            .build()
        audioFocusRequest = request
        val result = audioManager.requestAudioFocus(request)
        Log.d(TAG, "Audio focus request result: $result")
    }

    private fun abandonAudioFocus() {
        val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    private fun registerBluetoothReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        requireContext().registerReceiver(bluetoothReceiver, filter)
    }

    private fun isBluetoothAudioConnected(): Boolean {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val bluetoothManager =
            requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return false
        return adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED ||
            adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
    }

    private fun updateBluetoothState(connected: Boolean) {
        bluetoothConnected = connected
        if (!isAdded) return

        requireActivity().runOnUiThread {
            if (connected) {
                bluetoothIndicator.visibility = View.VISIBLE
            } else {
                bluetoothIndicator.visibility = View.GONE
                recordingViaHeadset = false
            }
        }
    }

    private fun playSilence() {
        // Play a brief silent clip so Android recognizes this app as the
        // active audio player and routes media button events to our session
        try {
            val mp = MediaPlayer.create(requireContext(), R.raw.silence)
            mp?.setOnCompletionListener { it.release() }
            mp?.start()
            Log.d(TAG, "Playing silent clip to claim media button session")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play silence", e)
        }
    }

    override fun onResume() {
        super.onResume()
        requestAudioFocus()
        mediaSession?.isActive = true
        playSilence()
        Log.d(TAG, "MediaSessionCompat active, audio focus requested")

        val connected = isBluetoothAudioConnected()
        Log.d(TAG, "Bluetooth audio connected: $connected")
        bluetoothConnected = connected
        bluetoothIndicator.visibility = if (connected) View.VISIBLE else View.GONE
    }

    override fun onPause() {
        super.onPause()
        mediaSession?.isActive = false
        abandonAudioFocus()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // Video capture
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind everything before rebinding
                cameraProvider.unbindAll()

                // Bind camera to lifecycle
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )

                statusText.text = "Ready to record"

            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
                statusText.text = "Camera error"
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun startRecording() {
        val videoCapture = videoCapture ?: return

        recordButton.isEnabled = false

        // Create name for video
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/HelloWorld")
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(requireActivity().contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        val startedViaHeadset = recordingViaHeadset

        // Start recording
        recording = videoCapture.output
            .prepareRecording(requireContext(), mediaStoreOutputOptions)
            .apply {
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(requireContext())) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        recordButton.apply {
                            text = "Stop"
                            isEnabled = true
                        }
                        statusText.text = if (startedViaHeadset) {
                            "Recording via headset..."
                        } else {
                            "Recording..."
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video saved: ${recordEvent.outputResults.outputUri}"
                            Toast.makeText(requireContext(), "Video saved!", Toast.LENGTH_SHORT).show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture error: ${recordEvent.error}")
                        }
                        recordButton.apply {
                            text = "Record"
                            isEnabled = true
                        }
                        statusText.text = "Ready to record"
                        recordingViaHeadset = false
                    }
                }
            }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Permissions not granted",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaSession?.release()
        mediaSession = null
        abandonAudioFocus()
        try {
            requireContext().unregisterReceiver(bluetoothReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver was not registered
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
