package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class PrimaryAttackWiringTest {
    @Test
    void forgewardenBonusIsPartOfThePrimaryHobgoblinHit() throws IOException {
        final String hobgoblin = source("HobgoblinEntity.java");
        final String runtime = source("CreatureBehaviorRuntime.java");
        assertTrue(hobgoblin.contains("PrimaryAttackModifier.withDamageBonus("));
        assertTrue(hobgoblin.contains("behavior.attackDamageBonus(this, level)"));
        assertFalse(runtime.contains("creature.damageSources().mobAttack(creature), bonus"));
    }

    @Test
    void transientPrimaryAttackBonusIsAlwaysRemoved() throws IOException {
        final String modifier = source("PrimaryAttackModifier.java");
        assertTrue(modifier.contains("addTransientModifier"));
        assertTrue(modifier.contains("finally"));
        assertTrue(modifier.contains("removeModifier(BONUS_ID)"));
    }

    private static String source(final String name) throws IOException {
        return Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/entity",
            name
        ));
    }
}
