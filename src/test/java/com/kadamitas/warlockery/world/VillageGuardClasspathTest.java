package com.kadamitas.warlockery.world;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class VillageGuardClasspathTest {
    @Test
    void guardRulesAreLoadableFromTheRuntimeClasspath() throws ClassNotFoundException {
        final ClassLoader loader = VillageGuardRuntime.class.getClassLoader();
        assertSame(VillageGuardRules.class, Class.forName(VillageGuardRules.class.getName(), true, loader));
        assertNotNull(loader.getResource("com/kadamitas/warlockery/world/VillageGuardRules.class"));
    }

    @Test
    void customVillagersAreRejectedBeforeGuardRulesAreInvoked() throws IOException {
        final String runtime = Files.readString(Path.of(
            "src", "main", "java", "com", "kadamitas", "warlockery", "world", "VillageGuardRuntime.java"
        ));
        final int targetGate = runtime.indexOf("!isCommissionableTarget(villager)");
        final int ruleInvocation = runtime.indexOf("VillageGuardRules.canCommission");
        assertTrue(targetGate >= 0 && ruleInvocation > targetGate);
    }
}
