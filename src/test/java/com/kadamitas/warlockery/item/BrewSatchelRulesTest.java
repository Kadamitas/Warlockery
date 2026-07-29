package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class BrewSatchelRulesTest {
    @Test
    void emptySatchelFailsClearly() {
        assertEquals(BrewSatchelRules.Diagnostic.EMPTY, BrewSatchelRules.diagnose(false, false, false));
    }

    @Test
    void invalidSelectedContentIsRejected() {
        assertEquals(BrewSatchelRules.Diagnostic.INVALID_BREW, BrewSatchelRules.diagnose(true, false, false));
        assertEquals(BrewSatchelRules.Diagnostic.INVALID_BREW, BrewSatchelRules.diagnose(true, true, false));
    }

    @Test
    void selectedTaggedProjectileUsesTheProjectilePath() throws IOException {
        assertEquals(BrewSatchelRules.Diagnostic.READY, BrewSatchelRules.diagnose(true, true, true));
        final String source = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/item/BrewSatchelItem.java"
        ));
        assertTrue(source.contains("Projectile.spawnProjectile("));
        assertTrue(source.contains("projectileItem.asProjectile("));
        assertTrue(source.contains("extractOne(satchel)"));
    }
}
