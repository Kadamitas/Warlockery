package com.kadamitas.warlockery.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The lexer contract, with the defective three pass strip reproduced so the difference is explicit.
 *
 * <p>The sweeps this replaces stripped block comments, then line comments, then string literals, in
 * that order. Each pass is plausible alone. Run in sequence over real Java they corrupt each other's
 * input, and the corruption is not symmetric: it can both delete real references and invent
 * references that were never written.</p>
 */
final class JavaLexerTest {

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\n]*");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");

    /** Exactly what the hand-rolled sweeps did. */
    private static String defectiveThreePassStrip(final String source) {
        String stripped = BLOCK_COMMENT.matcher(source).replaceAll(" ");
        stripped = LINE_COMMENT.matcher(stripped).replaceAll(" ");
        return STRING_LITERAL.matcher(stripped).replaceAll("\"\"");
    }

    private static boolean mentions(final String code, final String name) {
        return Pattern.compile("(?<![\\w.])" + Pattern.quote(name) + "\\b").matcher(code).find();
    }

    @Test
    void commentBodiesAndLiteralContentsAreBlankedAndCodeSurvives() {
        final String source = """
            int keep = MAX_READS; // note about SOMETHING_ELSE
            /* block mentioning ANOTHER_THING */
            String label = "TEXT_INSIDE";
            """;
        final String code = JavaLexer.code(source);
        assertTrue(mentions(code, "MAX_READS"));
        assertFalse(mentions(code, "SOMETHING_ELSE"));
        assertFalse(mentions(code, "ANOTHER_THING"));
        assertFalse(mentions(code, "TEXT_INSIDE"));
    }

    @Test
    void offsetsAndLineNumbersAreUnchanged() {
        final String source = """
            /* a comment
               spanning lines */
            int x = 1; // trailing
            String s = "content";
            """;
        final String code = JavaLexer.code(source);
        assertEquals(source.length(), code.length(), "blanking is space for space");
        assertEquals(source.chars().filter(character -> character == '\n').count(),
            code.chars().filter(character -> character == '\n').count(),
            "newlines survive so line numbers still resolve");
    }

    /**
     * The dangerous direction: fabricating a reference that was never written.
     *
     * <p>A char literal holding a double quote desynchronises the third pass's quote pairing. The
     * regex pairs the quote inside the char literal with the opening quote of the next real string,
     * blanks the code between them, and leaves the string's contents standing as bare code. A name
     * that only ever appeared inside a string literal is then indistinguishable from a live
     * reference, which is exactly how a dead member gets certified as wired.</p>
     */
    @Test
    void redAQuoteInsideACharLiteralMakesTheThreePassStripInventAReference() {
        final String source = "char quote = '\"'; String name = \"MAX_STATE_BYTES\";";

        final String defective = defectiveThreePassStrip(source);
        assertTrue(mentions(defective, "MAX_STATE_BYTES"),
            "the defective strip left string content standing as code");

        final String code = JavaLexer.code(source);
        assertFalse(mentions(code, "MAX_STATE_BYTES"),
            "one left to right pass never examines a quote inside a char literal as a quote");
    }

    /**
     * The other direction: deleting a real reference.
     *
     * <p>A {@code //} inside a string literal is seen by the line comment pass, which eats the rest
     * of the line including any genuine reference that followed it.</p>
     */
    @Test
    void redASlashSlashInsideAStringMakesTheThreePassStripEatRealCode() {
        final String source = "String url = \"scheme://host\"; int keep = MAX_READS;";

        final String defective = defectiveThreePassStrip(source);
        assertFalse(mentions(defective, "MAX_READS"),
            "the defective strip swallowed a genuine reference");

        final String code = JavaLexer.code(source);
        assertTrue(mentions(code, "MAX_READS"));
        assertFalse(mentions(code, "scheme"));
    }

    @Test
    void redABlockCommentTerminatorInsideAStringDoesNotEndTheComment() {
        final String source = "String pattern = \"*/\"; int keep = MAX_READS;";
        final String code = JavaLexer.code(source);
        assertTrue(mentions(code, "MAX_READS"));
    }

    @Test
    void aStringInsideABlockCommentIsNotAString() {
        final String source = "/* he said \"MAX_STATE_BYTES\" */ int keep = MAX_READS;";
        final String code = JavaLexer.code(source);
        assertFalse(mentions(code, "MAX_STATE_BYTES"));
        assertTrue(mentions(code, "MAX_READS"));
    }

    @Test
    void escapedQuotesDoNotTerminateTheirLiteral() {
        final String source = "String s = \"a \\\" MAX_STATE_BYTES b\"; int keep = MAX_READS;";
        final String code = JavaLexer.code(source);
        assertFalse(mentions(code, "MAX_STATE_BYTES"));
        assertTrue(mentions(code, "MAX_READS"));
    }

    @Test
    void escapedBackslashAtTheEndOfALiteralDoesNotSwallowTheClosingQuote() {
        final String source = "String s = \"trailing \\\\\"; int keep = MAX_READS;";
        final String code = JavaLexer.code(source);
        assertTrue(mentions(code, "MAX_READS"));
    }

    @Test
    void textBlocksAreBlankedWithoutTheirQuotesDesynchronising() {
        final String source = """
            String block = ""\"
                MAX_STATE_BYTES and a " quote
                ""\";
            int keep = MAX_READS;
            """;
        final String code = JavaLexer.code(source);
        assertFalse(mentions(code, "MAX_STATE_BYTES"));
        assertTrue(mentions(code, "MAX_READS"));
    }

    @Test
    void aCharLiteralHoldingABackslashOrASingleQuoteIsHandled() {
        final String source = "char a = '\\\\'; char b = '\\''; int keep = MAX_READS;";
        final String code = JavaLexer.code(source);
        assertTrue(mentions(code, "MAX_READS"));
    }

    @Test
    void bracesInsideLiteralsAndCommentsDoNotShiftBraceMatching() {
        final String source = """
            class Sample {
                void method() {
                    String open = "{";
                    // }
                    /* } */
                }
            }
            """;
        final String code = JavaLexer.code(source);
        int depth = 0;
        for (final char character : code.toCharArray()) {
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
            }
        }
        assertEquals(0, depth, "brace depth is balanced once literals and comments are blanked");
    }
}
