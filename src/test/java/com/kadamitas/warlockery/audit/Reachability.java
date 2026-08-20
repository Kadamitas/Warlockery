package com.kadamitas.warlockery.audit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Decides, for each member a family declares, whether live production code can reach it.
 *
 * <p>This replaces the per family sweeps. Three successive versions of one of those overclaimed,
 * and the failure mode was always the same: a bare name found somewhere in the tree was accepted as
 * a call. Under that rule {@code CircleMageRules.MAX_STATE_BYTES} was certified as wired by the
 * declaration of the identically named and equally dead {@code HedgeCroneRules.MAX_STATE_BYTES},
 * plus an unrelated {@code BansheeRules.MAX_STATE_BYTES}. No receiver, no import check, no filter
 * for the foreign file's own declarations.</p>
 *
 * <p>Three rules make the answers sound.</p>
 *
 * <ul>
 *   <li>A reference from another file counts only when it is qualified by the owning type
 *       ({@code HedgeCroneRules.MAX_STATE_BYTES}) and that file can actually see the type through an
 *       import or a shared package. A bare name in a foreign file resolves to nothing.</li>
 *   <li>The foreign file's own declarations are excluded, so one dead constant cannot vouch for
 *       another of the same name.</li>
 *   <li>Engine entry points root only when the declaring class is genuinely registered in
 *       {@code ModEntities}, and the entry set is read from {@code @Override} rather than a
 *       hardcoded list of method names.</li>
 * </ul>
 *
 * <p>The verdict is three way on purpose. {@link Verdict.Unresolved} is what an honest checker says
 * when a bare name does appear but nothing ties it to this type, typically an instance call through
 * a receiver whose type only a compiler could know. Reporting those as clean is how the earlier
 * instruments produced zero findings and were believed.</p>
 */
final class Reachability {

    /** What can be said about one member. */
    sealed interface Verdict {

        String member();

        /** Live production code reaches it, by the named route. */
        record Reachable(String member, String via) implements Verdict {}

        /**
         * Nothing anywhere references it, not even as a bare name once declarations are excluded.
         * A sound negative: this member is dead.
         */
        record Unreachable(String member, Set<String> seenOnlyIn) implements Verdict {}

        /**
         * The name appears in live code that this analysis cannot tie to this type. Neither wired
         * nor proven dead; a human must look.
         */
        record Unresolved(String member, String why, Set<String> candidates) implements Verdict {}

        /** Reached only from unit tests, which is ceremony rather than behaviour. */
        record TestOnly(String member, Set<String> tests) implements Verdict {}

        /** Reached only from a GameTest fixture, which is deferred rather than live. */
        record FixtureOnly(String member, Set<String> fixtures) implements Verdict {}
    }

    /** Everything one run looked at, so a clean result can be checked for being vacuous. */
    record Report(
        List<Verdict> verdicts,
        Set<String> registeredEntityTypes,
        int filesParsed,
        int membersExamined
    ) {

        List<Verdict> unreachable() {
            return verdicts.stream().filter(Verdict.Unreachable.class::isInstance).toList();
        }

        List<Verdict> unresolved() {
            return verdicts.stream().filter(Verdict.Unresolved.class::isInstance).toList();
        }

        List<Verdict> testOnly() {
            return verdicts.stream().filter(Verdict.TestOnly.class::isInstance).toList();
        }
    }

    private static final Pattern GAME_TEST_FIXTURE = Pattern.compile(".*GameTests\\.java");
    private static final Pattern ENTITY_CONSTRUCTION =
        Pattern.compile("\\b(\\w+)\\s*::\\s*new|\\bnew\\s+(\\w+)\\s*\\(");

    private final List<JavaFile> production;
    private final List<JavaFile> tests;
    private final Set<String> registeredEntityTypes;

    private Reachability(
        final List<JavaFile> production,
        final List<JavaFile> tests,
        final Set<String> registeredEntityTypes
    ) {
        this.production = production;
        this.tests = tests;
        this.registeredEntityTypes = registeredEntityTypes;
    }

    static Reachability over(final Path mainRoot, final Path testRoot) {
        final List<JavaFile> production = parseTree(mainRoot);
        final List<JavaFile> tests = parseTree(testRoot);
        return new Reachability(production, tests, registeredTypes(production));
    }

    private static List<JavaFile> parseTree(final Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                .map(JavaFile::parse)
                .toList();
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Every entity class actually registered, read from {@code ModEntities} and widened to the
     * supertypes a registered class names, because an override on an abstract base is reached
     * through its registered subclass.
     */
    private static Set<String> registeredTypes(final List<JavaFile> production) {
        final Optional<JavaFile> modEntities = production.stream()
            .filter(file -> file.typeName().equals("ModEntities"))
            .findFirst();
        if (modEntities.isEmpty()) {
            return Set.of();
        }
        final Set<String> registered = new LinkedHashSet<>();
        final Matcher matcher = ENTITY_CONSTRUCTION.matcher(modEntities.get().code());
        while (matcher.find()) {
            final String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (name != null && Character.isUpperCase(name.charAt(0))) {
                registered.add(name);
            }
        }
        boolean grew = true;
        while (grew) {
            grew = false;
            for (final JavaFile file : production) {
                if (!registered.contains(file.typeName())) {
                    continue;
                }
                for (final String supertype : supertypesOf(file)) {
                    grew |= registered.add(supertype);
                }
            }
        }
        return Set.copyOf(registered);
    }

    private static Set<String> supertypesOf(final JavaFile file) {
        final Matcher matcher = Pattern
            .compile("\\b(?:extends|implements)\\s+([\\w.<>,\\s]+?)(?:\\{|\\bpermits\\b)")
            .matcher(file.code());
        final Set<String> supertypes = new LinkedHashSet<>();
        while (matcher.find()) {
            for (final String part : matcher.group(1).split("[,<>]")) {
                final String name = part.trim();
                if (!name.isEmpty() && Character.isUpperCase(name.charAt(0))) {
                    supertypes.add(name.contains(".")
                        ? name.substring(name.lastIndexOf('.') + 1) : name);
                }
            }
        }
        return supertypes;
    }

    Set<String> registeredEntityTypes() {
        return registeredEntityTypes;
    }

    /** Analyses the named types. */
    Report examine(final List<String> typeNames) {
        final List<Verdict> verdicts = new ArrayList<>();
        int membersExamined = 0;
        for (final String typeName : typeNames) {
            final JavaFile target = production.stream()
                .filter(file -> file.typeName().equals(typeName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "no production file declares " + typeName));
            final List<Verdict> found = examine(target);
            membersExamined += found.size();
            verdicts.addAll(found);
        }
        verdicts.sort(Comparator.comparing(Verdict::member));
        return new Report(List.copyOf(verdicts), registeredEntityTypes,
            production.size() + tests.size(), membersExamined);
    }

    private List<Verdict> examine(final JavaFile target) {
        final String qualified = target.packageName().isEmpty()
            ? target.typeName()
            : target.packageName() + "." + target.typeName();

        final Map<String, Set<String>> qualifiedCallers = new LinkedHashMap<>();
        final Map<String, Set<String>> bareOnlyCallers = new LinkedHashMap<>();
        final Map<String, Set<String>> fixtureCallers = new LinkedHashMap<>();
        final Map<String, Set<String>> testCallers = new LinkedHashMap<>();

        for (final String member : target.members().keySet()) {
            qualifiedCallers.put(member, new LinkedHashSet<>());
            bareOnlyCallers.put(member, new LinkedHashSet<>());
            fixtureCallers.put(member, new LinkedHashSet<>());
            testCallers.put(member, new LinkedHashSet<>());
        }

        for (final JavaFile other : production) {
            if (other.path().equals(target.path())) {
                continue;
            }
            final boolean fixture =
                GAME_TEST_FIXTURE.matcher(other.path().getFileName().toString()).matches();
            classify(target, other, qualified, fixture ? fixtureCallers : qualifiedCallers,
                bareOnlyCallers);
        }
        for (final JavaFile test : tests) {
            classify(target, test, qualified, testCallers, new LinkedHashMap<>());
        }

        final Set<String> reachable = roots(target, qualifiedCallers);
        closeOver(target, reachable);

        final List<Verdict> verdicts = new ArrayList<>();
        for (final String member : target.members().keySet()) {
            if (reachable.contains(member)) {
                final Set<String> via = qualifiedCallers.get(member);
                verdicts.add(new Verdict.Reachable(target.typeName() + "." + member,
                    via.isEmpty() ? "live code inside " + target.typeName() : String.join(", ", via)));
                continue;
            }
            if (!fixtureCallers.get(member).isEmpty()) {
                verdicts.add(new Verdict.FixtureOnly(target.typeName() + "." + member,
                    Set.copyOf(fixtureCallers.get(member))));
                continue;
            }
            if (!testCallers.get(member).isEmpty()) {
                verdicts.add(new Verdict.TestOnly(target.typeName() + "." + member,
                    Set.copyOf(testCallers.get(member))));
                continue;
            }
            final Set<String> bare = bareOnlyCallers.get(member);
            if (bare.isEmpty()) {
                verdicts.add(new Verdict.Unreachable(target.typeName() + "." + member, Set.of()));
            } else {
                verdicts.add(new Verdict.Unresolved(target.typeName() + "." + member,
                    "the name appears in live code but no reference resolves to "
                        + target.typeName() + "; most likely an instance call whose receiver type"
                        + " only a compiler can determine",
                    Set.copyOf(bare)));
            }
        }
        return verdicts;
    }

    /**
     * Splits another file's references into those that provably name this type and those that are
     * merely the same word. The other file's own declarations are never references.
     */
    private void classify(
        final JavaFile target,
        final JavaFile other,
        final String qualifiedTarget,
        final Map<String, Set<String>> qualifiedCallers,
        final Map<String, Set<String>> bareOnlyCallers
    ) {
        final boolean visible = other.canSee(qualifiedTarget);
        final String code = other.code();
        for (final String member : target.members().keySet()) {
            final Matcher matcher =
                Pattern.compile("(?<![\\w])" + Pattern.quote(member) + "\\b").matcher(code);
            while (matcher.find()) {
                if (isOwnDeclaration(other, matcher.start(), matcher.end())) {
                    continue;
                }
                final Optional<String> receiver = receiverBefore(code, matcher.start());
                if (receiver.isEmpty()) {
                    // Unqualified. Java scoping resolves it to this file's own member when it
                    // declares one of that name, so it says nothing about the target. Without this
                    // a bare helper() call in one class vouched for an identically named and
                    // entirely dead helper in another.
                    if (!other.members().containsKey(member)) {
                        bareOnlyCallers.computeIfAbsent(member, _ -> new LinkedHashSet<>())
                            .add(other.typeName());
                    }
                    continue;
                }
                final String named = receiver.get();
                if (named.equals(target.typeName())) {
                    if (visible) {
                        qualifiedCallers.computeIfAbsent(member, _ -> new LinkedHashSet<>())
                            .add(other.typeName());
                    }
                    continue;
                }
                if (!named.isEmpty() && isKnownOtherType(named)) {
                    // Qualified by a different declared type, so it belongs to that type.
                    continue;
                }
                // A receiver this analysis cannot type: an expression, or a variable. Evidence of
                // nothing either way.
                bareOnlyCallers.computeIfAbsent(member, _ -> new LinkedHashSet<>())
                    .add(other.typeName());
            }
        }
    }

    /**
     * The receiver a reference is qualified by, if any. Empty means the reference is unqualified;
     * a present but blank name means it is qualified by an expression such as a cast or a call,
     * whose type only a compiler could resolve.
     */
    private static Optional<String> receiverBefore(final String code, final int start) {
        int index = start - 1;
        while (index >= 0 && Character.isWhitespace(code.charAt(index))) {
            index--;
        }
        if (index < 0 || code.charAt(index) != '.') {
            return Optional.empty();
        }
        index--;
        while (index >= 0 && Character.isWhitespace(code.charAt(index))) {
            index--;
        }
        final int end = index + 1;
        while (index >= 0 && Character.isJavaIdentifierPart(code.charAt(index))) {
            index--;
        }
        return Optional.of(code.substring(index + 1, end));
    }

    /** A reference qualified by some other declared type belongs to that type, not to ours. */
    private boolean isKnownOtherType(final String receiver) {
        return production.stream().anyMatch(file -> file.typeName().equals(receiver));
    }

    private static boolean isOwnDeclaration(final JavaFile file, final int start, final int end) {
        return file.declarationRanges().stream()
            .anyMatch(range -> start >= range[0] && end <= range[1] + 1);
    }

    /**
     * The seeds of the call graph: live external references, plus engine entry points but only on a
     * class that is genuinely registered, plus the always live class initialiser.
     */
    private Set<String> roots(
        final JavaFile target,
        final Map<String, Set<String>> qualifiedCallers
    ) {
        final Set<String> roots = new LinkedHashSet<>();
        roots.add(JavaFile.CLASS_INIT);
        final boolean registered = registeredEntityTypes.contains(target.typeName());
        target.members().forEach((name, member) -> {
            if (!qualifiedCallers.getOrDefault(name, Set.of()).isEmpty()) {
                roots.add(name);
            }
            if (member.engineOverride() && registered) {
                roots.add(name);
            }
        });
        return roots;
    }

    /** Transitive closure over the bodies of members already known to be live. */
    private static void closeOver(final JavaFile target, final Set<String> reachable) {
        boolean grew = true;
        while (grew) {
            grew = false;
            for (final String holder : List.copyOf(reachable)) {
                final String body = target.methodBodies().getOrDefault(holder, "");
                for (final String candidate : target.members().keySet()) {
                    if (reachable.contains(candidate)) {
                        continue;
                    }
                    final String searchable = holder.equals(JavaFile.CLASS_INIT)
                        ? withoutOwnDeclaration(body, candidate)
                        : body;
                    if (Pattern.compile("(?<![\\w.])" + Pattern.quote(candidate) + "\\b")
                        .matcher(searchable).find()
                        || Pattern.compile("(?<![\\w])this\\s*\\.\\s*" + Pattern.quote(candidate)
                            + "\\b").matcher(searchable).find()) {
                        reachable.add(candidate);
                        grew = true;
                    }
                }
            }
        }
    }

    /**
     * The class initialiser text contains every field declaration, so a constant tested against it
     * unmodified is trivially self reachable. That blind spot hid genuinely dead constants.
     */
    private static String withoutOwnDeclaration(final String body, final String member) {
        final Matcher matcher =
            Pattern.compile("(?<![\\w.])" + Pattern.quote(member) + "\\s*(?:=|;)").matcher(body);
        final StringBuilder out = new StringBuilder();
        int cursor = 0;
        while (matcher.find()) {
            final int lineStart = body.lastIndexOf('\n', matcher.start()) + 1;
            final int semicolon = body.indexOf(';', matcher.end() - 1);
            final int lineEnd = semicolon < 0 ? body.length() : semicolon + 1;
            if (lineStart >= cursor) {
                out.append(body, cursor, lineStart);
                cursor = lineEnd;
            }
        }
        out.append(body.substring(Math.min(cursor, body.length())));
        return out.toString();
    }
}
