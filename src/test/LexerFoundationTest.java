package f;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LexerFoundationTest {

    private static List<Token> tokenize(String src) {
        return new Lexer(src).tokenize();
    }

    @Test
    void emptyInputProducesOnlyEof() {
        List<Token> tokens = tokenize("");
        assertEquals(1, tokens.size());
        assertEquals(TokenType.EOF, tokens.get(0).type);
        assertEquals("", tokens.get(0).lexeme);
    }

    @Test
    void lparenAndRparen() {
        List<Token> tokens = tokenize("()");
        assertEquals(TokenType.LPAREN, tokens.get(0).type);
        assertEquals("(", tokens.get(0).lexeme);
        assertEquals(TokenType.RPAREN, tokens.get(1).type);
        assertEquals(")", tokens.get(1).lexeme);
        assertEquals(TokenType.EOF, tokens.get(2).type);
    }

    @Test
    void quoteToken() {
        List<Token> tokens = tokenize("'");
        assertEquals(TokenType.QUOTE, tokens.get(0).type);
        assertEquals("'", tokens.get(0).lexeme);
    }

    @Test
    void nestedParenthesesSequence() {
        List<Token> tokens = tokenize("(())");
        assertEquals(TokenType.LPAREN, tokens.get(0).type);
        assertEquals(TokenType.LPAREN, tokens.get(1).type);
        assertEquals(TokenType.RPAREN, tokens.get(2).type);
        assertEquals(TokenType.RPAREN, tokens.get(3).type);
        assertEquals(TokenType.EOF, tokens.get(4).type);
    }

    @Test
    void whitespaceIsSkipped() {
        List<Token> tokens = tokenize("   (   )   ");
        assertEquals(TokenType.LPAREN, tokens.get(0).type);
        assertEquals(TokenType.RPAREN, tokens.get(1).type);
        assertEquals(TokenType.EOF, tokens.get(2).type);
    }

    @Test
    void tabsAndCarriageReturnsAreSkipped() {
        List<Token> tokens = tokenize("\t(\r\n)");
        assertEquals(TokenType.LPAREN, tokens.get(0).type);
        assertEquals(TokenType.RPAREN, tokens.get(1).type);
    }

    @Test
    void columnStartsAtOne() {
        List<Token> tokens = tokenize("(");
        assertEquals(1, tokens.get(0).line);
        assertEquals(1, tokens.get(0).column);
    }

    @Test
    void columnAdvancesInsideLine() {
        List<Token> tokens = tokenize("( )");
        assertEquals(1, tokens.get(0).column);
        assertEquals(3, tokens.get(1).column);
    }

    @Test
    void newlineIncrementsLineAndResetsColumn() {
        List<Token> tokens = tokenize("(\n)");
        assertEquals(1, tokens.get(0).line);
        assertEquals(1, tokens.get(0).column);
        assertEquals(2, tokens.get(1).line);
        assertEquals(1, tokens.get(1).column);
    }

    @Test
    void columnAfterLeadingSpacesOnNewLine() {
        List<Token> tokens = tokenize("(\n   )");
        assertEquals(2, tokens.get(1).line);
        assertEquals(4, tokens.get(1).column);
    }

    @Test
    void lineCommentIsSkipped() {
        List<Token> tokens = tokenize("// hello world\n(");
        assertEquals(TokenType.LPAREN, tokens.get(0).type);
        assertEquals(2, tokens.get(0).line);
        assertEquals(1, tokens.get(0).column);
    }

    @Test
    void lineCommentBetweenTokens() {
        List<Token> tokens = tokenize("( // open paren\n)");
        assertEquals(TokenType.LPAREN, tokens.get(0).type);
        assertEquals(TokenType.RPAREN, tokens.get(1).type);
        assertEquals(2, tokens.get(1).line);
    }

    @Test
    void commentAtEndOfFileWithoutNewline() {
        List<Token> tokens = tokenize("( // trailing comment");
        assertEquals(TokenType.LPAREN, tokens.get(0).type);
        assertEquals(TokenType.EOF, tokens.get(1).type);
    }

    @Test
    void multipleCommentsAndLines() {
        List<Token> tokens = tokenize("// one\n// two\n(\n)");
        assertEquals(TokenType.LPAREN, tokens.get(0).type);
        assertEquals(3, tokens.get(0).line);
        assertEquals(TokenType.RPAREN, tokens.get(1).type);
        assertEquals(4, tokens.get(1).line);
    }

    @Test
    void quoteFollowedByListOpening() {
        List<Token> tokens = tokenize("'(");
        assertEquals(TokenType.QUOTE, tokens.get(0).type);
        assertEquals(1, tokens.get(0).column);
        assertEquals(TokenType.LPAREN, tokens.get(1).type);
        assertEquals(2, tokens.get(1).column);
    }

    @Test
    void unexpectedCharacterRaisesLexerException() {
        assertThrows(LexerException.class, () -> tokenize("@"));
    }

    @Test
    void tokenToStringContainsPosition() {
        Token t = new Token(TokenType.LPAREN, "(", 3, 7);
        String s = t.toString();
        assertEquals("LPAREN(\"(\") at 3:7", s);
    }
}
