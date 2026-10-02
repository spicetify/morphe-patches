import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;

/** Checks the Spicetify extensions patch's hooks in a patched APK, or that none are there without it. */
class VerifyExtensionsDex {
    static final String EXTENSIONS = "Lapp/spicetify/extension/spotify/extensions/";
    static final String BRIDGE = EXTENSIONS + "PlayerBridge;";
    static final String SERVICE = "Lcom/spotify/cosmos/sharedcosmosrouterservice/SharedCosmosRouterService;";

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static boolean calls(Instruction instruction, String owner, String name) {
        return instruction instanceof ReferenceInstruction ref && ref.getReference() instanceof MethodReference m
            && m.getDefiningClass().equals(owner) && m.getName().equals(name);
    }

    static List<Instruction> code(Method method) {
        var code = new ArrayList<Instruction>();
        method.getImplementation().getInstructions().forEach(code::add);
        return code;
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 2 && List.of("0", "1").contains(args[1]),
                "Usage: VerifyExtensionsDex.java APK EXTENSIONS_PATCH_ENABLED");
        boolean enabled = args[1].equals("1");
        Map<String, ClassDef> classes = new HashMap<>();
        var dex = DexFileFactory.loadDexContainer(new File(args[0]), Opcodes.forApi(35));
        for (var entry : dex.getDexEntryNames()) {
            for (var definition : dex.getEntry(entry).getDexFile().getClasses()) classes.put(definition.getType(), definition);
        }
        int bridgeHooks = 0;
        for (var definition : classes.values()) {
            if (definition.getType().startsWith(EXTENSIONS)) continue;
            for (var method : definition.getMethods()) {
                if (method.getImplementation() == null) continue;
                var code = code(method);
                for (int index = 0; index < code.size(); index++) {
                    if (!calls(code.get(index), BRIDGE, "onCosmos")) continue;
                    bridgeHooks++;
                    verifyBridgeHook(definition, method, code, index);
                }
            }
        }
        require(bridgeHooks == (enabled ? 1 : 0), "Unexpected player bridge hook count: " + bridgeHooks);
        if (enabled) {
            boolean target = false;
            if (classes.containsKey(BRIDGE)) for (var m : classes.get(BRIDGE).getMethods()) {
                target |= m.getName().equals("onCosmos") && m.getParameterTypes().equals(List.of("Ljava/lang/Object;"))
                        && m.getReturnType().equals("V") && AccessFlags.PUBLIC.isSet(m.getAccessFlags())
                        && AccessFlags.STATIC.isSet(m.getAccessFlags()) && m.getImplementation() != null;
            }
            require(target, "The player bridge hook's target must be a public static method with a body");
        }
        System.out.println("Spicetify extensions verified: " + bridgeHooks + " player bridge hook(s), selected=" + enabled);
    }

    /**
     * H1 passes the new service, p0, to the bridge right before its constructor returns, once the native
     * router takes requests.
     */
    static void verifyBridgeHook(ClassDef owner, Method method, List<Instruction> code, int index) {
        require(owner.getType().equals(SERVICE) && method.getName().equals("<init>")
                && method.getParameterTypes().size() == 2
                && method.getParameterTypes().get(1).equals("Lcom/spotify/cosmos/servicebasedrouter/RemoteNativeRouter;"),
                "Player bridge hook is outside SharedCosmosRouterService's constructor");
        int self = method.getImplementation().getRegisterCount() - 3;
        require(code.get(index).getOpcode() == Opcode.INVOKE_STATIC_RANGE
                && code.get(index) instanceof RegisterRangeInstruction call
                && call.getRegisterCount() == 1 && call.getStartRegister() == self
                && ((MethodReference) ((ReferenceInstruction) call).getReference()).getParameterTypes()
                    .equals(List.of("Ljava/lang/Object;")),
                "Player bridge hook must pass the service");
        require(index >= 1 && calls(code.get(index - 1), "Lcom/spotify/cosmos/cosmosimpl/NativeRouter;", "initializeScheduling")
                && index + 1 < code.size() && code.get(index + 1).getOpcode() == Opcode.RETURN_VOID,
                "Player bridge hook must sit between initializeScheduling and the constructor's return");
    }
}
