package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ReloadNamespaceCompatibilityTest {
    private static final String MOD_NAMESPACE_FILTER =
        ".filter(entry -> Warlockery.MOD_ID.equals(entry.getKey().getNamespace()))";

    @Test
    void machineRecipesAcceptDefinitionsFromOtherNamespaces() throws IOException {
        final String source = read("crafting/MachineRecipeManager.java");
        assertFalse(source.contains(MOD_NAMESPACE_FILTER));
        assertTrue(source.contains("FileToIdConverter.json(\"warlockery_machine\")"));
        assertTrue(source.contains(".filter(entry -> validate(entry.getKey(), entry.getValue()))"));
    }

    @Test
    void ritualsAcceptDefinitionsFromOtherNamespaces() throws IOException {
        final String source = read("ritual/RitualManager.java");
        assertFalse(source.contains(MOD_NAMESPACE_FILTER));
        assertTrue(source.contains("FileToIdConverter.json(\"ritual\")"));
        assertTrue(source.contains(".filter(entry -> validate(entry.getKey(), entry.getValue()))"));
    }

    private static String read(final String relative) throws IOException {
        return Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery"
        ).resolve(relative));
    }
}
