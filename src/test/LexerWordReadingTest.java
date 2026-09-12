package f;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class LexerWordReadingTest {

    @Test
    public void testIdentifiers() {
        Lexer lexer = new Lexer("x plus head cons greatereq var123");
        List<Token> tokens = lexer.tokenize();
        
        assertEquals(TokenType.IDENTIFIER, tokens.get(0).type);
        assertEquals("x", tokens.get(0).lexeme);
        
        assertEquals(TokenType.IDENTIFIER, tokens.get(1).type);
        assertEquals("plus", tokens.get(1).lexeme);
        
        assertEquals(TokenType.IDENTIFIER, tokens.get(5).type);
        assertEquals("var123", tokens.get(5).lexeme);
    }

    @Test
    public void testMixedCaseFunctionNames() {
        Lexer lexer = new Lexer("myFunc ParseInt calculateSum10");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.IDENTIFIER, tokens.get(0).type);
        assertEquals("myFunc", tokens.get(0).lexeme);

        assertEquals(TokenType.IDENTIFIER, tokens.get(1).type);
        assertEquals("ParseInt", tokens.get(1).lexeme);
    }

    @Test
    public void testKeywords() {
        Lexer lexer = new Lexer("lambda while return break setq cond");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.KEYWORD, tokens.get(0).type);
        assertEquals("lambda", tokens.get(0).lexeme);

        assertEquals(TokenType.KEYWORD, tokens.get(1).type);
        assertEquals("while", tokens.get(1).lexeme);
        
        assertEquals(TokenType.KEYWORD, tokens.get(4).type);
        assertEquals("setq", tokens.get(4).lexeme);
    }

    @Test
    public void testBooleansAndNull() {
        Lexer lexer = new Lexer("true false null");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.BOOLEAN, tokens.get(0).type);
        assertEquals("true", tokens.get(0).lexeme);

        assertEquals(TokenType.BOOLEAN, tokens.get(1).type);
        assertEquals("false", tokens.get(1).lexeme);

        assertEquals(TokenType.NULL, tokens.get(2).type);
        assertEquals("null", tokens.get(2).lexeme);
    }

    @Test
    public void testInvalidUnderscoreThrowsException() {
        Lexer lexer = new Lexer("my_variable");
        
        LexerException exception = assertThrows(LexerException.class, () -> {
            lexer.tokenize();
        });
        
        assertTrue(exception.getMessage().contains("Unexpected character '_'"));
    }

    @Test
    public void testPunctuationAndQuote() {
        Lexer lexer = new Lexer("( ) '");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.LPAREN, tokens.get(0).type);
        assertEquals("(", tokens.get(0).lexeme);

        assertEquals(TokenType.RPAREN, tokens.get(1).type);
        assertEquals(")", tokens.get(1).lexeme);

        assertEquals(TokenType.QUOTE, tokens.get(2).type);
        assertEquals("'", tokens.get(2).lexeme);
    }

    @Test
    public void testWordsWithPunctuation() {
        Lexer lexer = new Lexer("(lambda x)");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.LPAREN, tokens.get(0).type);
        assertEquals(TokenType.KEYWORD, tokens.get(1).type);
        assertEquals("lambda", tokens.get(1).lexeme);
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).type);
        assertEquals("x", tokens.get(2).lexeme);
        assertEquals(TokenType.RPAREN, tokens.get(3).type);
    }
}