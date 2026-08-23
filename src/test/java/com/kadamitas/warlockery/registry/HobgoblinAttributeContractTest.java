package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class HobgoblinAttributeContractTest {
    @Test
    void defensiveStrikeHasTheAttackDamageAttributeUsedByVanillaMelee() throws Exception {
        final String registry = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/registry/ModEntities.java"
        ));
        final String startMarker = "AttributeFactoryRule.exact(\"hobgoblin\"";
        final int start = registry.indexOf(startMarker);

        assertTrue(start >= 0, "the attacking Hobgoblin needs a dedicated attribute supplier");
        final int end = registry.indexOf(".build()),", start);
        assertTrue(end > start, "the Hobgoblin attribute supplier must be bounded");
        final String supplier = registry.substring(start, end);
        assertTrue(supplier.contains(".add(Attributes.ATTACK_DAMAGE, 3.0)"),
            "vanilla Mob.doHurtTarget reads ATTACK_DAMAGE and crashes when it is absent");
    }
}
