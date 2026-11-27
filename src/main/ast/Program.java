package f.ast;

import java.util.List;

//корень AST, хранит выражения в порядке выполнения
public class Program extends Node {
    public final List<Expression> exprs;

    public Program(List<Expression> exprs, int line, int column) {
        super(line, column);
        this.exprs = exprs;
    }

    @Override
    public String toString() {
        return "Program(" + exprs + ")";
    }
}
