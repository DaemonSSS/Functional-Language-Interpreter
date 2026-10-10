package f;

public class ParserException extends RuntimeException {
    public final int line;
    public final int column;

    public ParserException(String message, int line, int column) {
        super(message + " at " + line + ":" + column);
        this.line = line;
        this.column = column;
    }
}
