package com.example.studyvillage.ui.focus

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.studyvillage.R
import com.example.studyvillage.databinding.FragmentFocusBinding
import java.util.Locale

class FocusFragment : Fragment(R.layout.fragment_focus) {

	private var _binding: FragmentFocusBinding? = null
	private val binding get() = _binding!!

	private val viewModel: FocusViewModel by viewModels()

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		_binding = FragmentFocusBinding.bind(view)

		binding.btnStart.setOnClickListener {
			viewModel.onStartClicked()
		}

		binding.btnPause.setOnClickListener {
			viewModel.onPauseClicked()
		}

		binding.btnReset.setOnClickListener {
			viewModel.onResetClicked()
		}

		binding.btnSetInterval.setOnClickListener {
			val minutesText = binding.etIntervalMinutes.text?.toString().orEmpty().trim()
			val minutes = minutesText.toIntOrNull()
			if (minutes == null || minutes !in 1..180) {
				Toast.makeText(requireContext(), R.string.focus_invalid_interval, Toast.LENGTH_SHORT).show()
				return@setOnClickListener
			}
			viewModel.onSetIntervalMinutes(minutes)
		}

		viewModel.uiState.observe(viewLifecycleOwner) { state ->
			binding.tvTimeRemaining.text = formatMillis(state.remainingMillis)
			binding.btnStart.isEnabled = !state.isRunning
			binding.btnPause.isEnabled = state.isRunning
			if (!binding.etIntervalMinutes.hasFocus()) {
				val currentText = binding.etIntervalMinutes.text?.toString().orEmpty()
				val stateText = state.intervalMinutes.toString()
				if (currentText != stateText) {
					binding.etIntervalMinutes.setText(stateText)
				}
			}
		}
	}

	private fun formatMillis(millis: Long): String {
		val totalSeconds = millis / 1000
		val hours = totalSeconds / 3600
		val minutes = (totalSeconds % 3600) / 60
		val seconds = totalSeconds % 60

		return if (hours > 0) {
			String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
		} else {
			String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}
