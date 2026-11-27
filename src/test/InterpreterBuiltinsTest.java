package f;

import f.ast.Program;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

//проверяем все встроенные функции на исходном и оптимизированном дереве
class InterpreterBuiltinsTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "(plus 2 3) | 5", "(minus 2 3) | -1", "(times 2 3) | 6", "(divide -7 3) | -2",
            "(equal 2 2.0) | true", "(nonequal 2 3) | true", "(less 2 3) | true",
            "(lesseq 2 2) | true", "(greater 3 2) | true", "(greatereq 2 2) | true",
            "(equal true false) | false", "(less false true) | true",
            "(not false) | true", "(and true false) | false", "(or false true) | true",
            "(xor true true) | false",
            "(head '(1 2)) | 1", "(tail '(1 2)) | (2)", "(cons 1 '(2 3)) | (1 2 3)",
            "(cons 'x null) | (x)", "(tail '(1)) | ()",
            "(isint 5) | true", "(isreal 5.0) | true", "(isbool false) | true",
            "(isnull null) | true", "(isatom 'x) | true", "(islist '(1 2)) | true",
            "(isint 5.0) | false", "(isreal 5) | false", "(isbool null) | false",
            "(isnull '(1)) | false", "(isatom 5) | false", "(islist 'x) | false",
            "(islist (tail '(1))) | true", "(isnull (tail '(1))) | false",
            "(eval '(plus 1 2)) | 3", "(eval (cons 'plus '(1 2))) | 3",
            "(head '(setq x 1)) | setq", "(tail '(setq x 1)) | (x 1)",
            "(head (head '((1 2) (3 4)))) | 1",
            "(eval (cons 'setq '(x 4))) x | 4",
            "(eval (cons 'cond '((false 1) (else 7)))) | 7",
            "(eval (cons 'quote '(x))) | x",
            "(eval (cons 'func '(identity (x) x))) (identity 8) | 8",
            "(eval (cons 'prog '((x) (setq x 9) x))) | 9",
            "(eval (cons 'lambda '((x) (plus x 1)))) | <lambda>",
            "(eval (cons 'return '(7))) 9 | 7",
            "(eval (cons 'break null)) 9 | null",
            "(eval (cons 'while '(false 1))) | null",
            "(eval (cons 'func (cons 'f (cons (tail '(x)) '(1))))) (f) | 1"
    })
    void executesAllRegisteredOperations(String source, String expected) {
        equivalent(source, expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "(plus 1 2.5) | 3.5", "(minus 1.5 2) | -0.5", "(times 1.25 4) | 5.00",
            "(divide 1.0 4) | 0.25",
            "(divide 1.0 3) | 0.3333333333333333333333333333333333"
    })
    void promotesMixedNumbersAndRoundsRealArithmetic(String source, String expected) {
        Program program = parse(source);
        new Analyzer().check(program);
        for (Program tree : List.of(program, new Optimizer().optimize(program))) {
            Value value = new Interpreter().execute(tree);
            assertEquals(Type.REAL, value.type());
            assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) value.data()));
        }
    }

    @Test
    void keepsLogicalArgumentsEagerAndEvaluatesEachExactlyOnce() {
        equivalent("(setq count 0) (and false (setq count true)) count", "true");
        equivalent("(setq count false) (or true (setq count true)) count", "true");
        equivalent("(setq count 0) (plus (setq count (plus count 1)) (setq count (plus count 1)))", "3");
        equivalent("(setq count 0) (isint (setq count (plus count 1))) count", "1");
        sameRuntimeError("(and false (eval '(divide 1 0)))", "Division by zero");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "(divide 1.0 0.0)", "(divide 1 0.0)", "(divide 1.0 0)",
            "(head (tail '(1)))", "(tail (tail '(1)))",
            "(func f (x) (not x)) (f 1)",
            "(func f (x) (head x)) (f 1)",
            "(func f (x) (cons 1 x)) (f true)",
            "(func f (x) (less x 1)) (f false)",
            "(eval (cons 'setq '(1 2)))", "(eval (cons 'break '(1)))",
            "(eval (cons 'cond '((else 1) (true 2))))",
            "(eval (cons 'lambda '((x x) x)))",
            "(eval (cons 'while '(true)))"
    })
    void reportsTheSamePositionedFailureOnBothTrees(String source) {
        sameRuntimeError(source, null);
    }

    @Test
    void dataListsKeepTheirContentsUnevaluated() {
        equivalent("(setq x 1) (1 (setq x 2) missing) x", "1");
        equivalent("(head (4 missing (divide 1 0)))", "4");
        equivalent("(tail '(1 (plus 2 3)))", "((plus 2 3))");
    }

    @Test
    void evalCanCallAFunctionStoredInAList() {
        equivalent("(func square (x) (times x x)) (eval (cons square '(5)))", "25");
        equivalent("(eval (cons (lambda (x) (plus x 1)) '(5)))", "6");
        equivalent("(head (eval (cons 'quote (cons (cons (lambda (x) x) null) null))))", "<lambda>");
    }

    private Program parse(String source) {
        return new Parser(new Lexer(source).tokenize()).parse();
    }

    private void equivalent(String source, String expected) {
        Program original = parse(source);
        new Analyzer().check(original);
        Value first = new Interpreter().execute(original);
        Value second = new Interpreter().execute(new Optimizer().optimize(original));
        assertEquals(expected, first.toString());
        assertEquals(expected, second.toString());
        assertEquals(first.type(), second.type());
    }

    private void sameRuntimeError(String source, String expectedMessage) {
        Program original = parse(source);
        new Analyzer().check(original);
        InterpreterException first = assertThrows(InterpreterException.class,
                () -> new Interpreter().execute(original));
        InterpreterException second = assertThrows(InterpreterException.class,
                () -> new Interpreter().execute(new Optimizer().optimize(original)));
        assertEquals(first.getMessage(), second.getMessage());
        assertEquals(first.line, second.line);
        assertEquals(first.column, second.column);
        if (expectedMessage != null) {
            assertTrue(first.getMessage().contains(expectedMessage));
        }
    }
}
