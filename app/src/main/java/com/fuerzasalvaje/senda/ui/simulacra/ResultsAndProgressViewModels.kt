package com.fuerzasalvaje.senda.ui.simulacra

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuerzasalvaje.senda.domain.engine.ReadinessCalculator
import com.fuerzasalvaje.senda.domain.engine.SimulacrumScoring
import com.fuerzasalvaje.senda.domain.model.Course
import com.fuerzasalvaje.senda.domain.model.Question
import com.fuerzasalvaje.senda.domain.model.Simulacrum
import com.fuerzasalvaje.senda.domain.repository.SimulacrumRepository
import com.fuerzasalvaje.senda.domain.usecase.course.ObserveCoursesUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.ExplainMistakeUseCase
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.ObserveSimulacrumUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject

enum class AnswerOutcome { CORRECT, INCORRECT, UNANSWERED }

data class ResultItem(val number: Int, val question: Question, val selected: Int?, val outcome: AnswerOutcome)

data class ExplanationState(val loading: Boolean = false, val text: String? = null, val error: String? = null)

data class ResultsUiState(
    val loading: Boolean = true,
    val simulacrum: Simulacrum? = null,
    val courseName: String = "",
    val grade: Double = 0.0,
    val items: List<ResultItem> = emptyList(),
    /** Question shown in the AI explanation sheet. */
    val explaining: ResultItem? = null,
    val explanations: Map<String, ExplanationState> = emptyMap(),
) {
    val correct: Int get() = items.count { it.outcome == AnswerOutcome.CORRECT }
    val incorrect: Int get() = items.count { it.outcome == AnswerOutcome.INCORRECT }
    val unanswered: Int get() = items.count { it.outcome == AnswerOutcome.UNANSWERED }
}

/** HU-17: post-attempt breakdown and the AI tutor for each wrong answer. */
@HiltViewModel
class SimulacrumResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeSimulacrum: ObserveSimulacrumUseCase,
    observeCourses: ObserveCoursesUseCase,
    private val explainMistake: ExplainMistakeUseCase,
) : ViewModel() {

    private val simulacrumId: String = requireNotNull(savedStateHandle.get<String>("simulacrumId"))
    private val explaining = MutableStateFlow<String?>(null)
    private val explanations = MutableStateFlow<Map<String, ExplanationState>>(emptyMap())

    val state: StateFlow<ResultsUiState> = combine(
        observeSimulacrum(simulacrumId), observeCourses(), explaining, explanations,
    ) { session, courses, explainingId, explained ->
        if (session == null) return@combine ResultsUiState(loading = false)
        val answers = session.simulacrum.answers.associateBy { it.questionId }
        val items = session.questions.mapIndexed { index, question ->
            val answer = answers[question.id]
            val outcome = when {
                answer?.selectedOptionIndex == null -> AnswerOutcome.UNANSWERED
                answer.correct -> AnswerOutcome.CORRECT
                else -> AnswerOutcome.INCORRECT
            }
            ResultItem(index + 1, question, answer?.selectedOptionIndex, outcome)
        }
        ResultsUiState(
            loading = false,
            simulacrum = session.simulacrum,
            courseName = courses.firstOrNull { it.id == session.simulacrum.courseId }?.name.orEmpty(),
            grade = SimulacrumScoring.roundedGrade(session.simulacrum),
            items = items,
            explaining = items.firstOrNull { it.question.id == explainingId },
            explanations = explained,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResultsUiState())

    /** Opens the sheet and asks the tutor (or reads the cache). Also used to retry. */
    fun explain(item: ResultItem) {
        explaining.value = item.question.id
        val current = explanations.value[item.question.id]
        if (current?.text != null || current?.loading == true) return
        explanations.update { it + (item.question.id to ExplanationState(loading = true)) }
        viewModelScope.launch {
            val result = explainMistake(item.question, item.selected)
            explanations.update {
                it + (item.question.id to result.fold(
                    onSuccess = { text -> ExplanationState(text = text) },
                    onFailure = { ExplanationState(error = EXPLAIN_ERROR) },
                ))
            }
        }
    }

    fun closeExplanation() { explaining.value = null }

    companion object {
        const val EXPLAIN_ERROR = "No pudimos obtener la explicación. Revisa tu conexión e inténtalo de nuevo."
    }
}

data class CourseProgress(val course: Course, val grades: List<Double>, val readiness: Int?)

data class ProgressUiState(
    val loading: Boolean = true,
    val courses: List<CourseProgress> = emptyList(),
    /** Finished attempts, most recent first. */
    val history: List<Pair<Simulacrum, Course?>> = emptyList(),
)

/** HU-16: grade evolution, readiness per course and the attempt history. */
@HiltViewModel
class ProgressViewModel @Inject constructor(
    observeCourses: ObserveCoursesUseCase,
    simulacra: SimulacrumRepository,
    private val clock: Clock,
) : ViewModel() {

    val zone: ZoneId get() = clock.zone

    val state: StateFlow<ProgressUiState> = combine(observeCourses(), simulacra.observeAll()) { courses, attempts ->
        val finished = attempts.filter { it.isFinished }
        ProgressUiState(
            loading = false,
            courses = courses.map { course ->
                val own = finished.filter { it.courseId == course.id }
                CourseProgress(
                    course = course,
                    grades = own.sortedBy { it.finishedAt }.map(SimulacrumScoring::roundedGrade),
                    readiness = ReadinessCalculator.readiness(own),
                )
            }.filter { it.grades.isNotEmpty() },
            history = finished.sortedByDescending { it.finishedAt }
                .map { attempt -> attempt to courses.firstOrNull { it.id == attempt.courseId } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())
}
