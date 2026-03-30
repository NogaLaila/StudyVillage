package com.example.studyvillage.ui.focus

import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studyvillage.data.user.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class FocusTimerUiState(
    val intervalMinutes: Int,
    val totalMillis: Long,
    val remainingMillis: Long,
    val isRunning: Boolean,
    val coins: Long,
    val completedRewardCoins: Int?,
    val quoteText: String?,
    val quoteAuthor: String?
)

class FocusViewModel(
    private val userRepo: UserRepository,
    private val uid: String?,
    private val email: String?
) : ViewModel() {

    private var totalMillis: Long = minutesToMillis(DEFAULT_MINUTES)
    private var remainingMillis: Long = totalMillis
    private var countDownTimer: CountDownTimer? = null
    private var coins: Long = 0L
    private var quoteText: String? = null
    private var quoteAuthor: String? = null

    private val _uiState = MutableLiveData(
        FocusTimerUiState(
            intervalMinutes = DEFAULT_MINUTES,
            totalMillis = totalMillis,
            remainingMillis = remainingMillis,
            isRunning = false,
            coins = coins,
            completedRewardCoins = null,
            quoteText = quoteText,
            quoteAuthor = quoteAuthor
        )
    )
    val uiState: LiveData<FocusTimerUiState> = _uiState

    init {
        loadCoins()
        loadQuote()
    }

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

    fun onSetIntervalMinutes(minutes: Int) {
        if (minutes !in MIN_MINUTES..MAX_MINUTES) return
        stopTimerInternal()
        totalMillis = minutesToMillis(minutes)
        remainingMillis = totalMillis
        publishState(isRunning = false)
    }

    fun onResetClicked() {
        stopTimerInternal()
        remainingMillis = totalMillis
        publishState(isRunning = false)
    }

    fun onRewardMessageShown() {
        val current = _uiState.value ?: return
        if (current.completedRewardCoins == null) return
        _uiState.value = current.copy(completedRewardCoins = null)
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
                stopTimerInternal()
                publishState(isRunning = false)
                rewardCompletedSession()
            }
        }.start()

        publishState(isRunning = true)
    }

    private fun rewardCompletedSession() {
        val currentUid = uid ?: return
        val rewardCoins = (totalMillis / 60_000L).toInt()
        if (rewardCoins <= 0) return

        viewModelScope.launch {
            coins = runCatching {
                userRepo.addCoins(currentUid, rewardCoins.toLong())
            }.getOrElse { coins }

            publishState(
                isRunning = false,
                completedRewardCoins = rewardCoins
            )
        }
    }

    private fun loadCoins() {
        val currentUid = uid ?: return
        viewModelScope.launch {
            if (email != null) {
                runCatching { userRepo.syncUser(currentUid, email) }
            }

            coins = userRepo.getLocalUser(currentUid)?.coins ?: 0L
            publishState(isRunning = _uiState.value?.isRunning ?: false)
        }
    }

    private fun pauseTimer() {
        stopTimerInternal()
        publishState(isRunning = false)
    }

    private fun loadQuote() {
        viewModelScope.launch {
            val quote = runCatching { fetchQuoteFromApi() }
                .getOrDefault(DEFAULT_QUOTE to DEFAULT_QUOTE_AUTHOR)

            quoteText = quote.first
            quoteAuthor = quote.second
            publishState(isRunning = _uiState.value?.isRunning ?: false)
        }
    }

    private suspend fun fetchQuoteFromApi(): Pair<String, String> = withContext(Dispatchers.IO) {
        val connection = (URL(QUOTES_API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
        }

        try {
            if (connection.responseCode !in 200..299) {
                error("Unexpected response code: ${connection.responseCode}")
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val quotes = JSONArray(response)
            if (quotes.length() == 0) error("No quotes returned")

            val firstQuote = quotes.getJSONObject(0)
            val text = firstQuote.optString("q").trim()
            val author = firstQuote.optString("a").trim()

            if (text.isBlank()) error("Empty quote text")
            text to author.ifBlank { "Unknown" }
        } finally {
            connection.disconnect()
        }
    }

    private fun stopTimerInternal() {
        countDownTimer?.cancel()
        countDownTimer = null
    }

    private fun publishState(
        isRunning: Boolean,
        completedRewardCoins: Int? = null
    ) {
        val intervalMinutes = (totalMillis / 60_000L).toInt()
        _uiState.value = FocusTimerUiState(
            intervalMinutes = intervalMinutes,
            totalMillis = totalMillis,
            remainingMillis = remainingMillis,
            isRunning = isRunning,
            coins = coins,
            completedRewardCoins = completedRewardCoins,
            quoteText = quoteText,
            quoteAuthor = quoteAuthor
        )
    }

    override fun onCleared() {
        stopTimerInternal()
        super.onCleared()
    }

    private companion object {
        const val DEFAULT_MINUTES = 25
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 180
        const val TICK_MS = 1000L
        const val QUOTES_API_URL = "https://zenquotes.io/api/quotes/"
        const val DEFAULT_QUOTE = "Stay focused, one session at a time."
        const val DEFAULT_QUOTE_AUTHOR = "StudyVillage"

        fun minutesToMillis(minutes: Int): Long = minutes * 60_000L
    }
}
