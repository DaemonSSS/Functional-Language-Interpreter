package f;

import java.util.ArrayList;
import java.util.List;

public class Lexer {

    private final String source;
    private int current = 0;
    private int line = 1;
    private int column = 1;
    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        Token t;
        do {
            t = nextToken();
            tokens.add(t);
        } while (t.type != TokenType.EOF);
        return tokens;
    }

    private Token nextToken() {
        skipWhitespaceAndComments();
        if (isAtEnd()) {
            return new Token(TokenType.EOF, "", line, column);
        }
        int startLine = line;
        int startColumn = column;
        char c = peek();
        switch (c) {
            case '(':
                advance();
                return new Token(TokenType.LPAREN, "(", startLine, startColumn);
            case ')':
                advance();
                return new Token(TokenType.RPAREN, ")", startLine, startColumn);
            case '\'':
                advance();
                return new Token(TokenType.QUOTE, "'", startLine, startColumn);
        }
        if (Character.isLetter(c)) {
            return readWord(startLine, startColumn);
        }
        if (Character.isDigit(c)) {
            return readNumber(startLine, startColumn);
        }
        if ((c == '+' || c == '-') && Character.isDigit(peekNext())) {
            return readNumber(startLine, startColumn);
        }

        throw new LexerException(
                "Unexpected character '" + c + "' at " + startLine + ":" + startColumn);
    }

    private void skipWhitespaceAndComments() {
        while (!isAtEnd()) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                advance();
            } else if (c == '/' && peekNext() == '/') {
                while (!isAtEnd() && peek() != '\n') {
                    advance();
                }
            } else {
                break;
            }
        }
    }

    private char peek() {
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private char advance() {
        char c = source.charAt(current);
        current++;
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return c;
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private Token readWord(int startLine, int startColumn) {
        StringBuilder text = new StringBuilder();

        while (!isAtEnd()) {
            char c = peek();
            if (Character.isLetter(c) || Character.isDigit(c)) {
                text.append(advance());
            } else {
                break;
            }
        }

        String lexeme = text.toString();
        TokenType type;

        switch (lexeme) {
            case "quote":
            case "setq":
            case "func":
            case "lambda":
            case "prog":
            case "cond":
            case "while":
            case "return":
            case "break":
                type = TokenType.KEYWORD;
                break;
            case "true":
            case "false":
                type = TokenType.BOOLEAN;
                break;
            case "null":
                type = TokenType.NULL;
                break;
            default:
                type = TokenType.IDENTIFIER;
                break;
        }

        return new Token(type, lexeme, startLine, startColumn);
    }

    //часть рушана
    private Token readNumber(int startLine, int startColumn) {
        throw new UnsupportedOperationException("readNumber() — to be implemented by Rushan");
    }
}
