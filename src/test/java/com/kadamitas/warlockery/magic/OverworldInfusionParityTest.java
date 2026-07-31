package com.kadamitas.warlockery.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class OverworldInfusionParityTest {
    @Test
    void oreTransmutationProducesTwoNormalDrops() {
        assertEquals(2, OverworldInfusionRules.transmutationDropCopies());
    }

    @Test
    void oreTransmutationAcceptsCommonTaggedModdedOres() throws IOException {
        final Path tag = Path.of(
            "src", "main", "resources", "data", "warlockery", "tags", "block", "magic",
            "overworld_transmutable_ores.json"
        );
        assertTrue(Files.readString(tag).contains("#c:ores"));
    }

    @Test
    void launchedBlocksTravelForwardAndUpward() {
        final Vec3 velocity = OverworldInfusionRules.launchedBlockVelocity(new Vec3(0.0, 0.0, 4.0));
        assertEquals(0.0, velocity.x, 0.0001);
        assertTrue(velocity.y > 0.0);
        assertTrue(velocity.z > 1.0);
    }
}
