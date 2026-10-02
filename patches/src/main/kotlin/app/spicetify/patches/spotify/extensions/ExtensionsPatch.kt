package app.spicetify.patches.spotify.extensions

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.spicetify.patches.spotify.settings.NativeSettingsAbi
import app.spicetify.patches.spotify.settings.enableSetting
import app.spicetify.patches.spotify.settings.settingsPatch
import app.spicetify.patches.spotify.spotifyCompatibility
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.iface.value.IntEncodedValue
import java.util.Properties

private const val COSMOS_SERVICE = "Lcom/spotify/cosmos/sharedcosmosrouterservice/SharedCosmosRouterService;"
private const val MENU_BRIDGE = "Lapp/spicetify/extension/spotify/extensions/nativebridge/MenuBridge;"

// Every protobuf field number the extension's Esperanto.java writes or reads, as class#NAME_FIELD_NUMBER.
// These classes keep their names and constants, so a build that renumbers a field fails here. Map
// entries are not covered: protobuf numbers their key and value 1 and 2. Nor are enum values, since
// obfuscated enums keep no constants: the playlist query's BoolPredicate (4, 3, 7 and 6), and Your
// Library's Filter (PLAYLIST 2, ALBUM 0) and LinkType (TRACK 4).
internal val esperantoFieldNumbers = mapOf(
    "Lcom/spotify/player/esperanto/proto/EsContextPlayerState\$ContextPlayerState;" to mapOf("TRACK" to 7),
    "Lcom/spotify/player/esperanto/proto/EsProvidedTrack\$ProvidedTrack;" to mapOf("CONTEXT_TRACK" to 1),
    "Lcom/spotify/player/esperanto/proto/EsContextTrack\$ContextTrack;" to mapOf("URI" to 1, "UID" to 2, "METADATA" to 3),
    "Lcom/spotify/player/esperanto/proto/EsGetStateRequest\$GetStateRequest;" to
        mapOf("PREV_TRACKS_CAP" to 1, "NEXT_TRACKS_CAP" to 2),
    "Lcom/spotify/player/esperanto/proto/EsOptional\$OptionalInt64;" to mapOf("VALUE" to 1),
    "Lcom/spotify/player/esperanto/proto/EsResponseWithReasons\$ResponseWithReasons;" to mapOf("ERROR" to 1),
    "Lcom/spotify/player/esperanto/proto/EsPlay\$PlayRequest;" to mapOf("PREPARE_PLAY_REQUEST" to 1),
    "Lcom/spotify/player/esperanto/proto/EsPreparePlay\$PreparePlayRequest;" to mapOf("CONTEXT" to 1, "OPTIONS" to 2),
    "Lcom/spotify/player/esperanto/proto/EsContext\$Context;" to mapOf("URI" to 3, "URL" to 4),
    "Lcom/spotify/player/esperanto/proto/EsPreparePlayOptions\$PreparePlayOptions;" to mapOf("SKIP_TO" to 3),
    "Lcom/spotify/player/esperanto/proto/EsSkipToTrack\$SkipToTrack;" to mapOf("TRACK_URI" to 4),
    "Lspotify/playlist/esperanto/proto/PlaylistGetRequest;" to mapOf("URI" to 1, "QUERY" to 2, "POLICY" to 3),
    "Lspotify/playlist/esperanto/proto/PlaylistQuery;" to
        mapOf("BOOL_PREDICATES" to 1, "RANGE" to 4, "SHOW_UNAVAILABLE" to 8),
    "Lspotify/playlist/esperanto/proto/PlaylistRange;" to mapOf("START" to 1, "LENGTH" to 2),
    "Lcom/spotify/playlist/policy/proto/PlaylistRequestDecorationPolicy;" to mapOf("PLAYLIST" to 1, "ITEM" to 4),
    "Lcom/spotify/playlist/policy/proto/PlaylistDecorationPolicy;" to mapOf("UNRANGED_LENGTH" to 49),
    "Lcom/spotify/playlist/policy/proto/PlaylistItemDecorationPolicy;" to mapOf("URI" to 1),
    "Lspotify/playlist/esperanto/proto/PlaylistGetResponse;" to mapOf("STATUS" to 1, "DATA" to 2),
    "Lspotify/playlist/esperanto/proto/ResponseStatus;" to mapOf("STATUS_CODE" to 1),
    "Lcom/spotify/playlist/proto/PlaylistRequest\$Response;" to mapOf("ITEM" to 1, "UNRANGED_LENGTH" to 4),
    "Lcom/spotify/playlist/proto/PlaylistRequest\$Item;" to mapOf("URI" to 18),
    "Lcom/spotify/metadata/esperanto/proto/GetEntityRequest;" to mapOf("URI" to 1),
    "Lcom/spotify/metadata/esperanto/proto/GetEntityResponse;" to mapOf("ITEM" to 1),
    "Lcom/spotify/metadata/cosmos/proto/MetadataCosmos\$MetadataItem;" to mapOf("ALBUM" to 3),
    "Lcom/spotify/metadata/proto/Metadata\$Album;" to mapOf("DISC" to 11),
    "Lcom/spotify/metadata/proto/Metadata\$Disc;" to mapOf("TRACK" to 3),
    "Lcom/spotify/metadata/proto/Metadata\$Track;" to mapOf("GID" to 1),
    "Lspotify/your_library/esperanto/proto/YourLibraryRequest;" to mapOf("HEADER" to 1),
    "Lspotify/your_library/esperanto/proto/YourLibraryRequestHeader;" to mapOf(
        "LENGTH" to 12, "FILTERS" to 14, "ALL_PLAYLISTS" to 17, "NUM_LINK_TYPES_IN_PLAYLISTS" to 25,
        "IGNORE_PINNING" to 26,
    ),
    "Lspotify/your_library/proto/YourLibraryConfig\$YourLibraryFilters;" to mapOf("FILTER" to 1),
    "Lspotify/your_library/esperanto/proto/YourLibraryResponse;" to
        mapOf("HEADER" to 1, "ENTITY" to 2, "PINNED_ENTITY" to 3, "STATUS_CODE" to 98, "ERROR" to 99),
    "Lspotify/your_library/esperanto/proto/YourLibraryResponseHeader;" to mapOf("IS_LOADING" to 12),
    "Lspotify/your_library/proto/YourLibraryDecoratedEntityOuterClass\$YourLibraryDecoratedEntity;" to
        mapOf("ENTITY_INFO" to 1, "ALBUM" to 2, "PLAYLIST" to 4),
    "Lspotify/your_library/proto/YourLibraryDecoratedEntityOuterClass\$YourLibraryEntityInfo;" to mapOf("URI" to 3),
    "Lspotify/your_library/proto/YourLibraryDecoratedEntityOuterClass\$YourLibraryPlaylistExtraInfo;" to
        mapOf("NUMBER_OF_ITEMS_PER_LINK_TYPE" to 12),
    "Lspotify/your_library/proto/YourLibraryDecoratedEntityOuterClass\$NumberOfItemsForLinkType;" to
        mapOf("LINK_TYPE" to 1, "NUM_ITEMS" to 2),
).flatMap { (type, fields) -> fields.map { (name, number) -> "$type#${name}_FIELD_NUMBER" to number } }.toMap()

@Suppress("unused")
val extensionsPatch = bytecodePatch(
    name = "Spicetify extensions",
    description = "Adds Android versions of Spicetify extensions to the Spicetify Marketplace's Extensions tab, " +
        "in Spicetify settings, each off until you turn it on. Trash Bin skips the songs and artists you throw " +
        "away from their menus. Play a random song plays one from all of Spotify or your library.",
    default = false,
) {
    compatibleWith(spotifyCompatibility)
    dependsOn(settingsPatch)

    execute {
        val snapshot = Properties().apply {
            NativeSettingsAbi::class.java.getResourceAsStream("/extensions/9.1.80.2221.properties")!!.use(::load)
        }
        for (type in snapshot.stringPropertyNames()) {
            val definition = classDefByOrNull(type)
                ?: throw PatchException("Spotify extensions ABI changed: missing $type")
            if (NativeSettingsAbi.digest(definition) != snapshot.getProperty(type)) {
                throw PatchException("Spotify extensions ABI changed: $type. Use the verified Spotify 9.1.80.2221 APK.")
            }
        }

        val mismatches = fieldNumberMismatches(esperantoFieldNumbers) { key ->
            val (type, name) = key.split('#')
            val constant = classDefByOrNull(type)?.staticFields?.firstOrNull { it.name == name }
            (constant?.initialValue as? IntEncodedValue)?.value
        }
        if (mismatches.isNotEmpty()) throw PatchException("Spotify extensions protocol changed: $mismatches")

        // H1: hand each new service to the player bridge, which sends through its router.
        val constructor = mutableClassDefBy(COSMOS_SERVICE).methods.single {
            it.name == "<init>" &&
                it.parameterTypes.getOrNull(1) == "Lcom/spotify/cosmos/servicebasedrouter/RemoteNativeRouter;"
        }
        val index = bridgeHookIndex(constructor.implementation!!.instructions)
            ?: throw PatchException("Spotify extensions ABI changed: $COSMOS_SERVICE. Use the verified Spotify 9.1.80.2221 APK.")

        // T1 and T2: the menu bridge gets each context menu's frozen item list, with its CollectionTrack
        // (v11) or CollectionArtist (v22), right before the menu model is built from it. v1 is dead until
        // that new-instance, so T2 moves v22 there for invoke-static.
        val trackMenu = menuBuilder("Lp/b9p0;", 1678)
        val artistMenu = menuBuilder("Lp/lr5;", 633)

        constructor.addInstructions(index,
            "invoke-static/range {p0 .. p0}, Lapp/spicetify/extension/spotify/extensions/PlayerBridge;->onCosmos(Ljava/lang/Object;)V")
        trackMenu.addInstructions(1678, """
            invoke-static {v0, v11}, $MENU_BRIDGE->track(Ljava/util/List;Ljava/lang/Object;)Ljava/util/List;
            move-result-object v0
        """.trimIndent())
        artistMenu.addInstructions(633, """
            move-object/from16 v1, v22
            invoke-static {v0, v1}, $MENU_BRIDGE->artist(Ljava/util/List;Ljava/lang/Object;)Ljava/util/List;
            move-result-object v0
        """.trimIndent())
        enableSetting("extensions")
    }
}

/** Menu builder [type]'s `apply(Object)`, once [index] holds the menu model's new-instance. Throws otherwise. */
private fun BytecodePatchContext.menuBuilder(type: String, index: Int): MutableMethod {
    val apply = mutableClassDefBy(type).methods.single {
        it.name == "apply" && it.parameterTypes == listOf("Ljava/lang/Object;")
    }
    if (!isMenuModel(apply.implementation!!.instructions.getOrNull(index))) {
        throw PatchException("Spotify extensions ABI changed: $type. Use the verified Spotify 9.1.80.2221 APK.")
    }
    return apply
}

/** Whether [instruction] is `new-instance v1, Lp/krj;`, the menu model that T1 and T2 insert before. */
internal fun isMenuModel(instruction: Instruction?): Boolean =
    instruction?.opcode == Opcode.NEW_INSTANCE && (instruction as OneRegisterInstruction).registerA == 1 &&
        ((instruction as ReferenceInstruction).reference as TypeReference).type == "Lp/krj;"

/** Each expected `class#FIELD_NUMBER` whose value in the APK differs, or that the APK lacks. */
internal fun fieldNumberMismatches(expected: Map<String, Int>, actual: (String) -> Int?): List<String> =
    expected.mapNotNull { (key, number) ->
        when (val found = actual(key)) {
            number -> null
            null -> "$key missing"
            else -> "$key expected $number, found $found"
        }
    }

/**
 * H1's place in `SharedCosmosRouterService.<init>`: before its only return-void, which must directly follow
 * `NativeRouter.initializeScheduling`, so the router already takes requests. Null for any other shape.
 */
internal fun bridgeHookIndex(instructions: List<Instruction>): Int? {
    val index = instructions.indices.singleOrNull { instructions[it].opcode == Opcode.RETURN_VOID } ?: return null
    val call = instructions.getOrNull(index - 1)
    val method = (call as? ReferenceInstruction)?.reference as? MethodReference
    return index.takeIf {
        call?.opcode == Opcode.INVOKE_VIRTUAL && method?.name == "initializeScheduling" &&
            method.definingClass == "Lcom/spotify/cosmos/cosmosimpl/NativeRouter;"
    }
}
