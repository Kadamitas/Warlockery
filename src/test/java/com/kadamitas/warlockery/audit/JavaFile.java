package com.kadamitas.warlockery.audit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One parsed Java source file: its package, its imports, the members it declares, and the body text
 * of each method.
 *
 * <p>Bodies are found by brace matching over lexed code, so a brace inside a string or a comment
 * cannot shift the span. An unbalanced file raises rather than yielding a partial parse: the
 * hand-rolled sweeps dropped a method whose braces did not match, which fails open and quietly
 * removes exactly the evidence the sweep was run to find.</p>
 *
 * <p>Field declarations are collected whatever their modifiers, including package private constants
 * and instance fields, which the earlier regex skipped entirely by requiring
 * {@code public|private|protected} followed by {@code static final}.</p>
 */
record JavaFile(
    Path path,
    String packageName,
    String typeName,
    Set<String> importedTypes,
    boolean importsOnDemand,
    Map<String, MemberDeclaration> members,
    Map<String, String> methodBodies,
    String classInitBody,
    List<int[]> declarationRanges,
    String code
) {

    /** Everything a class declares that a caller could reference by name. */
    record MemberDeclaration(String name, Kind kind, boolean engineOverride, int line) {
        enum Kind { METHOD, FIELD }
    }

    /** Field initialisers, constructors and static blocks, which always run if the class is used. */
    static final String CLASS_INIT = "<class-init>";

    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern IMPORT =
        Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([\\w.]+)(\\.\\*)?\\s*;");
    private static final Pattern TYPE_DECLARATION =
        Pattern.compile("\\b(?:class|record|interface|enum)\\s+(\\w+)");
    private static final Pattern FIELD = Pattern.compile(
        "(?m)^[ \\t]+(?:(?:public|protected|private|static|final|transient|volatile|sealed"
            + "|non-sealed|abstract)\\s+)*([\\w.$]+(?:\\s*<[^;=]*?>)?(?:\\s*\\[\\s*\\])*)\\s+"
            + "(\\w+)\\s*(?:=[^=]|;)");
    private static final Set<String> NOT_A_DECLARATION = Set.of(
        "if", "for", "while", "switch", "catch", "try", "synchronized", "do", "else", "return",
        "new", "record", "class", "interface", "enum", "case", "yield", "throw", "assert", "super",
        "this", "import", "package", "instanceof", "extends", "implements", "permits");

    static JavaFile parse(final Path path) {
        try {
            return parse(path, Files.readString(path));
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    static JavaFile parse(final Path path, final String source) {
        final String code = JavaLexer.code(source);
        requireBalancedBraces(path, code);

        final Matcher packageMatch = PACKAGE.matcher(code);
        final String packageName = packageMatch.find() ? packageMatch.group(1) : "";

        final Set<String> importedTypes = new LinkedHashSet<>();
        boolean onDemand = false;
        final Matcher importMatch = IMPORT.matcher(code);
        while (importMatch.find()) {
            if (importMatch.group(2) == null) {
                importedTypes.add(importMatch.group(1));
            } else {
                onDemand = true;
            }
        }

        final String fileName = path.getFileName().toString();
        final String typeName = fileName.endsWith(".java")
            ? fileName.substring(0, fileName.length() - ".java".length())
            : fileName;

        final List<int[]> typeSpans = spans(code, TYPE_DECLARATION);
        final Map<String, MemberDeclaration> members = new LinkedHashMap<>();
        final Map<String, String> bodies = new LinkedHashMap<>();
        final List<int[]> methodSpans = new ArrayList<>();
        final List<int[]> declarationRanges = new ArrayList<>();

        collectMethods(code, typeName, members, bodies, methodSpans, declarationRanges);
        final String classInit =
            collectFields(code, members, methodSpans, typeSpans, declarationRanges);
        bodies.merge(CLASS_INIT, classInit, String::concat);

        return new JavaFile(path, packageName, typeName, Set.copyOf(importedTypes), onDemand,
            Map.copyOf(members), Map.copyOf(bodies), bodies.getOrDefault(CLASS_INIT, ""),
            List.copyOf(declarationRanges), code);
    }

    private static void requireBalancedBraces(final Path path, final String code) {
        int depth = 0;
        for (int index = 0; index < code.length(); index++) {
            final char current = code.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth < 0) {
                    throw new IllegalStateException(
                        path + " closes a brace that was never opened at line " + lineOf(code, index)
                            + "; the lexer or the file is wrong and the result cannot be trusted");
                }
            }
        }
        if (depth != 0) {
            throw new IllegalStateException(
                path + " ends with " + depth + " unclosed brace(s); refusing to report on a partial"
                    + " parse because dropping a method silently hides the evidence");
        }
    }

    private static void collectMethods(
        final String code,
        final String typeName,
        final Map<String, MemberDeclaration> members,
        final Map<String, String> bodies,
        final List<int[]> methodSpans,
        final List<int[]> declarationRanges
    ) {
        int index = 0;
        while (index < code.length()) {
            final char current = code.charAt(index);
            if (!Character.isJavaIdentifierStart(current)
                || (index > 0 && Character.isJavaIdentifierPart(code.charAt(index - 1)))) {
                index++;
                continue;
            }
            int end = index;
            while (end < code.length() && Character.isJavaIdentifierPart(code.charAt(end))) {
                end++;
            }
            final String name = code.substring(index, end);
            if (NOT_A_DECLARATION.contains(name)) {
                index = end;
                continue;
            }
            final int open = skipSpace(code, end);
            if (open >= code.length() || code.charAt(open) != '(' || !precededByType(code, index)) {
                index = end;
                continue;
            }
            final int close = matchParen(code, open);
            if (close < 0) {
                index = end;
                continue;
            }
            final int brace = skipToBrace(code, close + 1);
            if (brace < 0) {
                index = end;
                continue;
            }
            final int bodyEnd = matchBrace(code, brace);
            final String body = code.substring(brace + 1, bodyEnd);
            // A constructor has no caller of its own name to trace, so its body belongs to the
            // always live class-init rather than to a separately reachable method.
            final String owner = name.equals(typeName) || Character.isUpperCase(name.charAt(0))
                ? CLASS_INIT
                : name;
            bodies.merge(owner, body + "\n", String::concat);
            if (!owner.equals(CLASS_INIT)) {
                members.putIfAbsent(name, new MemberDeclaration(name,
                    MemberDeclaration.Kind.METHOD, hasOverride(code, index),
                    lineOf(code, index)));
            }
            methodSpans.add(new int[] {index, bodyEnd + 1});
            declarationRanges.add(new int[] {index, brace});
            index = bodyEnd + 1;
        }
    }

    private static String collectFields(
        final String code,
        final Map<String, MemberDeclaration> members,
        final List<int[]> methodSpans,
        final List<int[]> typeSpans,
        final List<int[]> declarationRanges
    ) {
        final StringBuilder remainder = new StringBuilder();
        int cursor = 0;
        final List<int[]> sorted = new ArrayList<>(methodSpans);
        sorted.sort((left, right) -> Integer.compare(left[0], right[0]));
        for (final int[] span : sorted) {
            if (span[0] > cursor) {
                remainder.append(code, cursor, span[0]).append('\n');
            }
            cursor = Math.max(cursor, span[1]);
        }
        remainder.append(code.substring(cursor));

        final Matcher field = FIELD.matcher(remainder);
        while (field.find()) {
            final String type = field.group(1);
            final String name = field.group(2);
            if (NOT_A_DECLARATION.contains(name) || NOT_A_DECLARATION.contains(type)) {
                continue;
            }
            members.putIfAbsent(name,
                new MemberDeclaration(name, MemberDeclaration.Kind.FIELD, false, 0));
            declarationRanges.add(new int[] {field.start(2), field.end(2)});
        }
        typeSpans.forEach(span -> { });
        return remainder.toString();
    }

    private static boolean precededByType(final String code, final int start) {
        int index = start - 1;
        while (index >= 0 && Character.isWhitespace(code.charAt(index))) {
            index--;
        }
        if (index < 0) {
            return false;
        }
        final char previous = code.charAt(index);
        if (previous == '.' || previous == '=' || previous == '(' || previous == ',') {
            return false;
        }
        return Character.isJavaIdentifierPart(previous) || previous == '>' || previous == ']';
    }

    private static boolean hasOverride(final String code, final int declarationStart) {
        final int from = Math.max(0, declarationStart - 400);
        final String preceding = code.substring(from, declarationStart);
        final int lastBrace = Math.max(preceding.lastIndexOf('{'),
            Math.max(preceding.lastIndexOf('}'), preceding.lastIndexOf(';')));
        return preceding.substring(lastBrace + 1).contains("@Override");
    }

    private static int skipSpace(final String code, final int from) {
        int index = from;
        while (index < code.length() && Character.isWhitespace(code.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int matchParen(final String code, final int open) {
        int depth = 0;
        for (int index = open; index < code.length(); index++) {
            if (code.charAt(index) == '(') {
                depth++;
            } else if (code.charAt(index) == ')') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    /** A method signature may carry a throws clause or type bounds before its body. */
    private static int skipToBrace(final String code, final int from) {
        int index = from;
        while (index < code.length()) {
            final char current = code.charAt(index);
            if (current == '{') {
                return index;
            }
            if (current == ';' || current == '=' || current == ')' || current == '(') {
                return -1;
            }
            if (!Character.isWhitespace(current) && !Character.isJavaIdentifierPart(current)
                && current != ',' && current != '.' && current != '<' && current != '>') {
                return -1;
            }
            index++;
        }
        return -1;
    }

    private static int matchBrace(final String code, final int open) {
        int depth = 0;
        for (int index = open; index < code.length(); index++) {
            if (code.charAt(index) == '{') {
                depth++;
            } else if (code.charAt(index) == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        throw new IllegalStateException("unbalanced braces from offset " + open);
    }

    private static List<int[]> spans(final String code, final Pattern pattern) {
        final List<int[]> found = new ArrayList<>();
        final Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            found.add(new int[] {matcher.start(), matcher.end()});
        }
        return found;
    }

    private static int lineOf(final String code, final int offset) {
        return (int) code.substring(0, Math.min(offset, code.length())).chars()
            .filter(character -> character == '\n').count() + 1;
    }

    /** Whether this file can see the given fully qualified type without an on demand import. */
    boolean canSee(final String qualifiedType) {
        final int lastDot = qualifiedType.lastIndexOf('.');
        final String owningPackage = lastDot < 0 ? "" : qualifiedType.substring(0, lastDot);
        return importedTypes.contains(qualifiedType)
            || packageName.equals(owningPackage)
            || (importsOnDemand && !owningPackage.isEmpty());
    }

    Optional<String> bodyOf(final String member) {
        return Optional.ofNullable(methodBodies.get(member));
    }
}
