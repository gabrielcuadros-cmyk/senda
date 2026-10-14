package com.fuerzasalvaje.senda.data.mapper

import com.fuerzasalvaje.senda.data.remote.dto.UserDto
import com.fuerzasalvaje.senda.domain.model.Plan
import com.fuerzasalvaje.senda.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Test

class UserMappersTest {

    @Test
    fun `launch promotion makes every new account Premium`() {
        assertEquals(Plan.PREMIUM, Plan.LAUNCH)
        assertEquals(Plan.PREMIUM, User(uid = "u", name = "Ana", email = "a@b.pe").plan)
        assertEquals(Plan.PREMIUM, UserDto().toDomain("u").plan)
    }

    @Test
    fun `unknown stored plan falls back to the launch plan`() {
        assertEquals(Plan.PREMIUM, UserDto(plan = "GOLD").toDomain("u").plan)
    }

    @Test
    fun `plan round-trips through Firestore`() {
        val user = User(uid = "u", name = "Ana", email = "a@b.pe", plan = Plan.PREMIUM)
        assertEquals("PREMIUM", user.toDto().plan)
        assertEquals(user, user.toDto().toDomain("u"))
    }
}
