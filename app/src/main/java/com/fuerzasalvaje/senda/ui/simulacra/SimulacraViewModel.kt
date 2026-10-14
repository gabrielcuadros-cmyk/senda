package com.fuerzasalvaje.senda.ui.simulacra

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuerzasalvaje.senda.domain.engine.SimulacrumTimer
import com.fuerzasalvaje.senda.domain.model.Course
import com.fuerzasalvaje.senda.domain.model.SendaRules
import com.fuerzasalvaje.senda.domain.model.Simulacrum
import com.fuerzasalvaje.senda.domain.repository.QuestionRepository
import com.fuerzasalvaje.senda.domain.repository.SimulacrumRepository
import com.fuerzasalvaje.senda.domain.usecase.course.ObserveCoursesUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.FinishSimulacrumUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.MissingSyllabusException
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.NoValidQuestionsException
import com.fuerzasalvaje.senda.ui.components.aiErrorMessage
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.ObserveReadinessUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.StartSimulacrumUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

data class CourseSimulacraItem(
    val course: Course,
    val readiness: Int?,
    val questionCount: Int,
    val attempts: Int,
)

data class SimulacraUiState(
    val loading: Boolean = true,
    val items: List<CourseSimulacraItem> = emptyList(),
    val inProgress: List<Pair<Simulacrum, Course?>> = emptyList(),
    val questionCount: Int = SendaRules.DEFAULT_SIMULACRUM_QUESTIONS,
    val minutes: Int = SendaRules.DEFAULT_SIMULACRUM_MINUTES,
    /** Course whose questions are being prepared. */
    val preparingCourseId: String? = null,
    /** Course that needs the student to type its topics before generating questions. */
    val askTopicsFor: Course? = null,
    val message: String? = null,
    /** One-shot navigation to a freshly started attempt. */
    val startedId: String? = null,
)

/** HU-15 and HU-16 entry point: courses, readiness and attempt settings. */
@HiltViewModel
class SimulacraViewModel @Inject constructor(
    observeCourses: ObserveCoursesUseCase,
    observeReadiness: ObserveReadinessUseCase,
    questions: QuestionRepository,
    simulacra: SimulacrumRepository,
    private val startSimulacrum: StartSimulacrumUseCase,
    private val finishSimulacrum: FinishSimulacrumUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val ui = MutableStateFlow(SimulacraUiState())

    val state: StateFlow<SimulacraUiState> = combine(
        observeCourses(), observeReadiness(), questions.observeAll(), simulacra.observeAll(), ui,
    ) { courses, readiness, questionList, attempts, current ->
        val questionsByCourse = questionList.groupingBy { it.courseId }.eachCount()
        val finishedByCourse = attempts.filter { it.isFinished }.groupingBy { it.courseId }.eachCount()
        val now = clock.instant()
        current.copy(
            loading = false,
            items = courses.map {
                CourseSimulacraItem(it, readiness[it.id], questionsByCourse[it.id] ?: 0, finishedByCourse[it.id] ?: 0)
            },
            inProgress = attempts
                .filter { !it.isFinished && !SimulacrumTimer.isExpired(it, now) }
                .map { attempt -> attempt to courses.firstOrNull { it.id == attempt.courseId } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SimulacraUiState())

    init {
        // Attempts that ran out of time while the app was closed are submitted now.
        viewModelScope.launch { finishSimulacrum.finishExpired() }
    }

    fun setQuestionCount(value: Int) = ui.update { it.copy(questionCount = value) }
    fun setMinutes(value: Int) = ui.update { it.copy(minutes = value) }

    fun start(course: Course, topics: String? = null) {
        if (ui.value.preparingCourseId != null) return
        ui.update { it.copy(preparingCourseId = course.id, askTopicsFor = null, message = null) }
        viewModelScope.launch {
            val current = ui.value
            startSimulacrum(course.id, current.questionCount, current.minutes, topics).fold(
                onSuccess = { attempt -> ui.update { it.copy(preparingCourseId = null, startedId = attempt.id) } },
                onFailure = { error ->
                    ui.update {
                        if (error is MissingSyllabusException) it.copy(preparingCourseId = null, askTopicsFor = course)
                        else it.copy(preparingCourseId = null, message = errorMessage(error))
                    }
                },
            )
        }
    }

    fun dismissTopics() = ui.update { it.copy(askTopicsFor = null) }

    companion object {
        /** Clear Spanish message for each failure (the technical detail goes to Logcat, tag SendaAI). */
        fun errorMessage(error: Throwable): String = when (error) {
            is NoValidQuestionsException ->
                "La IA solo generó ${error.valid} preguntas válidas de ${error.received}. Inténtalo otra vez o detalla más el temario."
            else -> aiErrorMessage(error, default = "No pudimos generar preguntas. Inténtalo de nuevo.")
        }
    }
    fun consumeMessage() = ui.update { it.copy(message = null) }
    fun consumeStarted() = ui.update { it.copy(startedId = null) }
}
