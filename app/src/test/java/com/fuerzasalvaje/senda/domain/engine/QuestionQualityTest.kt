package com.fuerzasalvaje.senda.domain.engine

import com.fuerzasalvaje.senda.testing.generated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuestionQualityTest {

    @Test
    fun `statements are compared without case, accents or punctuation`() {
        assertEquals("que es la aceleracion", QuestionQuality.normalize("  ¿Qué es la ACELERACIÓN?  "))
        assertTrue(QuestionQuality.isDuplicate("¿Qué es la aceleración?", listOf("que es la aceleracion")))
        // One extra word in a long statement is still the same question.
        assertTrue(
            QuestionQuality.isDuplicate(
                "¿Cuál es la unidad de la fuerza en el Sistema Internacional de unidades?",
                listOf("¿Cuál es la unidad de fuerza en el Sistema Internacional de unidades?"),
            )
        )
        assertFalse(QuestionQuality.isDuplicate("¿Qué es la velocidad media?", listOf("¿Qué es la aceleración centrípeta?")))
    }

    @Test
    fun `dedupe keeps the first of each group`() {
        val list = listOf(generated(1), generated(2), generated(1).copy(statement = "PREGUNTA 1 de cinemática: caso número 1"))

        assertEquals(listOf(generated(1), generated(2)), QuestionQuality.dedupe(list))
        assertEquals(listOf(generated(2)), QuestionQuality.dedupe(list, existing = listOf(generated(1).statement)))
    }

    @Test
    fun `relevance uses the syllabus keywords and is skipped for very short syllabi`() {
        val keywords = QuestionQuality.keywords("Unidad 3: Cinemática, movimiento rectilíneo y dinámica")

        assertTrue(QuestionQuality.isRelated(generated(1, "movimientos rectilíneos"), keywords))
        assertFalse(QuestionQuality.isRelated(generated(1, "historia del arte"), keywords))
        assertTrue(QuestionQuality.isRelated(generated(1, "historia del arte"), QuestionQuality.keywords("Óptica")))
    }

    @Test
    fun `catch-all options are rejected by the validator`() {
        assertFalse(QuestionValidator.isValid(generated(1).copy(options = listOf("A", "B", "C", "Ninguna de las anteriores."))))
        assertFalse(QuestionValidator.isValid(generated(1).copy(options = listOf("Todas las anteriores", "B", "C", "D"))))
    }

    @Test
    fun `a batch with more than half the answers in one letter is reordered`() {
        val batch = (1..6).map { generated(it).copy(correctOptionIndex = 2) }

        val balanced = QuestionQuality.rebalance(batch, Random(1))

        assertTrue(QuestionQuality.isUnbalanced(batch))
        assertFalse(QuestionQuality.isUnbalanced(balanced))
        balanced.zip(batch).forEach { (after, before) ->
            assertEquals(before.options[before.correctOptionIndex], after.options[after.correctOptionIndex])
            assertEquals(before.options.toSet(), after.options.toSet())
        }
    }

    @Test
    fun `a balanced batch is left as it is`() {
        val batch = (0..3).map { generated(it).copy(correctOptionIndex = it) }

        assertEquals(batch, QuestionQuality.rebalance(batch, Random(1)))
    }

    @Test
    fun `batches are 5 questions at most`() {
        assertEquals(listOf(5, 5, 2), com.fuerzasalvaje.senda.domain.usecase.simulacrum.GenerateQuestionBatchesUseCase.batchSizes(12))
        assertEquals(listOf(3), com.fuerzasalvaje.senda.domain.usecase.simulacrum.GenerateQuestionBatchesUseCase.batchSizes(3))
        assertTrue(com.fuerzasalvaje.senda.domain.usecase.simulacrum.GenerateQuestionBatchesUseCase.batchSizes(0).isEmpty())
    }
}
