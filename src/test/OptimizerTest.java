package f;

import f.ast.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OptimizerTest {

    private Program parse(String source) {
        return new Parser(new Lexer(source).tokenize()).parse();
    }

    private Program optimize(String source) {
        Program program = parse(source);
        new Analyzer().check(program);
        return new Optimizer().optimize(program);
    }

    private Expression only(String source) {
        Program program = optimize(source);
        assertEquals(1, program.exprs.size());
        return program.exprs.get(0);
    }

    @Test
    void foldsBooleanBuiltinCallsToLiterals() {
        assertEquals("true", ((LiteralExpr) only("(not (and true false))")).lexeme);
        assertEquals("true", ((LiteralExpr) only("(and true true)")).lexeme);
        assertEquals("false", ((LiteralExpr) only("(and true false)")).lexeme);
        assertEquals("true", ((LiteralExpr) only("(or false true)")).lexeme);
        assertEquals("false", ((LiteralExpr) only("(xor true true)")).lexeme);
        assertEquals("true", ((LiteralExpr) only("(not false)")).lexeme);
    }

    @Test
    void foldedLiteralUsesTheReplacedCallPosition() {
        LiteralExpr folded = (LiteralExpr) only("\n  (not false)");
        assertEquals(TokenType.BOOLEAN, folded.type);
        assertEquals("true", folded.lexeme);
        assertEquals(2, folded.line);
        assertEquals(3, folded.column);
    }

    @Test
    void foldsNestedBooleanCallsBottomUp() {
        Expression expression = only("(isbool (and true false))");
        ListExpr call = (ListExpr) expression;
        assertEquals("isbool", ((IdentifierExpr) call.items.get(0)).name);
        assertEquals("false", ((LiteralExpr) call.items.get(1)).lexeme);
    }

    @Test
    void o1EnablesO2OnConstantCondition() {
        Expression expression = only("(cond ((not false) 8) (else 0))");
        LiteralExpr value = (LiteralExpr) expression;
        assertEquals("8", value.lexeme);
        assertEquals(20, value.column);
    }

    @Test
    void keepsCallsWithNonLiteralOperands() {
        Expression expression = only("(and false (eval '(setq x true)))");
        ListExpr call = (ListExpr) expression;
        assertEquals(3, call.items.size());
        ListExpr evalCall = (ListExpr) call.items.get(2);
        assertSame(QuoteExpr.class, evalCall.items.get(1).getClass());

        ListExpr nested = (ListExpr) only("(not (and true (plus 1 2)))");
        assertSame(ListExpr.class, nested.items.get(1).getClass());
    }

    @Test
    void leavesNumericAndComparisonCallsIntact() {
        for (String source : List.of("(plus 1 2)", "(times 2 3)", "(divide 8 2)",
                "(equal 1 1)", "(less 1 2)", "(head '(1))")) {
            Expression expression = only(source);
            assertSame(ListExpr.class, expression.getClass(), source);
        }
    }

    @Test
    void preservesQuotedData() {
        Expression expression = only("'(not false)");
        QuoteExpr quote = (QuoteExpr) expression;
        assertSame(ListExpr.class, quote.inner.getClass());
        ListExpr inner = (ListExpr) quote.inner;
        assertEquals("false", ((LiteralExpr) inner.items.get(1)).lexeme);
    }

    @Test
    void preservesLiteralHeadedDataLists() {
        Expression expression = only("(4 (not false))");
        ListExpr data = (ListExpr) expression;
        assertSame(LiteralExpr.class, data.items.get(0).getClass());
        assertSame(ListExpr.class, data.items.get(1).getClass());

        ListExpr handout = (ListExpr) only("(4 minus times divide)");
        assertEquals(4, handout.items.size());
        assertSame(IdentifierExpr.class, handout.items.get(1).getClass());
    }

    @Test
    void preservesComputedCallHeadsUnchanged() {
        Program program = optimize("(setq plus (lambda (a b) (minus a b)))\n((cond (true plus)) 5 3)");
        ListExpr call = (ListExpr) program.exprs.get(1);
        assertSame(CondExpr.class, call.items.get(0).getClass());
        CondExpr head = (CondExpr) call.items.get(0);
        assertEquals(1, head.clauses.size());
        assertSame(LiteralExpr.class, head.clauses.get(0).test.getClass());
        assertSame(IdentifierExpr.class, head.clauses.get(0).value.getClass());
    }

    @Test
    void doesNotFoldConstantLookingComputedHeads() {
        Expression expression = only("((not false) 1)");
        ListExpr call = (ListExpr) expression;
        assertSame(ListExpr.class, call.items.get(0).getClass());
        ListExpr head = (ListExpr) call.items.get(0);
        assertEquals("false", ((LiteralExpr) head.items.get(1)).lexeme);
    }

    @Test
    void removesConstantFalseCondClauses() {
        assertEquals("2", ((LiteralExpr) only("(cond (false 1) (else 2))")).lexeme);

        Program program = optimize("(setq flag true)\n(cond (flag 1) (false 2) (else 3))");
        CondExpr cond = (CondExpr) program.exprs.get(1);
        assertEquals(2, cond.clauses.size());
        assertSame(IdentifierExpr.class, cond.clauses.get(0).test.getClass());
        assertTrue(cond.clauses.get(1).isElse);
        assertEquals("3", ((LiteralExpr) cond.clauses.get(1).value).lexeme);
    }

    @Test
    void replacesCondWithFirstSelectedValue() {
        assertEquals("8", ((LiteralExpr) only("(cond (true 8) (else 0))")).lexeme);
        assertEquals("7", ((LiteralExpr) only("(cond (else 7))")).lexeme);
        assertEquals("null", ((LiteralExpr) only("(cond (false 1))")).lexeme);
        assertEquals(TokenType.NULL, ((LiteralExpr) only("(cond)")).type);
    }

    @Test
    void keepsUnknownTestsBeforeTrueFallback() {
        Program program = optimize("(setq flag false)\n(setq x 0)\n"
                + "(cond (flag (setq x 1)) (true (setq x 2)) (else (setq x 3)))\nx");
        CondExpr cond = (CondExpr) program.exprs.get(2);
        assertEquals(2, cond.clauses.size());
        assertSame(IdentifierExpr.class, cond.clauses.get(0).test.getClass());
        SetqExpr first = (SetqExpr) cond.clauses.get(0).value;
        assertEquals("1", ((LiteralExpr) first.value).lexeme);
        assertTrue(cond.clauses.get(1).isElse);
        SetqExpr fallback = (SetqExpr) cond.clauses.get(1).value;
        assertEquals("2", ((LiteralExpr) fallback.value).lexeme);
    }

    @Test
    void doesNotRunUnselectedCondValues() {
        assertEquals("7", ((LiteralExpr) only("(cond (false (divide 1 0)) (else 7))")).lexeme);
    }

    @Test
    void removesUnreachableSuffixAfterDirectTransfer() {
        Program program = optimize("(return 1) 2 3");
        assertEquals(1, program.exprs.size());
        assertSame(ReturnExpr.class, program.exprs.get(0).getClass());

        ProgExpr prog = (ProgExpr) only("(prog () (return 1) 2)");
        assertEquals(1, prog.body.size());

        Program loop = optimize("(while true (break) (setq x 1))");
        WhileExpr whileExpr = (WhileExpr) loop.exprs.get(0);
        assertEquals(1, whileExpr.body.size());

        FuncExpr function = (FuncExpr) only("(func f () (return 1) (setq x 2))");
        assertEquals(1, function.body.size());
    }

    @Test
    void keepsCodeReachableAroundNestedTransfers() {
        ProgExpr prog = (ProgExpr) only("(prog () (prog () (return 1)) 2)");
        assertEquals(2, prog.body.size());

        Program program = optimize("(func f () (return 1)) 2");
        assertEquals(2, program.exprs.size());
    }

    @Test
    void neverExecutesEvalAtCompileTime() {
        Expression expression = only("(eval '(plus 1 2))");
        ListExpr call = (ListExpr) expression;
        assertSame(QuoteExpr.class, call.items.get(1).getClass());
    }

    @Test
    void leavesInputAstUnchanged() {
        Program program = parse("(not (and true false))\n(cond (false 1) (else 2))\n"
                + "(setq y (or true false))\n(return 1) 5");
        List<Object> snapshot = new ArrayList<>();
        rememberTree(program, snapshot);
        String tree = program.toString();

        new Optimizer().optimize(program);

        assertEquals(tree, program.toString());
        List<Object> after = new ArrayList<>();
        rememberTree(program, after);
        assertEquals(snapshot.size(), after.size());
        for (int i = 0; i < snapshot.size(); i++) {
            assertSame(snapshot.get(i), after.get(i));
        }
    }

    @Test
    void optimizedTreeRechecksAndIsStructurallyStable() {
        Optimizer optimizer = new Optimizer();
        for (String source : List.of(
                "(not (and true false))",
                "(cond ((not false) 8) (else 0))",
                "(cond (false 1))",
                "(setq flag false) (setq x 0) "
                        + "(cond (flag (setq x 1)) (true (setq x 2)) (else (setq x 3))) x",
                "(setq plus (lambda (a b) (minus a b))) ((cond (true plus)) 5 3)",
                "(prog () (prog () (return 1)) 2)",
                "'(plus missing true) (4 minus times divide) (eval '(plus 1 2)) (return 1)")) {
            Program program = parse(source);
            new Analyzer().check(program);
            Program once = optimizer.optimize(program);
            assertDoesNotThrow(() -> new Analyzer().check(once), source);
            Program twice = optimizer.optimize(once);
            assertEquals(once.toString(), twice.toString(), source);
        }
    }

    @Test
    void foldedBooleansWorkInsideTestsAndConditions() {
        assertEquals("1", ((LiteralExpr) only("(cond ((and true true) 1) (else 2))")).lexeme);
        WhileExpr loop = (WhileExpr) only("(while (not false) (break))");
        assertEquals("true", ((LiteralExpr) loop.condition).lexeme);
    }

    private void rememberTree(Object value, List<Object> snapshot) {
        snapshot.add(value);
        if (value instanceof List<?> list) {
            for (Object item : list) {
                rememberTree(item, snapshot);
            }
        } else if (value instanceof Program program) {
            rememberTree(program.exprs, snapshot);
        } else if (value instanceof ListExpr list) {
            rememberTree(list.items, snapshot);
        } else if (value instanceof QuoteExpr quote) {
            rememberTree(quote.inner, snapshot);
        } else if (value instanceof SetqExpr setq) {
            rememberTree(setq.value, snapshot);
        } else if (value instanceof FuncExpr function) {
            rememberTree(function.params, snapshot);
            rememberTree(function.body, snapshot);
        } else if (value instanceof LambdaExpr lambda) {
            rememberTree(lambda.params, snapshot);
            rememberTree(lambda.body, snapshot);
        } else if (value instanceof ProgExpr prog) {
            rememberTree(prog.locals, snapshot);
            rememberTree(prog.body, snapshot);
        } else if (value instanceof CondExpr cond) {
            rememberTree(cond.clauses, snapshot);
        } else if (value instanceof CondExpr.Clause clause) {
            rememberTree(clause.test, snapshot);
            rememberTree(clause.value, snapshot);
        } else if (value instanceof WhileExpr loop) {
            rememberTree(loop.condition, snapshot);
            rememberTree(loop.body, snapshot);
        } else if (value instanceof ReturnExpr result && result.value != null) {
            rememberTree(result.value, snapshot);
        }
    }
}
