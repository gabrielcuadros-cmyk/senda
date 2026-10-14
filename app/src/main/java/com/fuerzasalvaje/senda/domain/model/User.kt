package com.fuerzasalvaje.senda.domain.model

import java.time.Instant

data class User(
    val uid: String,
    val name: String,
    val email: String,
    val plan: Plan = Plan.FREE,
    val createdAt: Instant? = null,
)

/** Only the field exists in this version; there are no paid features (D-16). */
enum class Plan { FREE, PREMIUM }
