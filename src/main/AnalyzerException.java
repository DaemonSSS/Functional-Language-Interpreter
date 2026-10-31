package f;

public class AnalyzerException extends RuntimeException {
    public final int line;
    public final int column;

    public AnalyzerException(String message, int line, int column) {
        super(message + " at " + line + ":" + column);
        this.line = line;
        this.column = column;
    }
}
