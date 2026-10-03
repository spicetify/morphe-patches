import java.util.*;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VerifyThemeDexTest {
    static final String COMPOSE = VerifyThemeDex.THEME + "ComposeTheme;";
    static final ImmutableMethodReference PALETTE =
            new ImmutableMethodReference(COMPOSE, "palette", List.of("Ljava/lang/Object;"), "Ljava/lang/Object;");
    static final ImmutableMethodReference PRIMITIVES = new ImmutableMethodReference(COMPOSE, "primitives", List.of("Ljava/lang/Object;"), "V");
    static final ImmutableMethodReference SURFACE = new ImmutableMethodReference(COMPOSE, "surface", List.of("J"), "J");

    enum Change {
        NONE, PALETTE_MISSING, PALETTE_REGISTER, PALETTE_CAST, PALETTE_ELSEWHERE, PRIMITIVES_MISSING,
        SURFACE_MISSING, SURFACE_FIELD, SURFACE_VALUE, ROLES_EMPTY, COMPOSE_EMPTY, TABLES_EMPTY
    }

    @Test void acceptsEveryHook() { VerifyThemeDex.verify(apk(Change.NONE, true), true); }

    @Test void rejectsBrokenHooks() {
        for (var change : Change.values()) if (change != Change.NONE) {
            assertThrows(AssertionError.class, () -> VerifyThemeDex.verify(apk(change, true), true), change.name());
        }
    }

    @Test void rejectsHooksWhenNotSelected() {
        assertThrows(AssertionError.class, () -> VerifyThemeDex.verify(apk(Change.NONE, true), false));
        assertThrows(AssertionError.class, () -> VerifyThemeDex.verify(apk(Change.TABLES_EMPTY, true), false));
    }

    @Test void acceptsSpotifysOwnColorsWhenNotSelected() {
        VerifyThemeDex.verify(apk(Change.NONE, false), false);
        // Without any settings patch, the extension isn't there at all.
        VerifyThemeDex.verify(List.of(), false);
    }

    /** Spotify's hooked classes and the extension's table methods, patched or as they ship. */
    List<ClassDef> apk(Change change, boolean hooked) {
        var classes = new ArrayList<ClassDef>();
        classes.add(type("Lp/vcu;", "a", palette(hooked, change == Change.PALETTE_REGISTER ? 1 : 0, change != Change.PALETTE_CAST)));
        classes.add(type("Lp/p6s;", "invoke", palette(hooked && change != Change.PALETTE_MISSING, 0, true)));
        if (change == Change.PALETTE_ELSEWHERE) classes.add(type("Lp/other;", "a", palette(true, 0, true)));
        var primitives = new ArrayList<Instruction>(List.of(
                new ImmutableInstruction21c(Opcode.SPUT_OBJECT, 0, new ImmutableFieldReference("Lp/rut;", "a", "Lp/jua;"))));
        if (hooked && change != Change.PRIMITIVES_MISSING) primitives.add(call(PRIMITIVES, 0, 1));
        classes.add(type("Lp/rut;", "<clinit>", primitives));
        classes.add(type("Lp/pz01;", "<clinit>", surface(hooked, change == Change.SURFACE_VALUE ? 0xFF2A2A2AL : 0xFF282828L, "Lp/pz01;", "a")));
        classes.add(type("Lp/t5s0;", "<clinit>", surface(hooked && change != Change.SURFACE_MISSING, 0xFF282828L, "Lp/t5s0;",
                change == Change.SURFACE_FIELD ? "a" : "b")));
        classes.add(type(VerifyThemeDex.THEME + "ThemeRoleMap;", "encoded",
                table(hooked && change != Change.ROLES_EMPTY && change != Change.TABLES_EMPTY ? "main:gray_7|card:gray_15" : "")));
        classes.add(type(VerifyThemeDex.THEME + "ComposeTheme;", "table",
                table(hooked && change != Change.COMPOSE_EMPTY && change != Change.TABLES_EMPTY ? "a.a=gray_7@FF121212;d.c=gray_15@FF282828" : "")));
        return classes;
    }

    List<Instruction> palette(boolean hooked, int register, boolean cast) {
        var code = new ArrayList<Instruction>(List.of(
                new ImmutableInstruction21c(Opcode.SGET_OBJECT, 0, new ImmutableFieldReference("Lp/iwt;", "a", "Lp/avt;"))));
        if (hooked) {
            code.add(call(PALETTE, register, 1));
            code.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0));
            if (cast) code.add(new ImmutableInstruction21c(Opcode.CHECK_CAST, 0, new ImmutableTypeReference("Lp/avt;")));
        }
        return code;
    }

    List<Instruction> surface(boolean hooked, long value, String owner, String field) {
        var color = new ImmutableMethodReference("Lp/iae1;", "g", List.of("J"), "J");
        var code = new ArrayList<Instruction>(List.of(
                new ImmutableInstruction51l(Opcode.CONST_WIDE, 0, value),
                new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 2, 0, 1, 0, 0, 0, color),
                new ImmutableInstruction11x(Opcode.MOVE_RESULT_WIDE, 0)));
        if (hooked) {
            code.add(call(SURFACE, 0, 2));
            code.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_WIDE, 0));
        }
        code.add(new ImmutableInstruction21c(Opcode.SPUT_WIDE, 0, new ImmutableFieldReference(owner, field, "J")));
        return code;
    }

    List<Instruction> table(String value) {
        return List.of(new ImmutableInstruction21c(Opcode.CONST_STRING, 0, new ImmutableStringReference(value)),
                new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0));
    }

    Instruction call(ImmutableMethodReference method, int register, int count) {
        return new ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, register, count, method);
    }

    ClassDef type(String type, String name, List<Instruction> code) {
        var body = new ArrayList<>(code);
        // A table method already ends with its return.
        if (body.get(body.size() - 1).getOpcode() != Opcode.RETURN_OBJECT) body.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
        var method = new ImmutableMethod(type, name, List.of(), name.equals("encoded") || name.equals("table") ? "Ljava/lang/String;" : "V",
                8, Set.of(), Set.of(), new ImmutableMethodImplementation(4, body, List.of(), List.of()));
        return new ImmutableClassDef(type, 1, "Ljava/lang/Object;", List.of(), null, Set.of(), List.of(), List.of(method));
    }
}
