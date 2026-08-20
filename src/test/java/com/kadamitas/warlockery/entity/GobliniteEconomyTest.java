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
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class GobliniteEconomyTest {
    private static final Path DATA = Path.of("src/main/resources/data/warlockery");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void goblinitePurchasesUseScarceMaterialPrices() {
        final List<GoblinTradeCatalog.OfferSpec> miner = GoblinTradeCatalog.coreOffers(
            GoblinProfession.MINER
        );
        final List<GoblinTradeCatalog.OfferSpec> smith = GoblinTradeCatalog.coreOffers(
            GoblinProfession.SMITH
        );
        final List<GoblinTradeCatalog.OfferSpec> prospector = GoblinTradeCatalog.coreOffers(
            GoblinProfession.PROSPECTOR
        );

        assertAll(
            () -> assertTrue(hasOffer(miner, "minecraft:emerald", 8, "warlockery:raw_delvealloy", 1),
                "one raw goblinite must cost eight emeralds"),
            () -> assertTrue(hasOffer(smith, "minecraft:emerald", 32, "warlockery:delvealloypickaxe", 1),
                "a goblinite pickaxe must cost thirty-two emeralds"),
            () -> assertTrue(hasOffer(prospector, "minecraft:emerald", 12,
                "warlockery:ingredient_delvealloynugget", 1),
                "one goblinite nugget must cost twelve emeralds")
        );
    }

    @Test
    void gobliniteNuggetRequiresEighteenDust() {
        final List<GoblinTradeCatalog.OfferSpec> prospector = GoblinTradeCatalog.coreOffers(
            GoblinProfession.PROSPECTOR
        );

        assertTrue(hasOffer(prospector, "warlockery:ingredient_delvealloydust", 18,
            "warlockery:ingredient_delvealloynugget", 1), "one goblinite nugget must require eighteen dust");
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

    private static boolean hasOffer(
        final List<GoblinTradeCatalog.OfferSpec> offers,
        final String cost,
        final int costCount,
        final String reward,
        final int rewardCount
    ) {
        return offers.stream().anyMatch(offer -> offer.cost().id().equals(cost)
            && offer.costCount() == costCount
            && offer.reward().id().equals(reward)
            && offer.rewardCount() == rewardCount);
    }
}
