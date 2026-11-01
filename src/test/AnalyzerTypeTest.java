package f;

import f.ast.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AnalyzerTypeTest {

    private Program parse(String source) {
        Lexer lexer = new Lexer(source);
        Parser parser = new Parser(lexer.tokenize());
        return parser.parse();
    }

    private void check(String source) {
        Analyzer analyzer = new Analyzer();
        analyzer.check(parse(source));
    }

    @Test
    void testBuiltinArityValid() {
        assertDoesNotThrow(() -> check("(plus 1 2)"));
        assertDoesNotThrow(() -> check("(not true)"));
        assertDoesNotThrow(() -> check("(head '(1 2 3))"));
        assertDoesNotThrow(() -> check("(isint 5)"));
        assertDoesNotThrow(() -> check("(cons 1 null)"));
    }

    @Test
    void testBuiltinArityInvalid() {
        AnalyzerException e1 = assertThrows(AnalyzerException.class, () -> check("(plus 1)"));
        assertTrue(e1.getMessage().contains("Expected 2 arguments"));

        AnalyzerException e2 = assertThrows(AnalyzerException.class, () -> check("(not true false)"));
        assertTrue(e2.getMessage().contains("Expected 1 arguments"));
    }

    @Test
    void testUserFunctionAndLambdaArity() {
        assertDoesNotThrow(() -> check("(func square (x) (times x x)) (square 5)"));
        assertThrows(AnalyzerException.class, () -> check("(func square (x) (times x x)) (square 5 6)"));

        assertDoesNotThrow(() -> check("(func fact (n) (cond ((lesseq n 1) 1) (else (times n (fact (minus n 1)))))) (fact 5)"));

        assertDoesNotThrow(() -> check("((lambda (x) (plus x 1)) 5)"));
        assertThrows(AnalyzerException.class, () -> check("((lambda (x) (plus x 1)) 5 6)"));

        assertDoesNotThrow(() -> check("(setq inc (lambda (x) (plus x 1))) (inc 2)"));
        assertThrows(AnalyzerException.class, () -> check("(setq inc (lambda (x) (plus x 1))) (inc 2 3)"));
    }

    @Test
    void testNumericAndBooleanTypes() {
        assertDoesNotThrow(() -> check("(plus 1 2.5)"));
        assertDoesNotThrow(() -> check("(not false)"));

        AnalyzerException e1 = assertThrows(AnalyzerException.class, () -> check("(plus true 1)"));
        assertTrue(e1.getMessage().contains("Expected numeric argument"));

        AnalyzerException e2 = assertThrows(AnalyzerException.class, () -> check("(not 1)"));
        assertTrue(e2.getMessage().contains("Expected boolean argument"));

        AnalyzerException e3 = assertThrows(AnalyzerException.class, () -> check("(while 3 (break))"));
        assertTrue(e3.getMessage().contains("Condition must be boolean"));
        
        AnalyzerException e4 = assertThrows(AnalyzerException.class, () -> check("(cond (1 2) (else 3))"));
        assertTrue(e4.getMessage().contains("Condition must be boolean"));
    }

    @Test
    void testListOperations() {
        assertDoesNotThrow(() -> check("(cons 1 '(2))"));
        assertDoesNotThrow(() -> check("(cons 1 null)"));
        
        AnalyzerException e1 = assertThrows(AnalyzerException.class, () -> check("(head 1)"));
        assertTrue(e1.getMessage().contains("Expected list argument"));

        AnalyzerException e2 = assertThrows(AnalyzerException.class, () -> check("(cons 1 2)"));
        assertTrue(e2.getMessage().contains("Expected list or null as second argument"));
    }

    @Test
    void testUnknownParametersAndComputedCalls() {
        assertDoesNotThrow(() -> check("(func f (x) (plus x 1))"));

        assertDoesNotThrow(() -> check("((cond (true plus) (else minus)) 1)"));
        
        assertDoesNotThrow(() -> check("(plus (eval '(minus 2 1)) 1)"));
    }

    @Test
    void testIdentifiersAndQuotedData() {
        assertThrows(AnalyzerException.class, () -> check("plus"));
        assertDoesNotThrow(() -> check("(setq plus 1) plus"));

        assertDoesNotThrow(() -> check("'(plus missing true)"));

        assertDoesNotThrow(() -> check("(4 minus times divide)"));
    }

    @Test
    void testAstUnchangedOnFailure() {
        Program program = parse("(plus true)"); 
        ListExpr list = (ListExpr) program.exprs.get(0);
        int initialSize = list.items.size();
        Expression initialArg = list.items.get(1);

        assertThrows(AnalyzerException.class, () -> {
            new Analyzer().check(program);
        });

        assertEquals(initialSize, list.items.size());
        assertSame(initialArg, list.items.get(1));
    }
}