package com.fuerzasalvaje.senda.domain.model

import java.time.Instant

data class User(
    val uid: String,
    val name: String,
    val email: String,
    val plan: Plan = Plan.LAUNCH,
    val createdAt: Instant? = null,
)

/**
 * Plans are interface only: there are no payments and no feature is limited by plan (D-16, D-18).
 * Launch promotion: every account is Premium, including the ones created as FREE before it.
 */
enum class Plan {
    FREE, PREMIUM;

    companion object {
        val LAUNCH = PREMIUM
    }
}
