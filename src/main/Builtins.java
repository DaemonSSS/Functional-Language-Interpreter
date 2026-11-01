package f;

import java.util.Set;

public final class Builtins {
    private static final Set<String> NAMES = Set.of(
            "plus", "minus", "times", "divide",
            "head", "tail", "cons",
            "equal", "nonequal", "less", "lesseq", "greater", "greatereq",
            "isint", "isreal", "isbool", "isnull", "isatom", "islist",
            "and", "or", "xor", "not", "eval");

    private Builtins() {
    }

    public static boolean contains(String name) {
        return NAMES.contains(name);
    }

    public static Integer getArity(String name) {
        return switch (name) {
            case "head", "tail", "isint", "isreal", "isbool",
                 "isnull", "isatom", "islist", "not", "eval" -> 1;
            case "plus", "minus", "times", "divide", "cons",
                 "equal", "nonequal", "less", "lesseq", "greater",
                 "greatereq", "and", "or", "xor" -> 2;
            default -> null;
        };
    }

    public static Type getReturnType(String name) {
        return switch (name) {
            case "equal", "nonequal", "less", "lesseq", "greater",
                 "greatereq", "isint", "isreal", "isbool", "isnull",
                 "isatom", "islist", "and", "or", "xor", "not" -> Type.BOOLEAN;
            case "cons", "tail" -> Type.LIST;
            default -> Type.UNKNOWN;
        };
    }
}
