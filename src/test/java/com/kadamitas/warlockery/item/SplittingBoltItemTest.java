package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SplittingBoltItemTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void producesTwoDistinctHalfDamageFlightPaths() {
        final var directions = SplittingBoltItem.sideDirections(new Vec3(0.0, 0.0, 1.0));
        assertEquals(2, directions.size());
        assertNotEquals(directions.get(0), directions.get(1));
        assertEquals(directions.get(0).length(), directions.get(1).length(), 0.0001);
    }
}
