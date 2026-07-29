package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class LocalTagReferenceTest {
    private static final Path DATA = Path.of("src/main/resources/data");
    private static final Set<String> FLUID_IDS = Set.of(
        "spirit",
        "flowing_spirit",
        "hollow_tears",
        "flowing_hollow_tears",
        "colored_brew_water",
        "flowing_colored_brew_water",
        "erosion_brew",
        "flowing_erosion_brew"
    );

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyLocalItemTagEntryReferencesARegisteredItem() {
        assertLocalReferences("item", ModItems.ALL.keySet());
    }

    @Test
    void everyLocalBlockTagEntryReferencesARegisteredBlock() {
        assertLocalReferences("block", ModBlocks.ALL.keySet());
    }

    @Test
    void everyLocalEntityTagEntryReferencesARegisteredEntityType() {
        assertLocalReferences("entity_type", ModEntities.ALL.keySet());
    }

    @Test
    void everyLocalFluidTagEntryReferencesARegisteredFluid() {
        assertLocalReferences("fluid", FLUID_IDS);
    }

    private static void assertLocalReferences(final String registry, final Collection<String> validIds) {
        try (Stream<Path> files = Files.walk(DATA)) {
            final List<String> invalid = files
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().replace('\\', '/').contains("/tags/" + registry + "/"))
                .filter(path -> path.toString().endsWith(".json"))
                .flatMap(LocalTagReferenceTest::values)
                .filter(value -> value.startsWith("warlockery:"))
                .map(value -> value.substring("warlockery:".length()))
                .filter(id -> !validIds.contains(id))
                .distinct()
                .sorted()
                .toList();
            assertTrue(invalid.isEmpty(), () -> registry + ": " + invalid);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Stream<String> values(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject()
                .getAsJsonArray("values")
                .asList()
                .stream()
                .map(LocalTagReferenceTest::id)
                .filter(value -> !value.startsWith("#"));
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private static String id(final JsonElement value) {
        if (value.isJsonPrimitive()) {
            return value.getAsString();
        }
        final JsonObject object = value.getAsJsonObject();
        return object.has("id") ? object.get("id").getAsString() : "";
    }
}
