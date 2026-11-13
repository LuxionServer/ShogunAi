package app.luxion.shogunai.architecture

import kotlin.test.Test
import kotlin.test.assertEquals

class RepositoryArchitectureTest {

    private val tree = SourceTree.repository

    @Test
    fun `core layers depend only on the layers below them`() {
        assertEquals(emptyList(), CoreLayers.violations(tree))
    }

    @Test
    fun `the domain touches the system only in the listed files`() {
        assertEquals(emptyList(), DomainSystemAccess.violations(tree, DOMAIN_SYSTEM_ACCESS_EXCEPTIONS))
    }

    @Test
    fun `infrastructure is wired only in the composition roots`() {
        assertEquals(emptyList(), CompositionRoots.violations(tree))
    }

    @Test
    fun `sources have no comments`() {
        assertEquals(emptyList(), NoComments.violations(tree))
    }

    @Test
    fun `commands are run as argv`() {
        assertEquals(emptyList(), ArgvCommands.violations(tree, ARGV_EXCEPTIONS))
    }

    @Test
    fun `production files stay under their length cap`() {
        assertEquals(emptyList(), FileLength.violations(tree, FILE_LENGTH_CAPS))
    }
}
