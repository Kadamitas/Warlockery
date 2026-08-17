package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.magic.MagicPath;
import com.kadamitas.warlockery.testutil.JsonFixtureLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class RitualTargetCoverageTest {
    private static final Path RITUALS = Path.of("src", "main", "resources", "data", "warlockery", "ritual");
    private static final List<JsonFixtureLoader.Fixture<RitualDefinition>> DEFINITIONS =
        JsonFixtureLoader.load(RITUALS, RitualDefinition.CODEC);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyHexTargetReachesARegisteredBehavior() {
        final List<String> unreachable = definitionsFor(RitualAction.HEX)
            .filter(fixture -> !HexBehaviors.supports(fixture.value().target()))
            .map(fixture -> fixture.id() + " (target=" + fixture.value().target() + ")")
            .toList();
        assertTrue(unreachable.isEmpty(), "hex rituals whose target reaches no behavior: " + unreachable);
    }

    @Test
    void everyCleanseTargetReachesARegisteredBehavior() {
        final List<String> unreachable = definitionsFor(RitualAction.CLEANSE)
            .filter(fixture -> !HexBehaviors.supports(fixture.value().target()))
            .map(fixture -> fixture.id() + " (target=" + fixture.value().target() + ")")
            .toList();
        assertTrue(unreachable.isEmpty(), "cleanse rituals whose target reaches no behavior: " + unreachable);
    }

    @Test
    void everyPersistentHexHasACure() {
        final Set<String> cured = definitionsFor(RitualAction.CLEANSE)
            .map(fixture -> fixture.value().target())
            .collect(Collectors.toUnmodifiableSet());
        final List<String> uncurable = definitionsFor(RitualAction.HEX)
            .map(fixture -> fixture.value().target())
            .filter(HexBehaviors::isPersistent)
            .filter(target -> !cured.contains(target))
            .toList();
        assertTrue(uncurable.isEmpty(), "persistent hexes with no cleanse ritual: " + uncurable);
    }

    @Test
    void everyMagicPathTargetResolves() {
        final List<String> unresolved = definitionsFor(RitualAction.INFUSE_PATH)
            .filter(fixture -> MagicPath.find(fixture.value().target()).isEmpty())
            .map(fixture -> fixture.id() + " (target=" + fixture.value().target() + ")")
            .toList();
        assertTrue(unresolved.isEmpty(), "infuse_path rituals naming an unknown path: " + unresolved);
    }

    @Test
    void everyBindEntityTargetResolves() {
        final List<String> unresolved = definitionsFor(RitualAction.BIND_ENTITY)
            .filter(fixture -> RitualBindTarget.find(fixture.value().target()).isEmpty())
            .map(fixture -> fixture.id() + " (target=" + fixture.value().target() + ")")
            .toList();
        assertTrue(unresolved.isEmpty(), "bind_entity rituals naming an unknown bind target: " + unresolved);
    }

    @Test
    void everyRitualActionIsReachedByADefinition() {
        final Set<String> declared = DEFINITIONS.stream()
            .map(fixture -> fixture.value().action())
            .collect(Collectors.toUnmodifiableSet());
        final List<String> unreached = java.util.Arrays.stream(RitualAction.values())
            .map(RitualAction::id)
            .filter(id -> !declared.contains(id))
            .toList();
        assertTrue(unreached.isEmpty(), "ritual actions no definition can reach: " + unreached);
    }

    @Test
    void everyRegisteredHexBehaviorIsReachedByADefinition() {
        final Set<String> declared = DEFINITIONS.stream()
            .filter(fixture -> isHexRoute(fixture.value().action()))
            .map(fixture -> fixture.value().target())
            .collect(Collectors.toUnmodifiableSet());
        final List<String> unreached = HexBehaviors.registeredTargets().stream()
            .filter(target -> !declared.contains(target))
            .sorted()
            .toList();
        assertTrue(unreached.isEmpty(), "hex behaviors no definition can reach: " + unreached);
    }

    private static boolean isHexRoute(final String action) {
        return RitualAction.HEX.id().equals(action) || RitualAction.CLEANSE.id().equals(action);
    }

    private static java.util.stream.Stream<JsonFixtureLoader.Fixture<RitualDefinition>> definitionsFor(
        final RitualAction action
    ) {
        return DEFINITIONS.stream().filter(fixture -> action.id().equals(fixture.value().action()));
    }
}
