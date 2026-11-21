package f;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class InterpreterScopeTest {
    private final Interpreter interpreter = new Interpreter();

    private Value run(String source) {
        return interpreter.execute(new Parser(new Lexer(source).tokenize()).parse());
    }

    @Test
    void evaluatesLiteralsAndEmptyProgram() {
        assertEquals(new Value(Type.INTEGER, new BigInteger("98765432109876543210")), run("+98765432109876543210"));
        assertEquals(new Value(Type.REAL, new BigDecimal("-0.25")), run("-0.25"));
        assertEquals(new Value(Type.BOOLEAN, true), run("true"));
        assertEquals(Value.NULL, run("null"));
        assertEquals(Value.NULL, run(""));
    }

    @Test
    void assignmentReturnsItsValueAndUpdatesTheCurrentBinding() {
        assertEquals(new Value(Type.BOOLEAN, false), run("(setq x 1) (setq x false) x"));
        assertEquals(Value.NULL, run("(setq x null) x"));
        assertEquals(new Value(Type.INTEGER, BigInteger.ONE), run("(setq x 1)"));
    }

    @Test
    void progShadowsTheOuterVariable() {
        String program = "(setq x 5) (prog (x) (setq x 9) x)";
        assertEquals("9", run(program).toString());
        assertEquals("5", run(program + " x").toString());
    }

    @Test
    void assignmentReadsOuterValueBeforeCreatingALocalBinding() {
        String program = "(setq x 5) (prog () (setq x x) (setq copy x) (setq x 9) copy)";
        assertEquals("5", run(program).toString());
        assertEquals("5", run(program + " x").toString());
    }

    @Test
    void uninitializedLocalShadowsAnOuterBinding() {
        InterpreterException error = assertThrows(InterpreterException.class,
                () -> run("(setq x 5)\n(prog (x) x)"));
        assertEquals("Uninitialized variable 'x' at 2:11", error.getMessage());
        assertEquals(2, error.line);
        assertEquals(11, error.column);
    }

    @Test
    void localNamesDisappearAfterProg() {
        InterpreterException error = assertThrows(InterpreterException.class,
                () -> run("(prog () (setq local 3))\nlocal"));
        assertEquals("Unknown identifier 'local' at 2:1", error.getMessage());
    }

    @Test
    void eachExecutionStartsWithAnEmptyEnvironment() {
        run("(setq x 5)");
        InterpreterException error = assertThrows(InterpreterException.class, () -> run("x"));
        assertEquals("Unknown identifier 'x' at 1:1", error.getMessage());
    }

    @Test
    void firstAssignmentCannotReadItsOwnTarget() {
        InterpreterException error = assertThrows(InterpreterException.class, () -> run("(setq x x)"));
        assertEquals("Unknown identifier 'x' at 1:9", error.getMessage());
    }
}
