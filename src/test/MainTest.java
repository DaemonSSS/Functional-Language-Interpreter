package f;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

//проверяем чтение файлов, диагностику и коды завершения CLI
class MainTest {
    @TempDir
    Path directory;

    @Test
    void readsUtf8FileWithSpacesInItsName() throws Exception {
        Path file = directory.resolve("пример программы.txt");
        Files.writeString(file, "//комментарий\n(plus 20 22)", StandardCharsets.UTF_8);
        Output output = run(file.toString());
        assertEquals(0, output.status);
        assertTrue(output.out.contains("Result: 42"));
        assertTrue(output.out.contains("Semantic check: OK"));
        assertEquals("", output.err);
    }

    @Test
    void reloadsFileAfterEditingWithoutRecompilation() throws Exception {
        Path file = directory.resolve("demo.txt");
        Files.writeString(file, "(times 5 5)");
        assertTrue(run("--file", file.toString()).out.contains("Result: 25"));
        Files.writeString(file, "(times 8 8)");
        assertTrue(run("--file", file.toString()).out.contains("Result: 64"));
    }

    @Test
    void acceptsBomAndEmptyFiles() throws Exception {
        Path file = directory.resolve("bom.txt");
        Files.writeString(file, "\uFEFF(plus 1 2)");
        assertTrue(run(file.toString()).out.contains("Result: 3"));
        Files.writeString(file, "");
        assertTrue(run(file.toString()).out.contains("Result: null"));
    }

    @Test
    void reportsMissingFileAndMalformedUtf8AsInputErrors() throws Exception {
        Output missing = run(directory.resolve("missing.txt").toString());
        assertEquals(2, missing.status);
        assertTrue(missing.err.startsWith("Input error:"));
        Path invalid = directory.resolve("invalid.txt");
        Files.write(invalid, new byte[]{(byte) 0xc3, 0x28});
        assertEquals(2, run(invalid.toString()).status);
    }

    @Test
    void supportsSourceHelpAndInvalidArguments() {
        assertTrue(run("--source", "(plus 1 2)").out.contains("Result: 3"));
        assertEquals(0, run("--help").status);
        assertEquals(0, run("-h").status);
        for (String[] args : new String[][]{{"--unknown"}, {"--file"}, {"--source"}, {"a", "b"}}) {
            assertEquals(2, run(args).status);
        }
    }

    @Test
    void stopsAtTheFailingStageAndReturnsOne() {
        Output lexer = run("--source", "@");
        assertEquals(1, lexer.status);
        assertTrue(lexer.err.startsWith("Lexer error:"));
        Output parser = run("--source", "(setq x)");
        assertEquals(1, parser.status);
        assertTrue(parser.err.startsWith("Parser error:"));
        Output analyzer = run("--source", "(plus missing 1)");
        assertEquals(1, analyzer.status);
        assertTrue(analyzer.err.startsWith("Analyzer error:"));
        assertFalse(analyzer.out.contains("Optimized AST:"));
        Output runtime = run("--source", "(divide 1 0)");
        assertEquals(1, runtime.status);
        assertTrue(runtime.err.contains("Runtime error: Division by zero at 1:11"));
        assertFalse(runtime.out.contains("Result:"));
    }

    @Test
    void defaultDemoAndAllExampleFilesSucceed() {
        assertTrue(run().out.contains("Result: 25"));
        Output all = run("--demo");
        assertEquals(0, all.status, all.err);
        assertEquals("", all.err);
        assertTrue(all.out.contains("closures.txt"));
        assertTrue(all.out.contains("Result: 120"));
        assertFalse(all.out.contains("errors/runtime.txt"));
    }

    private Output run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = Main.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Output(status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record Output(int status, String out, String err) {
    }
}
