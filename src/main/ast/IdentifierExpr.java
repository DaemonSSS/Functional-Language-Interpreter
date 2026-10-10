package f.ast;

public class IdentifierExpr extends Expression {
    public final String name;

    public IdentifierExpr(String name, int line, int column) {
        super(line, column);
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
