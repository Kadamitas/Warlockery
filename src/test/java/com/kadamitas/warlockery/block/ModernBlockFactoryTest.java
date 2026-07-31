package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import org.junit.jupiter.api.Test;

class ModernBlockFactoryTest {
    private static final Map<String, Class<? extends Block>> EXPECTED_TYPES = Map.ofEntries(
        Map.entry("alderwooddoor", SignalDoorBlock.class),
        Map.entry("rowanwooddoor", RunedDoorBlock.class),
        Map.entry("icedoor", DoorBlock.class),
        Map.entry("icepressureplate", PressurePlateBlock.class),
        Map.entry("icefence", FenceBlock.class),
        Map.entry("stockade", StockadeBlock.class),
        Map.entry("icefencegate", FenceGateBlock.class),
        Map.entry("iceslab", SlabBlock.class),
        Map.entry("hexwooddoubleslab", SlabBlock.class),
        Map.entry("icestairs", StairBlock.class),
        Map.entry("stairswoodrowan", StairBlock.class),
        Map.entry("hex_ladder", WitchLadderBlock.class)
    );

    @Test
    void everyDeclaredShapeUsesItsVanillaBlockType() {
        EXPECTED_TYPES.forEach((id, type) -> assertEquals(type, ModernBlockFactory.implementationType(id), id));
    }

    @Test
    void supportLookupIsExact() {
        assertTrue(ModernBlockFactory.supports("snowstairs"));
        assertEquals(ModernBlockFactory.Shape.STAIRS, ModernBlockFactory.shapeOf("snowstairs").orElseThrow());
        assertFalse(ModernBlockFactory.supports("snowstairs_extra"));
        assertTrue(ModernBlockFactory.shapeOf("snowstairs_extra").isEmpty());
        assertFalse(ModernBlockFactory.supports("cbuttonwood"));
        assertFalse(ModernBlockFactory.supports("cwoodendoor"));
    }

    @Test
    void alderDoorSignalsOnlyWhileOpen() {
        assertEquals(0, DoorSignalRules.signalForOpen(false));
        assertEquals(15, DoorSignalRules.signalForOpen(true));
    }
}
