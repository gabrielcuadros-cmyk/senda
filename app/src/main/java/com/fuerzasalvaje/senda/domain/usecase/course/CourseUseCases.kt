package com.fuerzasalvaje.senda.domain.usecase.course

import com.fuerzasalvaje.senda.domain.engine.ValidationException
import com.fuerzasalvaje.senda.domain.engine.Validators
import com.fuerzasalvaje.senda.domain.model.Course
import com.fuerzasalvaje.senda.domain.repository.CourseRepository
import com.fuerzasalvaje.senda.domain.repository.DocumentStorage
import com.fuerzasalvaje.senda.domain.repository.EvaluationRepository
import com.fuerzasalvaje.senda.domain.usecase.evaluation.localPdfPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ObserveCoursesUseCase @Inject constructor(private val repository: CourseRepository) {
    operator fun invoke(): Flow<List<Course>> = repository.observeAll()
}

class ObserveCourseUseCase @Inject constructor(private val repository: CourseRepository) {
    operator fun invoke(id: String): Flow<Course?> = repository.observeById(id)
}

/** Creates or updates a course (KB-01, D-9, D-13). */
class SaveCourseUseCase @Inject constructor(private val repository: CourseRepository) {
    suspend operator fun invoke(course: Course): Result<Unit> {
        val errors = buildSet {
            Validators.courseName(course.name)?.let(::add)
            course.schedule.forEach { slot -> Validators.classSlot(slot.start, slot.end)?.let(::add) }
        }
        if (errors.isNotEmpty()) return Result.failure(ValidationException(errors))
        return repository.save(course.copy(name = course.name.trim(), teacher = course.teacher?.trim()?.ifEmpty { null }))
    }
}

/** Deletes a course together with its evaluations and their local PDFs. */
class DeleteCourseUseCase @Inject constructor(
    private val courses: CourseRepository,
    private val evaluations: EvaluationRepository,
    private val storage: DocumentStorage,
) {
    suspend operator fun invoke(courseId: String): Result<Unit> {
        evaluations.observeByCourse(courseId).first().forEach { evaluation ->
            evaluation.localPdfPath?.let { storage.delete(it) }
            evaluations.delete(evaluation.id)
        }
        return courses.delete(courseId)
    }
}
