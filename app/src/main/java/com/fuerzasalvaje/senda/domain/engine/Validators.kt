package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.domain.model.SendaRules
import java.time.Instant
import java.time.LocalTime

/** Form validation errors with the Spanish message shown under each field. */
enum class ValidationError(val message: String) {
    TITLE_REQUIRED("Ingresa un título."),
    NAME_REQUIRED("Ingresa el nombre del curso."),
    COURSE_REQUIRED("Elige un curso."),
    WEIGHT_INVALID("El peso debe ser mayor que 0 % y como máximo 100 %."),
    DATE_REQUIRED("Elige la fecha y la hora."),
    END_BEFORE_START("La hora de fin debe ser posterior a la de inicio."),
    HOURS_INVALID("Las horas de estudio deben estar entre 0 y 200."),
    PHASES_REQUIRED("Agrega al menos una fase."),
    PHASE_INVALID("Cada fase necesita nombre y fecha."),
    RUBRIC_INVALID("Cada criterio necesita nombre y un puntaje mayor que 0."),
    CLASS_SLOT_INVALID("Cada clase debe terminar después de empezar."),
}

class ValidationException(val errors: Set<ValidationError>) :
    IllegalArgumentException(errors.joinToString(" ") { it.message })

object Validators {

    fun title(value: String): ValidationError? = if (value.isBlank()) ValidationError.TITLE_REQUIRED else null

    fun courseName(value: String): ValidationError? = if (value.isBlank()) ValidationError.NAME_REQUIRED else null

    /** Accepts "30", "30.5" or "30,5". */
    fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

    /** 30.0 -> "30", 12.5 -> "12.5", for prefilling edit forms. */
    fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    fun weight(value: Double?): ValidationError? =
        if (value == null || !SendaRules.isValidWeight(value)) ValidationError.WEIGHT_INVALID else null

    fun studyHours(value: Double?): ValidationError? =
        if (value == null || value < 0.0 || value > StudyHoursEstimator.MAX_HOURS) ValidationError.HOURS_INVALID else null

    fun dateRange(start: Instant?, end: Instant?): ValidationError? = when {
        start == null -> ValidationError.DATE_REQUIRED
        end != null && !end.isAfter(start) -> ValidationError.END_BEFORE_START
        else -> null
    }

    fun classSlot(start: LocalTime, end: LocalTime): ValidationError? =
        if (!end.isAfter(start)) ValidationError.CLASS_SLOT_INVALID else null

    /**
     * D-4: returns the course total when it exceeds 100 %, so the form can warn (saving is allowed).
     * [otherWeights] are the weights of the course's other evaluations.
     */
    fun courseWeightTotalIfExceeded(otherWeights: List<Double>, newWeight: Double): Double? {
        val total = otherWeights.sum() + newWeight
        return total.takeIf { SendaRules.courseWeightExceeded(otherWeights + newWeight) }
    }
}
