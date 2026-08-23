package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class PreyDriveControlsTest {
    @Test
    void onlyAnActiveServerEpisodeSuppressesMovement() {
        assertFalse(PreyDriveControls.shouldSuppressInput(-1));
        assertTrue(PreyDriveControls.shouldSuppressInput(0));
        assertTrue(PreyDriveControls.shouldSuppressInput(42));
    }

    @Test
    void clientHookTouchesOnlyTheSevenMovementMappings() {
        final String source = read(Path.of(
            "src", "main", "java", "com", "kadamitas", "warlockery", "client", "PreyDriveControls.java"
        ));
        for (final String key : new String[] {
            "keyUp", "keyDown", "keyLeft", "keyRight", "keyJump", "keyShift", "keySprint"
        }) {
            assertTrue(source.contains("options." + key + ".setDown(false)"), key);
        }
        assertFalse(source.contains("keyAttack"));
        assertFalse(source.contains("keyUse"));
        assertTrue(source.contains("KeyMapping.setAll()"));

        final String client = read(Path.of(
            "src", "main", "java", "com", "kadamitas", "warlockery", "client", "WarlockeryClient.java"
        ));
        assertTrue(client.contains("ClientTickEvents.START_CLIENT_TICK.register(PreyDriveControls::tick)"));
        assertTrue(client.contains("PreyDriveControls.disconnect()"));
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
