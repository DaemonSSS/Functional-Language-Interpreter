package f;

import f.ast.Program;

import java.util.List;

public class Main {

    private static final String DEFAULT_SOURCE =
            "// F language parser demo\n"
            + "(func square (x) (times x x))\n"
            + "(setq answer +42)\n"
            + "(setq pi 3.14)\n"
            + "(setq offset -0.5)\n"
            + "(cond ((greatereq answer +7) true) (else false))\n"
            + "'(head (cons 1 null))";

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
                "(setq data '(10 20 30 40))",
                "(setq x 12)\n(cond ((greatereq x 7) true) (else false))",
                "((lambda (x) (plus x 1)) 5)\n"
                        + "(prog (i acc)\n"
                        + "  (setq i 1)\n"
                        + "  (setq acc 0)\n"
                        + "  (while (less i 5)\n"
                        + "    (setq acc (plus acc i))\n"
                        + "    (setq i (plus i 1)))\n"
                        + "  acc)"
        };
        for (String source : sources) {
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
                System.out.println("Optimized AST:");
                System.out.println("  " + new Optimizer().optimize(program));
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
