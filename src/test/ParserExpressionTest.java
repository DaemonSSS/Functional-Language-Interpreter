package f;

import f.ast.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ParserExpressionTest {

    private Program parseSource(String source) {
        Lexer lexer = new Lexer(source);
        Parser parser = new Parser(lexer.tokenize());
        return parser.parse();
    }

    @Test
    public void testAtoms() {
        Program prog = parseSource("x 123 3.14 true null");
        assertEquals(5, prog.exprs.size());
        assertInstanceOf(IdentifierExpr.class, prog.exprs.get(0));
        assertEquals("x", ((IdentifierExpr) prog.exprs.get(0)).name);
        
        assertInstanceOf(LiteralExpr.class, prog.exprs.get(1));
        assertEquals("123", ((LiteralExpr) prog.exprs.get(1)).lexeme);
    }

    @Test
    public void testQuoteSugar() {
        Program prog = parseSource("'x");
        assertEquals(1, prog.exprs.size());
        assertInstanceOf(QuoteExpr.class, prog.exprs.get(0));
        QuoteExpr quote = (QuoteExpr) prog.exprs.get(0);
        assertInstanceOf(IdentifierExpr.class, quote.inner);
        assertEquals("x", ((IdentifierExpr) quote.inner).name);
    }

    @Test
    public void testNestedList() {
        Program prog = parseSource("(plus (times 6 7) (divide 18 3))");
        assertEquals(1, prog.exprs.size());
        assertInstanceOf(ListExpr.class, prog.exprs.get(0));
        ListExpr list = (ListExpr) prog.exprs.get(0);
        assertEquals(3, list.items.size()); // plus, nested list 1, nested list 2
        assertInstanceOf(IdentifierExpr.class, list.items.get(0));
        assertEquals("plus", ((IdentifierExpr) list.items.get(0)).name);
    }

    @Test
    public void testEmptyListError() {
        ParserException ex = assertThrows(ParserException.class, () -> {
            parseSource("()");
        });
        assertTrue(ex.getMessage().contains("Empty list"), "Expected empty list error message");
    }

    @Test
    public void testUnclosedParenError() {
        ParserException ex = assertThrows(ParserException.class, () -> {
            parseSource("(plus 1 2");
        });
        assertNotNull(ex);
    }
}