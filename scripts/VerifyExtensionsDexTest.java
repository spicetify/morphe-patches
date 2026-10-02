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
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VerifyExtensionsDexTest {
    enum Change { NONE, MISSING_HOOK, SECOND_HOOK, WRONG_CALLER, WRONG_REGISTER, BEFORE_SCHEDULING, PRIVATE_TARGET }

    @Test void acceptsTheBridgeHook() throws Exception { check(Change.NONE, true); }
    @Test void acceptsNoHookWithoutThePatch() throws Exception { check(Change.MISSING_HOOK, false); }
    @Test void rejectsAMissingHook() { assertThrows(AssertionError.class, () -> check(Change.MISSING_HOOK, true)); }
    @Test void rejectsAHookWithoutThePatch() { assertThrows(AssertionError.class, () -> check(Change.NONE, false)); }
    @Test void rejectsASecondHook() { assertThrows(AssertionError.class, () -> check(Change.SECOND_HOOK, true)); }
    @Test void rejectsAHookInAnotherClass() { assertThrows(AssertionError.class, () -> check(Change.WRONG_CALLER, true)); }
    @Test void rejectsAHookThatPassesAnotherRegister() { assertThrows(AssertionError.class, () -> check(Change.WRONG_REGISTER, true)); }
    @Test void rejectsAHookBeforeTheRouterSchedules() { assertThrows(AssertionError.class, () -> check(Change.BEFORE_SCHEDULING, true)); }
    @Test void rejectsATargetSpotifyCantCall() { assertThrows(AssertionError.class, () -> check(Change.PRIVATE_TARGET, true)); }

    void check(Change change, boolean enabled) throws Exception {
        var hook = new ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, change == Change.WRONG_REGISTER ? 2 : 1, 1,
            new ImmutableMethodReference(VerifyExtensionsDex.BRIDGE, "onCosmos", List.of("Ljava/lang/Object;"), "V"));
        var schedule = new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 3, 0, 0, 0, 0,
            new ImmutableMethodReference("Lcom/spotify/cosmos/cosmosimpl/NativeRouter;", "initializeScheduling",
                List.of("Lcom/spotify/cosmos/cosmosimpl/Scheduler;"), "V"));
        List<Instruction> code = new ArrayList<>();
        if (change == Change.BEFORE_SCHEDULING) code.add(hook);
        code.add(schedule);
        if (change != Change.MISSING_HOOK && change != Change.WRONG_CALLER && change != Change.BEFORE_SCHEDULING) code.add(hook);
        code.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
        var constructor = method(VerifyExtensionsDex.SERVICE, "<init>",
            List.of("Lp/ddk;", "Lcom/spotify/cosmos/servicebasedrouter/RemoteNativeRouter;"), "V", 0x10001, code);

        var classes = new ArrayList<>(List.of(
            definition(VerifyExtensionsDex.SERVICE, constructor),
            definition(VerifyExtensionsDex.BRIDGE, method(VerifyExtensionsDex.BRIDGE, "onCosmos",
                List.of("Ljava/lang/Object;"), "V", change == Change.PRIVATE_TARGET ? 0x0a : 0x09,
                List.of(new ImmutableInstruction10x(Opcode.RETURN_VOID))))));
        if (change == Change.SECOND_HOOK || change == Change.WRONG_CALLER) {
            classes.add(definition("Lp/other;", method("Lp/other;", "run", List.of(), "V", 0x09,
                List.of(schedule, hook, new ImmutableInstruction10x(Opcode.RETURN_VOID)))));
        }
        var dex = Files.createTempFile("extensions-verifier-", ".dex");
        try {
            DexPool.writeTo(dex.toString(), new ImmutableDexFile(Opcodes.getDefault(), classes));
            VerifyExtensionsDex.main(new String[]{dex.toString(), enabled ? "1" : "0"});
        } finally {
            Files.deleteIfExists(dex);
        }
    }

    ImmutableMethod method(String owner, String name, List<String> parameters, String result, int flags, List<? extends Instruction> code) {
        return new ImmutableMethod(owner, name, parameters.stream().map(p -> new ImmutableMethodParameter(p, Set.of(), null)).toList(),
            result, flags, Set.of(), Set.of(), new ImmutableMethodImplementation(4, code, List.of(), List.of()));
    }

    ImmutableClassDef definition(String owner, ImmutableMethod method) {
        return new ImmutableClassDef(owner, 1, "Ljava/lang/Object;", List.of(), null, Set.of(), List.of(), List.of(method));
    }
}
