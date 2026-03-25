package com.example.studyvillage.ui.focus

import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

data class FocusTimerUiState(
    val totalMillis: Long,
    val remainingMillis: Long,
    val isRunning: Boolean
)

class FocusViewModel : ViewModel() {

    private var totalMillis: Long = minutesToMillis(DEFAULT_MINUTES)
    private var remainingMillis: Long = totalMillis
    private var countDownTimer: CountDownTimer? = null

    private val _uiState = MutableLiveData(
        FocusTimerUiState(
            totalMillis = totalMillis,
            remainingMillis = remainingMillis,
            isRunning = false
        )
    )
    val uiState: LiveData<FocusTimerUiState> = _uiState

    fun onStartClicked() {
        val current = _uiState.value ?: return
        if (current.isRunning) return
        startTimer()
    }

    fun onPauseClicked() {
        val current = _uiState.value ?: return
        if (!current.isRunning) return
        pauseTimer()
    }

    fun onSkipClicked() {
        stopTimerInternal()
        remainingMillis = 0L
        publishState(isRunning = false)
    }

    private fun startTimer() {
        if (remainingMillis <= 0L) {
            remainingMillis = totalMillis
        }

        stopTimerInternal()

        countDownTimer = object : CountDownTimer(remainingMillis, TICK_MS) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMillis = millisUntilFinished
                publishState(isRunning = true)
            }

            override fun onFinish() {
                remainingMillis = 0L
                publishState(isRunning = false)
            }
        }.start()

        publishState(isRunning = true)
    }

    private fun pauseTimer() {
        stopTimerInternal()
        publishState(isRunning = false)
    }

    private fun stopTimerInternal() {
        countDownTimer?.cancel()
        countDownTimer = null
    }

    private fun publishState(isRunning: Boolean) {
        _uiState.value = FocusTimerUiState(
            totalMillis = totalMillis,
            remainingMillis = remainingMillis,
            isRunning = isRunning
        )
    }

    override fun onCleared() {
        stopTimerInternal()
        super.onCleared()
    }

    private companion object {
        const val DEFAULT_MINUTES = 25
        const val TICK_MS = 1000L

        fun minutesToMillis(minutes: Int): Long = minutes * 60_000L
    }
}
