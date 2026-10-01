import java.util.*;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VerifyAnalyticsDexTest {
    enum Change { NONE, MISSING, DUPLICATE }
    enum PendingChange { NONE, MISSING, TARGET, NAME, PARAMS, RETURN, REGISTER }
    enum VoidChange { NONE, MISSING, EXTRA_RETURN }

    @Test void acceptsExpectedHooks() {
        VerifyAnalyticsDex.verifyHooks(fixture(Change.NONE, PendingChange.NONE, VoidChange.NONE), true);
    }

    @Test void rejectsBrokenHooks() {
        for (var change : Change.values()) if (change != Change.NONE) {
            assertThrows(AssertionError.class,
                () -> VerifyAnalyticsDex.verifyHooks(fixture(change, PendingChange.NONE, VoidChange.NONE), true),
                change.name());
        }
        for (var change : PendingChange.values()) if (change != PendingChange.NONE) {
            assertThrows(AssertionError.class,
                () -> VerifyAnalyticsDex.verifyHooks(fixture(Change.NONE, change, VoidChange.NONE), true),
                change.name());
        }
        for (var change : VoidChange.values()) if (change != VoidChange.NONE) {
            assertThrows(AssertionError.class,
                () -> VerifyAnalyticsDex.verifyHooks(fixture(Change.NONE, PendingChange.NONE, change), true),
                change.name());
        }
    }

    @Test void rejectsHooksWhenPatchIsNotSelected() {
        assertThrows(AssertionError.class,
            () -> VerifyAnalyticsDex.verifyHooks(fixture(Change.NONE, PendingChange.NONE, VoidChange.NONE), false));
    }

    @Test void acceptsOriginalBodiesWhenPatchIsNotSelected() {
        VerifyAnalyticsDex.verifyHooks(
            fixture(Change.MISSING, PendingChange.MISSING, VoidChange.MISSING), false);
    }

    List<ClassDef> fixture(Change funnelChange, PendingChange pendingChange, VoidChange voidChange) {
        var classes = new ArrayList<ClassDef>();

        // p.lmw.g, the event INSERT: dropped when enabled.
        var insertCode = new ArrayList<Instruction>();
        if (funnelChange != Change.MISSING) {
            insertCode.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
        } else {
            insertCode.add(new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, 4, 0, 0, 0, 0,
                new ImmutableMethodReference("Lp/iod1;", "x", List.of("Lp/arv0;", "Z", "Z", "Ljava/util/concurrent/Callable;"), "Ljava/lang/Object;")));
        }
        classes.add(voidMethod("Lp/lmw;", "g", VerifyAnalyticsDex.INSERT_PARAMS, "V", insertCode));

        // Metrics snapshots, flush worker scheduling, comScore start, Facebook events: return-void sites.
        classes.add(voidMethod("Lcom/spotify/eventsender/corebridge/EventSenderCoreBridgeImpl;",
            "queueMetricsDataSnapshotForSending", List.of("[B", "[B"), "V",
            voidChange == VoidChange.MISSING ? originalInvoke() : List.of(new ImmutableInstruction10x(Opcode.RETURN_VOID))));
        classes.add(voidMethod("Lp/ukw;", "a", List.of(), "V",
            voidChange == VoidChange.MISSING || voidChange == VoidChange.EXTRA_RETURN
                ? originalInvoke() : List.of(new ImmutableInstruction10x(Opcode.RETURN_VOID))));
        classes.add(voidMethod("Lp/uxe;", "b", List.of(), "V",
            voidChange == VoidChange.MISSING || voidChange == VoidChange.EXTRA_RETURN
                ? originalInvoke() : List.of(new ImmutableInstruction10x(Opcode.RETURN_VOID))));
        classes.add(voidMethod("Lp/a45;", "u", VerifyAnalyticsDex.FACEBOOK_PARAMS, "V",
            voidChange == VoidChange.MISSING ? originalInvoke() : List.of(new ImmutableInstruction10x(Opcode.RETURN_VOID))));

        // p.g2m0.a, the pending-events RPC answered locally.
        var pendingCode = new ArrayList<Instruction>();
        if (pendingChange != PendingChange.MISSING) {
            pendingCode.add(new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, 4, 0, 0, 0, 0,
                new ImmutableMethodReference(
                    pendingChange == PendingChange.TARGET ? "Ltest/Other;" : VerifyAnalyticsDex.ANALYTICS,
                    pendingChange == PendingChange.NAME ? "noEvents" : "noPendingEvents",
                    pendingChange == PendingChange.PARAMS ? List.of("Lp/g2m0;") : List.of("Ljava/lang/Object;"),
                    pendingChange == PendingChange.RETURN ? "Ljava/lang/Object;"
                        : "Lio/reactivex/rxjava3/core/Single;")));
            pendingCode.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 4));
            pendingCode.add(new ImmutableInstruction11x(Opcode.RETURN_OBJECT,
                pendingChange == PendingChange.REGISTER ? 5 : 4));
        } else {
            pendingCode.add(new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 4, 0, 0, 0, 0,
                new ImmutableMethodReference("Lp/g2m0;", "callSingle",
                    List.of("Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/Object;"),
                    "Lio/reactivex/rxjava3/core/Single;")));
        }
        classes.add(voidMethod("Lp/g2m0;", "a", VerifyAnalyticsDex.PENDING_PARAMS,
            "Lio/reactivex/rxjava3/core/Single;", pendingCode));

        if (funnelChange == Change.DUPLICATE) {
            classes.add(voidMethod("Lp/lmw;", "g", VerifyAnalyticsDex.INSERT_PARAMS, "V", new ArrayList<>(insertCode)));
        }
        return classes;
    }

    List<Instruction> originalInvoke() {
        return new ArrayList<>(List.of(new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, 4, 0, 0, 0, 0,
            new ImmutableMethodReference("Lp/ste1;", "j", List.of("Landroid/content/Context;"), "Ljava/lang/Object;"))));
    }

    ClassDef voidMethod(String owner, String name, List<String> parameters, String result,
                         List<? extends Instruction> code) {
        var method = new ImmutableMethod(owner, name,
            parameters.stream().map(type -> new ImmutableMethodParameter(type, Set.of(), null)).toList(),
            result, 9, Set.of(), Set.of(), new ImmutableMethodImplementation(8, code, List.of(), List.of()));
        return new ImmutableClassDef(owner, 1, "Ljava/lang/Object;", List.of(), null, Set.of(), List.of(), List.of(method));
    }
}
