package com.kadamitas.warlockery.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The reachability checker, tested against the exact false negatives an independent audit found in
 * the third revision of the hand-rolled sweep.
 */
final class ReachabilityTest {

    private static Path write(final Path root, final String relative, final String source)
        throws IOException {
        final Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, source);
        return path;
    }

    private static Path main(final Path root) {
        return root.resolve("src/main/java");
    }

    private static Path test(final Path root) {
        return root.resolve("src/test/java");
    }

    private static void modEntities(final Path root, final String... registeredTypes)
        throws IOException {
        final String registrations = Stream.of(registeredTypes)
            .map(type -> "    static Object make" + type + "() {\n"
                + "        return EntityType.Builder.of(" + type + "::new, MobCategory.MONSTER);\n"
                + "    }\n")
            .reduce("", String::concat);
        write(main(root), "com/kadamitas/warlockery/registry/ModEntities.java", """
            package com.kadamitas.warlockery.registry;

            final class ModEntities {
            """ + registrations + "}\n");
    }

    /**
     * The headline false negative. CircleMageRules.MAX_STATE_BYTES and
     * HedgeCroneRules.MAX_STATE_BYTES are both genuinely dead. The hand-rolled sweep matched the
     * bare name across every other production file with no receiver and no declaration filter, so
     * each dead constant was certified as wired partly by the other dead constant's declaration and
     * partly by an unrelated same-named constant in BansheeRules that is genuinely live.
     */
    @Test
    void redTwoDeadConstantsCannotVouchForEachOtherOrBorrowAThirdClassesUse(
        @TempDir final Path root
    ) throws IOException {
        modEntities(root);
        write(main(root), "com/kadamitas/warlockery/entity/HedgeCroneRules.java", """
            package com.kadamitas.warlockery.entity;

            public final class HedgeCroneRules {
                public static final int MAX_STATE_BYTES = 768;
                public static final int LIVE_LIMIT = 12;
            }
            """);
        write(main(root), "com/kadamitas/warlockery/entity/CircleMageRules.java", """
            package com.kadamitas.warlockery.entity;

            public final class CircleMageRules {
                public static final int MAX_STATE_BYTES = 768;
            }
            """);
        write(main(root), "com/kadamitas/warlockery/entity/BansheeRules.java", """
            package com.kadamitas.warlockery.entity;

            public final class BansheeRules {
                public static final int MAX_STATE_BYTES = 512;
            }
            """);
        write(main(root), "com/kadamitas/warlockery/entity/BansheeState.java", """
            package com.kadamitas.warlockery.entity;

            public final class BansheeState {
                /** Encodes below {@link BansheeRules#MAX_STATE_BYTES}. */
                int budget() {
                    return BansheeRules.MAX_STATE_BYTES;
                }
                int live() {
                    return HedgeCroneRules.LIVE_LIMIT;
                }
            }
            """);

        final Reachability.Report report = Reachability.over(main(root), test(root))
            .examine(List.of("HedgeCroneRules", "CircleMageRules", "BansheeRules"));

        assertTrue(names(report.unreachable())
                .containsAll(Set.of("HedgeCroneRules.MAX_STATE_BYTES",
                    "CircleMageRules.MAX_STATE_BYTES")),
            "both dead constants are reported dead, not vouched for by each other, got "
                + names(report.unreachable()));
        assertFalse(names(report.unreachable()).contains("BansheeRules.MAX_STATE_BYTES"),
            "the genuinely used one is not swept up");
        assertFalse(names(report.unreachable()).contains("HedgeCroneRules.LIVE_LIMIT"),
            "a qualified reference from a file that can see the type counts as a use");
    }

    /**
     * The second finding. The sweep rooted twelve methods on two entity classes that are not
     * registered anywhere, because a hardcoded list of twenty-two engine entry names matched by
     * method name alone with no check that the class is reachable from the engine at all.
     */
    @Test
    void redAnEngineEntryNameOnlyRootsWhenTheClassIsActuallyRegistered(@TempDir final Path root)
        throws IOException {
        modEntities(root, "RegisteredEntity");
        final String body = """
            package com.kadamitas.warlockery.entity;

            public class %s {
                @Override
                public void tick() {
                    helper();
                }
                private void helper() {
                }
            }
            """;
        write(main(root), "com/kadamitas/warlockery/entity/RegisteredEntity.java",
            body.formatted("RegisteredEntity"));
        write(main(root), "com/kadamitas/warlockery/entity/StrandedEntity.java",
            body.formatted("StrandedEntity"));

        final Reachability reachability = Reachability.over(main(root), test(root));
        assertTrue(reachability.registeredEntityTypes().contains("RegisteredEntity"));
        assertFalse(reachability.registeredEntityTypes().contains("StrandedEntity"));

        final Reachability.Report report =
            reachability.examine(List.of("RegisteredEntity", "StrandedEntity"));
        assertFalse(names(report.unreachable()).contains("RegisteredEntity.tick"));
        assertFalse(names(report.unreachable()).contains("RegisteredEntity.helper"),
            "reached transitively from the rooted override");
        assertTrue(names(report.unreachable()).contains("StrandedEntity.tick"),
            "an override on a class the engine never constructs is dead, got "
                + names(report.unreachable()));
        assertTrue(names(report.unreachable()).contains("StrandedEntity.helper"));
    }

    /** An override on an abstract base is reached through whichever subclass is registered. */
    @Test
    void anOverrideOnASupertypeOfARegisteredClassStillRoots(@TempDir final Path root)
        throws IOException {
        modEntities(root, "ConcreteEntity");
        write(main(root), "com/kadamitas/warlockery/entity/ArcaneMob.java", """
            package com.kadamitas.warlockery.entity;

            public abstract class ArcaneMob {
                @Override
                public void tick() {
                }
            }
            """);
        write(main(root), "com/kadamitas/warlockery/entity/ConcreteEntity.java", """
            package com.kadamitas.warlockery.entity;

            public class ConcreteEntity extends ArcaneMob {
            }
            """);
        final Reachability reachability = Reachability.over(main(root), test(root));
        assertTrue(reachability.registeredEntityTypes().contains("ArcaneMob"));
        assertFalse(names(reachability.examine(List.of("ArcaneMob")).unreachable())
            .contains("ArcaneMob.tick"));
    }

    /**
     * The checker must not claim a clean negative it cannot support. A bare name in live code that
     * cannot be tied to this type is neither wired nor proven dead.
     */
    @Test
    void anUnresolvableBareNameIsReportedAsUnresolvedRatherThanEitherVerdict(
        @TempDir final Path root
    ) throws IOException {
        modEntities(root);
        write(main(root), "com/kadamitas/warlockery/entity/CroneState.java", """
            package com.kadamitas.warlockery.entity;

            public final class CroneState {
                public int hungerLevel() {
                    return 3;
                }
            }
            """);
        write(main(root), "com/kadamitas/warlockery/entity/CroneRuntime.java", """
            package com.kadamitas.warlockery.entity;

            public final class CroneRuntime {
                static int read(Object state) {
                    return ((CroneState) state).hungerLevel();
                }
            }
            """);

        final Reachability.Report report =
            Reachability.over(main(root), test(root)).examine(List.of("CroneState"));
        assertEquals(List.of(), report.unreachable(),
            "an instance call through a receiver must never be called dead");
        assertTrue(names(report.unresolved()).contains("CroneState.hungerLevel"),
            "and must be reported as unresolved rather than silently clean, got "
                + report.verdicts());
    }

    @Test
    void aMemberUsedOnlyByUnitTestsIsCeremonyAndAFixtureUseIsDistinctFromBoth(
        @TempDir final Path root
    ) throws IOException {
        modEntities(root);
        write(main(root), "com/kadamitas/warlockery/entity/CroneRules.java", """
            package com.kadamitas.warlockery.entity;

            public final class CroneRules {
                public static final int TEST_ONLY_LIMIT = 4;
                public static final int FIXTURE_ONLY_LIMIT = 5;
            }
            """);
        write(main(root), "com/kadamitas/warlockery/entity/CroneGameTests.java", """
            package com.kadamitas.warlockery.entity;

            public final class CroneGameTests {
                static int check() {
                    return CroneRules.FIXTURE_ONLY_LIMIT;
                }
            }
            """);
        write(test(root), "com/kadamitas/warlockery/entity/CroneRulesTest.java", """
            package com.kadamitas.warlockery.entity;

            final class CroneRulesTest {
                int check() {
                    return CroneRules.TEST_ONLY_LIMIT;
                }
            }
            """);

        final Reachability.Report report =
            Reachability.over(main(root), test(root)).examine(List.of("CroneRules"));
        assertTrue(names(report.testOnly()).contains("CroneRules.TEST_ONLY_LIMIT"));
        assertTrue(report.verdicts().stream()
                .anyMatch(verdict -> verdict instanceof Reachability.Verdict.FixtureOnly fixture
                    && fixture.member().equals("CroneRules.FIXTURE_ONLY_LIMIT")),
            "the fixture set is derived from the GameTests pattern, not a hardcoded file name");
    }

    /** The regex the sweep used required public/private/protected plus static final. */
    @Test
    void redPackagePrivateConstantsAndInstanceFieldsAreAudited(@TempDir final Path root)
        throws IOException {
        modEntities(root);
        write(main(root), "com/kadamitas/warlockery/entity/CroneRules.java", """
            package com.kadamitas.warlockery.entity;

            public final class CroneRules {
                static final int PACKAGE_PRIVATE_DEAD = 7;
                private int instanceDead;
                public static final int PUBLIC_DEAD = 9;
            }
            """);
        final Reachability.Report report =
            Reachability.over(main(root), test(root)).examine(List.of("CroneRules"));
        final Set<String> dead = names(report.unreachable());
        assertTrue(dead.contains("CroneRules.PACKAGE_PRIVATE_DEAD"), "got " + dead);
        assertTrue(dead.contains("CroneRules.instanceDead"), "got " + dead);
        assertTrue(dead.contains("CroneRules.PUBLIC_DEAD"), "got " + dead);
    }

    /** The sweep silently dropped a method whose braces did not match, which fails open. */
    @Test
    void redUnbalancedBracesRaiseInsteadOfSilentlyDroppingTheMethod() {
        assertThrows(IllegalStateException.class,
            () -> JavaFile.parse(Path.of("Broken.java"), """
                package com.kadamitas.warlockery.entity;

                final class Broken {
                    void method() {
                }
                """));
        assertThrows(IllegalStateException.class,
            () -> JavaFile.parse(Path.of("Broken.java"), "class Broken { } }"));
    }

    @Test
    void aQualifiedReferenceFromAFileThatCannotSeeTheTypeDoesNotCount(@TempDir final Path root)
        throws IOException {
        modEntities(root);
        write(main(root), "com/kadamitas/warlockery/entity/CroneRules.java", """
            package com.kadamitas.warlockery.entity;

            public final class CroneRules {
                public static final int LIMIT = 4;
            }
            """);
        // A different CroneRules in another package, with no import of the entity one.
        write(main(root), "com/kadamitas/warlockery/ritual/RitualUse.java", """
            package com.kadamitas.warlockery.ritual;

            final class RitualUse {
                int read() {
                    return CroneRules.LIMIT;
                }
            }
            """);
        final Reachability.Report report =
            Reachability.over(main(root), test(root)).examine(List.of("CroneRules"));
        assertFalse(names(report.verdicts().stream()
                .filter(Reachability.Verdict.Reachable.class::isInstance).toList())
            .contains("CroneRules.LIMIT"),
            "a file in another package with no import cannot be naming this type");
    }

    /** The lexer and brace matcher have to survive the real codebase, not just fixtures. */
    @Test
    void theWholeProductionTreeParsesAndModEntitiesRegistrationsAreFound() {
        final Path mainRoot = Path.of("src/main/java");
        final Reachability reachability = Reachability.over(mainRoot, Path.of("src/test/java"));
        final Set<String> registered = reachability.registeredEntityTypes();
        assertTrue(registered.size() > 10,
            "ModEntities registrations must be discovered, found " + registered.size());
        assertTrue(registered.contains("BansheeEntity"), "got " + registered);
        assertTrue(registered.contains("HexBatEntity"));
    }

    private static Set<String> names(final List<Reachability.Verdict> verdicts) {
        return verdicts.stream().map(Reachability.Verdict::member)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
