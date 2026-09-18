package app.spicetify.patches.spotify.localfiles

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.spicetify.patches.spotify.settings.NativeSettingsAbi
import app.spicetify.patches.spotify.settings.enableSetting
import app.spicetify.patches.spotify.settings.settingsPatch
import app.spicetify.patches.spotify.spotifyCompatibility
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import java.util.Properties

private const val READER = "Lcom/spotify/localfiles/mediastore/MediaStoreReader;"
private const val HOOK = "Lapp/spicetify/extension/spotify/localserver/LocalServerHook;"
private const val ANDROID = "http://schemas.android.com/apk/res/android"

private val serverResourcesPatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { manifest ->
            val provider = manifest.createElement("provider")
            provider.setAttributeNS(ANDROID, "android:name", "app.spicetify.extension.spotify.localserver.ServerFileProvider")
            provider.setAttributeNS(ANDROID, "android:authorities", "com.spotify.music.spicetify.localserver")
            provider.setAttributeNS(ANDROID, "android:exported", "false")
            provider.setAttributeNS(ANDROID, "android:grantUriPermissions", "false")
            manifest.getElementsByTagName("application").item(0).appendChild(provider)
        }
    }
}

@Suppress("unused")
val localFilesFromServerPatch = bytecodePatch(
    name = "Local files from a server",
    description = "Streams audio from an HTTPS WebDAV folder into Local Files. Configure the server in Spicetify settings. Experimental; requires byte-range support.",
    default = false,
) {
    compatibleWith(spotifyCompatibility)
    dependsOn(settingsPatch, serverResourcesPatch)

    execute {
        val snapshot = Properties().apply {
            NativeSettingsAbi::class.java.getResourceAsStream("/localfiles/9.1.80.2221.properties")!!.use(::load)
        }
        for (type in snapshot.stringPropertyNames()) {
            val definition = classDefByOrNull(type)
                ?: throw PatchException("Spotify local-files ABI changed: missing $type")
            if (NativeSettingsAbi.digest(definition) != snapshot.getProperty(type)) {
                throw PatchException("Spotify local-files ABI changed: use the verified Spotify 9.1.80.2221 APK.")
            }
        }
        enableSetting("serverFiles")
        val reader = mutableClassDefBy(READER)
        val query = reader.methods.single { it.name == "runQuery" && it.parameterTypes.isEmpty() && it.returnType == "[B" }
        val returns = query.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_OBJECT }
        if (returns.size != 1) throw PatchException("Expected one MediaStoreReader query return.")
        val result = returns.single()
        val register = (result.value as OneRegisterInstruction).registerA
        query.addInstructions(result.index, """
            invoke-static/range {v$register .. v$register}, $HOOK->appendServerFiles([B)[B
            move-result-object v$register
        """.trimIndent())
        val start = reader.methods.single { it.name == "startListening" && it.parameterTypes == listOf("J") }
        val completed = start.implementation!!.instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }
        start.addInstructions(completed, "invoke-static/range {p0 .. p2}, $HOOK->onStartListening(Ljava/lang/Object;J)V")
        reader.methods.single { it.name == "stopListening" && it.parameterTypes.isEmpty() }
            .addInstructions(0, "invoke-static/range {p0 .. p0}, $HOOK->onStopListening(Ljava/lang/Object;)V")
    }
}
