package f.ast;

public class QuoteExpr extends Expression {
    public final Expression inner;

    public QuoteExpr(Expression inner, int line, int column) {
        super(line, column);
        this.inner = inner;
    }

    @Override
    public String toString() {
        return "QuoteExpr(" + inner + ")";
    }
}
