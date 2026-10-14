package com.fuerzasalvaje.senda.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTargetTest {

    @Test
    fun `every target survives encoding`() {
        listOf(
            NotificationTarget.Home,
            NotificationTarget.Wellbeing,
            NotificationTarget.Focus(),
            NotificationTarget.Focus("e1"),
            NotificationTarget.EvaluationDetail("e-2"),
        ).forEach { assertEquals(it, NotificationTarget.decode(it.encode())) }
    }

    @Test
    fun `unknown or incomplete values open nothing`() {
        assertNull(NotificationTarget.decode(null))
        assertNull(NotificationTarget.decode(""))
        assertNull(NotificationTarget.decode("settings"))
        assertNull(NotificationTarget.decode("evaluation:"))
    }
}
