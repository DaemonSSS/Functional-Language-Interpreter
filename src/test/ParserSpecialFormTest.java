package f;

import f.ast.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParserSpecialFormTest {

    private Program parse(String source) {
        return new Parser(new Lexer(source).tokenize()).parse();
    }

    private Expression expression(String source) {
        Program program = parse(source);
        assertEquals(1, program.exprs.size());
        return program.exprs.get(0);
    }

    @Test
    void quoteMatchesApostropheSugar() {
        QuoteExpr quote = assertInstanceOf(QuoteExpr.class, expression("(quote x)"));
        assertEquals("x", assertInstanceOf(IdentifierExpr.class, quote.inner).name);
        assertEquals(expression("'x").toString(), quote.toString());
        assertEquals(expression("'(10 20)").toString(), expression("(quote (10 20))").toString());
        assertEquals(1, quote.line);
        assertEquals(2, quote.column);
    }

    @Test
    void setqStoresNameAndValue() {
        SetqExpr setq = assertInstanceOf(SetqExpr.class, expression("(setq x -12)"));
        assertEquals("x", setq.name);
        LiteralExpr value = assertInstanceOf(LiteralExpr.class, setq.value);
        assertEquals(TokenType.INTEGER, value.type);
        assertEquals("-12", value.lexeme);
        assertInstanceOf(QuoteExpr.class,
                assertInstanceOf(SetqExpr.class, expression("(setq data '(10 20))")).value);
    }

    @Test
    void funcStoresNameParametersAndBody() {
        FuncExpr func = assertInstanceOf(FuncExpr.class,
                expression("(func square (x) (times x x) (return x))"));
        assertEquals("square", func.name);
        assertEquals(List.of("x"), func.params);
        assertEquals(2, func.body.size());
        assertInstanceOf(ListExpr.class, func.body.get(0));
        assertInstanceOf(ReturnExpr.class, func.body.get(1));
    }

    @Test
    void lambdaCanBeUsedAsCallOperator() {
        ListExpr call = assertInstanceOf(ListExpr.class, expression("((lambda (x) x) 5)"));
        LambdaExpr lambda = assertInstanceOf(LambdaExpr.class, call.items.get(0));
        assertEquals(List.of("x"), lambda.params);
        assertEquals("x", assertInstanceOf(IdentifierExpr.class, lambda.body.get(0)).name);
        assertEquals(2, call.items.size());
    }

    @Test
    void progStoresLocalsAndBody() {
        ProgExpr prog = assertInstanceOf(ProgExpr.class,
                expression("(prog (i acc) (setq i 1) (while (less i 5) (break)) acc)"));
        assertEquals(List.of("i", "acc"), prog.locals);
        assertEquals(3, prog.body.size());
        assertInstanceOf(SetqExpr.class, prog.body.get(0));
        assertInstanceOf(WhileExpr.class, prog.body.get(1));
        assertEquals("acc", assertInstanceOf(IdentifierExpr.class, prog.body.get(2)).name);
    }

    @Test
    void condStoresTestsValuesAndElseFlag() {
        CondExpr cond = assertInstanceOf(CondExpr.class,
                expression("(cond ((greatereq x 7) true) (else false))"));
        assertEquals(2, cond.clauses.size());
        assertFalse(cond.clauses.get(0).isElse);
        assertInstanceOf(ListExpr.class, cond.clauses.get(0).test);
        assertEquals("true", assertInstanceOf(LiteralExpr.class, cond.clauses.get(0).value).lexeme);
        assertTrue(cond.clauses.get(1).isElse);
        assertEquals("else", assertInstanceOf(IdentifierExpr.class, cond.clauses.get(1).test).name);
        assertEquals("false", assertInstanceOf(LiteralExpr.class, cond.clauses.get(1).value).lexeme);
    }

    @Test
    void whileStoresConditionAndMultipleBodyExpressions() {
        WhileExpr loop = assertInstanceOf(WhileExpr.class,
                expression("(while (less i 5) (setq i (plus i 1)) (break))"));
        assertInstanceOf(ListExpr.class, loop.condition);
        assertEquals(2, loop.body.size());
        assertInstanceOf(SetqExpr.class, loop.body.get(0));
        assertInstanceOf(BreakExpr.class, loop.body.get(1));
    }

    @Test
    void returnAcceptsZeroOrOneValue() {
        assertNull(assertInstanceOf(ReturnExpr.class, expression("(return)")).value);
        ReturnExpr result = assertInstanceOf(ReturnExpr.class, expression("(return x)"));
        assertEquals("x", assertInstanceOf(IdentifierExpr.class, result.value).name);
        assertEquals(TokenType.NULL,
                assertInstanceOf(LiteralExpr.class,
                        assertInstanceOf(ReturnExpr.class, expression("(return null)")).value).type);
    }

    @Test
    void breakBuildsBreakNode() {
        assertEquals("BreakExpr()", assertInstanceOf(BreakExpr.class, expression("(break)")).toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "(quote)", "(quote x y)",
            "(setq)", "(setq x)", "(setq 5 x)", "(setq x 1 2)",
            "(func 5 (x) x)", "(func f (x))", "(func f (1) x)", "(func f x x)",
            "(lambda (x))", "(lambda (1) x)", "(lambda x x)",
            "(prog (x))", "(prog (1) x)", "(prog x x)",
            "(cond ())", "(cond (true))", "(cond (true 1 2))", "(cond true)",
            "(cond (else 1) (true 2))", "(cond (else 1) (else 2))",
            "(while)", "(while true)",
            "(return 1 2)", "(break 1)"
    })
    void rejectsInvalidSpecialFormShapes(String source) {
        ParserException error = assertThrows(ParserException.class, () -> parse(source));
        assertTrue(error.line > 0);
        assertTrue(error.column > 0);
        assertTrue(error.getMessage().endsWith(" at " + error.line + ":" + error.column));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "(quote x", "(setq x 1", "(func f () x", "(lambda () x", "(prog () x",
            "(cond (true 1)", "(while true x", "(return x", "(break",
            "(func f (x", "(cond (true", "(setq x", "(lambda ()"
    })
    void rejectsIncompleteFormsAtEndOfInput(String source) {
        ParserException error = assertThrows(ParserException.class, () -> parse(source));
        assertTrue(error.getMessage().contains("end of input"));
        assertEquals(1, error.line);
        assertEquals(source.length() + 1, error.column);
    }

    @Test
    void reportsBadNamePosition() {
        ParserException error = assertThrows(ParserException.class,
                () -> parse("\n  (func 5 (x) x)"));
        assertEquals("Expected function name after func, found '5' at 2:9", error.getMessage());
        assertEquals(2, error.line);
        assertEquals(9, error.column);
    }

    @Test
    void reportsMissingValuePosition() {
        ParserException error = assertThrows(ParserException.class, () -> parse("(setq x)"));
        assertEquals("Expected value after setq name, found ')' at 1:8", error.getMessage());
    }

    @Test
    void reportsClauseAfterElsePosition() {
        ParserException error = assertThrows(ParserException.class,
                () -> parse("(cond (else 1)\n  (true 2))"));
        assertEquals("else clause must be last in cond at 2:3", error.getMessage());
    }

    @Test
    void allowsEmptyNameListsAndMultipleBodyExpressions() {
        assertTrue(assertInstanceOf(FuncExpr.class, expression("(func f () 1)")).params.isEmpty());
        LambdaExpr lambda = assertInstanceOf(LambdaExpr.class, expression("(lambda () 1 2)"));
        assertTrue(lambda.params.isEmpty());
        assertEquals(2, lambda.body.size());
        assertTrue(assertInstanceOf(ProgExpr.class, expression("(prog () 1)")).locals.isEmpty());
    }

    @Test
    void condAllowsZeroClausesAndOnlyRecognizesBareElse() {
        assertTrue(assertInstanceOf(CondExpr.class, expression("(cond)")).clauses.isEmpty());
        CondExpr cond = assertInstanceOf(CondExpr.class,
                expression("(cond ('else 1) (true 2))"));
        assertFalse(cond.clauses.get(0).isElse);
        assertFalse(cond.clauses.get(1).isElse);
    }

    @Test
    void consumesEachClosingParenthesisAndUsesKeywordPosition() {
        Program program = parse("(quote x)\n(setq x 1)\n(func f () 1)\n(lambda () 1)\n"
                + "(prog () 1)\n(cond)\n(while true 1)\n(return)\n(break)\nx");
        assertEquals(10, program.exprs.size());
        for (int i = 0; i < 9; i++) {
            assertEquals(i + 1, program.exprs.get(i).line);
            assertEquals(2, program.exprs.get(i).column);
        }
        assertInstanceOf(IdentifierExpr.class, program.exprs.get(9));
    }

    @Test
    void leavesSemanticChecksForLaterStages() {
        FuncExpr func = assertInstanceOf(FuncExpr.class, expression("(func f (x x) unknown)"));
        assertEquals(List.of("x", "x"), func.params);
        assertInstanceOf(IdentifierExpr.class, func.body.get(0));
        assertInstanceOf(ListExpr.class, expression("(plus)"));
    }
}
