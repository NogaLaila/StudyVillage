package com.example.studyvillage.ui.focus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
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
    private var intervalDialog: AlertDialog? = null

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

        binding.txtTitle.text = getString(R.string.focus_title)
        binding.txtCoins.text = "0"
        binding.tvFocusQuote.text = getString(R.string.focus_quote_loading)

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
            val selectedMinutes = viewModel.uiState.value?.intervalMinutes ?: DEFAULT_INTERVAL_MINUTES
            showIntervalPickerDialog(selectedMinutes)
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.tvTimeRemaining.text = formatMillis(state.remainingMillis)
            binding.tvCoins.text = state.coins.toString()
            binding.txtCoins.text = state.coins.toString()
            binding.tvIntervalValue.text = state.intervalMinutes.toString()
            binding.btnStart.isEnabled = !state.isRunning
            binding.btnPause.isEnabled = state.isRunning
            binding.tvFocusQuote.text = if (state.quoteText.isNullOrBlank()) {
                getString(R.string.focus_quote_fallback)
            } else {
                getString(
                    R.string.focus_quote_format,
                    state.quoteText,
                    state.quoteAuthor ?: getString(R.string.focus_quote_unknown_author)
                )
            }

            state.completedRewardCoins?.let { earned ->
                showRewardDialog(earned)
            }

        }
    }

    private fun showIntervalPickerDialog(initialMinutes: Int) {
        if (!isAdded) return
        if (intervalDialog?.isShowing == true) return

        var selectedMinutes = initialMinutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_focus_picker, null)

        val minutesInsideClock = dialogView.findViewById<TextView>(R.id.tvPickerMinutes)
        val caption = dialogView.findViewById<TextView>(R.id.tvPickerCaption)
        val seekBar = dialogView.findViewById<SeekBar>(R.id.seekInterval)

        fun render(minutes: Int) {
            minutesInsideClock.text = minutes.toString()
            caption.text = getString(R.string.focus_picker_caption, minutes)
        }

        seekBar.min = MIN_INTERVAL_MINUTES
        seekBar.max = MAX_INTERVAL_MINUTES
        seekBar.progress = selectedMinutes
        render(selectedMinutes)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedMinutes = progress.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
                render(selectedMinutes)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        intervalDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton(R.string.focus_picker_apply) { _, _ ->
                viewModel.onSetIntervalMinutes(selectedMinutes)
            }
            .setNegativeButton(R.string.focus_picker_cancel, null)
            .setOnDismissListener {
                intervalDialog = null
            }
            .show()
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
        dialogView.findViewById<View>(R.id.btnRewardClose).setOnClickListener {
            rewardDialog?.dismiss()
        }

        rewardDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .setOnDismissListener {
                viewModel.onRewardMessageShown()
                rewardDialog = null
            }
            .show()

        rewardDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override fun onDestroyView() {
        intervalDialog?.dismiss()
        intervalDialog = null
        rewardDialog?.dismiss()
        rewardDialog = null
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val DEFAULT_INTERVAL_MINUTES = 25
        const val MIN_INTERVAL_MINUTES = 1
        const val MAX_INTERVAL_MINUTES = 180
    }
}
