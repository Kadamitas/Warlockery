package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ReplicationChargePlacementTest {
    @Test
    void collisionIsCheckedAfterPlacementAtTheImpact() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/item/ReplicationChargeItem.java"
        ));
        final int placement = source.indexOf("duplicate.snapTo(hit.getLocation().x");
        final int collision = source.indexOf("level.noCollision(duplicate)");
        assertTrue(placement >= 0);
        assertTrue(collision > placement);
    }
}
