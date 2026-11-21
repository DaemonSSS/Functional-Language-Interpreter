package f;

import f.ast.Node;

public class InterpreterException extends RuntimeException {
    public final int line;
    public final int column;

    public InterpreterException(String message, Node node) {
        super(message + " at " + node.line + ":" + node.column);
        this.line = node.line;
        this.column = node.column;
    }
}
