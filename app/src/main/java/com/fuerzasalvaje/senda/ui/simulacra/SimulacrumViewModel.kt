package com.fuerzasalvaje.senda.ui.simulacra

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuerzasalvaje.senda.domain.engine.SimulacrumTimer
import com.fuerzasalvaje.senda.domain.model.Question
import com.fuerzasalvaje.senda.domain.model.Simulacrum
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.AnswerQuestionUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.FinishSimulacrumUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.ObserveSimulacrumUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject

data class SimulacrumUiState(
    val loading: Boolean = true,
    val simulacrum: Simulacrum? = null,
    val questions: List<Question> = emptyList(),
    val currentIndex: Int = 0,
    /** Selected option per question id. */
    val selections: Map<String, Int> = emptyMap(),
    val remainingSeconds: Long = 0,
    val confirmFinish: Boolean = false,
    val confirmExit: Boolean = false,
    val finished: Boolean = false,
    val error: String? = null,
) {
    val current: Question? get() = questions.getOrNull(currentIndex)
    val answeredCount: Int get() = questions.count { it.id in selections }
    val isLastMinute: Boolean get() = remainingSeconds in 0..59
    val progress: Float get() = if (questions.isEmpty()) 0f else answeredCount.toFloat() / questions.size
}

/** HU-15: the running attempt. The clock comes from timestamps and the answers are saved on every change. */
@HiltViewModel
class SimulacrumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeSimulacrum: ObserveSimulacrumUseCase,
    private val answerQuestion: AnswerQuestionUseCase,
    private val finishSimulacrum: FinishSimulacrumUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val simulacrumId: String = requireNotNull(savedStateHandle.get<String>("simulacrumId"))
    private val _state = MutableStateFlow(SimulacrumUiState())
    val state: StateFlow<SimulacrumUiState> = _state.asStateFlow()
    private var timerJob: Job? = null
    private val saveLock = Mutex()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val session = observeSimulacrum(simulacrumId).firstOrNull()
        if (session == null) {
            _state.update { it.copy(loading = false, error = "No encontramos este simulacro.") }
            return
        }
        val simulacrum = session.simulacrum
        _state.update {
            it.copy(
                loading = false,
                simulacrum = simulacrum,
                questions = session.questions,
                selections = simulacrum.answers.mapNotNull { a -> a.selectedOptionIndex?.let { idx -> a.questionId to idx } }.toMap(),
                finished = simulacrum.isFinished,
                remainingSeconds = SimulacrumTimer.remaining(simulacrum, clock.instant()).seconds,
            )
        }
        if (!simulacrum.isFinished) startTimer()
    }

    /** Ticks once per second against the deadline; at zero the attempt is submitted automatically. */
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val simulacrum = _state.value.simulacrum ?: return@launch
                val remaining = SimulacrumTimer.remaining(simulacrum, clock.instant())
                _state.update { it.copy(remainingSeconds = remaining.seconds) }
                if (remaining.isZero) {
                    finish()
                    return@launch
                }
                // Wake up exactly at the next whole second of the countdown.
                val millis = remaining.toMillis() % 1000
                delay(if (millis == 0L) 1000 else millis)
            }
        }
    }

    fun select(optionIndex: Int) {
        val s = _state.value
        val simulacrum = s.simulacrum ?: return
        val question = s.current ?: return
        if (s.finished) return
        val updated = answerQuestion.apply(simulacrum, question, optionIndex)
        _state.update { it.copy(simulacrum = updated, selections = it.selections + (question.id to optionIndex)) }
        // Saves are serialized and always write the latest state, so a slow write never undoes a newer one.
        viewModelScope.launch {
            saveLock.withLock {
                val latest = _state.value.simulacrum ?: return@withLock
                if (!latest.isFinished) answerQuestion.persist(latest)
            }
        }
    }

    fun next() = _state.update { it.copy(currentIndex = (it.currentIndex + 1).coerceAtMost(it.questions.lastIndex)) }
    fun previous() = _state.update { it.copy(currentIndex = (it.currentIndex - 1).coerceAtLeast(0)) }
    fun goTo(index: Int) = _state.update { it.copy(currentIndex = index.coerceIn(0, it.questions.lastIndex)) }

    /** Finishing before the time is up always asks for confirmation. */
    fun requestFinish() = _state.update { it.copy(confirmFinish = true) }
    fun dismissFinish() = _state.update { it.copy(confirmFinish = false) }
    fun confirmFinish() {
        _state.update { it.copy(confirmFinish = false) }
        timerJob?.cancel()
        viewModelScope.launch { finish() }
    }

    fun requestExit() = _state.update { it.copy(confirmExit = true) }
    fun dismissExit() = _state.update { it.copy(confirmExit = false) }

    /** Called by the timer at zero or after confirming. The submit must complete even if the timer is cancelled. */
    private suspend fun finish() = withContext(NonCancellable) {
        val simulacrum = _state.value.simulacrum ?: return@withContext
        if (_state.value.finished) return@withContext
        saveLock.withLock {
            finishSimulacrum(_state.value.simulacrum ?: simulacrum).fold(
                onSuccess = { done -> _state.update { it.copy(simulacrum = done, finished = true, remainingSeconds = 0) } },
                onFailure = { _state.update { it.copy(error = "No se pudo enviar el simulacro.") } },
            )
        }
    }
}
