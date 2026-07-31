package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class GobliniteEconomyTest {
    private static final Path ENTITY_SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/entity/HobgoblinEntity.java"
    );
    private static final Path DATA = Path.of("src/main/resources/data/warlockery");

    @Test
    void goblinitePurchasesUseScarceMaterialPrices() throws IOException {
        final String source = normalizedSource();

        assertAll(
            () -> assertTrue(source.contains(
                "new MerchantOffer(new ItemCost(Items.EMERALD, 8), "
                    + "new ItemStack(ModItems.ALL.get(\"raw_delvealloy\").get()),"
            ), "one raw goblinite must cost eight emeralds"),
            () -> assertTrue(source.contains(
                "new MerchantOffer(new ItemCost(Items.EMERALD, 32), "
                    + "new ItemStack(ModItems.ALL.get(\"delvealloypickaxe\").get()),"
            ), "a goblinite pickaxe must cost thirty-two emeralds"),
            () -> assertTrue(source.contains(
                "new MerchantOffer(new ItemCost(Items.EMERALD, 12), "
                    + "new ItemStack(ModItems.ALL.get(\"ingredient_delvealloynugget\").get()),"
            ), "one goblinite nugget must cost twelve emeralds")
        );
    }

    @Test
    void gobliniteNuggetRequiresEighteenDust() throws IOException {
        final String source = normalizedSource();

        assertTrue(source.contains(
            "new ItemCost(ModItems.ALL.get(\"ingredient_delvealloydust\").get(), 18), "
                + "new ItemStack(ModItems.ALL.get(\"ingredient_delvealloynugget\").get()),"
        ), "one goblinite nugget must require eighteen dust");
    }

    @Test
    void noDatapackRouteUndercutsTheDustConversion() throws IOException {
        final List<Path> acquisitionResources = List.of(
            DATA.resolve("recipe"),
            DATA.resolve("warlockery_machine"),
            DATA.resolve("villager_trade"),
            DATA.resolve("trade_set")
        );

        for (final Path root : acquisitionResources) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                assertTrue(paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .noneMatch(GobliniteEconomyTest::convertsDustToNuggets), root::toString);
            }
        }
    }

    @Test
    void gobliniteEconomyUsesCanonicalCommonTags() throws IOException {
        assertEquals(
            Set.of("warlockery:ingredient_delvealloydust"),
            values(Path.of("src/main/resources/data/c/tags/item/dusts/goblinite.json"))
        );
        assertEquals(
            Set.of("warlockery:ingredient_delvealloynugget"),
            values(Path.of("src/main/resources/data/c/tags/item/nuggets/goblinite.json"))
        );
    }

    private static boolean convertsDustToNuggets(final Path path) {
        try {
            final String content = Files.readString(path);
            final boolean consumesDust = content.contains("ingredient_delvealloydust")
                || content.contains("#c:dusts/goblinite");
            final boolean producesNuggets = content.contains("ingredient_delvealloynugget");
            return consumesDust && producesNuggets;
        } catch (final IOException exception) {
            throw new IllegalStateException("Unable to inspect " + path, exception);
        }
    }

    private static Set<String> values(final Path path) throws IOException {
        final JsonObject tag = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        return tag.getAsJsonArray("values").asList().stream()
            .map(value -> value.getAsString())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String normalizedSource() throws IOException {
        return Files.readString(ENTITY_SOURCE).replaceAll("\\s+", " ");
    }
}
