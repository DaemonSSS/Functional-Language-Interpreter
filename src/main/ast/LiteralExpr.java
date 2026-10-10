package f.ast;

import f.TokenType;

public class LiteralExpr extends Expression {
    public final TokenType type;
    public final String lexeme;

    public LiteralExpr(TokenType type, String lexeme, int line, int column) {
        super(line, column);
        this.type = type;
        this.lexeme = lexeme;
    }

    @Override
    public String toString() {
        return lexeme;
    }
}
