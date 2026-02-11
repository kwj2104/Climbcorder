package com.example.climbcorder

import android.graphics.Typeface
import android.os.Bundle
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
