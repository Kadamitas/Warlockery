package com.kadamitas.warlockery.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;

/** Uses the actual registered loot tables, enchantments, and mod items, not stand-ins. */
public final class LootSchemaGameTests {
    private static final Set<String> OPERATIONS = Set.of(
        "minecraft:all_of", "minecraft:any_of", "minecraft:inverted", "minecraft:match_block",
        "minecraft:killed_by_player", "minecraft:match_tool", "minecraft:random_chance",
        "minecraft:random_chance_with_enchanted_bonus", "minecraft:survives_explosion",
        "minecraft:table_bonus", "minecraft:apply_bonus", "minecraft:enchanted_count_increase",
        "minecraft:explosion_decay", "minecraft:set_count");

    private LootSchemaGameTests() { }

    public static void conditionsAndModifiersSurviveLoading(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final var server = level.getServer();
        final var lookup = server.reloadableRegistries().lookup();
        final var ops = lookup.createSerializationContext(JsonOps.INSTANCE);
        final var resources = server.getResourceManager().listResources("loot_table",
            id -> id.getNamespace().equals("warlockery") && id.getPath().endsWith(".json"));
        helper.assertValueEqual(resources.size(), 174, "every production loot table is audited");
        resources.forEach((path, resource) -> {
            try (final var reader = resource.openAsReader()) {
                final JsonElement original = JsonParser.parseReader(reader);
                rejectLegacyFields(helper, original, path.toString());
                final String name = path.getPath().substring("loot_table/".length(), path.getPath().length() - 5);
                final var key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("warlockery", name));
                final LootTable loaded = server.reloadableRegistries().getLootTable(key);
                final JsonElement encoded = LootTable.DIRECT_CODEC.encodeStart(ops, loaded).getOrThrow();
                helper.assertValueEqual(operations(encoded), operations(original),
                    path + " must retain every condition and modifier through the real codec");
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        });

        final var ore = ModBlocks.ALL.get("silver_ore").get().defaultBlockState();
        final ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        final var position = helper.absolutePos(new BlockPos(1, 1, 1));
        final var ordinary = Block.getDrops(ore, level, position, null, null, pickaxe);
        helper.assertValueEqual(ordinary.size(), 1, "ordinary silver ore produces one drop stack");
        helper.assertTrue(ordinary.getFirst().is(ModItems.ALL.get("raw_silver").get()),
            "without Silk Touch the raw-silver alternative is reachable");
        helper.assertValueEqual(ordinary.getFirst().getCount(), 1, "unenchanted ore count stays exact");
        pickaxe.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        final var silk = Block.getDrops(ore, level, position, null, null, pickaxe);
        helper.assertValueEqual(silk.size(), 1, "Silk Touch produces one drop stack");
        helper.assertTrue(silk.getFirst().is(ModItems.ALL.get("silver_ore").get()),
            "the real Silk Touch enchantment selects the ore-block alternative");
        helper.succeed();
    }

    private static Map<String, Integer> operations(final JsonElement value) {
        final Map<String, Integer> counts = new TreeMap<>();
        countOperations(value, counts);
        return counts;
    }

    private static void countOperations(final JsonElement value, final Map<String, Integer> counts) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(child -> countOperations(child, counts));
        else if (value.isJsonObject()) {
            final var object = value.getAsJsonObject();
            if (object.has("type") && OPERATIONS.contains(object.get("type").getAsString()))
                counts.merge(object.get("type").getAsString(), 1, Integer::sum);
            object.entrySet().forEach(entry -> countOperations(entry.getValue(), counts));
        }
    }

    private static void rejectLegacyFields(final GameTestHelper helper, final JsonElement value, final String path) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(child -> rejectLegacyFields(helper, child, path));
        else if (value.isJsonObject()) value.getAsJsonObject().entrySet().forEach(entry -> {
            helper.assertFalse(Set.of("conditions", "functions", "function").contains(entry.getKey()),
                path + " cannot contain silently ignored pre-26.3 loot fields");
            rejectLegacyFields(helper, entry.getValue(), path);
        });
    }
}
