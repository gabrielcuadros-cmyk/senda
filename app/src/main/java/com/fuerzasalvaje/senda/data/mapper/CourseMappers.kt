package com.fuerzasalvaje.senda.data.mapper

import com.fuerzasalvaje.senda.data.local.entity.CourseEntity
import com.fuerzasalvaje.senda.data.remote.dto.CourseDto
import com.fuerzasalvaje.senda.data.remote.dto.UserDto
import com.fuerzasalvaje.senda.domain.model.Course
import com.fuerzasalvaje.senda.domain.model.Importance
import com.fuerzasalvaje.senda.domain.model.Plan
import com.fuerzasalvaje.senda.domain.model.User

fun UserDto.toDomain(uid: String) = User(
    uid = uid,
    name = name,
    email = email,
    plan = enumOrDefault(plan, Plan.LAUNCH),
    createdAt = createdAt.takeIf { it > 0 }?.toInstant(),
)

fun User.toDto() = UserDto(
    name = name,
    email = email,
    plan = plan.name,
    createdAt = createdAt?.toMillis() ?: 0,
)

fun Course.toEntity(ownerUid: String) = CourseEntity(
    id = id,
    ownerUid = ownerUid,
    name = name,
    teacher = teacher,
    importance = importance.name,
    colorArgb = colorArgb,
    schedule = schedule.map { it.toData() },
    createdAt = createdAt.toMillis(),
)

fun CourseEntity.toDomain() = Course(
    id = id,
    name = name,
    teacher = teacher,
    importance = enumOrDefault(importance, Importance.MEDIUM),
    colorArgb = colorArgb,
    schedule = schedule.map { it.toDomain() },
    createdAt = createdAt.toInstant(),
)

fun Course.toDto() = CourseDto(
    name = name,
    teacher = teacher,
    importance = importance.name,
    colorArgb = colorArgb,
    schedule = schedule.map { it.toData() },
    createdAt = createdAt.toMillis(),
)

fun CourseDto.toDomain(id: String) = Course(
    id = id,
    name = name,
    teacher = teacher,
    importance = enumOrDefault(importance, Importance.MEDIUM),
    colorArgb = colorArgb,
    schedule = schedule.map { it.toDomain() },
    createdAt = createdAt.toInstant(),
)
