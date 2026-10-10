package f.ast;

import java.util.List;

public class WhileExpr extends Expression {
    public final Expression condition;
    public final List<Expression> body;

    public WhileExpr(Expression condition, List<Expression> body, int line, int column) {
        super(line, column);
        this.condition = condition;
        this.body = body;
    }

    @Override
    public String toString() {
        return "WhileExpr(" + condition + ", " + body + ")";
    }
}
