package com.fuerzasalvaje.senda.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuerzasalvaje.senda.domain.engine.CourseLoadReport
import com.fuerzasalvaje.senda.domain.model.Availability
import com.fuerzasalvaje.senda.domain.model.StudyBlock
import com.fuerzasalvaje.senda.domain.model.studyBlockTitle
import com.fuerzasalvaje.senda.domain.usecase.plan.AcceptStudyBlocksUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.ObserveAvailabilityUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.ObserveCourseLoadUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.ObservePendingStudyUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.PendingStudyLine
import com.fuerzasalvaje.senda.domain.usecase.plan.RemoveSuggestedStudyBlocksUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.SaveAvailabilityUseCase
import com.fuerzasalvaje.senda.domain.usecase.plan.SuggestStudyBlocksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/** One proposed block, ready to display. */
data class SuggestedBlock(val block: StudyBlock, val title: String, val courseName: String)

data class Shortfall(val title: String, val minutes: Int)

data class ProposalUiState(
    val generating: Boolean = false,
    val accepting: Boolean = false,
    /** Null until the student asks for a proposal. */
    val blocks: List<SuggestedBlock>? = null,
    val shortfalls: List<Shortfall> = emptyList(),
    val availabilityError: String? = null,
    val message: String? = null,
)

data class StudyPlanUiState(
    val loading: Boolean = true,
    val report: CourseLoadReport = CourseLoadReport(emptyList(), 0.0),
    val availability: Availability = Availability(),
    val proposal: ProposalUiState = ProposalUiState(),
    /** Pending evaluations with the hours still missing after blocks and Pomodoros. */
    val pendingStudy: List<PendingStudyLine> = emptyList(),
    /** Accepted blocks not done yet: what "Quitar todos los bloques sugeridos" would remove. */
    val scheduledBlocks: Int = 0,
)

/** HU-13 and HU-14: hours per course, availability and the "Aceptar y agendar" proposal. */
@HiltViewModel
class StudyPlanViewModel @Inject constructor(
    observeCourseLoad: ObserveCourseLoadUseCase,
    observeAvailability: ObserveAvailabilityUseCase,
    observePendingStudy: ObservePendingStudyUseCase,
    private val saveAvailability: SaveAvailabilityUseCase,
    private val suggestBlocks: SuggestStudyBlocksUseCase,
    private val acceptBlocks: AcceptStudyBlocksUseCase,
    private val removeSuggested: RemoveSuggestedStudyBlocksUseCase,
    private val clock: Clock,
) : ViewModel() {

    val zone: ZoneId get() = clock.zone
    private val proposal = MutableStateFlow(ProposalUiState())

    val state: StateFlow<StudyPlanUiState> =
        combine(
            observeCourseLoad(),
            observeAvailability(),
            proposal,
            observePendingStudy(),
            removeSuggested.pendingCount(),
        ) { report, availability, proposalState, pending, scheduled ->
            StudyPlanUiState(
                loading = false,
                report = report,
                availability = availability,
                proposal = proposalState,
                pendingStudy = pending,
                scheduledBlocks = scheduled,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyPlanUiState())

    fun onStart(time: LocalTime) = updateAvailability(state.value.availability.copy(start = time))
    fun onEnd(time: LocalTime) = updateAvailability(state.value.availability.copy(end = time))

    private fun updateAvailability(availability: Availability) {
        viewModelScope.launch {
            saveAvailability(availability).fold(
                // A changed availability makes the current proposal stale.
                onSuccess = { proposal.update { it.copy(availabilityError = null, blocks = null, shortfalls = emptyList()) } },
                onFailure = { proposal.update { it.copy(availabilityError = SaveAvailabilityUseCase.INVALID_MESSAGE) } },
            )
        }
    }

    fun suggest() {
        if (proposal.value.generating) return
        proposal.update { it.copy(generating = true, message = null) }
        viewModelScope.launch {
            val result = suggestBlocks()
            val blocks = result.plan.blocks.map { block ->
                val evaluation = block.evaluationId?.let(result.evaluations::get)
                SuggestedBlock(
                    block = block,
                    title = studyBlockTitle(evaluation?.title),
                    courseName = result.courses[block.courseId]?.name.orEmpty(),
                )
            }
            val shortfalls = result.plan.shortfallMinutes.map { (id, minutes) ->
                Shortfall(result.evaluations[id]?.title ?: "Una evaluación", minutes)
            }
            proposal.update { it.copy(generating = false, blocks = blocks, shortfalls = shortfalls) }
        }
    }

    fun accept() {
        val blocks = proposal.value.blocks?.map { it.block }.orEmpty()
        if (blocks.isEmpty() || proposal.value.accepting) return
        proposal.update { it.copy(accepting = true) }
        viewModelScope.launch {
            acceptBlocks(blocks).fold(
                onSuccess = { count ->
                    proposal.update {
                        ProposalUiState(message = "Agendamos $count ${if (count == 1) "bloque" else "bloques"} de estudio en tu calendario.")
                    }
                },
                onFailure = { proposal.update { it.copy(accepting = false, message = "No se pudieron agendar los bloques.") } },
            )
        }
    }

    /** Removes every accepted block that is not done yet; the screen asks for confirmation first. */
    fun removeAllSuggested() {
        viewModelScope.launch {
            removeSuggested().fold(
                onSuccess = { count ->
                    proposal.update {
                        it.copy(message = "Quitamos $count ${if (count == 1) "bloque" else "bloques"} de estudio de tu calendario.")
                    }
                },
                onFailure = { proposal.update { it.copy(message = "No se pudieron quitar los bloques.") } },
            )
        }
    }

    fun discard() = proposal.update { ProposalUiState(message = "Propuesta descartada.") }

    fun consumeMessage() = proposal.update { it.copy(message = null) }
}
