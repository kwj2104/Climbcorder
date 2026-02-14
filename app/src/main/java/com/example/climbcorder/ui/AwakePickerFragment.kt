package com.example.climbcorder.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.Context
import android.widget.RadioGroup
import androidx.fragment.app.Fragment
import com.example.climbcorder.R

class AwakePickerFragment : Fragment() {

    private val radioIdToSeconds = mapOf(
        R.id.radio_30s to 30,
        R.id.radio_60s to 60,
        R.id.radio_2m to 120,
        R.id.radio_5m to 300,
        R.id.radio_10m to 600
    )

    private val secondsToRadioId = radioIdToSeconds.entries.associate { (k, v) -> v to k }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_awake_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("climbcorder_prefs", Context.MODE_PRIVATE)
        val currentSeconds = prefs.getInt("keep_awake_seconds", 30)

        val radioGroup = view.findViewById<RadioGroup>(R.id.radio_group_awake)
        secondsToRadioId[currentSeconds]?.let { radioGroup.check(it) }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val seconds = radioIdToSeconds[checkedId] ?: return@setOnCheckedChangeListener
            prefs.edit().putInt("keep_awake_seconds", seconds).apply()
            parentFragmentManager.popBackStack()
        }

        view.findViewById<View>(R.id.btn_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }
}
