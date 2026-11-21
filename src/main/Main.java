package f;

import f.ast.Program;

import java.util.List;

public class Main {

    private static final String DEFAULT_SOURCE =
            "// F interpreter demo\n"
            + "(func square (x) (times x x))\n"
            + "(setq answer (square 5))\n"
            + "answer";

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--demo")) {
            runDemo();
            return;
        }
        String source = args.length > 0 ? String.join(" ", args) : DEFAULT_SOURCE;
        printSource(source);
    }

    private static void runDemo() {
        String[] sources = {
                "(plus (times 6 7) (divide 18 3))",
                "(func square (x) (times x x))\n(square 5)",
                "(setq x 5)\n(times x x)",
                "(setq x 12)\n(cond ((less 7 x) true) (else false))",
                "(setq flag false)\n(cond (flag (divide 1 0)) (else 7))",
                "(prog () (prog () (return 1)) 2)",
                "(setq x 0)\n(while true (setq x 1) (break) (setq x 2))\nx",
                "(return 7)\n(divide 1 0)",
                "(prog (i total)\n"
                        + "  (setq i 1)\n"
                        + "  (setq total 0)\n"
                        + "  (while (less i 4)\n"
                        + "    (setq total (plus total i))\n"
                        + "    (setq i (plus i 1)))\n"
                        + "  total)"
        };
        for (String source : sources) {
            printSource(source);
            System.out.println();
        }
        String[] runtimeErrors = {
                "(divide 1 0)",
                "(setq divisor 0)\n(divide 1 divisor)"
        };
        for (String source : runtimeErrors) {
            printSource(source);
            System.out.println();
        }
        String[] optimizations = {
                "(not (and true false))",
                "(cond ((not false) 8) (else 0))",
                "(cond (false 1))",
                "(setq flag false)\n"
                        + "(setq x 0)\n"
                        + "(cond (flag (setq x 1)) (true (setq x 2)) (else (setq x 3)))\n"
                        + "x",
                "'(plus missing true)\n(4 minus times divide)\n(eval '(plus 1 2))\n(return 1)"
        };
        for (String source : optimizations) {
            printSource(source);
            System.out.println();
        }
        String[] errors = {"(setq x)", "(func 5 (x) x)", "'()", "(plus 1", "(break 1)"};
        for (String source : errors) {
            printSource(source);
            System.out.println();
        }
        String[] semanticErrors = {"(plus missing 1)", "(func bad (x x) x)", "(plus 1)", "(not 1)"};
        for (String source : semanticErrors) {
            printSource(source);
            System.out.println();
        }
    }

    private static void printSource(String source) {

        System.out.println("Source:");
        System.out.println(source);
        System.out.println();
        System.out.println("Tokens:");

        try {
            List<Token> tokens = new Lexer(source).tokenize();
            for (Token token : tokens) {
                System.out.println("  " + token);
            }
            System.out.println();
            Program program = new Parser(tokens).parse();
            System.out.println("AST:");
            System.out.println("  " + program);
            System.out.println();
            try {
                new Analyzer().check(program);
                System.out.println("Semantic check: OK");
                System.out.println();
                Program optimized = new Optimizer().optimize(program);
                System.out.println("Optimized AST:");
                System.out.println("  " + optimized);
                try {
                    Value result = new Interpreter().execute(optimized);
                    System.out.println();
                    System.out.println("Result: " + result);
                } catch (InterpreterException e) {
                    System.out.println();
                    System.out.println("Runtime error: " + e.getMessage());
                }
            } catch (AnalyzerException e) {
                System.out.println("  Analyzer error: " + e.getMessage());
            }
        } catch (ParserException e) {
            System.out.println("  Parser error: " + e.getMessage());
        } catch (LexerException e) {
            System.out.println("  Lexer error: " + e.getMessage());
        }
    }
}
