package com.fuerzasalvaje.senda.domain.usecase.auth

import com.fuerzasalvaje.senda.domain.model.AuthError
import com.fuerzasalvaje.senda.domain.model.User
import com.fuerzasalvaje.senda.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthUseCasesTest {

    private val repository = mockk<AuthRepository>()
    private val user = User(uid = "u1", name = "Ana", email = "ana@tecsup.edu.pe")

    @Test
    fun `sign up trims input and delegates when valid`() = runTest {
        coEvery { repository.signUp("Ana", "ana@tecsup.edu.pe", "secreto1") } returns Result.success(user)

        val result = SignUpUseCase(repository)("  Ana ", " ana@tecsup.edu.pe ", "secreto1")

        assertEquals(user, result.getOrThrow())
    }

    @Test
    fun `sign up validates name, email and password locally`() = runTest {
        val signUp = SignUpUseCase(repository)

        assertEquals(AuthError.EmptyName, signUp(" ", "ana@tecsup.edu.pe", "secreto1").exceptionOrNull())
        assertEquals(AuthError.InvalidEmail, signUp("Ana", "ana@", "secreto1").exceptionOrNull())
        assertEquals(AuthError.WeakPassword, signUp("Ana", "ana@tecsup.edu.pe", "123").exceptionOrNull())
        coVerify(exactly = 0) { repository.signUp(any(), any(), any()) }
    }

    @Test
    fun `sign in rejects empty password and invalid email`() = runTest {
        val signIn = SignInUseCase(repository)

        assertEquals(AuthError.InvalidEmail, signIn("no-es-correo", "x").exceptionOrNull())
        assertEquals(AuthError.EmptyPassword, signIn("ana@tecsup.edu.pe", "").exceptionOrNull())
    }

    @Test
    fun `errors keep their Spanish message`() = runTest {
        coEvery { repository.signIn(any(), any()) } returns Result.failure(AuthError.Network)

        val error = SignInUseCase(repository)("ana@tecsup.edu.pe", "secreto1").exceptionOrNull()

        assertTrue(error is AuthError.Network)
        assertEquals("Sin conexión. Revisa tu internet e inténtalo de nuevo.", error?.message)
    }

    @Test
    fun `reset password validates email`() = runTest {
        coEvery { repository.sendPasswordReset("ana@tecsup.edu.pe") } returns Result.success(Unit)
        val reset = ResetPasswordUseCase(repository)

        assertEquals(AuthError.InvalidEmail, reset("ana").exceptionOrNull())
        assertTrue(reset("ana@tecsup.edu.pe").isSuccess)
    }
}
