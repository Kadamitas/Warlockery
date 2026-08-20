package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ReplicationChargePlacementTest {
    @Test
    void collisionIsCheckedAfterPlacementInTheAdjacentFreeCell() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/item/ReplicationChargeItem.java"
        ));
        final int adjacent = source.indexOf("blockHit.getBlockPos().relative(blockHit.getDirection())");
        final int placement = source.indexOf("duplicate.snapTo(spawnLocation.x");
        final int collision = source.indexOf("level.noCollision(duplicate)");
        assertTrue(adjacent >= 0);
        assertTrue(placement > adjacent);
        assertTrue(collision > placement);
    }
}
