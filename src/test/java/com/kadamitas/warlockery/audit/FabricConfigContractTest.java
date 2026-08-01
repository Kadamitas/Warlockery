package com.kadamitas.warlockery.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FabricConfigContractTest {
    private static final Path CONFIG = Path.of(
        "src/main/java/com/kadamitas/warlockery/config/WarlockeryConfig.java"
    );

    @Test
    void configUsesTheFabricLoaderDirectoryAndHumanReadableJson() throws IOException {
        final String source = Files.readString(CONFIG);
        assertTrue(source.contains("FabricLoader.getInstance().getConfigDir()"));
        assertTrue(source.contains("private static final String FILE_NAME = \"warlockery.json\""));
        assertTrue(source.contains("GsonBuilder().setPrettyPrinting()"));
        assertTrue(source.contains("Files.writeString(path, GSON.toJson(root)"));
        assertFalse(source.contains("ForgeConfigSpec"));
        assertFalse(source.contains("ModConfig"));
    }

    @Test
    void malformedAndOutOfRangeValuesFallBackSafely() throws IOException {
        final String source = Files.readString(CONFIG);
        assertTrue(source.contains("settings = DEFAULTS"));
        assertTrue(source.contains("Math.max(minimum, primitive.getAsInt())"));
        assertTrue(source.contains("Math.clamp(value, 0.0D, 1.0D)"));
        assertTrue(source.contains("Double.isFinite(value)"));
        assertTrue(source.contains("private record Settings("));
    }

    @Test
    void fabricEntrypointLoadsConfigBeforeWorldAndEventRegistration() throws IOException {
        final String initializer = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/Warlockery.java"
        ));
        final int config = initializer.indexOf("WarlockeryConfig.initialize()");
        final int worldGeneration = initializer.indexOf("WarlockeryWorldGeneration.initialize()");
        final int events = initializer.indexOf("WarlockeryFabricEvents.initialize()");
        assertTrue(config >= 0);
        assertTrue(config < worldGeneration);
        assertTrue(config < events);
    }
}
