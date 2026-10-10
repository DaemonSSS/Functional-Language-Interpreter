package f.ast;

import java.util.List;

public class ProgExpr extends Expression {
    public final List<String> locals;
    public final List<Expression> body;

    public ProgExpr(List<String> locals, List<Expression> body, int line, int column) {
        super(line, column);
        this.locals = locals;
        this.body = body;
    }

    @Override
    public String toString() {
        return "ProgExpr(" + locals + ", " + body + ")";
    }
}
