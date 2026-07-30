package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.registry.ContentCatalog;
import com.kadamitas.warlockery.registry.CreativeInventoryCatalog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

final class ReleaseItemPresentationTest {
    private static final Path MAIN = Path.of("src/main");
    private static final Set<String> TEXT_EXTENSIONS = Set.of(".java", ".json", ".mcmeta", ".txt", ".md");

    @Test
    void throwableChargesDoNotGeneratePotionEffectTranslationKeys() throws ReflectiveOperationException {
        assertChargeName(SunGrenadeItem.class);
        assertChargeName(ReplicationChargeItem.class);
    }

    @Test
    void unusedDevelopmentItemsAreNotRegisteredAndBlankBiomeNotesStayHidden() {
        final Set<String> itemIds = ContentCatalog.ITEMS.stream()
            .map(ContentCatalog::modernize)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List.of("brewbottle", "ingredient", "potion").forEach(id -> assertFalse(itemIds.contains(id), id));
        assertTrue(itemIds.contains("biomenote"));
        assertFalse(CreativeInventoryCatalog.isVisible("biomenote"));
    }

    @Test
    void customLambchopsAreFullyReplacedByVanillaMutton() throws IOException {
        final List<String> legacyIds = List.of("ingredient_muttonraw", "ingredient_muttoncooked");
        try (Stream<Path> paths = Files.walk(MAIN)) {
            final List<Path> legacyPaths = paths
                .filter(path -> legacyIds.stream().anyMatch(id -> path.toString().contains(id)))
                .toList();
            assertTrue(legacyPaths.isEmpty(), () -> "legacy lambchop paths remain: " + legacyPaths);
        }
        try (Stream<Path> paths = Files.walk(MAIN)) {
            final List<Path> references = paths.filter(Files::isRegularFile)
                .filter(ReleaseItemPresentationTest::isTextFile)
                .filter(path -> legacyIds.stream().anyMatch(id -> read(path).contains(id)))
                .toList();
            assertTrue(references.isEmpty(), () -> "legacy lambchop references remain: " + references);
        }
        assertTrue(read(MAIN.resolve("resources/data/warlockery/tags/item/wolf_form_meats.json"))
            .contains("minecraft:mutton"));
        assertTrue(read(MAIN.resolve("resources/data/warlockery/tags/item/wolf_altar_offerings.json"))
            .contains("minecraft:mutton"));
        assertTrue(read(MAIN.resolve("java/com/kadamitas/warlockery/item/ResourceInteractionEvents.java"))
            .contains("Items.MUTTON"));
    }

    private static void assertChargeName(final Class<?> type) throws ReflectiveOperationException {
        assertEquals(type, type.getDeclaredMethod("getName", ItemStack.class).getDeclaringClass());
        final String source = read(MAIN.resolve("java/" + type.getName().replace('.', '/') + ".java"));
        assertTrue(source.contains("java.util.List.of(), Optional.empty())"), type.getName());
    }

    private static boolean isTextFile(final Path path) {
        final String name = path.getFileName().toString();
        return TEXT_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
