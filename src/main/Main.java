package f;

import f.ast.Program;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

//читает файл и показывает каждый этап выполнения программы
public class Main {
    private static final Path EXAMPLES = Path.of("examples");

    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    //отдельный метод позволяет проверить CLI без завершения JVM
    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            if (args.length == 0) {
                return runFile(EXAMPLES.resolve("demo.txt"), out, err);
            }
            if (args.length == 1 && (args[0].equals("--help") || args[0].equals("-h"))) {
                printUsage(out);
                return 0;
            }
            if (args.length == 1 && args[0].equals("--demo")) {
                return runDemo(out, err);
            }
            if (args.length == 2 && args[0].equals("--file")) {
                return runFile(Path.of(args[1]), out, err);
            }
            if (args.length >= 2 && args[0].equals("--source")) {
                return printSource(String.join(" ", List.of(args).subList(1, args.length)), out, err);
            }
            if (args.length == 1 && !args[0].startsWith("--")) {
                return runFile(Path.of(args[0]), out, err);
            }
            printUsage(err);
            return 2;
        } catch (IOException | InvalidPathException e) {
            err.println("Input error: " + e.getMessage());
            return 2;
        }
    }

    //UTF-8 сохраняет русские комментарии, BOM в начале файла пропускаем
    private static int runFile(Path path, PrintStream out, PrintStream err) throws IOException {
        String source = Files.readString(path, StandardCharsets.UTF_8);
        if (source.startsWith("\uFEFF")) {
            source = source.substring(1);
        }
        out.println("File: " + path);
        return printSource(source, out, err);
    }

    //каждый txt из examples запускается отдельно, в порядке имён файлов
    private static int runDemo(PrintStream out, PrintStream err) throws IOException {
        List<Path> paths;
        try (var files = Files.list(EXAMPLES)) {
            paths = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .sorted().toList();
        }
        if (paths.isEmpty()) {
            err.println("Input error: no .txt examples in " + EXAMPLES);
            return 2;
        }
        int status = 0;
        for (Path path : paths) {
            status = Math.max(status, runFile(path, out, err));
            out.println();
        }
        return status;
    }

    private static void printUsage(PrintStream out) {
        out.println("Usage: java -cp target/classes f.Main [file.txt]");
        out.println("       java -cp target/classes f.Main --file file.txt");
        out.println("       java -cp target/classes f.Main --source \"(plus 1 2)\"");
        out.println("       java -cp target/classes f.Main --demo");
        out.println("Without arguments: examples/demo.txt. Run from the repository root.");
    }

    //проверки идут до оптимизации, чтобы она не скрыла ошибку в исходнике
    private static int printSource(String source, PrintStream out, PrintStream err) {
        out.println("Source:");
        out.println(source);
        out.println();
        try {
            List<Token> tokens = new Lexer(source).tokenize();
            out.println("Tokens:");
            for (Token token : tokens) {
                out.println("  " + token);
            }
            Program program = new Parser(tokens).parse();
            out.println("\nAST:\n  " + program);
            new Analyzer().check(program);
            out.println("\nSemantic check: OK");
            Program optimized = new Optimizer().optimize(program);
            out.println("\nOptimized AST:\n  " + optimized);
            Value result = new Interpreter().execute(optimized);
            out.println("\nResult: " + result);
            return 0;
        } catch (LexerException e) {
            err.println("Lexer error: " + e.getMessage());
        } catch (ParserException e) {
            err.println("Parser error: " + e.getMessage());
        } catch (AnalyzerException e) {
            err.println("Analyzer error: " + e.getMessage());
        } catch (InterpreterException e) {
            err.println("Runtime error: " + e.getMessage());
        }
        return 1;
    }
}
