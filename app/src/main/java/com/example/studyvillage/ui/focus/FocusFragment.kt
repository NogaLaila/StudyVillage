package com.example.studyvillage.ui.focus

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.studyvillage.R
import com.example.studyvillage.databinding.FragmentFocusBinding

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

		binding.btnSkip.setOnClickListener {
			viewModel.onSkipClicked()
		}

		viewModel.uiState.observe(viewLifecycleOwner) { state ->
			binding.tvTimeRemaining.text = formatMillis(state.remainingMillis)
			binding.btnStart.isEnabled = !state.isRunning
			binding.btnPause.isEnabled = state.isRunning
			binding.btnSkip.isEnabled = state.remainingMillis > 0L
		}
	}

	private fun formatMillis(millis: Long): String {
		val totalSeconds = millis / 1000
		val hours = totalSeconds / 3600
		val minutes = (totalSeconds % 3600) / 60
		val seconds = totalSeconds % 60

		return if (hours > 0) {
			String.format("%d:%02d:%02d", hours, minutes, seconds)
		} else {
			String.format("%02d:%02d", minutes, seconds)
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}
