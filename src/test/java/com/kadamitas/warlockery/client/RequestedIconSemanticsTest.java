package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.registry.ContentCatalog;
import com.kadamitas.warlockery.registry.CreativeInventoryCatalog;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class RequestedIconSemanticsTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");
    private static final Path DEFINITIONS = ASSETS.resolve("items");
    private static final Path MODELS = ASSETS.resolve("models/item");
    private static final Path TEXTURES = ASSETS.resolve("textures/item");
    private static final Map<String, Set<String>> ALLOWED_SHARED_LAYER_ZERO = Map.of(
        "warlockery:item/brew_splash_bottle", Stream.concat(
            ContentCatalog.BREWS.stream()
                .filter(id -> !Set.of("brew_combustion", "brew_endless_water").contains(id)),
            ContentCatalog.INGREDIENTS.stream()
                .filter(id -> id.startsWith("brew"))
                .map(ContentCatalog::ingredientId)
        )
            .collect(Collectors.toUnmodifiableSet()),
        "warlockery:item/brew_fuel", Set.of("brew.fuel", "brew_combustion"),
        "warlockery:item/brew_water", Set.of("brew.water", "brew_endless_water"),
        "warlockery:item/ingredient_broom", Set.of("ingredient_broom", "ingredient_broom_enchanted"),
        "warlockery:item/ruby_slippers", Set.of("emberstep_slippers", "ruby_slippers")
    );
    private static final Set<String> REQUESTED_ICONS = Set.of(
        "arcane_focus", "beast_speech_charm", "bitingbelt", "brew.fuel", "brew.water",
        "buckethollowtears", "bucketspirit", "deathscowl", "deathsfeet", "deathshand",
        "canesword", "replication_staff", "thorn_spear",
        "delvealloyaxe", "delvealloyhoe", "delvealloypickaxe", "delvealloyshovel", "delvealloysword",
        "divinerlava", "divinerwater", "earmuffs", "ingredient_clay_jar", "ingredient_clay_jar_soft",
        "ingredient_annointing_paste", "ingredient_apple_wormy", "ingredient_ash_wood",
        "ingredient_bat_ball", "ingredient_bat_wool", "ingredient_bolt_holy", "ingredient_bolt_silver",
        "ingredient_bolt_splitting", "ingredient_bolt_stake", "ingredient_broom",
        "ingredient_broom_enchanted", "ingredient_candelabra", "ingredient_charm_disrupted_dreams",
        "ingredient_condensed_fear", "ingredient_contract", "ingredient_contract_blaze",
        "ingredient_contract_fiery_touch", "ingredient_contract_resist_fire",
        "ingredient_contract_smelting", "ingredient_contract_torment", "ingredient_redstone_soup",
        "ingredient_toe_of_frog", "ingredient_verdant_catalyst", "ingredient_verdant_catalyst_prime",
        "ingredient_woven_cruor",
        "ingredient_diamond_vapour", "ingredient_disturbed_cotton", "ingredient_dog_tongue",
        "ingredient_drop_of_luck", "ingredient_ender_dew", "ingredient_exhale_of_the_horned_one",
        "ingredient_fanciful_thread", "ingredient_flying_ointment", "ingredient_focused_will",
        "ingredient_fool_skull", "ingredient_foul_fume", "ingredient_frozen_heart",
        "ingredient_fume_filter", "ingredient_ghost_of_the_light", "ingredient_golden_thread",
        "ingredient_gypsum", "ingredient_happenstance_oil", "ingredient_heartofgold",
        "ingredient_heartwood_splinter", "ingredient_hint_of_rebirth", "ingredient_icy_needle",
        "ingredient_impregnated_leather", "ingredient_infernal_animus", "ingredient_infernal_blood",
        "ingredient_infusion_base", "ingredient_matriarchs_blood", "ingredient_mellifluous_hunger",
        "ingredient_mysticunguent", "ingredient_necro_stone", "ingredient_nullcatalyst",
        "ingredient_nullifiedleather", "ingredient_odour_of_purity", "ingredient_oil_of_vitriol",
        "ingredient_owlets_wing", "ingredient_pentacle", "ingredient_purified_milk",
        "ingredient_quartz_sphere", "ingredient_quicklime", "ingredient_reek_of_misfortune",
        "ingredient_refined_evil", "ingredient_rock", "ingredient_seer_stone",
        "ingredient_soul_of_the_world", "ingredient_soul_of_torment", "ingredient_spirit_of_the_veil",
        "ingredient_stake", "ingredient_subdued_spirit", "ingredient_subdued_spirit_village",
        "ingredient_tear_of_the_goddess", "ingredient_warm_blood", "louse", "mirror", "mutator",
        "replication_charge", "seedswormwood", "silver_tongue_charm", "silversword", "spectralstone",
        "stew", "stewraw", "sungrenade", "twisting_band", "vampirechaincoat_female", "vampirecoat",
        "vampirelegs", "vampirelegs_kilt", "witchhat", "witchrobe", "wolftoken"
    );
    private static final Set<String> SCULPTED_REQUESTS = Set.of(
        "ingredient_broom",
        "ingredient_broom_enchanted"
    );

    @Test
    void visibleCreativeIconsNeverUseTheLegacyFourPointStarFallback() throws IOException {
        final BufferedImage fallback = legacyStarMask();
        final List<String> offenders = new ArrayList<>();
        registeredItemIds().stream()
            .filter(CreativeInventoryCatalog::isVisible)
            .filter(id -> Files.isRegularFile(DEFINITIONS.resolve(id + ".json")))
            .forEach(id -> resolvedTexturePaths(id).stream()
                .filter(Files::isRegularFile)
                .filter(path -> hasSameOpaqueMask(fallback, image(path)))
                .findFirst()
                .ifPresent(path -> offenders.add(id + " -> " + path.getFileName())));
        assertTrue(offenders.isEmpty(), () -> "generic star icons remain: " + offenders);
    }

    @Test
    void visibleCreativeIconsNeverUseTheLegacyGreenBlobFallback() throws IOException {
        final BufferedImage fallback = legacyGreenBlobMask();
        final List<String> offenders = new ArrayList<>();
        registeredItemIds().stream()
            .filter(CreativeInventoryCatalog::isVisible)
            .filter(id -> Files.isRegularFile(DEFINITIONS.resolve(id + ".json")))
            .forEach(id -> resolvedTexturePaths(id).stream()
                .filter(Files::isRegularFile)
                .filter(path -> hasSameOpaqueMask(fallback, image(path)))
                .findFirst()
                .ifPresent(path -> offenders.add(id + " -> " + path.getFileName())));
        assertTrue(offenders.isEmpty(), () -> "generic green-blob icons remain: " + offenders);
    }

    @Test
    void visibleCreativeItemsDoNotAliasUnrelatedLayerZeroTextures() {
        final Map<String, List<String>> itemsByTexture = registeredItemIds().stream()
            .filter(CreativeInventoryCatalog::isVisible)
            .filter(id -> Files.isRegularFile(DEFINITIONS.resolve(id + ".json")))
            .flatMap(id -> resolvedLayerZeroReferences(id).map(texture -> Map.entry(texture, id)))
            .filter(entry -> entry.getKey().startsWith("warlockery:item/"))
            .collect(Collectors.groupingBy(
                Map.Entry::getKey,
                LinkedHashMap::new,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())
            ));
        itemsByTexture.replaceAll((_, ids) -> ids.stream().distinct().sorted().toList());
        itemsByTexture.entrySet().removeIf(entry -> entry.getValue().size() == 1);
        itemsByTexture.entrySet().removeIf(entry -> Set.copyOf(entry.getValue())
            .equals(ALLOWED_SHARED_LAYER_ZERO.get(entry.getKey())));
        assertTrue(itemsByTexture.isEmpty(), () -> "unrelated items share layer0 textures: " + itemsByTexture);
    }

    @Test
    void specificallyRequestedIconsResolveToDedicatedSprites() {
        final Map<String, Set<String>> texturesByItem = REQUESTED_ICONS.stream()
            .filter(id -> !SCULPTED_REQUESTS.contains(id))
            .collect(Collectors.toMap(
                id -> id,
                id -> resolvedTextureReferences(id).collect(Collectors.toCollection(LinkedHashSet::new)),
                (_, right) -> right,
                LinkedHashMap::new
            ));
        texturesByItem.forEach((id, textures) -> assertFalse(textures.isEmpty(), id));
        final Map<String, List<String>> shared = texturesByItem.entrySet().stream()
            .flatMap(entry -> entry.getValue().stream().map(texture -> Map.entry(texture, entry.getKey())))
            .collect(Collectors.groupingBy(Map.Entry::getKey,
                LinkedHashMap::new,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        shared.entrySet().removeIf(entry -> entry.getValue().size() == 1);
        shared.entrySet().removeIf(entry -> Set.copyOf(entry.getValue())
            .equals(ALLOWED_SHARED_LAYER_ZERO.get(entry.getKey())));
        assertTrue(shared.isEmpty(), () -> "requested icons share generic textures: " + shared);
    }

    @Test
    void fakeFoodsUseTheirVanillaCounterpartIcons() {
        Map.of(
            "ingredient_odd_porkchop_raw", "minecraft:item/porkchop",
            "ingredient_odd_porkchop_cooked", "minecraft:item/cooked_porkchop",
            "ingredient_sleeping_apple", "minecraft:item/apple"
        ).forEach((id, expected) -> assertTrue(modelReferences(id).anyMatch(expected::equals), id));
    }

    @Test
    void writesRequestedIconContactSheet() throws IOException {
        final List<String> ids = REQUESTED_ICONS.stream()
            .filter(id -> !SCULPTED_REQUESTS.contains(id))
            .sorted()
            .toList();
        final int columns = 8;
        final int cellWidth = 200;
        final int cellHeight = 132;
        final BufferedImage sheet = new BufferedImage(
            columns * cellWidth,
            Math.ceilDiv(ids.size(), columns) * cellHeight,
            BufferedImage.TYPE_INT_ARGB
        );
        final Graphics2D graphics = sheet.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        for (int index = 0; index < ids.size(); index++) {
            final int x = index % columns * cellWidth;
            final int y = index / columns * cellHeight;
            graphics.setColor(new Color(0xC8CBD0));
            graphics.fillRect(x, y, cellWidth, 104);
            graphics.setColor(new Color(0xA4A8AE));
            for (int tileY = 0; tileY < 104; tileY += 8) {
                for (int tileX = 0; tileX < cellWidth; tileX += 8) {
                    if (((tileX + tileY) / 8 & 1) == 0) {
                        graphics.fillRect(x + tileX, y + tileY, 8, 8);
                    }
                }
            }
            final String id = ids.get(index);
            final Path texture = resolvedTexturePaths(id).stream().findFirst().orElseThrow();
            graphics.drawImage(image(texture), x + 52, y + 4, 96, 96, null);
            graphics.setColor(new Color(0x20242A));
            graphics.fillRect(x, y + 104, cellWidth, cellHeight - 104);
            graphics.setColor(Color.WHITE);
            graphics.drawString(id, x + 4, y + 121);
        }
        graphics.dispose();
        final Path output = Path.of("build/reports/visual-audit/requested-item-sprites.png");
        Files.createDirectories(output.getParent());
        assertTrue(ImageIO.write(sheet, "png", output.toFile()));
        assertTrue(Files.size(output) > 20_000L);
    }

    @Test
    void bothCompassFamiliesAreWiredToWarlockeryFrames() {
        Map.of("playercompass", "warlockery:item/playercompass", "shelfcompass", "warlockery:item/shelfcompass_")
            .forEach((id, prefix) -> {
                final List<String> references = definitionReferences(id).filter(value -> value.contains(":item/"))
                    .toList();
                assertEquals(32, references.stream().distinct().count(), id);
                assertTrue(references.stream().allMatch(value -> value.startsWith(prefix)), id);
                references.forEach(reference -> {
                    final String model = reference.substring("warlockery:item/".length());
                    assertTrue(Files.isRegularFile(MODELS.resolve(model + ".json")), reference);
                    assertTrue(Files.isRegularFile(TEXTURES.resolve(model + ".png")), reference);
                });
            });
    }

    private static Set<String> registeredItemIds() {
        final Stream<String> blocks = ContentCatalog.BLOCKS.stream()
            .filter(id -> !ContentCatalog.CROPS.contains(id))
            .map(ContentCatalog::modernize)
            .filter(id -> !"pentacle".equals(id));
        final Stream<String> items = ContentCatalog.ITEMS.stream().map(ContentCatalog::modernize);
        final Stream<String> ingredients = ContentCatalog.INGREDIENTS.stream().map(ContentCatalog::ingredientId);
        final Stream<String> brews = ContentCatalog.BREWS.stream();
        final Stream<String> eggs;
        try {
            eggs = Files.list(DEFINITIONS)
                .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                .filter(id -> id.endsWith("_spawn_egg"));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        try (eggs) {
            return Stream.of(blocks, items, ingredients, brews, eggs)
                .flatMap(stream -> stream)
                .collect(Collectors.toUnmodifiableSet());
        }
    }

    private static Stream<String> modelReferences(final String id) {
        return Stream.concat(definitionReferences(id), resolvedModelObjects(id).flatMap(RequestedIconSemanticsTest::strings));
    }

    private static Stream<String> definitionReferences(final String id) {
        return strings(json(DEFINITIONS.resolve(id + ".json")));
    }

    private static Stream<JsonObject> resolvedModelObjects(final String id) {
        return definitionReferences(id)
            .filter(value -> value.startsWith("warlockery:item/"))
            .map(value -> MODELS.resolve(value.substring("warlockery:item/".length()) + ".json"))
            .filter(Files::isRegularFile)
            .map(RequestedIconSemanticsTest::json);
    }

    private static Stream<String> resolvedTextureReferences(final String id) {
        return resolvedModelObjects(id).flatMap(model -> model.has("textures")
            ? strings(model.get("textures")).filter(value -> value.contains(":item/"))
            : Stream.empty());
    }

    private static Stream<String> resolvedLayerZeroReferences(final String id) {
        return resolvedModelObjects(id)
            .filter(model -> model.has("textures"))
            .map(model -> model.getAsJsonObject("textures"))
            .filter(textures -> textures.has("layer0"))
            .map(textures -> textures.get("layer0").getAsString());
    }

    private static List<Path> resolvedTexturePaths(final String id) {
        return resolvedTextureReferences(id)
            .filter(value -> value.startsWith("warlockery:item/"))
            .map(value -> TEXTURES.resolve(value.substring("warlockery:item/".length()) + ".png"))
            .toList();
    }

    private static Stream<String> strings(final JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return Stream.of(element.getAsString());
        }
        if (element.isJsonArray()) {
            return element.getAsJsonArray().asList().stream().flatMap(RequestedIconSemanticsTest::strings);
        }
        if (element.isJsonObject()) {
            return element.getAsJsonObject().entrySet().stream().flatMap(entry -> strings(entry.getValue()));
        }
        return Stream.empty();
    }

    private static BufferedImage legacyStarMask() {
        final BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        final var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillPolygon(new Polygon(new int[]{8, 14, 8, 2}, new int[]{2, 8, 14, 8}, 4));
        graphics.dispose();
        return image;
    }

    private static BufferedImage legacyGreenBlobMask() {
        final BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        final int[] left = {5, 4, 3, 3, 3, 2, 3, 3, 3, 4, 5};
        final int[] width = {7, 9, 11, 11, 11, 12, 11, 11, 11, 9, 7};
        for (int row = 0; row < left.length; row++) {
            for (int column = left[row]; column < left[row] + width[row]; column++) {
                image.setRGB(column, row + 3, Color.WHITE.getRGB());
            }
        }
        return image;
    }

    private static boolean hasSameOpaqueMask(final BufferedImage left, final BufferedImage right) {
        if (left.getWidth() != right.getWidth() || left.getHeight() != right.getHeight()) {
            return false;
        }
        return java.util.stream.IntStream.range(0, left.getWidth() * left.getHeight())
            .allMatch(index -> alpha(left, index) == alpha(right, index));
    }

    private static boolean alpha(final BufferedImage image, final int index) {
        return image.getRGB(index % image.getWidth(), index / image.getWidth()) >>> 24 > 0;
    }

    private static JsonObject json(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private static BufferedImage image(final Path path) {
        try {
            return ImageIO.read(path.toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
