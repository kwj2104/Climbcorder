package com.example.climbcorder

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
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.concurrent.Executors

class HomeFragment : Fragment() {

    private val displayedMonth = Calendar.getInstance()
    private val currentMonth = Calendar.getInstance()
    private lateinit var db: AppDatabase

    private var bluetoothIndicator: LinearLayout? = null
    private var bluetoothReceiver: BroadcastReceiver? = null

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
            populateCalendar(view)
        }

        btnNext.setOnClickListener {
            displayedMonth.add(Calendar.MONTH, 1)
            populateCalendar(view)
        }

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

        // Check if any A2DP device is currently connected
        val connectedDevices = adapter.getProfileConnectionState(BluetoothProfile.A2DP)
        if (connectedDevices == BluetoothProfile.STATE_CONNECTED) {
            bluetoothIndicator?.visibility = View.VISIBLE
        }

        // Listen for connect/disconnect events
        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!hasBluetoothPermission()) return
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val majorClass = device?.bluetoothClass?.majorDeviceClass
                // Accept audio/video devices (0x0400), or if class is unknown (null)
                val isAudioDevice = majorClass == null || majorClass == 0x0400

                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        if (isAudioDevice) {
                            bluetoothIndicator?.visibility = View.VISIBLE
                        }
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        if (isAudioDevice) {
                            // Re-check if any A2DP device is still connected
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
    }

    private fun populateCalendar(view: View) {
        val header = view.findViewById<TextView>(R.id.calendar_header)
        val grid = view.findViewById<GridLayout>(R.id.calendar_grid)
        val btnNext = view.findViewById<ImageButton>(R.id.btn_next_month)

        val month = displayedMonth.get(Calendar.MONTH)
        val year = displayedMonth.get(Calendar.YEAR)

        val monthName = DateFormatSymbols().months[month]
        header.text = "$monthName $year"

        // Disable forward arrow if we're at the current month
        val atCurrentMonth = year == currentMonth.get(Calendar.YEAR)
                && month == currentMonth.get(Calendar.MONTH)
        btnNext.isEnabled = !atCurrentMonth
        btnNext.alpha = if (atCurrentMonth) 0.3f else 1.0f

        // Determine first day offset and days in month
        val cal = displayedMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val todayDay = if (atCurrentMonth) currentMonth.get(Calendar.DAY_OF_MONTH) else -1

        // Query recording days on a background thread
        val startCal = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, 1)
        }

        Executors.newSingleThreadExecutor().execute {
            val recordings = db.recordingDao().getRecordingsInRange(
                startCal.timeInMillis, endCal.timeInMillis
            )
            val recordingDays = recordings.map { rec ->
                Calendar.getInstance().apply { timeInMillis = rec.timestamp }
                    .get(Calendar.DAY_OF_MONTH)
            }.toSet()

            view.post {
                buildGrid(grid, firstDayOfWeek, daysInMonth, todayDay, recordingDays)
            }
        }
    }

    private fun buildGrid(
        grid: GridLayout,
        firstDayOfWeek: Int,
        daysInMonth: Int,
        todayDay: Int,
        recordingDays: Set<Int>
    ) {
        grid.removeAllViews()

        val dayLabels = arrayOf("S", "M", "T", "W", "T", "F", "S")

        // Day-of-week header row
        for (label in dayLabels) {
            val tv = createCellTextView(label)
            tv.setTypeface(null, Typeface.BOLD)
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_grey))
            grid.addView(tv)
        }

        // Blank cells before day 1
        val blanks = firstDayOfWeek - Calendar.SUNDAY
        for (i in 0 until blanks) {
            grid.addView(createCellView("", isToday = false, hasRecording = false))
        }

        // Make cells square after layout
        grid.post {
            val cellWidth = grid.width / 7
            for (i in 0 until grid.childCount) {
                val child = grid.getChildAt(i)
                val params = child.layoutParams as GridLayout.LayoutParams
                params.height = cellWidth
                child.layoutParams = params
            }
        }

        // Day numbers
        for (day in 1..daysInMonth) {
            val cell = createCellView(
                day.toString(),
                isToday = day == todayDay,
                hasRecording = day in recordingDays
            )
            grid.addView(cell)
        }

        // Fill remaining cells so the grid always has 7 rows (1 header + 6 data)
        val totalCells = 7 + blanks + daysInMonth
        val targetCells = 7 * 7
        for (i in totalCells until targetCells) {
            grid.addView(createCellView("", isToday = false, hasRecording = false))
        }
    }

    private fun createCellView(text: String, isToday: Boolean, hasRecording: Boolean): View {
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
            setBackgroundResource(R.drawable.recording_dot)
            visibility = if (hasRecording) View.VISIBLE else View.GONE
        }
        layout.addView(dot)

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
