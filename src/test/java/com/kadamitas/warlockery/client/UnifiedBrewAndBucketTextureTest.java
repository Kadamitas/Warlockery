package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class UnifiedBrewAndBucketTextureTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");
    private static final Path ITEMS = ASSETS.resolve("textures/item");
    private static final List<String> BREWS = List.of(
        "bats", "congealed_spirit", "depths", "erosion", "flowing_spirit", "frogs_tongue", "grave",
        "grotesque", "hexed_leaping", "hitchcock", "hollow_tears", "ice", "infection", "ink", "love",
        "murder_of_crows", "raising", "revealing", "sleep", "soaring", "solid_dirt", "solid_erosion",
        "solid_sand", "solid_sandstone", "solid_stone", "soul_anguish", "soul_fear", "soul_hunger",
        "soul_torment", "sprouting", "substitution", "thorns", "vines", "wasting", "web"
    );
    private static final List<String> RENDERED_BREWS = BREWS.stream()
        .filter(id -> !Set.of("flowing_spirit", "hollow_tears").contains(id))
        .toList();

    @Test
    void everyIngredientBrewUsesOneBottleSilhouetteWithUniqueColorArtwork() {
        final String expectedMask = alphaMask(ITEMS.resolve("brew_splash_bottle.png"));
        final Set<String> artwork = BREWS.stream().map(id -> ITEMS.resolve("ingredient_brew_" + id + ".png"))
            .peek(path -> {
                final BufferedImage image = image(path);
                assertEquals(16, image.getWidth(), path.toString());
                assertEquals(16, image.getHeight(), path.toString());
                assertEquals(expectedMask, alphaMask(path), path + " uses a competing bottle design");
            })
            .map(UnifiedBrewAndBucketTextureTest::sha256)
            .collect(Collectors.toSet());
        assertEquals(BREWS.size(), artwork.size());
    }

    @Test
    void legacyAndModernBrewsUseTheSameRenderedBottleModel() throws IOException {
        final Set<Integer> tints = RENDERED_BREWS.stream().map(id -> {
            final String itemId = "ingredient_brew_" + id;
            final var model = json(ASSETS.resolve("models/item/" + itemId + ".json"));
            assertEquals("minecraft:item/generated", model.get("parent").getAsString(), itemId);
            assertEquals(
                "warlockery:item/brew_splash_bottle",
                model.getAsJsonObject("textures").get("layer0").getAsString(),
                itemId
            );
            final var definition = json(ASSETS.resolve("items/" + itemId + ".json"))
                .getAsJsonObject("model");
            final var tint = definition.getAsJsonArray("tints").get(0).getAsJsonObject();
            assertEquals("minecraft:constant", tint.get("type").getAsString(), itemId);
            return tint.get("value").getAsInt();
        }).collect(Collectors.toSet());
        assertEquals(RENDERED_BREWS.size(), tints.size());

        for (String itemId : List.of("brew_love", "brew_steal_buffs", "brew_transpose")) {
            final var model = json(ASSETS.resolve("models/item/" + itemId + ".json"));
            assertEquals(
                "warlockery:item/brew_splash_bottle",
                model.getAsJsonObject("textures").get("layer0").getAsString(),
                itemId
            );
        }
    }

    @Test
    void everyArcaneBucketUsesTheVanillaBucketSilhouetteAndDistinctContents() throws IOException {
        final List<String> buckets = List.of("bucket_brew", "bucket_erosionbrew", "bucket_spirit", "bucket_hollowtears");
        final Set<String> masks = buckets.stream().map(id -> ITEMS.resolve(id + ".png"))
            .peek(path -> {
                final BufferedImage image = image(path);
                assertEquals(16, image.getWidth(), path.toString());
                assertEquals(16, image.getHeight(), path.toString());
                assertTrue((image.getRGB(0, 0) >>> 24) == 0, path.toString());
            })
            .map(UnifiedBrewAndBucketTextureTest::alphaMask)
            .collect(Collectors.toSet());
        assertEquals(1, masks.size());
        assertEquals(buckets.size(), buckets.stream().map(id -> sha256(ITEMS.resolve(id + ".png"))).distinct().count());
        final var erosionModel = JsonParser.parseString(Files.readString(
            ASSETS.resolve("models/item/bucketerosionbrew.json")
        )).getAsJsonObject();
        assertEquals("warlockery:item/bucket_erosionbrew",
            erosionModel.getAsJsonObject("textures").get("layer0").getAsString());
    }

    private static String alphaMask(final Path path) {
        final BufferedImage image = image(path);
        final StringBuilder mask = new StringBuilder(image.getWidth() * image.getHeight());
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                mask.append((image.getRGB(x, y) >>> 24) > 0 ? '1' : '0');
            }
        }
        return mask.toString();
    }

    private static BufferedImage image(final Path path) {
        try {
            return ImageIO.read(path.toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static com.google.gson.JsonObject json(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String sha256(final Path path) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
