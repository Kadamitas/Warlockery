package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.block.AltarBlock;
import com.kadamitas.warlockery.block.ConnectedGlyphBlock;
import com.kadamitas.warlockery.block.DisturbedCottonBlock;
import com.kadamitas.warlockery.block.ErosionBrewLiquidBlock;
import com.kadamitas.warlockery.block.MagicMachineBlock;
import com.kadamitas.warlockery.block.MagicalPlantBlockFactory;
import com.kadamitas.warlockery.block.MagicalWoodBlockFactory;
import com.kadamitas.warlockery.block.ModernBlockFactory;
import com.kadamitas.warlockery.block.PlantMineBlock;
import com.kadamitas.warlockery.block.SpiritLiquidBlock;
import com.kadamitas.warlockery.block.HollowTearsLiquidBlock;
import com.kadamitas.warlockery.block.UtilityDeviceBlockFactory;
import com.kadamitas.warlockery.block.WolfTrapBlock;
import com.kadamitas.warlockery.block.WarlockeryCropBlock;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister<Block> REGISTRY = DeferredRegister.create(Registries.BLOCK, Warlockery.MOD_ID);
    private static final Map<String, DeferredHolder<Block, Block>> MUTABLE_BLOCKS = new LinkedHashMap<>();

    public static final DeferredHolder<Block, Block> ALTAR;
    public static final Map<String, DeferredHolder<Block, Block>> ALL;

    static {
        ContentCatalog.BLOCKS.forEach(catalogName -> {
            final String id = ContentCatalog.modernize(catalogName);
            MUTABLE_BLOCKS.put(id, REGISTRY.register(id, () -> create(id)));
        });
        ALTAR = MUTABLE_BLOCKS.get("altar");
        ALL = Collections.unmodifiableMap(MUTABLE_BLOCKS);
    }

    private ModBlocks() {
    }

    private static Block create(final String id) {
        final BlockBehaviour.Properties properties = properties(id);
        if ("altar".equals(id)) {
            return new AltarBlock(properties);
        }
        if (ConnectedGlyphBlock.supports(id)) {
            return new ConnectedGlyphBlock(properties.noCollision().noOcclusion().instabreak().sound(SoundType.STONE));
        }
        if (MachineProfiles.isMachineBlock(id)) {
            return new MagicMachineBlock(properties);
        }
        if ("wolftrap".equals(id)) {
            return new WolfTrapBlock(properties.noOcclusion());
        }
        if ("spiritflowing".equals(id)) {
            return new SpiritLiquidBlock(ModFluids.SPIRIT_SOURCE.get(), properties
                .noCollision()
                .noOcclusion()
                .replaceable()
                .liquid()
                .noLootTable());
        }
        if ("hollowtears".equals(id)) {
            return new HollowTearsLiquidBlock(ModFluids.HOLLOW_TEARS_SOURCE.get(), properties
                .noCollision()
                .noOcclusion()
                .replaceable()
                .liquid()
                .noLootTable());
        }
        if ("brewliquid".equals(id)) {
            return new LiquidBlock(ModFluids.COLORED_BREW_WATER_SOURCE.get(), properties
                .noCollision()
                .noOcclusion()
                .replaceable()
                .liquid()
                .noLootTable());
        }
        if ("erosionbrew".equals(id)) {
            return new ErosionBrewLiquidBlock(ModFluids.EROSION_SOURCE.get(), properties
                .noCollision()
                .noOcclusion()
                .replaceable()
                .liquid()
                .noLootTable());
        }
        if ("somniancotton".equals(id)) {
            return new DisturbedCottonBlock(properties.noCollision().noOcclusion());
        }
        if ("plantmine".equals(id)) {
            return new PlantMineBlock(properties.noCollision().noOcclusion().instabreak().sound(SoundType.GRASS));
        }
        if (UtilityDeviceBlockFactory.supports(id)) {
            return UtilityDeviceBlockFactory.create(id, properties);
        }
        if (MagicalPlantBlockFactory.supports(id)) {
            return MagicalPlantBlockFactory.create(id, properties);
        }
        if (ContentCatalog.CROPS.contains(id)) {
            return new WarlockeryCropBlock(properties.noCollision().randomTicks().instabreak().sound(SoundType.CROP));
        }
        if (MagicalWoodBlockFactory.supports(id)) {
            return MagicalWoodBlockFactory.create(id, properties);
        }
        if (ModernBlockFactory.supports(id)) {
            return ModernBlockFactory.create(id, properties);
        }
        return new Block(properties);
    }

    private static BlockBehaviour.Properties properties(final String id) {
        final var properties = BlockBehaviour.Properties.of()
            .setId(key(id))
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

    private static ResourceKey<Block> key(final String id) {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, id));
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
}
