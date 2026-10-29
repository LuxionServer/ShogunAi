package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.domain.model.LocalFileCandidate
import app.luxion.shogunai.domain.model.MobileSecretKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalFilePickerStateTest {

    private val env = LocalFileCandidate(".env", isFolder = false)
    private val keys = LocalFileCandidate("keys", isFolder = true)
    private val localProperties = LocalFileCandidate("local.properties", isFolder = false, secretKind = MobileSecretKind.LOCAL_PROPERTIES)
    private val firebase = LocalFileCandidate("app/google-services.json", isFolder = false, secretKind = MobileSecretKind.FIREBASE_CONFIG)
    private val keystore = LocalFileCandidate("release.jks", isFolder = false, secretKind = MobileSecretKind.KEYSTORE)
    private val provided = LocalFileCandidate("signing.properties", isFolder = false, providedByRepository = true)

    private fun picker(declared: Set<String> = emptySet(), vararg candidates: LocalFileCandidate) =
        LocalFilePickerState(declared).apply { load = LocalFileCandidatesLoad.Loaded(candidates.toList()) }

    @Test
    fun `declared entries start checked and the others unchecked`() {
        val state = picker(setOf("local.properties"), localProperties, env)

        assertTrue(state.isChecked(localProperties))
        assertFalse(state.isChecked(env))
    }

    @Test
    fun `lists suggested entries first, each group by path`() {
        val state = picker(emptySet(), env, localProperties, keys, firebase)

        assertEquals(listOf("app/google-services.json", "local.properties", ".env", "keys"), state.visible.map { it.path })
    }

    @Test
    fun `filters by text ignoring case`() {
        val state = picker(emptySet(), env, keys, localProperties)

        state.filter = "PROP"

        assertEquals(listOf(localProperties), state.visible)
    }

    @Test
    fun `filtering keeps what is checked`() {
        val state = picker(emptySet(), env, keys)

        state.setChecked(env, true)
        state.filter = "keys"
        state.filter = ""

        assertTrue(state.isChecked(env))
    }

    @Test
    fun `a pattern filter narrows the list and is offered as a pattern`() {
        val state = picker(emptySet(), keys, keystore, env)

        state.filter = "*.jks"

        assertEquals(listOf(keystore), state.visible)
        assertEquals("*.jks", state.offeredPattern)
    }

    @Test
    fun `adding the offered pattern keeps it for applying and clears the filter`() {
        val state = picker(emptySet(), keystore)

        state.filter = "*.jks"
        state.addOfferedPattern()

        assertEquals("", state.filter)
        assertEquals(LocalFileSelection(added = emptyList(), removed = emptyList(), patterns = listOf("*.jks")), state.selection())
    }

    @Test
    fun `a declared or absolute pattern isn't offered`() {
        val state = picker(setOf("*.jks"), keystore)

        state.filter = "*.jks"
        assertNull(state.offeredPattern)

        state.filter = "/home/dev/keys/*.jks"
        assertNull(state.offeredPattern)
        assertTrue(state.isFilterPatternAbsolute)
    }

    @Test
    fun `plain text isn't a pattern`() {
        val state = picker(emptySet(), env)

        state.filter = "env"

        assertNull(state.offeredPattern)
        assertFalse(state.isFilterPatternAbsolute)
    }

    @Test
    fun `entries provided by the repository can't be checked`() {
        val state = picker(emptySet(), provided)

        state.setChecked(provided, true)

        assertFalse(state.isChecked(provided))
    }

    @Test
    fun `the selection adds what was checked and removes what was unchecked, keeping unlisted entries`() {
        val state = picker(setOf("local.properties", "*.p12", "typed/secret.txt"), localProperties, env, keys)

        state.setChecked(localProperties, false)
        state.setChecked(env, true)
        state.setChecked(keys, true)

        assertEquals(LocalFileSelection(added = listOf(".env", "keys"), removed = listOf("local.properties"), patterns = emptyList()), state.selection())
    }

    @Test
    fun `counts the checked entries and the added patterns`() {
        val state = picker(setOf("local.properties"), localProperties, env)

        state.setChecked(env, true)
        state.filter = "*.jks"
        state.addOfferedPattern()

        assertEquals(3, state.selectedCount)
    }

    @Test
    fun `nothing is listed until the candidates load`() {
        val state = LocalFilePickerState(setOf("local.properties"))

        assertEquals(emptyList(), state.visible)
        assertEquals(LocalFileSelection(emptyList(), emptyList(), emptyList()), state.selection())
    }

    @Test
    fun `local patterns match names anywhere unless they hold a separator`() {
        assertTrue(matchesLocalPattern("*.jks", "keys/release.jks"))
        assertTrue(matchesLocalPattern("*.jks", "release.jks"))
        assertFalse(matchesLocalPattern("*.jks", "release.jks.bak"))
        assertTrue(matchesLocalPattern("/local.properties", "local.properties"))
        assertFalse(matchesLocalPattern("/local.properties", "app/local.properties"))
        assertTrue(matchesLocalPattern("app/*.json", "app/google-services.json"))
        assertFalse(matchesLocalPattern("app/*.json", "app/src/google-services.json"))
        assertTrue(matchesLocalPattern("**/google-services.json", "app/src/debug/google-services.json"))
        assertTrue(matchesLocalPattern("**/google-services.json", "google-services.json"))
        assertTrue(matchesLocalPattern("key?.properties", "keys.properties"))
        assertTrue(matchesLocalPattern("[kK]eys", "Keys"))
        assertFalse(matchesLocalPattern("[!k]eys", "keys"))
        assertTrue(matchesLocalPattern("keys/", "keys"))
        assertTrue(matchesLocalPattern(".env.*", ".env.local"))
    }
}
