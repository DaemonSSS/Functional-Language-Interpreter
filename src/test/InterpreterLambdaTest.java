package f;

import f.ast.Program;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

//лямбды, рекурсия и замыкания должны сохранять окружение и границы return
class InterpreterLambdaTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "((lambda (x) (plus x 1)) 5) | 6",
            "(setq inc (lambda (x) (plus x 1))) (inc 2) | 3",
            "((lambda () 1 2)) | 2",
            "((lambda (x) (return x) 99) 7) | 7",
            "(setq make (lambda (offset) (lambda (x) (plus x offset)))) (setq add (make 10)) (add 2) | 12",
            "(setq x 1) (setq f (lambda () x)) (setq x 2) (f) | 2",
            "(setq f (lambda (n) (cond ((less n 2) 1) (else (times n (f (minus n 1))))))) (f 5) | 120",
            "(func fact (n) (cond ((less n 2) 1) (else (times n (fact (minus n 1)))))) (fact 5) | 120",
            "(func apply (f x) (f x)) (apply (lambda (x) (times x x)) 6) | 36",
            "(prog () ((lambda () (prog () (return 1)) 2)) 3) | 3",
            "(setq x 0) (while true ((lambda () (break))) (setq x 1)) x | 0",
            "(prog () ((lambda () (return 5))) 9) | 9",
            "(setq f (lambda (x) (setq x (plus x 1)) x)) (plus (f 1) (f 1)) | 4"
    })
    void executesClosuresOnOriginalAndOptimizedTrees(String source, String expected) {
        Program program = new Parser(new Lexer(source).tokenize()).parse();
        new Analyzer().check(program);
        Value original = new Interpreter().execute(program);
        Value optimized = new Interpreter().execute(new Optimizer().optimize(program));
        assertEquals(expected, original.toString());
        assertEquals(original, optimized);
    }
}
