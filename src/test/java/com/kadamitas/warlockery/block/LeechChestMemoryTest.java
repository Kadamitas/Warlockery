package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.item.SympatheticBinding;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class LeechChestMemoryTest {
    @Test
    void portableMemoryKeepsTheThreeMostRecentUniqueSamples() {
        final List<SympatheticBinding> samples = List.of(
            binding(1), binding(2), binding(3), binding(4)
        );
        final CompoundTag chest = new CompoundTag();
        LeechChestMemory.writePortable(chest, samples);
        assertEquals(samples.subList(0, 3), LeechChestMemory.readPortable(chest).reversed());
    }

    @Test
    void malformedPortableSamplesAreIgnored() {
        assertTrue(LeechChestMemory.readPortable(new CompoundTag()).isEmpty());
    }

    private static SympatheticBinding binding(final int suffix) {
        return new SympatheticBinding(
            UUID.fromString("00000000-0000-0000-0000-00000000000" + suffix),
            "Visitor " + suffix,
            "minecraft:player"
        );
    }
}
