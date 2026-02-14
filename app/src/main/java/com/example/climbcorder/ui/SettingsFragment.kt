package com.example.climbcorder.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.Context
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.climbcorder.R

class SettingsFragment : Fragment() {

    private lateinit var tvAwakeValue: TextView

    private val displayMap = mapOf(
        30 to "30s",
        60 to "60s",
        120 to "2m",
        300 to "5m",
        600 to "10m"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvAwakeValue = view.findViewById(R.id.tv_awake_value)
        view.findViewById<View>(R.id.row_keep_awake).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, AwakePickerFragment())
                .addToBackStack(null)
                .commit()
        }
        updateDisplayedValue()
    }

    override fun onResume() {
        super.onResume()
        updateDisplayedValue()
    }

    private fun updateDisplayedValue() {
        val prefs = requireContext().getSharedPreferences("climbcorder_prefs", Context.MODE_PRIVATE)
        val seconds = prefs.getInt("keep_awake_seconds", 30)
        tvAwakeValue.text = "${displayMap[seconds] ?: "${seconds}s"} >"
    }
}
