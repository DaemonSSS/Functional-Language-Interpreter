package f;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LexerNumberTest {

    private static List<Token> tokenize(String src) {
        return new Lexer(src).tokenize();
    }

    @Test
    void positiveInteger() {
        List<Token> tokens = tokenize("123");
        assertEquals(TokenType.INTEGER, tokens.get(0).type);
        assertEquals("123", tokens.get(0).lexeme);
    }

    @Test
    void negativeInteger() {
        List<Token> tokens = tokenize("-45");
        assertEquals(TokenType.INTEGER, tokens.get(0).type);
        assertEquals("-45", tokens.get(0).lexeme);
    }

    @Test
    void explicitlyPositiveInteger() {
        List<Token> tokens = tokenize("+7");
        assertEquals(TokenType.INTEGER, tokens.get(0).type);
        assertEquals("+7", tokens.get(0).lexeme);
    }

    @Test
    void mixedIntegers() {
        List<Token> tokens = tokenize("123   -45   +7");
        assertEquals(TokenType.INTEGER, tokens.get(0).type);
        assertEquals("123", tokens.get(0).lexeme);
        assertEquals(TokenType.INTEGER, tokens.get(1).type);
        assertEquals("-45", tokens.get(1).lexeme);
        assertEquals(TokenType.INTEGER, tokens.get(2).type);
        assertEquals("+7", tokens.get(2).lexeme);
    }

    @Test
    void realNumbers() {
        List<Token> tokens = tokenize("3.14 -0.5 +12.25");
        assertEquals(TokenType.REAL, tokens.get(0).type);
        assertEquals("3.14", tokens.get(0).lexeme);
        assertEquals(TokenType.REAL, tokens.get(1).type);
        assertEquals("-0.5", tokens.get(1).lexeme);
        assertEquals(TokenType.REAL, tokens.get(2).type);
        assertEquals("+12.25", tokens.get(2).lexeme);
    }

    @Test
    void multiDigitRealParts() {
        List<Token> tokens = tokenize("123.456");
        assertEquals(TokenType.REAL, tokens.get(0).type);
        assertEquals("123.456", tokens.get(0).lexeme);
    }

    @Test
    void numberCoordinatesAreTracked() {
        List<Token> tokens = tokenize("(\n  42)");
        Token fortyTwo = tokens.get(1);
        assertEquals(TokenType.INTEGER, fortyTwo.type);
        assertEquals(2, fortyTwo.line);
        assertEquals(3, fortyTwo.column);
    }

    @Test
    void numberAfterCommentHasCorrectCoordinates() {
        List<Token> tokens = tokenize("// header\n+12.25");
        Token number = tokens.get(0);
        assertEquals(TokenType.REAL, number.type);
        assertEquals("+12.25", number.lexeme);
        assertEquals(2, number.line);
        assertEquals(1, number.column);
    }

    @Test
    void trailingDecimalPointIsError() {
        LexerException e = assertThrows(LexerException.class, () -> tokenize("12."));
        assertTrue(e.getMessage().contains("12."));
        assertTrue(e.getMessage().contains("1:1"));
    }

    @Test
    void leadingDecimalPointIsError() {
        LexerException e = assertThrows(LexerException.class, () -> tokenize(".5"));
        assertTrue(e.getMessage().contains("Unexpected character '.'"));
        assertTrue(e.getMessage().contains("1:1"));
    }

    @Test
    void unexpectedCharacterIsError() {
        LexerException e = assertThrows(LexerException.class, () -> tokenize("@"));
        assertTrue(e.getMessage().contains("Unexpected character '@'"));
        assertTrue(e.getMessage().contains("1:1"));
    }

    @Test
    void signWithoutDigitsIsError() {
        LexerException e = assertThrows(LexerException.class, () -> tokenize("-x"));
        assertTrue(e.getMessage().contains("Unexpected character '-'"));
    }

    @Test
    void errorCoordinatesReflectLineAndColumn() {
        LexerException e = assertThrows(LexerException.class, () -> tokenize("(\n  @"));
        assertTrue(e.getMessage().contains("2:3"));
    }
}
