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
}
