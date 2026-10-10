package f.ast;

import java.util.List;

public class ListExpr extends Expression {
    public final List<Expression> items;

    public ListExpr(List<Expression> items, int line, int column) {
        super(line, column);
        this.items = items;
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("ListExpr(");
        String sep = "";
        for (Expression item : items) {
            text.append(sep).append(item);
            sep = ", ";
        }
        return text.append(")").toString();
    }
}
