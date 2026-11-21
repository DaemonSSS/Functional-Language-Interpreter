package f;

import f.ast.Program;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InterpreterCallTest {
    private final Interpreter interpreter = new Interpreter();

    @Test
    void callsNamedFunction() {
        assertValue(integer("25"), "(func square (x) (times x x))\n(square 5)");
    }

    @Test
    void evaluatesBuiltinArgumentsLeftToRight() {
        assertValue(integer("2"), "(setq x 0) (plus (setq x 1) x)");
    }

    @Test
    void evaluatesUserArgumentsOnceInTheCallerEnvironment() {
        assertValue(integer("12"), """
                (setq counter 0)
                (func combine (first second) (plus (times first 10) second))
                (combine (setq counter (plus counter 1)) (setq counter (plus counter 1)))
                """);
    }

    @Test
    void readsFreeVariablesFromTheDefiningEnvironment() {
        assertValue(integer("12"), """
                (setq saved (prog (offset)
                  (setq offset 10)
                  (func add (n) (plus n offset))
                  add))
                (prog (offset) (setq offset 100) (saved 2))
                """);
    }

    @Test
    void usesFreshLocalBindingsForEachCall() {
        assertValue(integer("15"), """
                (setq marker 7)
                (func get (n) (setq marker (plus marker n)) marker)
                (get 3)
                (plus (get 1) marker)
                """);
    }

    @Test
    void defersFunctionBodyAndSupportsZeroParameters() {
        String definition = "(func fail ()\n  (divide 1 0))\n";
        assertValue(integer("7"), definition + "7");
        assertRuntimeError(definition + "(fail)", "Division by zero", 2, 13);
    }

    @Test
    void selectsBuiltinForDirectHeadAndBindingForAnAlias() {
        assertValue(integer("10"), """
                (func plus (a b) (times a b))
                (setq multiply plus)
                (plus (multiply 2 3) 4)
                """);
    }

    @Test
    void usesBigIntegersForArithmeticAndReturnsBooleanForLess() {
        assertValue(integer("9223372036854775808"), "(plus 9223372036854775807 1)");
        assertValue(integer("-18446744073709551616"), "(times 9223372036854775808 -2)");
        assertValue(new Value(Type.BOOLEAN, true), "(less 9223372036854775807 9223372036854775808)");
        assertValue(new Value(Type.BOOLEAN, false), "(less 2 2)");
    }

    @Test
    void dividesIntegersWithTruncationTowardZero() {
        assertValue(integer("3"), "(divide 6 2)");
        assertValue(integer("2"), "(divide 7 3)");
        assertValue(integer("-2"), "(divide -7 3)");
    }

    @Test
    void reportsDivisionByZeroAtTheDivisor() {
        assertRuntimeError("(setq divisor 0)\n(divide 1 divisor)", "Division by zero", 2, 11);
    }

    @Test
    void checksRuntimeArgumentTypes() {
        assertRuntimeError("(func double (x)\n  (times 2 x))\n(double false)",
                "Expected integer argument for 'times'", 2, 12);
    }

    @Test
    void checksRuntimeArityForAnIndirectCall() {
        assertRuntimeError("""
                (func identity (x) x)
                (func invoke (target)
                  (target 1 2))
                (invoke identity)
                """, "Expected 1 arguments for 'identity', got 2", 3, 4);
    }

    @Test
    void checksBuiltinArityWhenExecutingAnUncheckedTree() {
        Program program = parse("(plus 1)");
        InterpreterException error = assertThrows(InterpreterException.class, () -> interpreter.execute(program));
        assertEquals("Expected 2 arguments for 'plus', got 1 at 1:2", error.getMessage());
    }

    @Test
    void reportsValuesThatCannotBeCalled() {
        assertRuntimeError("(setq x null)\n(x 1)", "Expected a function", 2, 2);
    }

    @Test
    void reportsUnsupportedDataListsWithoutEvaluatingTheirItems() {
        assertRuntimeError("(4 minus times divide (divide 1 0))", "Data lists are not supported", 1, 1);
    }

    @Test
    void reportsUnsupportedBuiltinAtTheCallHead() {
        assertRuntimeError("(head '(1 2))", "Unsupported built-in 'head'", 1, 2);
    }

    private Value integer(String value) {
        return new Value(Type.INTEGER, new BigInteger(value));
    }

    private Program parse(String source) {
        return new Parser(new Lexer(source).tokenize()).parse();
    }

    private void assertValue(Value expected, String source) {
        Program original = parse(source);
        new Analyzer().check(original);
        Program optimized = new Optimizer().optimize(original);
        assertEquals(expected, interpreter.execute(original), "Original AST");
        assertEquals(expected, interpreter.execute(optimized), "Optimized AST");
    }

    private void assertRuntimeError(String source, String message, int line, int column) {
        Program original = parse(source);
        new Analyzer().check(original);
        Program optimized = new Optimizer().optimize(original);
        for (Program program : List.of(original, optimized)) {
            InterpreterException error = assertThrows(InterpreterException.class, () -> interpreter.execute(program));
            assertEquals(message + " at " + line + ":" + column, error.getMessage());
            assertEquals(line, error.line);
            assertEquals(column, error.column);
        }
    }
}
