package f.ast;

import java.util.List;

public class FuncExpr extends Expression {
    public final String name;
    public final List<String> params;
    public final List<Expression> body;

    public FuncExpr(String name, List<String> params, List<Expression> body, int line, int column) {
        super(line, column);
        this.name = name;
        this.params = params;
        this.body = body;
    }

    @Override
    public String toString() {
        return "FuncExpr(" + name + ", " + params + ", " + body + ")";
    }
}
