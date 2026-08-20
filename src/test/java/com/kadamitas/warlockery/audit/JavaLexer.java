package com.kadamitas.warlockery.audit;

/**
 * Blanks out everything in a Java source file that is not executable code, in one left to right
 * pass.
 *
 * <p>The hand-rolled sweeps did this with three regex passes in sequence: strip block comments,
 * then line comments, then string literals. Each pass is individually plausible and the order is
 * what breaks them. A {@code //} inside a string literal is treated as a line comment by pass two
 * and eats the rest of the line, and a char literal containing a quote desynchronises the quote
 * pairing in pass three so subsequent code is read as string content, or worse, string content is
 * read as code. That last direction is the dangerous one: it fabricates references that were never
 * written, which is precisely how a dead member gets certified as wired.</p>
 *
 * <p>A single pass with one state variable cannot desynchronise, because a quote inside a comment is
 * never examined as a quote and a slash inside a string is never examined as a comment.</p>
 *
 * <p>Blanked regions are replaced space for space and newlines are preserved, so offsets and line
 * numbers in the result match the original file exactly.</p>
 */
final class JavaLexer {

    private JavaLexer() {
    }

    private enum Mode { CODE, LINE_COMMENT, BLOCK_COMMENT, STRING, TEXT_BLOCK, CHARACTER }

    /**
     * The source with comment bodies and literal contents replaced by spaces. Quotes, comment
     * markers and all code are left in place.
     */
    static String code(final String source) {
        final char[] out = source.toCharArray();
        Mode mode = Mode.CODE;
        int index = 0;
        while (index < source.length()) {
            final char current = source.charAt(index);
            final char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            switch (mode) {
                case CODE -> {
                    if (current == '/' && next == '/') {
                        mode = Mode.LINE_COMMENT;
                        index += 2;
                        continue;
                    }
                    if (current == '/' && next == '*') {
                        mode = Mode.BLOCK_COMMENT;
                        blank(out, index, index + 2);
                        index += 2;
                        continue;
                    }
                    if (current == '"' && next == '"' && charAt(source, index + 2) == '"') {
                        mode = Mode.TEXT_BLOCK;
                        index += 3;
                        continue;
                    }
                    if (current == '"') {
                        mode = Mode.STRING;
                        index++;
                        continue;
                    }
                    if (current == '\'') {
                        mode = Mode.CHARACTER;
                        index++;
                        continue;
                    }
                    index++;
                }
                case LINE_COMMENT -> {
                    if (current == '\n') {
                        mode = Mode.CODE;
                        index++;
                        continue;
                    }
                    out[index] = ' ';
                    index++;
                }
                case BLOCK_COMMENT -> {
                    if (current == '*' && next == '/') {
                        mode = Mode.CODE;
                        blank(out, index, index + 2);
                        index += 2;
                        continue;
                    }
                    if (current != '\n') {
                        out[index] = ' ';
                    }
                    index++;
                }
                case STRING -> {
                    if (current == '\\') {
                        blank(out, index, index + 2);
                        index += 2;
                        continue;
                    }
                    if (current == '"') {
                        mode = Mode.CODE;
                        index++;
                        continue;
                    }
                    out[index] = ' ';
                    index++;
                }
                case CHARACTER -> {
                    if (current == '\\') {
                        blank(out, index, index + 2);
                        index += 2;
                        continue;
                    }
                    if (current == '\'') {
                        mode = Mode.CODE;
                        index++;
                        continue;
                    }
                    out[index] = ' ';
                    index++;
                }
                case TEXT_BLOCK -> {
                    if (current == '\\') {
                        blank(out, index, index + 2);
                        index += 2;
                        continue;
                    }
                    if (current == '"' && next == '"' && charAt(source, index + 2) == '"') {
                        mode = Mode.CODE;
                        index += 3;
                        continue;
                    }
                    if (current != '\n') {
                        out[index] = ' ';
                    }
                    index++;
                }
            }
        }
        return new String(out);
    }

    private static void blank(final char[] out, final int from, final int toExclusive) {
        for (int index = from; index < Math.min(toExclusive, out.length); index++) {
            if (out[index] != '\n') {
                out[index] = ' ';
            }
        }
    }

    private static char charAt(final String source, final int index) {
        return index < source.length() ? source.charAt(index) : '\0';
    }
}
