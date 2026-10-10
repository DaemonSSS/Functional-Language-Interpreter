package f.ast;

public class ReturnExpr extends Expression {
    public final Expression value;

    public ReturnExpr(Expression value, int line, int column) {
        super(line, column);
        this.value = value;
    }

    @Override
    public String toString() {
        if (value == null) {
            return "ReturnExpr()";
        }
        return "ReturnExpr(" + value + ")";
    }
}
