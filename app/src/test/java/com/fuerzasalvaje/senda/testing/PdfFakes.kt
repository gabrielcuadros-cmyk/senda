package com.fuerzasalvaje.senda.testing

import com.fuerzasalvaje.senda.domain.model.PdfDigest
import com.fuerzasalvaje.senda.domain.model.PdfDigestStatus
import com.fuerzasalvaje.senda.domain.model.PdfText
import com.fuerzasalvaje.senda.domain.repository.PdfDigestRepository
import com.fuerzasalvaje.senda.domain.repository.PdfDigestScheduler
import com.fuerzasalvaje.senda.domain.repository.PdfTextExtractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FakePdfDigestRepository : FakeOwnedRepository<PdfDigest>({ it.id }), PdfDigestRepository {
    override fun observeByCourse(courseId: String): Flow<List<PdfDigest>> =
        items.map { all -> all.values.filter { it.courseId == courseId } }
}

/** Records the jobs that would be sent to WorkManager. */
class FakePdfDigestScheduler : PdfDigestScheduler {
    val scheduled = mutableListOf<Pair<String, Boolean>>()
    override fun schedule(evaluationId: String, restart: Boolean) { scheduled += evaluationId to restart }
}

class FakePdfTextExtractor(var result: Result<PdfText> = Result.success(PdfText(listOf("Ley de Ohm: la tensión es V = I x R", "Caso: una resistencia de 4 ohm conectada a 12 V")))) :
    PdfTextExtractor {
    val calls = mutableListOf<String>()
    override suspend fun extract(path: String): Result<PdfText> {
        calls += path
        return result
    }
}

fun pdfDigest(
    evaluationId: String,
    pdfKey: String,
    status: PdfDigestStatus = PdfDigestStatus.READY,
    summary: String = if (status == PdfDigestStatus.READY) "## Fórmulas\n- V = I x R (diap. 3)" else "",
    courseId: String = "c1",
) = PdfDigest(evaluationId, courseId, pdfKey, status, pageCount = 10, extractedChars = 900, summary = summary, updatedAt = TEST_NOW)
