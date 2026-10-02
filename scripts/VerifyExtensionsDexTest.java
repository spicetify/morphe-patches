import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableTypeReference;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VerifyExtensionsDexTest {
    static final String MENUS = VerifyExtensionsDex.MENU_BRIDGE;

    enum Change {
        NONE, NO_HOOKS, MISSING_HOOK, SECOND_HOOK, WRONG_CALLER, WRONG_REGISTER, BEFORE_SCHEDULING, PRIVATE_TARGET,
        MISSING_TRACK_HOOK, TRACK_PASSES_ANOTHER_ROW, TRACK_RESULT_DROPPED, TRACK_AFTER_MODEL, TRACK_BEFORE_FREEZE,
        TRACK_AFTER_ANOTHER_LIST, TRACK_BEFORE_ANOTHER_MODEL, TRACK_IN_ANOTHER_CLASS, ARTIST_WITHOUT_ITS_ROW,
        ARTIST_FROM_ANOTHER_ROW, ARTIST_IN_TRACK_MENU, MISSING_MENU_TARGET, PRIVATE_TRACK_TARGET
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
        boolean withoutHook = change == Change.NO_HOOKS || change == Change.MISSING_HOOK || change == Change.WRONG_CALLER;
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
        boolean hooked = change != Change.NO_HOOKS && !(change == Change.MISSING_TRACK_HOOK && !artist);
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
