package com.example.climbcorder.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.climbcorder.R
import com.example.climbcorder.data.AppDatabase
import com.example.climbcorder.data.VideoItem
import com.example.climbcorder.data.VideoRepository
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.concurrent.Executors

class HomeFragment : Fragment() {

    private val displayedMonth = Calendar.getInstance()
    private val currentMonth = Calendar.getInstance()
    private lateinit var db: AppDatabase

    private var bluetoothIndicator: LinearLayout? = null
    private var bluetoothReceiver: BroadcastReceiver? = null

    private var selectedDay: Int? = null
    private var allMonthVideos: List<VideoItem> = emptyList()
    private var feedAdapter: ActivityFeedAdapter? = null

    private val heatmapColors = intArrayOf(
        R.color.heatmap_1,
        R.color.heatmap_2,
        R.color.heatmap_3,
        R.color.heatmap_4,
        R.color.heatmap_5
    )

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            view?.let { setupBluetoothIndicator(it) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getInstance(requireContext())

        val btnPrev = view.findViewById<ImageButton>(R.id.btn_prev_month)
        val btnNext = view.findViewById<ImageButton>(R.id.btn_next_month)

        btnPrev.setOnClickListener {
            displayedMonth.add(Calendar.MONTH, -1)
            selectedDay = null
            populateCalendar(view)
        }

        btnNext.setOnClickListener {
            displayedMonth.add(Calendar.MONTH, 1)
            selectedDay = null
            populateCalendar(view)
        }

        // Setup activity feed RecyclerView
        val feedRecycler = view.findViewById<RecyclerView>(R.id.feed_recycler)
        feedAdapter = ActivityFeedAdapter { video ->
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, PlayerFragment.newInstance(video.uri))
                .addToBackStack(null)
                .commit()
        }
        feedRecycler.layoutManager = LinearLayoutManager(requireContext())
        feedRecycler.adapter = feedAdapter

        populateCalendar(view)

        setupBluetoothIndicator(view)
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                requireContext(), android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun setupBluetoothIndicator(view: View) {
        bluetoothIndicator = view.findViewById(R.id.bluetooth_indicator)

        if (!hasBluetoothPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bluetoothPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
            return
        }

        val bluetoothManager =
            requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return

        val connectedDevices = adapter.getProfileConnectionState(BluetoothProfile.A2DP)
        if (connectedDevices == BluetoothProfile.STATE_CONNECTED) {
            bluetoothIndicator?.visibility = View.VISIBLE
        }

        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!hasBluetoothPermission()) return
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val majorClass = device?.bluetoothClass?.majorDeviceClass
                val isAudioDevice = majorClass == null || majorClass == 0x0400

                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        if (isAudioDevice) {
                            bluetoothIndicator?.visibility = View.VISIBLE
                        }
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        if (isAudioDevice) {
                            val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                            val stillConnected = mgr?.adapter?.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
                            bluetoothIndicator?.visibility = if (stillConnected) View.VISIBLE else View.GONE
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        requireContext().registerReceiver(bluetoothReceiver, filter)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bluetoothReceiver?.let {
            requireContext().unregisterReceiver(it)
            bluetoothReceiver = null
        }
        bluetoothIndicator = null
        feedAdapter = null
    }

    private fun populateCalendar(view: View) {
        val header = view.findViewById<TextView>(R.id.calendar_header)
        val grid = view.findViewById<GridLayout>(R.id.calendar_grid)
        val btnNext = view.findViewById<ImageButton>(R.id.btn_next_month)

        val month = displayedMonth.get(Calendar.MONTH)
        val year = displayedMonth.get(Calendar.YEAR)

        val monthName = DateFormatSymbols().months[month]
        header.text = "$monthName $year"

        val atCurrentMonth = year == currentMonth.get(Calendar.YEAR)
                && month == currentMonth.get(Calendar.MONTH)
        btnNext.isEnabled = !atCurrentMonth
        btnNext.alpha = if (atCurrentMonth) 0.3f else 1.0f

        val cal = displayedMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val todayDay = if (atCurrentMonth) currentMonth.get(Calendar.DAY_OF_MONTH) else -1

        val startCal = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, 1)
        }

        val startMillis = startCal.timeInMillis
        val endMillis = endCal.timeInMillis

        Executors.newSingleThreadExecutor().execute {
            val recordings = db.recordingDao().getRecordingsInRange(startMillis, endMillis)
            val recordingDays = recordings.map { rec ->
                Calendar.getInstance().apply { timeInMillis = rec.timestamp }
                    .get(Calendar.DAY_OF_MONTH)
            }.toSet()

            // Load videos from MediaStore for this month
            val ctx = context ?: return@execute
            val monthVideos = VideoRepository.loadVideosInRange(ctx, startMillis, endMillis)

            // Compute duration by day (dateAdded is in seconds)
            val durationByDay = mutableMapOf<Int, Long>()
            for (video in monthVideos) {
                val videoCal = Calendar.getInstance().apply { timeInMillis = video.dateAdded * 1000 }
                val day = videoCal.get(Calendar.DAY_OF_MONTH)
                durationByDay[day] = (durationByDay[day] ?: 0L) + video.duration
            }

            // Compute stats
            val sessionCount = recordingDays.size
            val totalDurationMs = monthVideos.sumOf { it.duration }

            // Compute streak from all timestamps
            val allTimestamps = db.recordingDao().getAllTimestampsDesc()
            val streak = calculateStreak(allTimestamps)

            view.post {
                if (!isAdded) return@post
                allMonthVideos = monthVideos
                buildGrid(grid, firstDayOfWeek, daysInMonth, todayDay, recordingDays, durationByDay, view)
                updateStats(view, sessionCount, totalDurationMs, streak)
                updateActivityFeed(view)
            }
        }
    }

    private fun calculateStreak(timestampsDesc: List<Long>): Int {
        if (timestampsDesc.isEmpty()) return 0

        val days = timestampsDesc.map { ts ->
            Calendar.getInstance().apply {
                timeInMillis = ts
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }.distinct().sorted().reversed()

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val oneDayMs = 24 * 60 * 60 * 1000L

        // Streak must start at today or yesterday
        val firstDay = days[0]
        if (firstDay != today && firstDay != today - oneDayMs) return 0

        var streak = 1
        for (i in 1 until days.size) {
            if (days[i] == days[i - 1] - oneDayMs) {
                streak++
            } else {
                break
            }
        }
        return streak
    }

    private fun updateStats(view: View, sessions: Int, totalDurationMs: Long, streak: Int) {
        view.findViewById<TextView>(R.id.stat_sessions).text = sessions.toString()
        view.findViewById<TextView>(R.id.stat_streak).text = streak.toString()

        val totalSeconds = totalDurationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        view.findViewById<TextView>(R.id.stat_rec_time).text =
            String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateActivityFeed(view: View) {
        val feedHeader = view.findViewById<TextView>(R.id.feed_header)
        val feedRecycler = view.findViewById<RecyclerView>(R.id.feed_recycler)
        val feedEmpty = view.findViewById<TextView>(R.id.feed_empty)

        val videosToShow = if (selectedDay != null) {
            val month = displayedMonth.get(Calendar.MONTH)
            val year = displayedMonth.get(Calendar.YEAR)
            val monthName = DateFormatSymbols().months[month]
            feedHeader.text = "Recordings on $monthName $selectedDay"

            allMonthVideos.filter { video ->
                val videoCal = Calendar.getInstance().apply { timeInMillis = video.dateAdded * 1000 }
                videoCal.get(Calendar.DAY_OF_MONTH) == selectedDay
            }
        } else {
            feedHeader.text = "Recent Recordings"
            allMonthVideos.take(3)
        }

        feedAdapter?.updateItems(videosToShow)

        if (videosToShow.isEmpty()) {
            feedRecycler.visibility = View.GONE
            feedEmpty.visibility = View.VISIBLE
        } else {
            feedRecycler.visibility = View.VISIBLE
            feedEmpty.visibility = View.GONE
        }
    }

    private fun buildGrid(
        grid: GridLayout,
        firstDayOfWeek: Int,
        daysInMonth: Int,
        todayDay: Int,
        recordingDays: Set<Int>,
        durationByDay: Map<Int, Long>,
        rootView: View
    ) {
        grid.removeAllViews()

        val dayLabels = arrayOf("S", "M", "T", "W", "T", "F", "S")

        // Compute max duration for heatmap scaling
        val maxDuration = durationByDay.values.maxOrNull() ?: 0L

        for (label in dayLabels) {
            val tv = createCellTextView(label)
            tv.setTypeface(null, Typeface.BOLD)
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_grey))
            grid.addView(tv)
        }

        val blanks = firstDayOfWeek - Calendar.SUNDAY
        for (i in 0 until blanks) {
            grid.addView(createCellView("", isToday = false, hasRecording = false, heatmapLevel = 0, day = 0, rootView = rootView))
        }

        grid.post {
            val cellWidth = grid.width / 7
            for (i in 0 until grid.childCount) {
                val child = grid.getChildAt(i)
                val params = child.layoutParams as GridLayout.LayoutParams
                params.height = cellWidth
                child.layoutParams = params
            }
        }

        for (day in 1..daysInMonth) {
            val hasRecording = day in recordingDays
            val duration = durationByDay[day] ?: 0L
            val heatmapLevel = if (hasRecording && maxDuration > 0 && duration > 0) {
                val ratio = duration.toFloat() / maxDuration
                when {
                    ratio <= 0.2f -> 1
                    ratio <= 0.4f -> 2
                    ratio <= 0.6f -> 3
                    ratio <= 0.8f -> 4
                    else -> 5
                }
            } else if (hasRecording) {
                // Has Room recording but no MediaStore video — show level 1
                1
            } else {
                0
            }

            val cell = createCellView(
                day.toString(),
                isToday = day == todayDay,
                hasRecording = hasRecording,
                heatmapLevel = heatmapLevel,
                day = day,
                rootView = rootView
            )
            grid.addView(cell)
        }

        val totalCells = 7 + blanks + daysInMonth
        val targetCells = 7 * 7
        for (i in totalCells until targetCells) {
            grid.addView(createCellView("", isToday = false, hasRecording = false, heatmapLevel = 0, day = 0, rootView = rootView))
        }
    }

    private fun createCellView(
        text: String,
        isToday: Boolean,
        hasRecording: Boolean,
        heatmapLevel: Int,
        day: Int,
        rootView: View
    ): View {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
        }

        val tv = TextView(ctx).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            if (isToday) {
                val circleSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 32f, resources.displayMetrics
                ).toInt()
                layoutParams = LinearLayout.LayoutParams(circleSize, circleSize).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                setBackgroundResource(R.drawable.today_circle)
                setTextColor(ContextCompat.getColor(ctx, R.color.white))
                setTypeface(null, Typeface.BOLD)
            } else {
                setTextColor(ContextCompat.getColor(ctx, R.color.dark_grey))
            }
        }
        layout.addView(tv)

        val dot = View(ctx).apply {
            val dotSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 6f, resources.displayMetrics
            ).toInt()
            layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics
                ).toInt()
            }
            if (hasRecording && heatmapLevel > 0) {
                val colorRes = heatmapColors[heatmapLevel - 1]
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(ctx, colorRes))
                    setSize(dotSize, dotSize)
                }
                background = drawable
            } else {
                setBackgroundResource(R.drawable.recording_dot)
            }
            visibility = if (hasRecording) View.VISIBLE else View.GONE
        }
        layout.addView(dot)

        // Make days with recordings clickable
        if (hasRecording && day > 0) {
            layout.setOnClickListener {
                selectedDay = if (selectedDay == day) null else day
                updateActivityFeed(rootView)
            }
            layout.isClickable = true
            layout.isFocusable = true
        }

        return layout
    }

    private fun createCellTextView(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
        }
    }
}
