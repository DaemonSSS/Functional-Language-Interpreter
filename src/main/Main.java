package f;

import java.util.List;

public class Main {

    private static final String DEFAULT_SOURCE =
            "// F language lexer demo\n"
            + "(func square (x) (times x x))\n"
            + "(setq answer +42)\n"
            + "(setq pi 3.14)\n"
            + "(setq offset -0.5)\n"
            + "(cond ((greatereq answer +7) true) (else false))\n"
            + "'(head (cons 1 null))";

    public static void main(String[] args) {
        String source = args.length > 0 ? String.join(" ", args) : DEFAULT_SOURCE;

        System.out.println("Source:");
        System.out.println(source);
        System.out.println();
        System.out.println("Tokens:");

        try {
            List<Token> tokens = new Lexer(source).tokenize();
            for (Token token : tokens) {
                System.out.println("  " + token);
            }
        } catch (LexerException e) {
            System.out.println("  Lexer error: " + e.getMessage());
        }
    }
}
