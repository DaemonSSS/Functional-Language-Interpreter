package f.ast;

//базовый класс всех выражений языка
public abstract class Expression extends Node implements Cloneable {
    private int diagnosticLine;
    private int diagnosticColumn;

    protected Expression(int line, int column) {
        super(line, column);
        diagnosticLine = line;
        diagnosticColumn = column;
    }

    //замена cond сохраняет позицию всего выражения, не меняя выбранный узел исходного AST
    public final Expression withDiagnosticPosition(Node source) {
        try {
            Expression copy = (Expression) super.clone();
            copy.diagnosticLine = source.diagnosticLine();
            copy.diagnosticColumn = source.diagnosticColumn();
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public final int diagnosticLine() {
        return diagnosticLine;
    }

    @Override
    public final int diagnosticColumn() {
        return diagnosticColumn;
    }
}
