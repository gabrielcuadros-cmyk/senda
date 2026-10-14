package com.fuerzasalvaje.senda.domain.model

/**
 * A mock-exam question produced by the AI from a course syllabus.
 * Case questions carry a [scenario]; both kinds are multiple choice and graded automatically.
 */
data class GeneratedQuestion(
    val statement: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val explanation: String,
    val scenario: String? = null,
) {
    val isCase: Boolean get() = scenario != null
}
