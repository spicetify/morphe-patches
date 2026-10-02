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
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;

/** Checks the Spicetify extensions patch's hooks in a patched APK, or that none are there without it. */
class VerifyExtensionsDex {
    static final String EXTENSIONS = "Lapp/spicetify/extension/spotify/extensions/";
    static final String BRIDGE = EXTENSIONS + "PlayerBridge;";
    static final String SERVICE = "Lcom/spotify/cosmos/sharedcosmosrouterservice/SharedCosmosRouterService;";
    static final String MENU_BRIDGE = EXTENSIONS + "nativebridge/MenuBridge;";
    static final String INSTALLED = "Lapp/spicetify/extension/spotify/settings/InstalledPatches;";

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
        int bridgeHooks = 0, trackHooks = 0, artistHooks = 0;
        for (var definition : classes.values()) {
            if (definition.getType().startsWith(EXTENSIONS)) continue;
            for (var method : definition.getMethods()) {
                if (method.getImplementation() == null) continue;
                var code = code(method);
                for (int index = 0; index < code.size(); index++) {
                    if (calls(code.get(index), BRIDGE, "onCosmos")) {
                        bridgeHooks++;
                        verifyBridgeHook(definition, method, code, index);
                    } else if (calls(code.get(index), MENU_BRIDGE, "track")) {
                        trackHooks++;
                        verifyMenuHook(definition, method, code, index, "Lp/b9p0;", 11);
                    } else if (calls(code.get(index), MENU_BRIDGE, "artist")) {
                        artistHooks++;
                        verifyMenuHook(definition, method, code, index, "Lp/lr5;", 1);
                    }
                }
            }
        }
        int expected = enabled ? 1 : 0;
        require(bridgeHooks == expected && trackHooks == expected && artistHooks == expected,
                "Unexpected hook counts: player bridge " + bridgeHooks + ", track menu " + trackHooks
                        + ", artist menu " + artistHooks);
        verifyCapability(classes.get(INSTALLED), enabled);
        if (enabled) {
            requireTarget(classes, BRIDGE, "onCosmos", List.of("Ljava/lang/Object;"), "V");
            requireTarget(classes, MENU_BRIDGE, "track", List.of("Ljava/util/List;", "Ljava/lang/Object;"), "Ljava/util/List;");
            requireTarget(classes, MENU_BRIDGE, "artist", List.of("Ljava/util/List;", "Ljava/lang/Object;"), "Ljava/util/List;");
        }
        System.out.println("Spicetify extensions verified: " + (bridgeHooks + trackHooks + artistHooks)
                + " hook(s), selected=" + enabled);
    }

    /**
     * The extension's InstalledPatches.extensions() answers whether the patch is in: true only with
     * it. An APK without the settings patch has no extension classes at all.
     */
    static void verifyCapability(ClassDef installed, boolean enabled) {
        Method capability = null;
        if (installed != null) for (var m : installed.getMethods()) {
            if (m.getName().equals("extensions") && m.getParameterTypes().isEmpty() && m.getReturnType().equals("Z")) capability = m;
        }
        if (capability == null) {
            require(!enabled, "Missing the extensions capability");
            return;
        }
        var code = code(capability);
        require(AccessFlags.PUBLIC.isSet(capability.getAccessFlags()) && AccessFlags.STATIC.isSet(capability.getAccessFlags())
                && code.size() == 2 && code.get(0).getOpcode() == Opcode.CONST_4
                && ((NarrowLiteralInstruction) code.get(0)).getNarrowLiteral() == (enabled ? 1 : 0)
                && code.get(1).getOpcode() == Opcode.RETURN
                && ((OneRegisterInstruction) code.get(0)).getRegisterA() == ((OneRegisterInstruction) code.get(1)).getRegisterA(),
                "Extensions capability does not match patch selection");
    }

    /** Spotify calls each hook's target, so it must be a public static method with a body. */
    static void requireTarget(Map<String, ClassDef> classes, String owner, String name, List<String> parameters, String result) {
        boolean found = false;
        if (classes.containsKey(owner)) for (var m : classes.get(owner).getMethods()) {
            found |= m.getName().equals(name) && m.getParameterTypes().equals(parameters) && m.getReturnType().equals(result)
                    && AccessFlags.PUBLIC.isSet(m.getAccessFlags()) && AccessFlags.STATIC.isSet(m.getAccessFlags())
                    && m.getImplementation() != null;
        }
        require(found, "Hook target " + owner + "->" + name + " must be a public static method with a body");
    }

    /**
     * T1 and T2 hand a context menu's frozen item list, v0, and the menu's row to the menu bridge, and put
     * its answer back in v0 right before the menu model is built from it. The track menu's row is v11; the
     * artist menu's is v22, which T2 moves to v1 first.
     */
    static void verifyMenuHook(ClassDef owner, Method method, List<Instruction> code, int index, String menu, int row) {
        require(owner.getType().equals(menu) && method.getName().equals("apply")
                && method.getParameterTypes().equals(List.of("Ljava/lang/Object;")),
                "Menu hook is outside " + menu + "->apply");
        require(code.get(index).getOpcode() == Opcode.INVOKE_STATIC
                && code.get(index) instanceof FiveRegisterInstruction call
                && call.getRegisterCount() == 2 && call.getRegisterC() == 0 && call.getRegisterD() == row,
                "Menu hook must pass the frozen list v0 and the menu's row");
        int frozen = index - 1;
        if (row == 1) {
            require(frozen >= 0 && code.get(frozen).getOpcode() == Opcode.MOVE_OBJECT_FROM16
                    && code.get(frozen) instanceof TwoRegisterInstruction move
                    && move.getRegisterA() == 1 && move.getRegisterB() == 22,
                    "Artist menu hook must move the artist from v22 to v1");
            frozen--;
        }
        require(frozen >= 1 && code.get(frozen).getOpcode() == Opcode.MOVE_RESULT_OBJECT
                && ((OneRegisterInstruction) code.get(frozen)).getRegisterA() == 0
                && calls(code.get(frozen - 1), "Lp/qte;", "J"),
                "Menu hook must follow the frozen item list");
        require(index + 2 < code.size() && code.get(index + 1).getOpcode() == Opcode.MOVE_RESULT_OBJECT
                && ((OneRegisterInstruction) code.get(index + 1)).getRegisterA() == 0
                && code.get(index + 2).getOpcode() == Opcode.NEW_INSTANCE
                && ((OneRegisterInstruction) code.get(index + 2)).getRegisterA() == 1
                && ((ReferenceInstruction) code.get(index + 2)).getReference().toString().equals("Lp/krj;"),
                "Menu hook's list must become the menu model's");
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
