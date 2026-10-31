package f;

import f.ast.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzerScopeTest {
    private Program parse(String source) {
        return new Parser(new Lexer(source).tokenize()).parse();
    }

    private void check(String source) {
        new Analyzer().check(parse(source));
    }

    @Test
    void acceptsEmptyProgram() {
        assertDoesNotThrow(() -> check(""));
    }

    @Test
    void allowsSequentialDefinitionsAndReassignment() {
        assertDoesNotThrow(() -> check("(setq x 1) x (setq x true) x (setq x (not x))"));
    }

    @Test
    void reportsUnknownIdentifierAndItsPosition() {
        AnalyzerException error = assertThrows(AnalyzerException.class,
                () -> check("(setq x 1)\n  missing"));
        assertEquals("Unknown identifier 'missing' at 2:3", error.getMessage());
        assertEquals(2, error.line);
        assertEquals(3, error.column);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "x (setq x 1)",
            "(setq x (plus x 1))",
            "(missing 1)",
            "(later) (func later () 1)",
            "(plus x (setq x 1))"
    })
    void rejectsDefinitelyMissingNamesInSourceOrder(String source) {
        assertThrows(AnalyzerException.class, () -> check(source));
    }

    @Test
    void checksCallArgumentsFromLeftToRight() {
        assertDoesNotThrow(() -> check("(plus (setq x 1) x)"));
    }

    @Test
    void permitsParametersProgLocalsAndNestedShadowing() {
        assertDoesNotThrow(() -> check("(setq x 5) "
                + "(func f (x) (prog (x y) (setq x 10) y) x) "
                + "(prog (x) (setq x (plus x 1)) x) x"));
    }

    @Test
    void readsOuterValueBeforeCreatingALocalShadow() {
        assertDoesNotThrow(() -> check("(setq x 5) (prog () (setq x (plus x 1)) x) x"));
    }

    @Test
    void makesAllProgLocalsAvailableFromTheStart() {
        assertDoesNotThrow(() -> check("(prog (x y) x y (setq x 1))"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "(prog (hidden) hidden) hidden",
            "(prog () (setq hidden 1)) hidden",
            "(func f (hidden) hidden) hidden",
            "(func f () (setq hidden 1)) hidden",
            "(func f () (setq hidden 1)) (f) (f) hidden",
            "(setq f (lambda () (setq hidden 1))) hidden",
            "((lambda () (setq hidden 1))) hidden"
    })
    void doesNotExposeLocalBindingsOutsideTheirContext(String source) {
        assertThrows(AnalyzerException.class, () -> check(source));
    }

    @Test
    void permitsSelfRecursionForwardFreeNamesAndLambdaValues() {
        assertDoesNotThrow(() -> check("(func f (n) (f n)) "
                + "(func later () global) (setq global 1) "
                + "(setq identity (lambda (x) x)) (identity 2) "
                + "((lambda (x) (plus x 1)) 5) (later)"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "(func f (x x) x)",
            "(lambda (x x) x)",
            "(prog (x x) x)",
            "(cond (false (lambda (x x) x)))"
    })
    void rejectsDuplicateNamesWithinOneNameList(String source) {
        assertThrows(AnalyzerException.class, () -> check(source));
    }

    @Test
    void duplicateErrorsUseTheEnclosingNodePosition() {
        AnalyzerException parameter = assertThrows(AnalyzerException.class,
                () -> check("\n  (func f (x x) x)"));
        assertEquals("Duplicate parameter 'x' at 2:4", parameter.getMessage());
        assertEquals(2, parameter.line);
        assertEquals(4, parameter.column);

        AnalyzerException local = assertThrows(AnalyzerException.class,
                () -> check("(prog (a a) a)"));
        assertEquals("Duplicate local 'a' at 1:2", local.getMessage());
    }

    @Test
    void quotedContentsAndLiteralHeadedListsRemainData() {
        assertDoesNotThrow(() -> check("'missing '(plus unknown true) "
                + "'(func f (x x) missing) (4 minus times divide) "
                + "(1 (lambda (x x) missing))"));
    }

    @Test
    void builtinsAreRecognizedOnlyInCallHeadPosition() {
        assertDoesNotThrow(() -> check("(plus 1 2) (setq plus 3) plus"));
        assertThrows(AnalyzerException.class, () -> check("plus"));
        assertThrows(AnalyzerException.class, () -> check("(cons plus null)"));
    }

    @Test
    void registryContainsAllPredefinedFunctions() {
        for (String name : List.of("plus", "minus", "times", "divide", "head", "tail", "cons",
                "equal", "nonequal", "less", "lesseq", "greater", "greatereq", "isint", "isreal",
                "isbool", "isnull", "isatom", "islist", "and", "or", "xor", "not", "eval")) {
            assertTrue(Builtins.contains(name), name);
            assertDoesNotThrow(() -> check("(" + name + " 1)"));
        }
        assertFalse(Builtins.contains("unknown"));
        assertFalse(Builtins.contains("setq"));
    }

    @Test
    void skipsElseMarkerButChecksClauseValues() {
        assertDoesNotThrow(() -> check("(cond (false 1) (else 2)) (cond)"));
        assertThrows(AnalyzerException.class, () -> check("(cond (else missing))"));
        assertThrows(AnalyzerException.class, () -> check("(cond (false missing) (else 1))"));
        assertThrows(AnalyzerException.class, () -> check("else"));
    }

    @Test
    void condAndWhileDoNotIntroduceLocalContexts() {
        assertDoesNotThrow(() -> check("(cond (true (setq x 1))) x "
                + "(while false (setq y 2))"));
        assertDoesNotThrow(() -> check("(while (setq flag false) flag) flag"));
        assertThrows(AnalyzerException.class, () -> check("(while false (setq y 2)) y"));
    }

    @Test
    void keepsUnknownBranchesAndLoopDefinitionsConservative() {
        assertDoesNotThrow(() -> check("(setq flag true) "
                + "(cond (flag (setq x 1))) x "
                + "(while flag (setq y 2)) y"));
        assertThrows(AnalyzerException.class, () -> check("(setq flag true) "
                + "(cond (flag (setq x 1)) (else x))"));
        assertThrows(AnalyzerException.class, () -> check("(cond (false (setq x 1))) x"));
    }

    @Test
    void carriesTestEffectsToLaterCondClauses() {
        assertDoesNotThrow(() -> check("(cond ((setq x false) 1) (x 2) (else x)) x"));
    }

    @Test
    void defersNamesAfterDynamicEvaluationWithoutEvaluatingItsData() {
        assertDoesNotThrow(() -> check("(eval '(setq dynamic 1)) dynamic"));
        assertThrows(AnalyzerException.class, () -> check("(eval missing)"));
        assertThrows(AnalyzerException.class, () -> check("'(eval anything) missing"));
        assertThrows(AnalyzerException.class, () -> check("(prog () (eval '(setq local 1))) local"));
    }

    @Test
    void allowsTopLevelControlTransfersAndChecksReturnValues() {
        assertDoesNotThrow(() -> check("(return) (return 1) (break) "
                + "(prog () (return 2)) (while true (break))"));
        assertThrows(AnalyzerException.class, () -> check("(return missing)"));
    }

    @Test
    void leavesArityAndTypeChecksForPartTwo() {
        assertDoesNotThrow(() -> check("(plus) (not 1) "
                + "(func f (x) x) (f 1 2) (while 3 (break))"));
    }

    @Test
    void startsWithFreshStateAfterSuccessfulAndFailedChecks() {
        Analyzer analyzer = new Analyzer();
        analyzer.check(parse("(setq x 1)"));
        assertThrows(AnalyzerException.class, () -> analyzer.check(parse("x")));
        assertThrows(AnalyzerException.class, () -> analyzer.check(parse("(setq y 2) missing")));
        assertThrows(AnalyzerException.class, () -> analyzer.check(parse("y")));
        analyzer.check(parse("(setq x 3) x"));
    }

    @Test
    void preservesAllAstListsAndNodeIdentitiesOnSuccessAndFailure() {
        String source = "(setq x 1) (setq data '(missing (plus true false))) "
                + "(func f (p) (setq local p) (return local)) "
                + "(setq g (lambda (q) q)) "
                + "(prog (i) (setq i 0) "
                + "(cond ((less i 2) (setq i (plus i 1))) (else i)) "
                + "(while (less i 2) (break)) (return i))";
        for (boolean failure : List.of(false, true)) {
            Program program = parse(source + (failure ? " missing" : ""));
            List<Object> snapshot = new ArrayList<>();
            rememberTree(program, snapshot);
            String tree = program.toString();
            if (failure) {
                assertThrows(AnalyzerException.class, () -> new Analyzer().check(program));
            } else {
                new Analyzer().check(program);
            }
            assertEquals(tree, program.toString());
            List<Object> after = new ArrayList<>();
            rememberTree(program, after);
            assertEquals(snapshot.size(), after.size());
            for (int i = 0; i < snapshot.size(); i++) {
                assertSame(snapshot.get(i), after.get(i));
            }
        }
    }

    @Test
    void scopeCopiesAndMergesKeepUncertainBindingsDistinct() {
        Scope scope = new Scope(null);
        scope.define("stable", new Scope.Binding(Type.INTEGER, null));
        Scope first = scope.copy();
        Scope second = scope.copy();
        first.define("branch", new Scope.Binding(Type.FUNCTION, 2));
        first.define("stable", new Scope.Binding(Type.BOOLEAN, null));
        scope.merge(List.of(first, second));
        assertEquals(Type.UNKNOWN, scope.find("stable").type);
        assertTrue(scope.find("stable").definite);
        assertFalse(scope.find("branch").definite);
        assertEquals(Type.UNKNOWN, scope.find("branch").type);
        assertNull(scope.find("branch").arity);
        assertNull(second.find("branch"));
    }

    @Test
    void scopeDefinitionsShadowParentsWithoutUpdatingThem() {
        Scope outer = new Scope(null);
        outer.define("f", new Scope.Binding(Type.FUNCTION, 2));
        Scope inner = new Scope(outer);
        assertEquals(2, inner.find("f").arity);
        inner.define("f", new Scope.Binding(Type.INTEGER, null));
        assertEquals(Type.INTEGER, inner.find("f").type);
        assertEquals(Type.FUNCTION, outer.find("f").type);
        Scope copy = inner.copy();
        copy.forgetValues();
        assertEquals(Type.UNKNOWN, copy.find("f").type);
        assertEquals(Type.INTEGER, inner.find("f").type);
        assertEquals(2, outer.find("f").arity);
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
