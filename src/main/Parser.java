package f;

import f.ast.*;

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
        if (isAtEnd()) {
            throw new ParserException("Unbalanced '(', reached end of input", open.line, open.column);
        }
        if (items.isEmpty()) {
            throw new ParserException("Empty list", open.line, open.column);
        }
        expect(TokenType.RPAREN, "')'");
        return new ListExpr(items, open.line, open.column);
    }

    private Expression parseSpecialForm(Token keyword) {
        switch (keyword.lexeme) {
            case "quote": {
                Expression inner = parseRequiredExpr("expression after quote");
                expect(TokenType.RPAREN, "')' after quote expression");
                return new QuoteExpr(inner, keyword.line, keyword.column);
            }
            case "setq": {
                Token name = expect(TokenType.IDENTIFIER, "variable name after setq");
                Expression value = parseRequiredExpr("value after setq name");
                expect(TokenType.RPAREN, "')' after setq value");
                return new SetqExpr(name.lexeme, value, keyword.line, keyword.column);
            }
            case "func": {
                Token name = expect(TokenType.IDENTIFIER, "function name after func");
                List<String> params = parseParamList();
                List<Expression> body = parseBody();
                expect(TokenType.RPAREN, "')' after func body");
                return new FuncExpr(name.lexeme, params, body, keyword.line, keyword.column);
            }
            case "lambda": {
                List<String> params = parseParamList();
                List<Expression> body = parseBody();
                expect(TokenType.RPAREN, "')' after lambda body");
                return new LambdaExpr(params, body, keyword.line, keyword.column);
            }
            case "prog": {
                List<String> locals = parseParamList();
                List<Expression> body = parseBody();
                expect(TokenType.RPAREN, "')' after prog body");
                return new ProgExpr(locals, body, keyword.line, keyword.column);
            }
            case "cond": {
                List<CondExpr.Clause> clauses = new ArrayList<>();
                boolean seenElse = false;
                while (!check(TokenType.RPAREN) && !isAtEnd()) {
                    if (seenElse) {
                        Token t = peek();
                        throw new ParserException("else clause must be last in cond", t.line, t.column);
                    }
                    expect(TokenType.LPAREN, "'(' before cond clause");
                    Expression test = parseRequiredExpr("test in cond clause");
                    Expression value = parseRequiredExpr("value in cond clause");
                    expect(TokenType.RPAREN, "')' after two expressions in cond clause");
                    boolean isElse = test instanceof IdentifierExpr
                            && ((IdentifierExpr) test).name.equals("else");
                    clauses.add(new CondExpr.Clause(test, value, isElse));
                    seenElse = isElse;
                }
                expect(TokenType.RPAREN, "')' after cond clauses");
                return new CondExpr(clauses, keyword.line, keyword.column);
            }
            case "while": {
                Expression condition = parseRequiredExpr("condition after while");
                List<Expression> body = parseBody();
                expect(TokenType.RPAREN, "')' after while body");
                return new WhileExpr(condition, body, keyword.line, keyword.column);
            }
            case "return": {
                Expression value = check(TokenType.RPAREN) ? null : parseRequiredExpr("return value or ')'");
                expect(TokenType.RPAREN, "')' after optional return value");
                return new ReturnExpr(value, keyword.line, keyword.column);
            }
            case "break": {
                expect(TokenType.RPAREN, "')' after break (no arguments)");
                return new BreakExpr(keyword.line, keyword.column);
            }
            default:
                throw new ParserException("Unknown special form '" + keyword.lexeme + "'",
                        keyword.line, keyword.column);
        }
    }

    private List<String> parseParamList() {
        expect(TokenType.LPAREN, "'(' before name list");
        List<String> names = new ArrayList<>();
        while (!check(TokenType.RPAREN) && !isAtEnd()) {
            names.add(expect(TokenType.IDENTIFIER, "identifier in name list").lexeme);
        }
        expect(TokenType.RPAREN, "')' after name list");
        return names;
    }

    private List<Expression> parseBody() {
        List<Expression> body = new ArrayList<>();
        body.add(parseRequiredExpr("at least one expression in body"));
        while (!check(TokenType.RPAREN) && !isAtEnd()) {
            body.add(parseExpr());
        }
        return body;
    }

    private Expression parseRequiredExpr(String what) {
        if (check(TokenType.RPAREN) || isAtEnd()) {
            Token t = peek();
            String found = isAtEnd() ? "end of input" : "'" + t.lexeme + "'";
            throw new ParserException("Expected " + what + ", found " + found, t.line, t.column);
        }
        return parseExpr();
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
