package app.luxion.shogunai

import app.luxion.shogunai.e2e.E2eEnvironment
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class E2eEnvironmentGuardTest {

    @Test
    fun `end-to-end tests refuse to run outside their isolated task`() {
        val failure = assertFailsWith<AssertionError> { E2eEnvironment.prepare() }

        assertTrue(failure.message.orEmpty().contains(":desktopApp:desktopE2eTest"), failure.message)
    }
}
