package app.spicetify.patches.spotify.extensions

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableTypeReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtensionsPatchTest {
    @Test
    fun `reports each field number that changed`() {
        val apk = mapOf("A#X" to 1, "A#Y" to 3)
        assertEquals(listOf("A#Y expected 2, found 3"),
            fieldNumberMismatches(mapOf("A#X" to 1, "A#Y" to 2), apk::get))
    }

    @Test
    fun `reports a field whose class is missing`() {
        assertEquals(listOf("A#X missing"), fieldNumberMismatches(mapOf("A#X" to 1)) { null })
    }

    @Test
    fun `hooks the return that follows initializeScheduling`() {
        assertEquals(2, bridgeHookIndex(listOf(schedule, schedule, returnVoid)))
    }

    @Test
    fun `refuses a constructor whose ending changed`() {
        assertNull(bridgeHookIndex(listOf(returnVoid)))
        assertNull(bridgeHookIndex(listOf(schedule, returnVoid, schedule, returnVoid)))
        assertNull(bridgeHookIndex(listOf(invoke(Opcode.INVOKE_DIRECT), returnVoid)))
        assertNull(bridgeHookIndex(listOf(invoke(Opcode.INVOKE_VIRTUAL, "shutdown"), returnVoid)))
        assertNull(bridgeHookIndex(listOf(invoke(Opcode.INVOKE_VIRTUAL, owner = "Lp/other;"), returnVoid)))
    }

    @Test
    fun `accepts the menu model the menu hooks insert before`() {
        assertTrue(isMenuModel(type(Opcode.NEW_INSTANCE, 1, "Lp/krj;")))
    }

    @Test
    fun `refuses any other instruction at a menu hook`() {
        assertFalse(isMenuModel(null))
        assertFalse(isMenuModel(type(Opcode.NEW_INSTANCE, 2, "Lp/krj;")))
        assertFalse(isMenuModel(type(Opcode.NEW_INSTANCE, 1, "Lp/other;")))
        assertFalse(isMenuModel(type(Opcode.CONST_CLASS, 1, "Lp/krj;")))
        assertFalse(isMenuModel(returnVoid))
    }

    private fun type(opcode: Opcode, register: Int, type: String) =
        ImmutableInstruction21c(opcode, register, ImmutableTypeReference(type))

    private val returnVoid = ImmutableInstruction10x(Opcode.RETURN_VOID)
    private val schedule = invoke(Opcode.INVOKE_VIRTUAL)

    private fun invoke(
        opcode: Opcode,
        name: String = "initializeScheduling",
        owner: String = "Lcom/spotify/cosmos/cosmosimpl/NativeRouter;",
    ) = ImmutableInstruction35c(opcode, 2, 3, 0, 0, 0, 0,
        ImmutableMethodReference(owner, name, listOf("Lcom/spotify/cosmos/cosmosimpl/Scheduler;"), "V"))
}
