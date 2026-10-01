import java.io.File;
import java.util.List;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;

class VerifyAnalyticsDex {
    static final String ANALYTICS = "Lapp/spicetify/extension/spotify/privacy/Analytics;";
    static final List<String> INSERT_PARAMS = List.of("Ljava/lang/String;", "[B", "Lp/axw0;",
        "[B", "Lp/gm70;", "Lp/xl90;", "Z", "Ljava/lang/String;", "Ljava/lang/String;", "J");
    static final List<String> PENDING_PARAMS =
        List.of("Lcom/spotify/pending_events/esperanto/proto/ReplacePendingEventRequest;");
    static final List<String> FACEBOOK_PARAMS = List.of("Ljava/lang/String;", "Ljava/lang/Double;",
        "Landroid/os/Bundle;", "Z", "Ljava/util/UUID;", "Lp/yck0;");

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void verifyHooks(List<? extends ClassDef> classes, boolean enabled) {
        // The native event contract is untouched; the final Room INSERT is skipped so
        // nothing is ever stored for the flusher to upload.
        verifyReturnsVoid(classes, "Lp/lmw;", "g", INSERT_PARAMS, enabled);

        // Metrics snapshots bypass the funnel with their own in-memory transport.
        verifyReturnsVoid(classes, "Lcom/spotify/eventsender/corebridge/EventSenderCoreBridgeImpl;",
            "queueMetricsDataSnapshotForSending", List.of("[B", "[B"), enabled);

        // The WorkManager schedule site for the event-sender flush worker.
        verifyReturnsVoid(classes, "Lp/ukw;", "a", List.of(), enabled);

        // Playback logging RPC answered locally with an error.
        var pending = one(classes, "Lp/g2m0;", "a", PENDING_PARAMS, "Lio/reactivex/rxjava3/core/Single;");
        var pendingCode = code(pending);
        boolean answered = pendingCode.size() > 2
                && pendingCode.get(0) instanceof ReferenceInstruction reference
                && reference.getReference() instanceof MethodReference target
                && target.getDefiningClass().equals(ANALYTICS)
                && target.getName().equals("noPendingEvents")
                && target.getParameterTypes().equals(List.of("Ljava/lang/Object;"))
                && target.getReturnType().equals("Lio/reactivex/rxjava3/core/Single;")
                && pendingCode.get(0).getOpcode() == Opcode.INVOKE_STATIC
                && pendingCode.get(1).getOpcode() == Opcode.MOVE_RESULT_OBJECT
                && pendingCode.get(2).getOpcode() == Opcode.RETURN_OBJECT
                && ((OneRegisterInstruction) pendingCode.get(1)).getRegisterA()
                    == ((OneRegisterInstruction) pendingCode.get(2)).getRegisterA();
        if (enabled) {
            require(answered, "Pending events RPC must answer with the local error helper");
        } else {
            require(!answered, "Pending events RPC must keep calling Spotify");
        }

        // comScore start and the Facebook App Events funnel.
        verifyReturnsVoid(classes, "Lp/uxe;", "b", List.of(), enabled);
        verifyReturnsVoid(classes, "Lp/a45;", "u", FACEBOOK_PARAMS, enabled);
    }

    static void verifyReturnsVoid(List<? extends ClassDef> classes, String type, String name,
                                  List<String> parameters, boolean enabled) {
        var hook = one(classes, type, name, parameters, "V");
        var hookCode = code(hook);
        if (enabled) {
            require(!hookCode.isEmpty() && hookCode.get(0).getOpcode() == Opcode.RETURN_VOID,
                type + "->" + name + " must return immediately");
        } else {
            require(hookCode.isEmpty() || hookCode.get(0).getOpcode() != Opcode.RETURN_VOID,
                type + "->" + name + " must keep its body");
        }
    }

    static Method one(List<? extends ClassDef> classes, String type, String name,
                      List<String> parameters, String result) {
        var found = new java.util.ArrayList<Method>();
        for (var cls : classes) {
            if (!cls.getType().equals(type)) continue;
            for (var method : cls.getMethods()) {
                if (method.getName().equals(name)
                        && method.getParameterTypes().equals(parameters)
                        && method.getReturnType().equals(result)) {
                    found.add(method);
                }
            }
        }
        require(found.size() == 1, "Expected one " + type + "->" + name + ", found " + found.size());
        return found.get(0);
    }

    static List<Instruction> code(Method method) {
        require(method.getImplementation() != null, method.getDefiningClass() + "->" + method.getName() + " has no code");
        var instructions = new java.util.ArrayList<Instruction>();
        method.getImplementation().getInstructions().forEach(instructions::add);
        return instructions;
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 2 && List.of("0", "1").contains(args[1]),
                "Usage: VerifyAnalyticsDex.java APK ANALYTICS_REMOVAL_ENABLED");
        boolean enabled = args[1].equals("1");
        var container = DexFileFactory.loadDexContainer(new File(args[0]), Opcodes.forApi(35));
        var classes = new java.util.ArrayList<ClassDef>();
        var funnelSeen = false;
        for (String entry : container.getDexEntryNames()) {
            for (var cls : container.getEntry(entry).getDexFile().getClasses()) {
                if (cls.getType().equals("Lp/lmw;")) {
                    require(!funnelSeen, "Lp/lmw; defined in multiple dex files");
                    funnelSeen = true;
                }
                classes.add(cls);
            }
        }
        require(funnelSeen, "Lp/lmw; not found");
        verifyHooks(classes, enabled);
    }
}
