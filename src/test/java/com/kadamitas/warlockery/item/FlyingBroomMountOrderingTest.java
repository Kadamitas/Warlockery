package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FlyingBroomMountOrderingTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/item/FlyingBroomItem.java"
    );
    private static final Path ENTITY_SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/entity/BroomEntity.java"
    );

    @Test
    void vehicleIsTrackedBeforeMountingCanSendItsPassengerPacket() throws Exception {
        final String source = Files.readString(SOURCE);
        final int mount = source.indexOf("player.startRiding(broom, true, true)");
        final int add = source.indexOf("serverLevel.addFreshEntity(broom)");
        assertTrue(add >= 0);
        assertTrue(mount > add);
    }

    @Test
    void failedSpawnReturnsTheExactStoredBroom() throws Exception {
        final String source = Files.readString(SOURCE);
        final int add = source.indexOf("serverLevel.addFreshEntity(broom)");
        final int returnBroom = source.indexOf("broom.returnBroomTo(player)", add);
        assertTrue(add >= 0);
        assertTrue(returnBroom > add);
    }

    @Test
    void dismountCallbackDoesNotDeleteTheVehicleBeforeTheFinalPassengerPacket() throws Exception {
        final String source = Files.readString(ENTITY_SOURCE);
        final int method = source.indexOf("protected void removePassenger");
        final int nextMethod = source.indexOf("@Override", method + 1);
        assertTrue(method >= 0 && nextMethod > method);
        assertFalse(source.substring(method, nextMethod).contains("discard()"));
    }
}
