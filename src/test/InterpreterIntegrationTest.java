package f;

import f.ast.Program;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

//полный конвейер и одинаковое выполнение двух версий дерева
class InterpreterIntegrationTest {
    private final Interpreter interpreter = new Interpreter();

    @Test
    void runsEveryAcceptanceProgramOnOriginalAndOptimizedTrees() {
        assertEquivalent(integer("25"), "(setq x 5) (times x x)");
        assertEquivalent(integer("7"), "(setq flag false) (cond (flag (divide 1 0)) (else 7))");
        assertEquivalent(integer("2"), "(prog () (prog () (return 1)) 2)");
        assertEquivalent(integer("1"), "(setq x 0) (while true (setq x 1) (break) (setq x 2)) x");
        assertEquivalent(integer("7"), "(return 7) (divide 1 0)");
    }

    @Test
    void executesTheLoopDemoAndYieldsSix() {
        String source = """
                (prog (i total)
                  (setq i 1)
                  (setq total 0)
                  (while (less i 4)
                    (setq total (plus total i))
                    (setq i (plus i 1)))
                  total)
                """;
        assertEquivalent(integer("6"), source);
        assertEquivalent(Value.NULL, """
                (prog (i total)
                  (setq i 1)
                  (setq total 0)
                  (while (less i 4)
                    (setq total (plus total i))
                    (setq i (plus i 1))))
                """);
    }

    @Test
    void reportsTheSamePositionedRuntimeErrorOnBothTrees() {
        Program original = parseAndCheck("(divide 1 0)");
        Program optimized = new Optimizer().optimize(original);
        for (Program program : List.of(original, optimized)) {
            InterpreterException error = assertThrows(InterpreterException.class,
                    () -> new Interpreter().execute(program));
            assertEquals("Division by zero at 1:11", error.getMessage());
            assertEquals(1, error.line);
            assertEquals(11, error.column);
        }
    }

    @Test
    void keepsStaticErrorsInTheAnalyzerBeforeExecution() {
        Program program = parse("(plus missing 1)");
        AnalyzerException error = assertThrows(AnalyzerException.class,
                () -> new Analyzer().check(program));
        assertEquals("Unknown identifier 'missing' at 1:7", error.getMessage());
        assertEquals(1, error.line);
        assertEquals(7, error.column);
    }

    @Test
    void startsEachExecutionWithAFreshEnvironment() {
        assertEquals(integer("5"), interpreter.execute(parse("(setq x 5) x")));
        InterpreterException error = assertThrows(InterpreterException.class,
                () -> interpreter.execute(parse("x")));
        assertEquals("Unknown identifier 'x' at 1:1", error.getMessage());
    }

    private Value integer(String value) {
        return new Value(Type.INTEGER, new BigInteger(value));
    }

    //позиция внешнего операнда должна пережить замену cond на его значение
    @Test
    void preservesErrorPositionsWhenConditionalOperandsAreReplaced() {
        for (String source : List.of("(divide 1 (cond (true 0)))",
                "(eval (cond (true (cons 'divide '(1 0)))))",
                "(func invoke (x) (x)) (invoke (cond (true 1)))")) {
            Program original = parseAndCheck(source);
            InterpreterException expected = assertThrows(InterpreterException.class,
                    () -> new Interpreter().execute(original));
            Program optimized = new Optimizer().optimize(original);
            Program twice = new Optimizer().optimize(optimized);
            for (Program tree : List.of(optimized, twice)) {
                InterpreterException actual = assertThrows(InterpreterException.class,
                        () -> new Interpreter().execute(tree));
                assertEquals(expected.getMessage(), actual.getMessage());
            }
        }
    }

    private Program parse(String source) {
        return new Parser(new Lexer(source).tokenize()).parse();
    }

    private Program parseAndCheck(String source) {
        Program program = parse(source);
        new Analyzer().check(program);
        return program;
    }

    private void assertEquivalent(Value expected, String source) {
        Program original = parseAndCheck(source);
        Program optimized = new Optimizer().optimize(original);
        Value originalResult = interpreter.execute(original);
        Value optimizedResult = new Interpreter().execute(optimized);
        assertEquals(expected, originalResult, "Original AST");
        assertEquals(expected, optimizedResult, "Optimized AST");
        assertEquals(originalResult.type(), optimizedResult.type(), "Result type");
    }
}
