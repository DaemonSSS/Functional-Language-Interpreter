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
        int line = first.type == TokenType.EOF ? 1 : first.line;
        int col = first.type == TokenType.EOF ? 1 : first.column;
        return new Program(exprs, line, col);
    }

    public Expression parseExpr() {
        if (isAtEnd()) {
            Token t = peek();
            throw new ParserException("Unexpected end of input", t.line, t.column);
        }
        Token t = peek();
        if (t.type == TokenType.QUOTE) {
            return parseQuoted();
        } else if (t.type == TokenType.LPAREN) {
            return parseList();
        } else {
            return parseAtom();
        }
    }

    private Expression parseAtom() {
        Token t = peek();
        if (t.type == TokenType.IDENTIFIER) {
            advance();
            return new IdentifierExpr(t.lexeme, t.line, t.column);
        } else if (t.type == TokenType.INTEGER || t.type == TokenType.REAL || 
                   t.type == TokenType.BOOLEAN || t.type == TokenType.NULL) {
            advance();
            return new LiteralExpr(t.type, t.lexeme, t.line, t.column);
        } else {
            throw new ParserException("Expected expression, found '" + t.lexeme + "'", t.line, t.column);
        }
    }

    private Expression parseQuoted() {
        Token quoteToken = advance();
        Expression inner = parseExpr();
        return new QuoteExpr(inner, quoteToken.line, quoteToken.column);
    }

    private Expression parseList() {
        Token open = expect(TokenType.LPAREN, "'('");
        if (check(TokenType.KEYWORD)) {
            Token keyword = advance();
            return parseSpecialForm(keyword);
        }
        List<Expression> items = new ArrayList<>();
        while (!check(TokenType.RPAREN) && !isAtEnd()) {
            items.add(parseExpr());
        }
        if (items.isEmpty()) {
            throw new ParserException("Empty list at " + open.line + ":" + open.column, open.line, open.column);
        }
        expect(TokenType.RPAREN, "')'");
        return new ListExpr(items, open.line, open.column);
    }

    private Expression parseSpecialForm(Token keyword) {
        // This will be implemented by Diliia, but ensures compilation if tested independently.
        throw new ParserException("Special form '" + keyword.lexeme + "' not implemented yet", keyword.line, keyword.column);
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
