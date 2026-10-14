package com.fuerzasalvaje.senda.data.mapper

import com.fuerzasalvaje.senda.data.local.entity.PdfDigestEntity
import com.fuerzasalvaje.senda.data.remote.dto.PdfDigestDto
import com.fuerzasalvaje.senda.domain.model.PdfDigest
import com.fuerzasalvaje.senda.domain.model.PdfDigestStatus

fun PdfDigest.toEntity(ownerUid: String) = PdfDigestEntity(
    id = evaluationId, ownerUid = ownerUid, courseId = courseId, pdfKey = pdfKey, status = status.name,
    pageCount = pageCount, extractedChars = extractedChars, scanned = scanned, summary = summary,
    updatedAt = updatedAt.toMillis(), extractedText = extractedText,
)

fun PdfDigestEntity.toDomain() = PdfDigest(
    evaluationId = id, courseId = courseId, pdfKey = pdfKey, status = enumOrDefault(status, PdfDigestStatus.READING),
    pageCount = pageCount, extractedChars = extractedChars, scanned = scanned, summary = summary,
    updatedAt = updatedAt.toInstant(), extractedText = extractedText,
)

/** The extracted text is not part of the document (D-11). */
fun PdfDigest.toDto() = PdfDigestDto(
    courseId = courseId, pdfKey = pdfKey, status = status.name, pageCount = pageCount,
    extractedChars = extractedChars, scanned = scanned, summary = summary, updatedAt = updatedAt.toMillis(),
)

/** Keeps the text extracted on this device when the remote copy is about the same PDF. */
fun PdfDigestDto.toDomain(id: String, local: PdfDigestEntity?) = PdfDigest(
    evaluationId = id, courseId = courseId, pdfKey = pdfKey, status = enumOrDefault(status, PdfDigestStatus.READING),
    pageCount = pageCount, extractedChars = extractedChars, scanned = scanned, summary = summary,
    updatedAt = updatedAt.toInstant(), extractedText = local?.takeIf { it.pdfKey == pdfKey }?.extractedText,
)
