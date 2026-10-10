package f;

import f.ast.Expression;
import f.ast.Program;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Program parse() {
        Token first = peek();
        List<Expression> exprs = new ArrayList<>();
        while (!isAtEnd()) {
            exprs.add(parseExpr());
        }
        return new Program(exprs, first.line, first.column);
    }

    public Expression parseExpr() {
        Token t = peek();
        throw new ParserException("Expression parsing is not implemented", t.line, t.column);
    }

    Token peek() {
        return tokens.get(current);
    }

    Token advance() {
        Token t = tokens.get(current);
        if (!isAtEnd()) {
            current++;
        }
        return t;
    }

    boolean check(TokenType type) {
        return peek().type == type;
    }

    Token expect(TokenType type, String what) {
        Token t = peek();
        if (t.type != type) {
            String found = t.type == TokenType.EOF ? "end of input" : "'" + t.lexeme + "'";
            throw new ParserException("Expected " + what + ", found " + found, t.line, t.column);
        }
        return advance();
    }

    boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }
}
