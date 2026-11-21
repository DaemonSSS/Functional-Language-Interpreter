package f;

import f.ast.Program;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class InterpreterControlTest {
    private final Interpreter interpreter = new Interpreter();

    @Test
    void condEvaluatesOnlyTheSelectedValue() {
        assertValue(integer("7"), """
                (setq flag false)
                (cond (flag (divide 1 0)) (else 7))
                """);
    }

    @Test
    void condUsesTheFirstTrueTestAndReturnsNullWhenNothingMatches() {
        assertValue(integer("2"), "(cond (false 1) (true 2) (else 3))");
        assertValue(Value.NULL, "(cond (false 1) (false 2))");
        assertValue(Value.NULL, "(cond)");
    }

    @Test
    void condRequiresABooleanTestAtRuntime() {
        Program program = parse("(cond (1 2))");
        InterpreterException error = assertThrows(InterpreterException.class,
                () -> interpreter.execute(program));
        assertEquals("Condition must be boolean at 1:8", error.getMessage());
        assertEquals(1, error.line);
        assertEquals(8, error.column);
    }

    @Test
    void whileRechecksItsConditionAndYieldsNull() {
        assertValue(integer("6"), """
                (prog (i total)
                  (setq i 1)
                  (setq total 0)
                  (while (less i 4)
                    (setq total (plus total i))
                    (setq i (plus i 1)))
                  total)
                """);
        assertValue(Value.NULL, "(while false 1)");
        assertValue(Value.NULL, "(setq i 0) (while (less i 3) (setq i (plus i 1)))");
    }

    @Test
    void breakExitsTheNearestLoopAndSkipsTheRestOfItsBody() {
        assertValue(integer("1"), """
                (setq x 0)
                (while true
                  (setq x 1)
                  (break)
                  (setq x 2))
                x
                """);
        assertValue(integer("2"), """
                (setq x 0)
                (while (less x 2)
                  (setq x (plus x 1))
                  (while true (break)))
                x
                """);
    }

    @Test
    void breakInsideProgStillExitsTheSurroundingLoop() {
        assertValue(integer("1"), """
                (setq i 0)
                (while true
                  (setq i (plus i 1))
                  (prog ()
                    (break))
                  (setq i 99))
                i
                """);
    }

    @Test
    void returnExitsOnlyTheNearestProg() {
        assertValue(integer("2"), "(prog () (prog () (return 1)) 2)");
        assertValue(integer("7"), "(prog () 1 (return 7) 2)");
        assertValue(integer("6"), "(plus (prog () (return 5)) 1)");
        assertValue(Value.NULL, "(prog () (return) 7)");
    }

    @Test
    void returnExitsTheNearestFunction() {
        assertValue(integer("4"), "(func stop (n) (return n) 0)\n(stop 4)");
        assertValue(integer("3"), "(func stop () (return 1) 0)\n(prog () (stop) 3)");
        assertValue(integer("5"), "(prog () (while true (return 5)) 9)");
    }

    @Test
    void topLevelReturnEndsTheProgram() {
        assertValue(integer("7"), "(return 7) (divide 1 0)");
        assertValue(Value.NULL, "(return) (divide 1 0)");
    }

    @Test
    void topLevelBreakEndsTheProgramWithoutAnError() {
        assertValue(Value.NULL, "(break) (divide 1 0)");
    }

    @Test
    void whileRequiresABooleanConditionAtRuntime() {
        Program program = parse("(while 1 2)");
        InterpreterException error = assertThrows(InterpreterException.class,
                () -> interpreter.execute(program));
        assertEquals("Condition must be boolean at 1:8", error.getMessage());
        assertEquals(1, error.line);
        assertEquals(8, error.column);
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
}
