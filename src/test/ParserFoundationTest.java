package f;

import f.ast.IdentifierExpr;
import f.ast.ListExpr;
import f.ast.LiteralExpr;
import f.ast.Program;
import f.ast.QuoteExpr;
import f.ast.SetqExpr;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserFoundationTest {

    private static Parser parser(String src) {
        return new Parser(new Lexer(src).tokenize());
    }

    @Test
    void emptySourceYieldsEmptyProgram() {
        Program program = parser("").parse();
        assertTrue(program.exprs.isEmpty());
        assertEquals("Program([])", program.toString());
        assertEquals(1, program.line);
        assertEquals(1, program.column);
    }

    @Test
    void expectReturnsMatchingToken() {
        Parser p = parser("x");
        Token t = p.expect(TokenType.IDENTIFIER, "a name");
        assertEquals(TokenType.IDENTIFIER, t.type);
        assertEquals("x", t.lexeme);
        assertTrue(p.isAtEnd());
    }

    @Test
    void expectFailureMessageShowsWhatAndPosition() {
        Parser p = parser("x");
        ParserException e = assertThrows(ParserException.class,
                () -> p.expect(TokenType.RPAREN, "')'"));
        assertTrue(e.getMessage().contains("Expected ')'"));
        assertTrue(e.getMessage().contains("found 'x'"));
        assertTrue(e.getMessage().contains("at 1:1"));
    }

    @Test
    void expectReportsTokenLineAndColumn() {
        Parser p = parser("(\n  x");
        p.advance();
        ParserException e = assertThrows(ParserException.class,
                () -> p.expect(TokenType.RPAREN, "')'"));
        assertTrue(e.getMessage().contains("at 2:3"));
        assertEquals(2, e.line);
        assertEquals(3, e.column);
    }

    @Test
    void expectAtEndOfInputReportsEndOfInput() {
        Parser p = parser("");
        ParserException e = assertThrows(ParserException.class,
                () -> p.expect(TokenType.RPAREN, "')'"));
        assertTrue(e.getMessage().contains("Expected ')'"));
        assertTrue(e.getMessage().contains("found end of input"));
        assertTrue(e.getMessage().contains("at 1:1"));
    }

    @Test
    void peekCheckAdvanceAndIsAtEnd() {
        Parser p = parser("( x");
        assertEquals(TokenType.LPAREN, p.peek().type);
        assertTrue(p.check(TokenType.LPAREN));
        assertFalse(p.check(TokenType.IDENTIFIER));

        Token open = p.advance();
        assertEquals("(", open.lexeme);
        assertEquals(TokenType.IDENTIFIER, p.peek().type);
        assertFalse(p.isAtEnd());

        p.advance();
        assertTrue(p.isAtEnd());
        assertEquals(TokenType.EOF, p.advance().type);
    }

    @Test
    void setqToStringMatchesContractExample() {
        SetqExpr setq = new SetqExpr("x",
                new LiteralExpr(TokenType.INTEGER, "-12", 1, 10), 1, 2);
        assertEquals("SetqExpr(x, -12)", setq.toString());
        assertEquals(1, setq.line);
        assertEquals(2, setq.column);
    }

    @Test
    void smallHandBuiltAstToString() {
        ListExpr call = new ListExpr(List.of(
                new IdentifierExpr("plus", 1, 2),
                new LiteralExpr(TokenType.INTEGER, "1", 1, 7),
                new LiteralExpr(TokenType.INTEGER, "2", 1, 9)), 1, 1);
        assertEquals("ListExpr(plus, 1, 2)", call.toString());

        QuoteExpr quote = new QuoteExpr(new ListExpr(List.of(
                new LiteralExpr(TokenType.INTEGER, "10", 1, 3),
                new LiteralExpr(TokenType.INTEGER, "20", 1, 6)), 1, 2), 1, 1);
        assertEquals("QuoteExpr(ListExpr(10, 20))", quote.toString());

        Program program = new Program(List.of(
                new SetqExpr("x", new LiteralExpr(TokenType.INTEGER, "-12", 1, 10), 1, 2),
                call), 1, 1);
        assertEquals("Program([SetqExpr(x, -12), ListExpr(plus, 1, 2)])", program.toString());
    }
}
