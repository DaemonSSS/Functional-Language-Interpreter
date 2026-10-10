package f.ast;

public abstract class Node {
    public final int line;
    public final int column;

    protected Node(int line, int column) {
        this.line = line;
        this.column = column;
    }
}
