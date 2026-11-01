package f;

import f.ast.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AnalyzerIntegrationTest {

    private Program parse(String source) {
        return new Parser(new Lexer(source).tokenize()).parse();
    }

    private void check(String source) {
        new Analyzer().check(parse(source));
    }

    private Program pipeline(String source) {
        Program program = parse(source);
        new Analyzer().check(program);
        Program optimized = new Optimizer().optimize(program);
        assertDoesNotThrow(() -> new Analyzer().check(optimized), source);
        return optimized;
    }

    @Test
    void runsOptimizationExamplesFromTheSharedTestSet() {
        assertEquals("true", ((LiteralExpr) pipeline("(not (and true false))").exprs.get(0)).lexeme);
        assertEquals("8", ((LiteralExpr) pipeline("(cond ((not false) 8) (else 0))").exprs.get(0)).lexeme);
        assertEquals("null", ((LiteralExpr) pipeline("(cond (false 1))").exprs.get(0)).lexeme);
    }

    @Test
    void reportsSemanticErrorsFromTheSharedTestSet() {
        AnalyzerException c1 = assertThrows(AnalyzerException.class, () -> check("(plus missing 1)"));
        assertTrue(c1.getMessage().contains("Unknown identifier 'missing'"));

        AnalyzerException c2param = assertThrows(AnalyzerException.class, () -> check("(func bad (x x) x)"));
        assertTrue(c2param.getMessage().contains("Duplicate parameter 'x'"));

        AnalyzerException c2local = assertThrows(AnalyzerException.class, () -> check("(prog (a a) 1)"));
        assertTrue(c2local.getMessage().contains("Duplicate local 'a'"));

        AnalyzerException c3 = assertThrows(AnalyzerException.class, () -> check("(plus 1)"));
        assertTrue(c3.getMessage().contains("Expected 2 arguments"));

        AnalyzerException c4type = assertThrows(AnalyzerException.class, () -> check("(not 1)"));
        assertTrue(c4type.getMessage().contains("Expected boolean argument"));

        AnalyzerException c4cond = assertThrows(AnalyzerException.class, () -> check("(while 3 (break))"));
        assertTrue(c4cond.getMessage().contains("Condition must be boolean"));
    }

    @Test
    void reportsErrorsBeforeOptimizationCouldEraseTheBranch() {
        assertThrows(AnalyzerException.class, () -> pipeline("(cond (false (plus missing 1)))"));
        assertThrows(AnalyzerException.class, () -> pipeline("(cond (false (not 1)) (else 2))"));
    }

    @Test
    void acceptsKnownSignaturesFromTheSharedTestSet() {
        assertDoesNotThrow(() -> pipeline("(func square (x) (times x x))\n(square 5)"));
        assertDoesNotThrow(() -> pipeline("((lambda (x) (plus x 1)) 5)"));
        assertDoesNotThrow(() -> pipeline("(setq inc (lambda (x) (plus x 1)))\n(inc 2)"));
    }

    @Test
    void preservesSharedDataAndControlCases() {
        Program program = pipeline("'(plus missing true)\n(4 minus times divide)\n"
                + "(eval '(plus 1 2))\n(return 1)");
        QuoteExpr quote = (QuoteExpr) program.exprs.get(0);
        assertSame(ListExpr.class, quote.inner.getClass());
        assertSame(ListExpr.class, program.exprs.get(1).getClass());
        assertSame(ListExpr.class, program.exprs.get(2).getClass());
        assertSame(ReturnExpr.class, program.exprs.get(3).getClass());
    }

    @Test
    void keepsUnknownConditionsAndTheirEffects() {
        Program program = pipeline("(setq flag false)\n(setq x 0)\n"
                + "(cond (flag (setq x 1)) (true (setq x 2)) (else (setq x 3)))\nx");
        CondExpr cond = (CondExpr) program.exprs.get(2);
        assertEquals(2, cond.clauses.size());
        assertEquals("flag", ((IdentifierExpr) cond.clauses.get(0).test).name);
        assertEquals("1", ((LiteralExpr) ((SetqExpr) cond.clauses.get(0).value).value).lexeme);
        assertTrue(cond.clauses.get(1).isElse);
        assertEquals("2", ((LiteralExpr) ((SetqExpr) cond.clauses.get(1).value).value).lexeme);
    }

    @Test
    void keepsIndirectOperatorsAndEvalArgumentsExecuting() {
        Program indirect = pipeline("(setq plus (lambda (a b) (minus a b)))\n((cond (true plus)) 5 3)");
        ListExpr call = (ListExpr) indirect.exprs.get(1);
        assertSame(CondExpr.class, call.items.get(0).getClass());

        Program eager = pipeline("(and false (eval '(setq x true)))");
        ListExpr and = (ListExpr) eager.exprs.get(0);
        assertEquals(3, and.items.size());
    }

    @Test
    void innerReturnDoesNotEscapeItsProg() {
        Program program = pipeline("(prog () (prog () (return 1)) 2)");
        ProgExpr outer = (ProgExpr) program.exprs.get(0);
        assertEquals(2, outer.body.size());
        assertSame(ProgExpr.class, outer.body.get(0).getClass());
    }

    @Test
    void optimizesTheSemanticLoopDemo() {
        Program program = pipeline("((lambda (x) (plus x 1)) 5)\n"
                + "(prog (i acc)\n"
                + "  (setq i 1)\n"
                + "  (setq acc 0)\n"
                + "  (while (less i 5)\n"
                + "    (setq acc (plus acc i))\n"
                + "    (setq i (plus i 1)))\n"
                + "  acc)");
        assertEquals(2, program.exprs.size());
        ProgExpr prog = (ProgExpr) program.exprs.get(1);
        assertEquals(4, prog.body.size());
    }

    @Test
    void preservesNumericCallsUntilExecutionSemanticsAreFixed() {
        Program program = pipeline("(plus (times 6 7) (divide 18 3))");
        ListExpr plus = (ListExpr) program.exprs.get(0);
        assertEquals("plus", ((IdentifierExpr) plus.items.get(0)).name);
        assertSame(ListExpr.class, plus.items.get(1).getClass());
    }

    @Test
    void acceptsTopLevelTransfersAndNestedData() {
        assertDoesNotThrow(() -> pipeline("(return) (return 1) (break) "
                + "(prog () (return 2)) (while true (break))"));
        assertDoesNotThrow(() -> pipeline("(setq data '(10 20 30 40))"));
    }
}
