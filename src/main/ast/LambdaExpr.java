package f.ast;

import java.util.List;

public class LambdaExpr extends Expression {
    public final List<String> params;
    public final List<Expression> body;

    public LambdaExpr(List<String> params, List<Expression> body, int line, int column) {
        super(line, column);
        this.params = params;
        this.body = body;
    }

    @Override
    public String toString() {
        return "LambdaExpr(" + params + ", " + body + ")";
    }
}
