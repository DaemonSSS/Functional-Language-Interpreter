package f.ast;

public class BreakExpr extends Expression {
    public BreakExpr(int line, int column) {
        super(line, column);
    }

    @Override
    public String toString() {
        return "BreakExpr()";
    }
}
