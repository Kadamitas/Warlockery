package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.registry.ContentCatalog;
import com.kadamitas.warlockery.registry.KobolditeMaterials;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class KobolditeEquipmentTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");
    private static final Path DATA = Path.of("src/main/resources/data");
    private static final Set<String> EQUIPMENT_IDS = Set.of(
        "delvealloysword",
        "delvealloyaxe",
        "delvealloypickaxe",
        "delvealloyshovel",
        "delvealloyhoe",
        "delvealloyhelm",
        "delvealloychestplate",
        "delvealloyleggings",
        "delvealloyboots"
    );
    private static final Set<String> TOOL_IDS = Set.of(
        "delvealloysword",
        "delvealloyaxe",
        "delvealloypickaxe",
        "delvealloyshovel",
        "delvealloyhoe"
    );
    private static final Set<Integer> PALETTE = Set.of(
        0xFF09130F,
        0xFF173326,
        0xFF245A3B,
        0xFF3F8652,
        0xFF77BD67,
        0xFFB7E58D,
        0xFF251A14,
        0xFF805333
    );

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void factoryAndCatalogExposeTheCompleteSaveCompatibleFamily() {
        assertEquals(EQUIPMENT_IDS, KobolditeEquipmentFactory.ids());
        final Set<String> catalogIds = ContentCatalog.ITEMS.stream()
            .map(ContentCatalog::modernize)
            .filter(EQUIPMENT_IDS::contains)
            .collect(Collectors.toUnmodifiableSet());
        assertEquals(EQUIPMENT_IDS, catalogIds);
    }

    @Test
    void toolMaterialMatchesNetheriteTierAndUsesTheKobolditeRepairTag() {
        assertEquals(ToolMaterial.NETHERITE.incorrectBlocksForDrops(), KobolditeMaterials.TOOL.incorrectBlocksForDrops());
        assertEquals(ToolMaterial.NETHERITE.durability(), KobolditeMaterials.TOOL.durability());
        assertEquals(ToolMaterial.NETHERITE.speed(), KobolditeMaterials.TOOL.speed());
        assertEquals(ToolMaterial.NETHERITE.attackDamageBonus(), KobolditeMaterials.TOOL.attackDamageBonus());
        assertEquals(ToolMaterial.NETHERITE.enchantmentValue(), KobolditeMaterials.TOOL.enchantmentValue());
        assertEquals("c:ingots/koboldite", KobolditeMaterials.TOOL.repairItems().location().toString());
    }

    @Test
    void armorMaterialMatchesNetheriteTierAndUsesItsOwnEquipmentAsset() {
        assertEquals(ArmorMaterials.NETHERITE.durability(), KobolditeMaterials.ARMOR.durability());
        assertEquals(ArmorMaterials.NETHERITE.enchantmentValue(), KobolditeMaterials.ARMOR.enchantmentValue());
        assertEquals(ArmorMaterials.NETHERITE.equipSound(), KobolditeMaterials.ARMOR.equipSound());
        assertEquals(ArmorMaterials.NETHERITE.toughness(), KobolditeMaterials.ARMOR.toughness());
        assertEquals(ArmorMaterials.NETHERITE.knockbackResistance(), KobolditeMaterials.ARMOR.knockbackResistance());
        assertEquals(ArmorMaterials.NETHERITE.defense(), KobolditeMaterials.ARMOR.defense());
        assertEquals("c:ingots/koboldite", KobolditeMaterials.ARMOR.repairIngredient().location().toString());
        assertEquals("warlockery:delvealloy", KobolditeMaterials.ARMOR.assetId().identifier().toString());
    }

    @Test
    void everyEquipmentRecipeAcceptsCommonKobolditeAndWoodenRodTags() {
        EQUIPMENT_IDS.forEach(id -> {
            final JsonObject recipe = readJson(DATA.resolve("warlockery/recipe/" + id + ".json"));
            assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
            assertEquals("warlockery:" + id, recipe.getAsJsonObject("result").get("id").getAsString());
            assertEquals("#c:ingots/koboldite", recipe.getAsJsonObject("key").get("I").getAsString());
            if (TOOL_IDS.contains(id)) {
                assertEquals("#c:rods/wooden", recipe.getAsJsonObject("key").get("S").getAsString());
            }
        });
    }

    @Test
    void everyEquipmentItemHasAClientDefinitionModelAndTransparentPixelTexture() {
        EQUIPMENT_IDS.forEach(id -> {
            final Path definition = ASSETS.resolve("items/" + id + ".json");
            final Path model = ASSETS.resolve("models/item/" + id + ".json");
            final Path texture = ASSETS.resolve("textures/item/" + id + ".png");
            assertTrue(Files.isRegularFile(definition), definition::toString);
            assertTrue(Files.isRegularFile(model), model::toString);
            assertTrue(Files.isRegularFile(texture), texture::toString);
            final BufferedImage image = readImage(texture);
            assertEquals(16, image.getWidth(), texture::toString);
            assertEquals(16, image.getHeight(), texture::toString);
            final List<Integer> pixels = pixels(image);
            assertTrue(pixels.stream().anyMatch(pixel -> alpha(pixel) == 0), texture::toString);
            assertTrue(pixels.stream().anyMatch(pixel -> alpha(pixel) > 0), texture::toString);
            assertTrue(
                pixels.stream().filter(pixel -> alpha(pixel) > 0).allMatch(PALETTE::contains),
                texture::toString
            );
        });
    }

    @Test
    void armorAssetDefinesEveryHumanoidLayerAndMatchingTextures() {
        final JsonObject layers = readJson(ASSETS.resolve("equipment/delvealloy.json")).getAsJsonObject("layers");
        Stream.of("humanoid", "humanoid_baby", "humanoid_leggings").forEach(layer -> {
            final JsonArray definitions = layers.getAsJsonArray(layer);
            assertEquals("warlockery:delvealloy", definitions.get(0).getAsJsonObject().get("texture").getAsString());
            final Path texture = ASSETS.resolve("textures/entity/equipment/" + layer + "/delvealloy.png");
            final BufferedImage image = readImage(texture);
            assertEquals(64, image.getWidth(), texture::toString);
            assertEquals(32, image.getHeight(), texture::toString);
            assertTrue(pixels(image).stream().anyMatch(pixel -> alpha(pixel) > 0), texture::toString);
        });
    }

    @Test
    void kobolditeCommonTagsAliasEveryLegacyRegistryObject() {
        final Map<String, Set<String>> aliases = Map.ofEntries(
            Map.entry("c/tags/item/ingots/koboldite.json", Set.of("warlockery:ingredient_delvealloyingot")),
            Map.entry("c/tags/item/raw_materials/koboldite.json", Set.of("warlockery:raw_delvealloy")),
            Map.entry("c/tags/item/dusts/koboldite.json", Set.of("warlockery:ingredient_delvealloydust")),
            Map.entry("c/tags/item/nuggets/koboldite.json", Set.of("warlockery:ingredient_delvealloynugget")),
            Map.entry(
                "c/tags/item/ores/koboldite.json",
                Set.of("warlockery:delvealloy_ore", "warlockery:deepslate_delvealloy_ore")
            ),
            Map.entry("c/tags/item/storage_blocks/koboldite.json", Set.of("warlockery:delvealloy_block")),
            Map.entry("c/tags/item/storage_blocks/raw_koboldite.json", Set.of("warlockery:raw_delvealloy_block")),
            Map.entry(
                "c/tags/block/ores/koboldite.json",
                Set.of("warlockery:delvealloy_ore", "warlockery:deepslate_delvealloy_ore")
            ),
            Map.entry("c/tags/block/storage_blocks/koboldite.json", Set.of("warlockery:delvealloy_block")),
            Map.entry("c/tags/block/storage_blocks/raw_koboldite.json", Set.of("warlockery:raw_delvealloy_block"))
        );
        aliases.forEach((path, expected) -> assertTrue(tagValues(path).containsAll(expected), path));
    }

    @Test
    void equipmentPublishesVanillaAndCommonClassificationTags() {
        final Map<String, Set<String>> tags = Map.ofEntries(
            Map.entry("minecraft/tags/item/swords.json", Set.of("warlockery:delvealloysword")),
            Map.entry("minecraft/tags/item/axes.json", Set.of("warlockery:delvealloyaxe")),
            Map.entry("minecraft/tags/item/pickaxes.json", Set.of("warlockery:delvealloypickaxe")),
            Map.entry("minecraft/tags/item/shovels.json", Set.of("warlockery:delvealloyshovel")),
            Map.entry("minecraft/tags/item/hoes.json", Set.of("warlockery:delvealloyhoe")),
            Map.entry("minecraft/tags/item/head_armor.json", Set.of("warlockery:delvealloyhelm")),
            Map.entry("minecraft/tags/item/chest_armor.json", Set.of("warlockery:delvealloychestplate")),
            Map.entry("minecraft/tags/item/leg_armor.json", Set.of("warlockery:delvealloyleggings")),
            Map.entry("minecraft/tags/item/foot_armor.json", Set.of("warlockery:delvealloyboots")),
            Map.entry(
                "c/tags/item/tools/melee_weapon.json",
                Set.of("warlockery:delvealloysword", "warlockery:delvealloyaxe")
            ),
            Map.entry(
                "c/tags/item/tools.json",
                Set.of(
                    "#minecraft:axes",
                    "#minecraft:hoes",
                    "#minecraft:pickaxes",
                    "#minecraft:shovels",
                    "#minecraft:swords"
                )
            ),
            Map.entry("c/tags/item/tools/mining_tool.json", Set.of("warlockery:delvealloypickaxe")),
            Map.entry(
                "c/tags/item/armors/humanoid.json",
                Set.of(
                    "warlockery:delvealloyhelm",
                    "warlockery:delvealloychestplate",
                    "warlockery:delvealloyleggings",
                    "warlockery:delvealloyboots"
                )
            )
        );
        tags.forEach((path, expected) -> assertTrue(tagValues(path).containsAll(expected), path));
        assertTrue(
            tagValues("warlockery/tags/item/magic/metal_equipment.json").containsAll(
                EQUIPMENT_IDS.stream().map(id -> "warlockery:" + id).collect(Collectors.toUnmodifiableSet())
            )
        );
    }

    @Test
    void hobgoblinMiningUsesTagDrivenToolsOresAndEnhancements() {
        final Map<String, Set<String>> tags = Map.of(
            "warlockery/tags/item/hobgoblin_mining_tools.json", Set.of("#minecraft:pickaxes"),
            "warlockery/tags/item/enhanced_hobgoblin_mining_tools.json", Set.of("warlockery:delvealloypickaxe"),
            "warlockery/tags/block/hobgoblin_mineables.json", Set.of("#c:ores"),
            "warlockery/tags/block/hobgoblin_auto_smeltable_ores.json", Set.of("#c:ores")
        );
        tags.forEach((path, expected) -> assertTrue(tagValues(path).containsAll(expected), path));
    }

    @Test
    void translationsUseKobolditeWithoutRenamingRegistryKeys() throws IOException {
        final JsonObject translations = readJson(ASSETS.resolve("lang/en_us.json"));
        EQUIPMENT_IDS.forEach(id -> {
            final String name = translations.get("item.warlockery." + id).getAsString();
            assertTrue(name.startsWith("Koboldite "), id);
            assertFalse(name.contains("Delvealloy"), id);
        });
        assertEquals("Koboldite Ingot", translations.get("item.warlockery.ingredient_delvealloyingot").getAsString());
        assertTrue(translations.has("tooltip.warlockery.koboldite_pickaxe.hobgoblin"));
        assertTrue(translations.has("tooltip.warlockery.koboldite_pickaxe.ores"));
        assertTrue(Files.readString(Path.of("docs/CROSS_MOD_COMPATIBILITY.md")).contains(
            "save-compatible `delvealloy` aliases"
        ));
    }

    private static Set<String> tagValues(final String relativePath) {
        return readJson(DATA.resolve(relativePath)).getAsJsonArray("values").asList().stream()
            .map(value -> value.getAsString())
            .collect(Collectors.toUnmodifiableSet());
    }

    private static JsonObject readJson(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private static BufferedImage readImage(final Path path) {
        try {
            return ImageIO.read(path.toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private static List<Integer> pixels(final BufferedImage image) {
        return java.util.stream.IntStream.range(0, image.getWidth() * image.getHeight())
            .map(index -> image.getRGB(index % image.getWidth(), index / image.getWidth()))
            .boxed()
            .toList();
    }

    private static int alpha(final int pixel) {
        return pixel >>> 24;
    }
}
