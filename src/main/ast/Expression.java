package f.ast;

public abstract class Expression extends Node {
    protected Expression(int line, int column) {
        super(line, column);
    }
}
