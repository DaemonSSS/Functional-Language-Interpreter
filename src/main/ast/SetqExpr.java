package f.ast;

public class SetqExpr extends Expression {
    public final String name;
    public final Expression value;

    public SetqExpr(String name, Expression value, int line, int column) {
        super(line, column);
        this.name = name;
        this.value = value;
    }

    @Override
    public String toString() {
        return "SetqExpr(" + name + ", " + value + ")";
    }
}
