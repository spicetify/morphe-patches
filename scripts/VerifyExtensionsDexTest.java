import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableTypeReference;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VerifyExtensionsDexTest {
    static final String MENUS = VerifyExtensionsDex.MENU_BRIDGE;
    static final String CHIPS = VerifyExtensionsDex.CHIP_BRIDGE;
    static final String SHUFFLE = VerifyExtensionsDex.NOW_PLAYING_SHUFFLE;

    enum Change {
        NONE, NO_HOOKS, MISSING_HOOK, SECOND_HOOK, WRONG_CALLER, WRONG_REGISTER, BEFORE_SCHEDULING, PRIVATE_TARGET,
        MISSING_TRACK_HOOK, TRACK_PASSES_ANOTHER_ROW, TRACK_RESULT_DROPPED, TRACK_AFTER_MODEL, TRACK_BEFORE_FREEZE,
        TRACK_AFTER_ANOTHER_LIST, TRACK_BEFORE_ANOTHER_MODEL, TRACK_IN_ANOTHER_CLASS, ARTIST_WITHOUT_ITS_ROW,
        ARTIST_FROM_ANOTHER_ROW, ARTIST_IN_TRACK_MENU, MISSING_MENU_TARGET, PRIVATE_TRACK_TARGET, CAPABILITY_OFF,
        CAPABILITY_ON, MISSING_CHIPS_HOOK, CHIPS_IN_ANOTHER_CLASS, CHIPS_PASS_ANOTHER_REGISTER, CHIPS_RESULT_DROPPED,
        CHIPS_BEFORE_THE_REWRITE, CHIPS_AFTER_THE_COPY, MISSING_TAP_HOOK, TAP_IN_ANOTHER_CLASS,
        TAP_PASSES_ANOTHER_REGISTER, TAP_ANSWER_IGNORED, TAP_SKIPS_ELSEWHERE, TAP_AFTER_THE_SEND,
        TAP_BEFORE_THE_EVENT, MISSING_CHIP_TARGET, PRIVATE_TAP_TARGET, CHIPS_AFTER_ANOTHER_CALL,
        CHIPS_BEFORE_ANOTHER_COPY, TAP_AFTER_ANOTHER_EVENT, TAP_BRANCHES_ON_ANOTHER_REGISTER, TAP_BEFORE_SOMETHING_ELSE,
        MISSING_SHUFFLE_HOOK, SHUFFLE_IN_ANOTHER_CLASS, SHUFFLE_IN_ANOTHER_METHOD, SHUFFLE_PASSES_ANOTHER_REGISTER,
        SHUFFLE_BEFORE_THE_STORE, SHUFFLE_AFTER_ANOTHER_FIELD, SHUFFLE_AFTER_ANOTHER_REGISTERS_STORE,
        SHUFFLE_BEFORE_SOMETHING_ELSE, MISSING_SHUFFLE_TARGET, PRIVATE_SHUFFLE_TARGET
    }

    @Test void acceptsEveryHook() throws Exception { check(Change.NONE, true); }
    @Test void acceptsNoHookWithoutThePatch() throws Exception { check(Change.NO_HOOKS, false); }
    @Test void rejectsAMissingHook() { rejects(Change.MISSING_HOOK); }
    @Test void rejectsHooksWithoutThePatch() { assertThrows(AssertionError.class, () -> check(Change.NONE, false)); }
    @Test void rejectsASecondHook() { rejects(Change.SECOND_HOOK); }
    @Test void rejectsAHookInAnotherClass() { rejects(Change.WRONG_CALLER); }
    @Test void rejectsAHookThatPassesAnotherRegister() { rejects(Change.WRONG_REGISTER); }
    @Test void rejectsAHookBeforeTheRouterSchedules() { rejects(Change.BEFORE_SCHEDULING); }
    @Test void rejectsATargetSpotifyCantCall() { rejects(Change.PRIVATE_TARGET); }
    @Test void rejectsAMissingMenuHook() { rejects(Change.MISSING_TRACK_HOOK); }
    @Test void rejectsAMenuHookThatPassesAnotherRow() { rejects(Change.TRACK_PASSES_ANOTHER_ROW); }
    @Test void rejectsAMenuHookWhoseListIsDropped() { rejects(Change.TRACK_RESULT_DROPPED); }
    @Test void rejectsAMenuHookAfterTheModelIsBuilt() { rejects(Change.TRACK_AFTER_MODEL); }
    @Test void rejectsAMenuHookBeforeTheListIsFrozen() { rejects(Change.TRACK_BEFORE_FREEZE); }
    @Test void rejectsAMenuHookAfterAnotherList() { rejects(Change.TRACK_AFTER_ANOTHER_LIST); }
    @Test void rejectsAMenuHookBeforeAnotherModel() { rejects(Change.TRACK_BEFORE_ANOTHER_MODEL); }
    @Test void rejectsAMenuHookInAnotherClass() { rejects(Change.TRACK_IN_ANOTHER_CLASS); }
    @Test void rejectsAnArtistHookWithoutTheArtist() { rejects(Change.ARTIST_WITHOUT_ITS_ROW); }
    @Test void rejectsAnArtistHookThatPassesAnotherRow() { rejects(Change.ARTIST_FROM_ANOTHER_ROW); }
    @Test void rejectsAnArtistHookInTheTrackMenu() { rejects(Change.ARTIST_IN_TRACK_MENU); }
    @Test void rejectsAMissingMenuBridge() { rejects(Change.MISSING_MENU_TARGET); }
    @Test void rejectsAPrivateMenuTarget() { rejects(Change.PRIVATE_TRACK_TARGET); }
    @Test void rejectsTheCapabilityOffWithThePatch() { rejects(Change.CAPABILITY_OFF); }
    @Test void rejectsTheCapabilityOnWithoutThePatch() { assertThrows(AssertionError.class, () -> check(Change.CAPABILITY_ON, false)); }
    @Test void rejectsAMissingChipsHook() { rejects(Change.MISSING_CHIPS_HOOK); }
    @Test void rejectsAChipsHookInAnotherClass() { rejects(Change.CHIPS_IN_ANOTHER_CLASS); }
    @Test void rejectsAChipsHookThatPassesAnotherRegister() { rejects(Change.CHIPS_PASS_ANOTHER_REGISTER); }
    @Test void rejectsAChipsHookWhoseListIsDropped() { rejects(Change.CHIPS_RESULT_DROPPED); }
    @Test void rejectsAChipsHookBeforeSpotifyRewritesTheChips() { rejects(Change.CHIPS_BEFORE_THE_REWRITE); }
    @Test void rejectsAChipsHookAfterTheChipsAreCopied() { rejects(Change.CHIPS_AFTER_THE_COPY); }
    @Test void rejectsAMissingTapHook() { rejects(Change.MISSING_TAP_HOOK); }
    @Test void rejectsATapHookInAnotherClass() { rejects(Change.TAP_IN_ANOTHER_CLASS); }
    @Test void rejectsATapHookThatPassesAnotherRegister() { rejects(Change.TAP_PASSES_ANOTHER_REGISTER); }
    @Test void rejectsATapHookWhoseAnswerIsIgnored() { rejects(Change.TAP_ANSWER_IGNORED); }
    @Test void rejectsATapHookThatSkipsElsewhere() { rejects(Change.TAP_SKIPS_ELSEWHERE); }
    @Test void rejectsATapHookAfterTheTapIsSent() { rejects(Change.TAP_AFTER_THE_SEND); }
    @Test void rejectsATapHookBeforeTheTapsEvent() { rejects(Change.TAP_BEFORE_THE_EVENT); }
    @Test void rejectsAMissingChipBridge() { rejects(Change.MISSING_CHIP_TARGET); }
    @Test void rejectsAPrivateTapTarget() { rejects(Change.PRIVATE_TAP_TARGET); }
    @Test void rejectsAChipsHookAfterAnotherCall() { rejects(Change.CHIPS_AFTER_ANOTHER_CALL); }
    @Test void rejectsAChipsHookBeforeAnotherCopy() { rejects(Change.CHIPS_BEFORE_ANOTHER_COPY); }
    @Test void rejectsATapHookAfterAnotherEvent() { rejects(Change.TAP_AFTER_ANOTHER_EVENT); }
    @Test void rejectsATapHookThatBranchesOnAnotherRegister() { rejects(Change.TAP_BRANCHES_ON_ANOTHER_REGISTER); }
    @Test void rejectsATapHookBeforeSomethingElse() { rejects(Change.TAP_BEFORE_SOMETHING_ELSE); }
    @Test void rejectsAMissingShuffleHook() { rejects(Change.MISSING_SHUFFLE_HOOK); }
    @Test void rejectsAShuffleHookInAnotherClass() { rejects(Change.SHUFFLE_IN_ANOTHER_CLASS); }
    @Test void rejectsAShuffleHookInAnotherMethod() { rejects(Change.SHUFFLE_IN_ANOTHER_METHOD); }
    @Test void rejectsAShuffleHookThatPassesAnotherRegister() { rejects(Change.SHUFFLE_PASSES_ANOTHER_REGISTER); }
    @Test void rejectsAShuffleHookBeforeTheButtonIsStored() { rejects(Change.SHUFFLE_BEFORE_THE_STORE); }
    @Test void rejectsAShuffleHookAfterAnotherFieldIsStored() { rejects(Change.SHUFFLE_AFTER_ANOTHER_FIELD); }
    @Test void rejectsAShuffleHookAfterAnotherRegisterIsStored() { rejects(Change.SHUFFLE_AFTER_ANOTHER_REGISTERS_STORE); }
    @Test void rejectsAShuffleHookBeforeSomethingElse() { rejects(Change.SHUFFLE_BEFORE_SOMETHING_ELSE); }
    @Test void rejectsAMissingShuffleTarget() { rejects(Change.MISSING_SHUFFLE_TARGET); }
    @Test void rejectsAPrivateShuffleTarget() { rejects(Change.PRIVATE_SHUFFLE_TARGET); }

    void rejects(Change change) {
        assertThrows(AssertionError.class, () -> check(change, true));
    }

    void check(Change change, boolean enabled) throws Exception {
        var classes = new ArrayList<ImmutableClassDef>();
        classes.add(definition(VerifyExtensionsDex.SERVICE, service(change)));
        classes.add(definition(VerifyExtensionsDex.BRIDGE, target(VerifyExtensionsDex.BRIDGE, "onCosmos",
            List.of("Ljava/lang/Object;"), "V", change == Change.PRIVATE_TARGET ? 0x0a : 0x09)));
        boolean moved = change == Change.TRACK_IN_ANOTHER_CLASS;
        classes.add(definition("Lp/b9p0;", menu("Lp/b9p0;", moved ? Change.MISSING_TRACK_HOOK : change, false)));
        if (moved) classes.add(definition("Lp/other;", menu("Lp/other;", Change.NONE, false)));
        classes.add(definition("Lp/lr5;", menu("Lp/lr5;", change, true)));
        if (change != Change.MISSING_MENU_TARGET) {
            classes.add(new ImmutableClassDef(MENUS, 1, "Ljava/lang/Object;", List.of(), null, Set.of(), List.of(), List.of(
                target(MENUS, "track", List.of("Ljava/util/List;", "Ljava/lang/Object;"), "Ljava/util/List;",
                    change == Change.PRIVATE_TRACK_TARGET ? 0x0a : 0x09),
                target(MENUS, "artist", List.of("Ljava/util/List;", "Ljava/lang/Object;"), "Ljava/util/List;", 0x09))));
        }
        boolean capability = change == Change.CAPABILITY_ON || enabled && change != Change.CAPABILITY_OFF;
        classes.add(definition(VerifyExtensionsDex.INSTALLED, method(VerifyExtensionsDex.INSTALLED, "extensions", List.of(), "Z",
            0x09, 1, List.of(new ImmutableInstruction11n(Opcode.CONST_4, 0, capability ? 1 : 0), new ImmutableInstruction11x(Opcode.RETURN, 0)))));
        boolean chipsMoved = change == Change.CHIPS_IN_ANOTHER_CLASS;
        classes.add(definition("Lp/qrl;", feeds("Lp/qrl;", chipsMoved ? Change.MISSING_CHIPS_HOOK : change)));
        if (chipsMoved) classes.add(definition("Lp/elsewhere;", feeds("Lp/elsewhere;", Change.NONE)));
        boolean tapMoved = change == Change.TAP_IN_ANOTHER_CLASS;
        classes.add(definition("Lp/a4v;", chipEvents("Lp/a4v;", tapMoved ? Change.MISSING_TAP_HOOK : change)));
        if (tapMoved) classes.add(definition("Lp/somewhere;", chipEvents("Lp/somewhere;", Change.NONE)));
        if (change != Change.MISSING_CHIP_TARGET) {
            classes.add(new ImmutableClassDef(CHIPS, 1, "Ljava/lang/Object;", List.of(), null, Set.of(), List.of(), List.of(
                target(CHIPS, "chips", List.of("Ljava/util/List;"), "Ljava/util/List;", 0x09),
                method(CHIPS, "onTap", List.of("Ljava/lang/String;"), "Z", change == Change.PRIVATE_TAP_TARGET ? 0x0a : 0x09,
                    2, List.of(new ImmutableInstruction11n(Opcode.CONST_4, 0, 0), new ImmutableInstruction11x(Opcode.RETURN, 0))))));
        }
        boolean buttonMoved = change == Change.SHUFFLE_IN_ANOTHER_CLASS;
        classes.add(shuffleButton("Lp/xkp;", buttonMoved ? Change.MISSING_SHUFFLE_HOOK : change));
        if (buttonMoved) classes.add(shuffleButton("Lp/otherbutton;", Change.NONE));
        if (change != Change.MISSING_SHUFFLE_TARGET) {
            classes.add(definition(SHUFFLE, target(SHUFFLE, "onButton", List.of("Landroid/view/View;"), "V",
                change == Change.PRIVATE_SHUFFLE_TARGET ? 0x0a : 0x09)));
        }
        if (change == Change.SECOND_HOOK || change == Change.WRONG_CALLER) {
            classes.add(definition("Lp/other;", method("Lp/other;", "run", List.of(), "V", 0x09, 4,
                List.of(schedule(), bridgeHook(1), new ImmutableInstruction10x(Opcode.RETURN_VOID)))));
        }
        var dex = Files.createTempFile("extensions-verifier-", ".dex");
        try {
            DexPool.writeTo(dex.toString(), new ImmutableDexFile(Opcodes.getDefault(), classes));
            VerifyExtensionsDex.main(new String[]{dex.toString(), enabled ? "1" : "0"});
        } finally {
            Files.deleteIfExists(dex);
        }
    }

    /** SharedCosmosRouterService's constructor: 4 registers, so p0 is v1. */
    ImmutableMethod service(Change change) {
        boolean withoutHook = unhooked(change) || change == Change.MISSING_HOOK || change == Change.WRONG_CALLER;
        List<Instruction> code = new ArrayList<>();
        if (change == Change.BEFORE_SCHEDULING) code.add(bridgeHook(1));
        code.add(schedule());
        if (!withoutHook && change != Change.BEFORE_SCHEDULING) code.add(bridgeHook(change == Change.WRONG_REGISTER ? 2 : 1));
        code.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
        return method(VerifyExtensionsDex.SERVICE, "<init>",
            List.of("Lp/ddk;", "Lcom/spotify/cosmos/servicebasedrouter/RemoteNativeRouter;"), "V", 0x10001, 4, code);
    }

    /**
     * A menu builder's apply: the frozen list lands in v0, the menu bridge replaces it, and the menu model
     * is built from it. The track menu's row is v11, and the artist menu's is v22, moved to v1 first.
     */
    ImmutableMethod menu(String owner, Change change, boolean artist) {
        boolean asArtist = artist || change == Change.ARTIST_IN_TRACK_MENU;
        String name = asArtist ? "artist" : "track";
        List<Instruction> hook = new ArrayList<>();
        if (asArtist && change != Change.ARTIST_WITHOUT_ITS_ROW) {
            hook.add(new ImmutableInstruction22x(Opcode.MOVE_OBJECT_FROM16, 1, change == Change.ARTIST_FROM_ANOTHER_ROW ? 21 : 22));
        }
        int row = asArtist ? 1 : change == Change.TRACK_PASSES_ANOTHER_ROW ? 12 : 11;
        hook.add(new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 2, 0, row, 0, 0, 0,
            new ImmutableMethodReference(MENUS, name, List.of("Ljava/util/List;", "Ljava/lang/Object;"), "Ljava/util/List;")));
        hook.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, change == Change.TRACK_RESULT_DROPPED && !artist ? 2 : 0));
        var freeze = List.of(
            new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, 13, 0, 0, 0, 0, new ImmutableMethodReference(
                change == Change.TRACK_AFTER_ANOTHER_LIST && !artist ? "Lp/other;" : "Lp/qte;", "J",
                List.of("Ljava/util/List;"), "Lp/dm70;")),
            new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0));
        var model = new ImmutableInstruction21c(Opcode.NEW_INSTANCE, 1, new ImmutableTypeReference(
            change == Change.TRACK_BEFORE_ANOTHER_MODEL && !artist ? "Lp/other;" : "Lp/krj;"));
        boolean hooked = !unhooked(change) && !(change == Change.MISSING_TRACK_HOOK && !artist);
        boolean moved = !artist && (change == Change.TRACK_BEFORE_FREEZE || change == Change.TRACK_AFTER_MODEL);
        List<Instruction> code = new ArrayList<>();
        if (hooked && moved && change == Change.TRACK_BEFORE_FREEZE) code.addAll(hook);
        code.addAll(freeze);
        if (hooked && !moved) code.addAll(hook);
        code.add(model);
        if (hooked && moved && change == Change.TRACK_AFTER_MODEL) code.addAll(hook);
        code.add(new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0));
        return method(owner, "apply", List.of("Ljava/lang/Object;"), "Ljava/lang/Object;", 0x01, 24, code);
    }

    /** A build without the patch: no hooks, whatever its capability says. */
    static boolean unhooked(Change change) {
        return change == Change.NO_HOOKS || change == Change.CAPABILITY_ON;
    }

    /**
     * Home's feed mapping, Lp/qrl;->invokeSuspend: Lp/xqw;->a rewrites the chips into v1, the chip bridge
     * replaces them in v1, and a new ArrayList copies them.
     */
    ImmutableMethod feeds(String owner, Change change) {
        var rewrite = List.of(
            new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, 2, 0, 0, 0, 0, new ImmutableMethodReference(
                change == Change.CHIPS_AFTER_ANOTHER_CALL ? "Lp/other;" : "Lp/xqw;", "a", List.of("Ljava/util/List;"),
                "Ljava/util/ArrayList;")),
            new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 1));
        var hook = List.of(
            new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, change == Change.CHIPS_PASS_ANOTHER_REGISTER ? 2 : 1, 0, 0, 0, 0,
                new ImmutableMethodReference(CHIPS, "chips", List.of("Ljava/util/List;"), "Ljava/util/List;")),
            new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, change == Change.CHIPS_RESULT_DROPPED ? 0 : 1));
        var copy = new ImmutableInstruction21c(Opcode.NEW_INSTANCE, 2, new ImmutableTypeReference(
            change == Change.CHIPS_BEFORE_ANOTHER_COPY ? "Ljava/util/LinkedList;" : "Ljava/util/ArrayList;"));
        boolean hooked = !unhooked(change) && change != Change.MISSING_CHIPS_HOOK;
        List<Instruction> code = new ArrayList<>();
        if (hooked && change == Change.CHIPS_BEFORE_THE_REWRITE) code.addAll(hook);
        code.addAll(rewrite);
        if (hooked && change != Change.CHIPS_BEFORE_THE_REWRITE && change != Change.CHIPS_AFTER_THE_COPY) code.addAll(hook);
        code.add(copy);
        if (hooked && change == Change.CHIPS_AFTER_THE_COPY) code.addAll(hook);
        code.add(new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 2));
        return method(owner, "invokeSuspend", List.of("Ljava/lang/Object;"), "Ljava/lang/Object;", 0x01, 5, code);
    }

    /**
     * Home's chip events, Lp/a4v;->invoke: a tap's Lp/q8w; is built from the chip's id in v1, the chip bridge
     * takes the id and skips to the case's return-object v13 when it handled the tap, and otherwise the event
     * goes to Home's loop, Lp/bay;->invoke.
     */
    ImmutableMethod chipEvents(String owner, Change change) {
        String type = change == Change.TAP_AFTER_ANOTHER_EVENT ? "Lp/other;" : "Lp/q8w;";
        var event = List.of(
            new ImmutableInstruction21c(Opcode.NEW_INSTANCE, 2, new ImmutableTypeReference(type)),
            new ImmutableInstruction35c(Opcode.INVOKE_DIRECT, 2, 2, 1, 0, 0, 0,
                new ImmutableMethodReference(type, "<init>", List.of("Ljava/lang/String;"), "V")));
        var send = new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 3, 2, 0, 0, 0,
            new ImmutableMethodReference("Lp/bay;", "invoke", List.of("Ljava/lang/Object;"), "Ljava/lang/Object;"));
        boolean hooked = !unhooked(change) && change != Change.MISSING_TAP_HOOK;
        // A branch counts code units from the if-nez: itself 2, the event 2 and 3, a nop 1, the send 3 and the
        // goto 1. TAP_SKIPS_ELSEWHERE lands on the goto instead of the return.
        boolean nop = change == Change.TAP_BEFORE_SOMETHING_ELSE;
        List<Instruction> code = new ArrayList<>();
        if (hooked && change == Change.TAP_BEFORE_THE_EVENT) code.addAll(tapHook(change, 2 + 5 + 3 + 1));
        code.addAll(event);
        if (hooked && change != Change.TAP_BEFORE_THE_EVENT && change != Change.TAP_AFTER_THE_SEND) {
            code.addAll(tapHook(change, change == Change.TAP_SKIPS_ELSEWHERE ? 2 + 3 : 2 + (nop ? 1 : 0) + 3 + 1));
        }
        if (nop) code.add(new ImmutableInstruction10x(Opcode.NOP));
        code.add(send);
        if (hooked && change == Change.TAP_AFTER_THE_SEND) code.addAll(tapHook(change, 2 + 1));
        code.add(new ImmutableInstruction10t(Opcode.GOTO, 1));
        code.add(new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 13));
        return method(owner, "invoke", List.of("Ljava/lang/Object;", "Ljava/lang/Object;"), "Ljava/lang/Object;", 0x01, 16, code);
    }

    /** B: the chip's id in v1 goes to the chip bridge, whose answer, when true, branches {@code offset} code units on. */
    List<Instruction> tapHook(Change change, int offset) {
        return List.of(
            new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, change == Change.TAP_PASSES_ANOTHER_REGISTER ? 2 : 1, 0, 0, 0, 0,
                new ImmutableMethodReference(CHIPS, "onTap", List.of("Ljava/lang/String;"), "Z")),
            new ImmutableInstruction11x(Opcode.MOVE_RESULT, 1),
            new ImmutableInstruction21t(change == Change.TAP_ANSWER_IGNORED ? Opcode.IF_EQZ : Opcode.IF_NEZ,
                change == Change.TAP_BRANCHES_ON_ANOTHER_REGISTER ? 2 : 1, offset));
    }

    /**
     * Now Playing's shuffle button, Lp/xkp;: its constructor stores the button from v2 in field i, N1 hands v2 to
     * NowPlayingShuffle, and the constructor returns. 6 registers, so this is v4. SHUFFLE_IN_ANOTHER_METHOD
     * moves that ending, hook and all, into another method of the class with the same parameters, and
     * SHUFFLE_IN_ANOTHER_CLASS gives the same code, still storing into Lp/xkp;'s field, to another class.
     */
    ImmutableClassDef shuffleButton(String owner, Change change) {
        boolean hooked = !unhooked(change) && change != Change.MISSING_SHUFFLE_HOOK;
        boolean moved = change == Change.SHUFFLE_IN_ANOTHER_METHOD;
        var methods = new ArrayList<ImmutableMethod>();
        methods.add(method(owner, "<init>", List.of("Landroid/content/Context;"), "V", 0x10001, 6,
            buttonEnd(change, hooked && !moved)));
        if (moved) {
            methods.add(method(owner, "a", List.of("Landroid/content/Context;"), "V", 0x11, 6, buttonEnd(Change.NONE, true)));
        }
        return new ImmutableClassDef(owner, 1, "Ljava/lang/Object;", List.of(), null, Set.of(), List.of(), methods);
    }

    /** The store of the button into Lp/xkp;'s field, N1 when {@code hooked}, and the return. */
    List<Instruction> buttonEnd(Change change, boolean hooked) {
        var store = new ImmutableInstruction22c(Opcode.IPUT_OBJECT,
            change == Change.SHUFFLE_AFTER_ANOTHER_REGISTERS_STORE ? 3 : 2, 4, new ImmutableFieldReference("Lp/xkp;",
                change == Change.SHUFFLE_AFTER_ANOTHER_FIELD ? "h" : "i", "Landroidx/appcompat/widget/AppCompatImageButton;"));
        var hook = new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, change == Change.SHUFFLE_PASSES_ANOTHER_REGISTER ? 3 : 2,
            0, 0, 0, 0, new ImmutableMethodReference(SHUFFLE, "onButton", List.of("Landroid/view/View;"), "V"));
        List<Instruction> code = new ArrayList<>();
        if (hooked && change == Change.SHUFFLE_BEFORE_THE_STORE) code.add(hook);
        code.add(store);
        if (hooked && change != Change.SHUFFLE_BEFORE_THE_STORE) code.add(hook);
        if (change == Change.SHUFFLE_BEFORE_SOMETHING_ELSE) code.add(new ImmutableInstruction10x(Opcode.NOP));
        code.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
        return code;
    }

    ImmutableInstruction3rc bridgeHook(int register) {
        return new ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, register, 1,
            new ImmutableMethodReference(VerifyExtensionsDex.BRIDGE, "onCosmos", List.of("Ljava/lang/Object;"), "V"));
    }

    ImmutableInstruction35c schedule() {
        return new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 3, 0, 0, 0, 0,
            new ImmutableMethodReference("Lcom/spotify/cosmos/cosmosimpl/NativeRouter;", "initializeScheduling",
                List.of("Lcom/spotify/cosmos/cosmosimpl/Scheduler;"), "V"));
    }

    ImmutableMethod target(String owner, String name, List<String> parameters, String result, int flags) {
        return method(owner, name, parameters, result, flags, 3, List.of(result.equals("V")
            ? new ImmutableInstruction10x(Opcode.RETURN_VOID) : new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0)));
    }

    ImmutableMethod method(String owner, String name, List<String> parameters, String result, int flags, int registers,
            List<? extends Instruction> code) {
        return new ImmutableMethod(owner, name, parameters.stream().map(p -> new ImmutableMethodParameter(p, Set.of(), null)).toList(),
            result, flags, Set.of(), Set.of(), new ImmutableMethodImplementation(registers, code, List.of(), List.of()));
    }

    ImmutableClassDef definition(String owner, ImmutableMethod method) {
        return new ImmutableClassDef(owner, 1, "Ljava/lang/Object;", List.of(), null, Set.of(), List.of(), List.of(method));
    }
}
