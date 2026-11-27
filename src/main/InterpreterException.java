package f;

import f.ast.Node;

//ошибка выполнения с координатами выражения
public class InterpreterException extends RuntimeException {
    public final int line;
    public final int column;

    public InterpreterException(String message, Node node) {
        super(message + " at " + node.diagnosticLine() + ":" + node.diagnosticColumn());
        this.line = node.diagnosticLine();
        this.column = node.diagnosticColumn();
    }
}
