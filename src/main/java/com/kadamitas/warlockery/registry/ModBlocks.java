package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.block.AltarBlock;
import com.kadamitas.warlockery.block.ConnectedGlyphBlock;
import com.kadamitas.warlockery.block.DisturbedCottonBlock;
import com.kadamitas.warlockery.block.ErosionBrewLiquidBlock;
import com.kadamitas.warlockery.block.FumeFunnelBlock;
import com.kadamitas.warlockery.block.MagicMachineBlock;
import com.kadamitas.warlockery.block.MagicalPlantBlockFactory;
import com.kadamitas.warlockery.block.MagicalWoodBlockFactory;
import com.kadamitas.warlockery.block.ModernBlockFactory;
import com.kadamitas.warlockery.block.PerpetualIceBlock;
import com.kadamitas.warlockery.block.PlantMineBlock;
import com.kadamitas.warlockery.block.ShadedGlassBlock;
import com.kadamitas.warlockery.block.SpiritLiquidBlock;
import com.kadamitas.warlockery.block.HollowTearsLiquidBlock;
import com.kadamitas.warlockery.block.UtilityDeviceBlockFactory;
import com.kadamitas.warlockery.block.WolfTrapBlock;
import com.kadamitas.warlockery.block.WarlockeryCropBlock;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
    private static final Map<String, RegistrationHandle<Block>> MUTABLE_BLOCKS = new LinkedHashMap<>();
    private static final FactoryCatalog<BlockBehaviour.Properties, Block> FIXED_FACTORIES = new FactoryCatalog<>(
        "block",
        Map.ofEntries(
            FactoryCatalog.entry("altar", AltarBlock::new),
            FactoryCatalog.entry("fumefunnel", properties -> new FumeFunnelBlock(properties.noOcclusion())),
            FactoryCatalog.entry("filteredfumefunnel", properties -> new FumeFunnelBlock(properties.noOcclusion())),
            FactoryCatalog.entry("wolftrap", properties -> new WolfTrapBlock(properties.noOcclusion())),
            FactoryCatalog.entry("spiritflowing", properties -> new SpiritLiquidBlock(
                ModFluids.SPIRIT_SOURCE.get(),
                liquidProperties(properties).lightLevel(state -> 4)
            )),
            FactoryCatalog.entry("hollowtears", properties -> new HollowTearsLiquidBlock(
                ModFluids.HOLLOW_TEARS_SOURCE.get(),
                liquidProperties(properties).lightLevel(state -> 2)
            )),
            FactoryCatalog.entry("brewliquid", properties -> new LiquidBlock(
                ModFluids.COLORED_BREW_WATER_SOURCE.get(),
                liquidProperties(properties)
            )),
            FactoryCatalog.entry("erosionbrew", properties -> new ErosionBrewLiquidBlock(
                ModFluids.EROSION_SOURCE.get(),
                liquidProperties(properties)
            )),
            FactoryCatalog.entry("somniancotton", properties ->
                new DisturbedCottonBlock(properties.noCollision().noOcclusion())),
            FactoryCatalog.entry("plantmine", properties -> new PlantMineBlock(
                properties.noCollision().noOcclusion().instabreak().sound(SoundType.GRASS)
            )),
            FactoryCatalog.entry("perpetualice", PerpetualIceBlock::new),
            FactoryCatalog.entry("paradox_egg", properties -> new DragonEggBlock(properties.noOcclusion()))
        )
    );
    private static final List<BlockFactoryRule> FACTORY_RULES = List.of(
        new BlockFactoryRule(ConnectedGlyphBlock::supports, (_, properties) -> new ConnectedGlyphBlock(
            properties.noCollision().noOcclusion().instabreak().noLootTable().sound(SoundType.STONE)
        )),
        new BlockFactoryRule(MachineProfiles::isMachineBlock, (_, properties) -> new MagicMachineBlock(
            properties.lightLevel(state -> state.getValue(MagicMachineBlock.LIT) ? 10 : 0)
        )),
        new BlockFactoryRule(id -> id.contains("shadedglass"), (id, properties) ->
            new ShadedGlassBlock(properties.noOcclusion(), id.endsWith("_active"))),
        new BlockFactoryRule(UtilityDeviceBlockFactory::supports, UtilityDeviceBlockFactory::create),
        new BlockFactoryRule(MagicalPlantBlockFactory::supports, MagicalPlantBlockFactory::create),
        new BlockFactoryRule(ContentCatalog.CROPS::contains, (_, properties) -> new WarlockeryCropBlock(
            properties.noCollision().randomTicks().instabreak().sound(SoundType.CROP)
        )),
        new BlockFactoryRule(MagicalWoodBlockFactory::supports, MagicalWoodBlockFactory::create),
        new BlockFactoryRule(ModernBlockFactory::supports, ModernBlockFactory::create)
    );

    public static final RegistrationHandle<Block> ALTAR;
    public static final Map<String, RegistrationHandle<Block>> ALL;

    static {
        ContentCatalog.BLOCKS.forEach(catalogName -> {
            final String id = ContentCatalog.modernize(catalogName);
            MUTABLE_BLOCKS.put(id, RegistrationHandle.create(id, () -> create(id)));
        });
        ALTAR = MUTABLE_BLOCKS.get("altar");
        ALL = Collections.unmodifiableMap(new LinkedHashMap<>(MUTABLE_BLOCKS));
    }

    private ModBlocks() {
    }

    public static void register() {
        ALL.values().forEach(handle -> handle.register(BuiltInRegistries.BLOCK));
    }

    private static Block create(final String id) {
        final BlockBehaviour.Properties properties = properties(id);
        return FIXED_FACTORIES.factoryFor(id)
            .map(factory -> factory.create(properties))
            .orElseGet(() -> FACTORY_RULES.stream()
                .filter(rule -> rule.supports(id))
                .findFirst()
                .map(rule -> rule.create(id, properties))
                .orElseGet(() -> new Block(properties)));
    }

    private static BlockBehaviour.Properties liquidProperties(final BlockBehaviour.Properties properties) {
        return properties.noCollision().noOcclusion().replaceable().liquid().noLootTable();
    }

    private static BlockBehaviour.Properties properties(final String id) {
        final var properties = BlockBehaviour.Properties.of()
            .setId(ResourceKey.create(
                Registries.BLOCK,
                Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, id)
            ))
            .mapColor(mapColor(id))
            .strength(id.contains("barrier") ? -1.0F : 1.5F, 6.0F)
            .sound(sound(id));
        if ("perpetualice".equals(id)) {
            properties.friction(0.98F);
        }
        if (isMetalBlock(id)) {
            properties.requiresCorrectToolForDrops();
        }
        if (ContentCatalog.NON_SOLID.contains(id)) {
            properties.noCollision().noOcclusion();
        }
        if (SculptedBlockCatalog.contains(id)) {
            properties.noOcclusion();
        }
        if (id.contains("light") || id.contains("glow") || id.contains("ember") || id.contains("portal")) {
            properties.lightLevel(_ -> 12);
        }
        return properties;
    }

    private static boolean isMetalBlock(final String id) {
        return id.contains("silver") || id.contains("delvealloy");
    }

    private static MapColor mapColor(final String id) {
        if (id.contains("ice") || id.contains("snow")) {
            return MapColor.ICE;
        }
        if (id.contains("wood") || id.contains("log") || id.contains("leaves")) {
            return MapColor.WOOD;
        }
        return id.contains("blood") ? MapColor.COLOR_RED : MapColor.STONE;
    }

    private static SoundType sound(final String id) {
        if (id.contains("ice") || id.contains("glass") || id.contains("mirror")) {
            return SoundType.GLASS;
        }
        if (id.contains("wood") || id.contains("log") || id.contains("leaves")) {
            return SoundType.WOOD;
        }
        return SoundType.STONE;
    }

    private record BlockFactoryRule(
        Predicate<String> selector,
        BiFunction<String, BlockBehaviour.Properties, Block> factory
    ) {
        private boolean supports(final String id) {
            return selector.test(id);
        }

        private Block create(final String id, final BlockBehaviour.Properties properties) {
            return factory.apply(id, properties);
        }
    }
}
