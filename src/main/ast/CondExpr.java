package f.ast;

import java.util.List;

public class CondExpr extends Expression {
    public final List<Clause> clauses;

    public CondExpr(List<Clause> clauses, int line, int column) {
        super(line, column);
        this.clauses = clauses;
    }

    @Override
    public String toString() {
        return "CondExpr(" + clauses + ")";
    }

    public static class Clause {
        public final Expression test;
        public final Expression value;
        public final boolean isElse;

        public Clause(Expression test, Expression value, boolean isElse) {
            this.test = test;
            this.value = value;
            this.isElse = isElse;
        }

        @Override
        public String toString() {
            if (isElse) {
                return "Clause(else, " + value + ")";
            }
            return "Clause(" + test + ", " + value + ")";
        }
    }
}
