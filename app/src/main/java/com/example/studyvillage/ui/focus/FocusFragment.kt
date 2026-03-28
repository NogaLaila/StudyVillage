package com.example.studyvillage.ui.focus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.studyvillage.R
import com.example.studyvillage.data.shop.local.DataBaseProvider
import com.example.studyvillage.data.user.UserRepository
import com.example.studyvillage.data.user.remote.UserRemote
import com.example.studyvillage.databinding.FragmentFocusBinding
import com.example.studyvillage.util.UserSession
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

class FocusFragment : Fragment(R.layout.fragment_focus) {

    private var _binding: FragmentFocusBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: FocusViewModel
    private var rewardDialog: AlertDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFocusBinding.bind(view)

        val db = DataBaseProvider.get(requireContext())
        val userRepo = UserRepository(db.userDao(), UserRemote())
        val factory = FocusViewModelFactory(
            userRepo = userRepo,
            uid = UserSession.currentUid,
            email = UserSession.currentEmail
        )
        viewModel = ViewModelProvider(this, factory)[FocusViewModel::class.java]

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
            binding.tvCoins.text = state.coins.toString()
            binding.btnStart.isEnabled = !state.isRunning
            binding.btnPause.isEnabled = state.isRunning

            state.completedRewardCoins?.let { earned ->
                showRewardDialog(earned)
            }

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

    private fun showRewardDialog(earned: Int) {
        if (!isAdded) return
        if (rewardDialog?.isShowing == true) return

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_focus_completion, null)
        dialogView.findViewById<TextView>(R.id.tvRewardCoins).text = earned.toString()

        rewardDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton(R.string.focus_reward_close) { _, _
                -> viewModel.onRewardMessageShown() }
            .setOnDismissListener {
                viewModel.onRewardMessageShown()
                rewardDialog = null
            }
            .show()
    }

    override fun onDestroyView() {
        rewardDialog?.dismiss()
        rewardDialog = null
        super.onDestroyView()
        _binding = null
    }
}
