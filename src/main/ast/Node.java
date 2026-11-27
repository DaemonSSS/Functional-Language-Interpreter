package f.ast;

//общий узел AST с координатами для сообщений об ошибках
public abstract class Node {
    public final int line;
    public final int column;

    protected Node(int line, int column) {
        this.line = line;
        this.column = column;
    }

    //обычный узел сообщает свою позицию
    public int diagnosticLine() {
        return line;
    }

    public int diagnosticColumn() {
        return column;
    }
}
