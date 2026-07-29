package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;

public record CircleTalismanState(List<Glyph> glyphs) {
    private static final String GLYPHS = "WarlockeryCircleGlyphs";
    private static final int CAPTURE_RADIUS = 6;
    private static final int MAX_GLYPHS = 192;

    public CircleTalismanState {
        glyphs = List.copyOf(glyphs);
    }

    public static Optional<CircleTalismanState> capture(final ServerLevel level, final BlockPos center) {
        final List<Glyph> glyphs = BlockPos.betweenClosedStream(
                center.offset(-CAPTURE_RADIUS, -1, -CAPTURE_RADIUS),
                center.offset(CAPTURE_RADIUS, 1, CAPTURE_RADIUS)
            )
            .filter(pos -> level.getBlockState(pos).is(WarlockeryTags.Blocks.CHALK_GLYPHS))
            .sorted(Comparator.comparingLong(BlockPos::asLong))
            .limit(MAX_GLYPHS)
            .map(pos -> new Glyph(
                pos.getX() - center.getX(),
                pos.getY() - center.getY(),
                pos.getZ() - center.getZ(),
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString()
            ))
            .toList();
        return glyphs.isEmpty() ? Optional.empty() : Optional.of(new CircleTalismanState(glyphs));
    }

    public static Optional<CircleTalismanState> read(final ItemStack stack) {
        return read(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    public static Optional<CircleTalismanState> read(final CompoundTag data) {
        final ListTag entries = data.getListOrEmpty(GLYPHS);
        final List<Glyph> glyphs = entries.stream()
            .filter(CompoundTag.class::isInstance)
            .map(CompoundTag.class::cast)
            .map(Glyph::read)
            .flatMap(Optional::stream)
            .limit(MAX_GLYPHS)
            .toList();
        return glyphs.isEmpty() ? Optional.empty() : Optional.of(new CircleTalismanState(glyphs));
    }

    public void write(final ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.put(GLYPHS, glyphEntries()));
    }

    public CompoundTag toTag() {
        final CompoundTag data = new CompoundTag();
        data.put(GLYPHS, glyphEntries());
        return data;
    }

    public static void clear(final ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.remove(GLYPHS));
    }

    public void removeCaptured(final ServerLevel level, final BlockPos center) {
        glyphs.stream().map(glyph -> glyph.position(center)).forEach(pos -> level.destroyBlock(pos, false));
    }

    public RestoreResult restore(final ServerLevel level, final BlockPos center) {
        final List<Placement> placements = glyphs.stream()
            .map(glyph -> glyph.placement(level, center))
            .flatMap(Optional::stream)
            .toList();
        final long blocked = placements.stream()
            .filter(placement -> !level.isInWorldBounds(placement.position())
                || !level.getBlockState(placement.position()).canBeReplaced())
            .count();
        if (placements.size() != glyphs.size() || blocked > 0) {
            return new RestoreResult(false, glyphs.size() - placements.size() + Math.toIntExact(blocked));
        }
        placements.forEach(placement -> level.setBlockAndUpdate(placement.position(), placement.block().defaultBlockState()));
        return new RestoreResult(true, 0);
    }

    private ListTag glyphEntries() {
        final ListTag entries = new ListTag();
        glyphs.stream().map(Glyph::write).forEach(entries::add);
        return entries;
    }

    public record Glyph(int x, int y, int z, String block) {
        private CompoundTag write() {
            final CompoundTag tag = new CompoundTag();
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            tag.putString("block", block);
            return tag;
        }

        private static Optional<Glyph> read(final CompoundTag tag) {
            final String block = tag.getStringOr("block", "");
            return Identifier.tryParse(block) == null
                ? Optional.empty()
                : Optional.of(new Glyph(
                    tag.getIntOr("x", 0),
                    tag.getIntOr("y", 0),
                    tag.getIntOr("z", 0),
                    block
                ));
        }

        private BlockPos position(final BlockPos center) {
            return center.offset(x, y, z);
        }

        private Optional<Placement> placement(final ServerLevel level, final BlockPos center) {
            final Identifier id = Identifier.tryParse(block);
            if (id == null) {
                return Optional.empty();
            }
            final Block value = BuiltInRegistries.BLOCK.getValue(id);
            return value != null && value.defaultBlockState().is(WarlockeryTags.Blocks.CHALK_GLYPHS)
                ? Optional.of(new Placement(position(center), value))
                : Optional.empty();
        }
    }

    public record RestoreResult(boolean success, int blocked) {
    }

    private record Placement(BlockPos position, Block block) {
    }
}
