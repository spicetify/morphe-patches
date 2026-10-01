package app.spicetify.patches.spotify.privacy

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.spicetify.patches.spotify.settings.settingsPatch
import app.spicetify.patches.spotify.spotifyCompatibility
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val ANDROID_CLIENT_ID = "9a8d2f0ce77a4e248bb71fefcb557637"
private const val DESKTOP_CLIENT_ID = "65b708073fc0480ea92a077233ca87bd"
private const val DESKTOP_VERSION = "1.3.3.264"
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/151.0.7922.138 Spotify/$DESKTOP_VERSION Safari/537.36"

@Suppress("unused")
val desktopClientPatch = bytecodePatch(
    name = "Present as the desktop client",
    description = "Reports the desktop client's identifier, version, and browser User-Agent on " +
        "every request, including the login configuration and the authenticated scope, and " +
        "stamps App-Platform as WebPlayer. The desktop client needs no Google Play attestation; " +
        "this tests whether the catalog gate then serves the app content. Experimental.",
    default = false,
) {
    compatibleWith(spotifyCompatibility)
    dependsOn(settingsPatch)

    execute {
        replaceClientIdentifier()
        replaceStringReturn("Lp/vn30;", "x", DESKTOP_VERSION)
        replaceStringReturn("Lp/vn30;", "z", DESKTOP_USER_AGENT)
        replaceAppPlatform()
    }
}

private fun BytecodePatchContext.replaceClientIdentifier() {
    // The client identifier is a build constant duplicated across dex files; every copy must
    // agree or one request can name two different clients. Six consumers are known: the
    // identifier provider, the login configuration, and the two scope configurations.
    val owners = mutableListOf<ClassDef>()
    classDefForEach { definition ->
        if (definition.methods.any { it.carriesClientId() }) owners += definition
    }
    if (owners.isEmpty()) throw PatchException("Android client identifier not found.")

    val replaced = owners.sumOf { definition ->
        var count = 0
        for (method in mutableClassDefBy(definition).methods) {
            val code = method.implementation ?: continue
            for ((index, instruction) in code.instructions.withIndex()) {
                if (!instruction.carriesClientId()) continue
                val register = (instruction as OneRegisterInstruction).registerA
                method.replaceInstruction(index, "const-string v$register, \"$DESKTOP_CLIENT_ID\"")
                count++
            }
        }
        count
    }
    if (replaced < 6) {
        throw PatchException("Expected at least 6 copies of the client identifier, replaced $replaced.")
    }
}

private fun BytecodePatchContext.replaceStringReturn(owner: String, methodName: String, value: String) {
    val method = mutableClassDefBy(owner).methods.singleOrNull {
        it.name == methodName && it.returnType == "Ljava/lang/String;"
    } ?: throw PatchException("Identity source $owner->$methodName not found.")
    // Both sources are static and return their argument register first, so claiming any
    // single register for the constant is safe: nothing else runs after the return.
    method.addInstructions(0, "const-string v0, \"$value\"\n    return-object v0")
}

private fun BytecodePatchContext.replaceAppPlatform() {
    val platform = mutableClassDefBy("Lp/in4;").methods.singleOrNull {
        it.name == "a" && it.parameterTypes == listOf("Lp/jiw0;") && it.returnType == "Lp/tty0;"
    } ?: throw PatchException("Request identity interceptor p.in4.a not found.")
    val code = platform.implementation?.instructions?.toList().orEmpty()
    val headerNames = code.mapNotNull { instruction ->
        (instruction as? ReferenceInstruction)?.reference as? StringReference
    }.map { it.string }
    for (required in listOf("User-Agent", "Spotify-App-Version", "X-Client-Id", "App-Platform")) {
        if (required !in headerNames) throw PatchException("Interceptor no longer stamps $required.")
    }
    val index = code.indexOfFirst { it.carriesPlatformAndroid() }
    if (index < 0) throw PatchException("Interceptor no longer declares App-Platform Android.")
    val register = (code[index] as OneRegisterInstruction).registerA
    platform.replaceInstruction(index, "const-string v$register, \"WebPlayer\"")
}

private fun Method.carriesClientId(): Boolean = implementation?.instructions?.any { it.carriesClientId() } == true

private fun Instruction.carriesClientId(): Boolean {
    if (opcode != Opcode.CONST_STRING && opcode != Opcode.CONST_STRING_JUMBO) return false
    val reference = (this as? ReferenceInstruction)?.reference as? StringReference ?: return false
    return reference.string == ANDROID_CLIENT_ID
}

private fun Instruction.carriesPlatformAndroid(): Boolean {
    if (opcode != Opcode.CONST_STRING) return false
    val reference = (this as? ReferenceInstruction)?.reference as? StringReference ?: return false
    return reference.string == "Android"
}
