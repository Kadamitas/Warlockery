package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.testutil.JsonFixtureLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class HexBehaviorsTest {
    private static final Path RITUALS = Path.of("src", "main", "resources", "data", "warlockery", "ritual");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theRegisteredBehaviorsAreExactlyTheTargetsTheDatapackNames() {
        final Set<String> declared = JsonFixtureLoader.load(RITUALS, RitualDefinition.CODEC).stream()
            .map(JsonFixtureLoader.Fixture::value)
            .filter(definition -> RitualAction.HEX.id().equals(definition.action())
                || RitualAction.CLEANSE.id().equals(definition.action()))
            .map(RitualDefinition::target)
            .collect(Collectors.toUnmodifiableSet());
        assertEquals(declared, Set.copyOf(HexBehaviors.registeredTargets()));
    }

    @Test
    void unknownTargetsResolveToNothingRatherThanADefault() {
        assertFalse(HexBehaviors.supports("unknown"));
        assertTrue(HexBehaviors.find("unknown").isEmpty());
    }

    @Test
    void everyRegisteredTargetResolvesToABehavior() {
        final List<String> targets = HexBehaviors.registeredTargets();
        assertFalse(targets.isEmpty());
        targets.forEach(target -> assertTrue(HexBehaviors.find(target).isPresent(), target));
    }

    @Test
    void onlyHexKindBackedTargetsArePersistent() {
        assertTrue(HexBehaviors.isPersistent("heat_metal"));
        assertTrue(HexBehaviors.isPersistent("misfortune"));
        assertFalse(HexBehaviors.isPersistent("blindness"));
        assertFalse(HexBehaviors.isPersistent("corrupt_doll"));
    }
}
