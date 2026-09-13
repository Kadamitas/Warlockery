package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.ritual.ChalkCircleLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class CircleTalismanGameTests {
    private static final BlockPos SOURCE = new BlockPos(9, 2, 9);
    private static final BlockPos DESTINATION = new BlockPos(29, 2, 9);
    private static final List<BlockPos> LARGE_RING = ChalkCircleLayout.Size.LARGE.offsets();

    private CircleTalismanGameTests() {
    }

    public static void circleTalismanCapturesAndRestoresFullLargeRing(final GameTestHelper helper) {
        final Map<BlockPos, Block> expected = fullLargeCircle();
        placeSupportedGlyphs(helper, SOURCE, expected);
        final BlockPos absoluteSource = helper.absolutePos(SOURCE);
        final CircleTalismanState captured = CircleTalismanState.capture(helper.getLevel(), absoluteSource)
            .orElseThrow();
        helper.assertValueEqual(captured.glyphs().size(), LARGE_RING.size() + 1,
            "captured outer ring and golden heart count");
        final Set<BlockPos> capturedOffsets = captured.glyphs().stream()
            .map(glyph -> new BlockPos(glyph.x(), glyph.y(), glyph.z()))
            .collect(Collectors.toUnmodifiableSet());
        List.of(
            new BlockPos(-7, 0, 0),
            new BlockPos(7, 0, 0),
            new BlockPos(0, 0, -7),
            new BlockPos(0, 0, 7)
        ).forEach(offset -> helper.assertTrue(capturedOffsets.contains(offset),
            "the talisman must capture the large ring mark at " + offset + "; captured " + capturedOffsets));

        captured.removeCaptured(helper.getLevel(), absoluteSource);
        expected.keySet().forEach(offset -> helper.assertTrue(
            helper.getBlockState(SOURCE.offset(offset)).isAir(),
            "capturing the circle must remove the source glyph at " + offset
        ));
        expected.keySet().forEach(offset -> helper.setBlock(DESTINATION.offset(offset).below(), Blocks.STONE));

        final CircleTalismanState.RestoreResult restored = captured.restore(
            helper.getLevel(),
            helper.absolutePos(DESTINATION)
        );
        helper.assertTrue(restored.success(), "the supported empty destination must accept the full large circle");
        helper.assertValueEqual(restored.blocked(), 0, "blocked marks after a successful restore");
        expected.forEach((offset, block) -> helper.assertTrue(
            helper.getBlockState(DESTINATION.offset(offset)).is(block),
            "the restored circle must preserve the glyph type at " + offset
        ));
        helper.succeed();
    }

    public static void circleTalismanMissingSupportFailsAtomically(final GameTestHelper helper) {
        final BlockPos center = new BlockPos(4, 2, 4);
        final CircleTalismanState state = threeGlyphState();
        helper.setBlock(center.below(), Blocks.STONE);
        helper.setBlock(center.east().below(), Blocks.STONE);

        final CircleTalismanState.RestoreResult result = state.restore(
            helper.getLevel(),
            helper.absolutePos(center)
        );
        helper.assertTrue(!result.success(), "a destination with missing support must reject the circle");
        helper.assertValueEqual(result.blocked(), 1, "unsupported glyph count");
        assertTargetsUnchanged(helper, center, Blocks.AIR, Blocks.AIR, Blocks.AIR);
        helper.succeed();
    }

    public static void circleTalismanOccupiedTargetFailsAtomically(final GameTestHelper helper) {
        final BlockPos center = new BlockPos(4, 2, 4);
        final CircleTalismanState state = threeGlyphState();
        List.of(center, center.east(), center.west())
            .forEach(position -> helper.setBlock(position.below(), Blocks.STONE));
        helper.setBlock(center.east(), Blocks.COBBLESTONE);

        final CircleTalismanState.RestoreResult result = state.restore(
            helper.getLevel(),
            helper.absolutePos(center)
        );
        helper.assertTrue(!result.success(), "an occupied target must reject the circle");
        helper.assertValueEqual(result.blocked(), 1, "occupied glyph count");
        assertTargetsUnchanged(helper, center, Blocks.AIR, Blocks.COBBLESTONE, Blocks.AIR);
        helper.succeed();
    }

    private static Map<BlockPos, Block> fullLargeCircle() {
        final List<Block> ringGlyphs = List.of(
            ModBlocks.ALL.get("circleglyphritual").get(),
            ModBlocks.ALL.get("circleglyphinfernal").get(),
            ModBlocks.ALL.get("circleglyph_veil").get(),
            ModBlocks.ALL.get("circleglyphgolden").get()
        );
        final Map<BlockPos, Block> expected = new LinkedHashMap<>();
        expected.put(BlockPos.ZERO, ModBlocks.ALL.get("circle").get());
        for (int index = 0; index < LARGE_RING.size(); index++) {
            expected.put(LARGE_RING.get(index), ringGlyphs.get(index % ringGlyphs.size()));
        }
        return Map.copyOf(expected);
    }

    private static void placeSupportedGlyphs(
        final GameTestHelper helper,
        final BlockPos center,
        final Map<BlockPos, Block> glyphs
    ) {
        glyphs.forEach((offset, block) -> {
            final BlockPos position = center.offset(offset);
            helper.setBlock(position.below(), Blocks.STONE);
            helper.setBlock(position, block);
        });
    }

    private static CircleTalismanState threeGlyphState() {
        return new CircleTalismanState(List.of(
            new CircleTalismanState.Glyph(0, 0, 0, "warlockery:circle"),
            new CircleTalismanState.Glyph(1, 0, 0, "warlockery:circleglyphritual"),
            new CircleTalismanState.Glyph(-1, 0, 0, "warlockery:circleglyphinfernal")
        ));
    }

    private static void assertTargetsUnchanged(
        final GameTestHelper helper,
        final BlockPos center,
        final Block centerBlock,
        final Block eastBlock,
        final Block westBlock
    ) {
        helper.assertTrue(helper.getBlockState(center).is(centerBlock), "center target must remain unchanged");
        helper.assertTrue(helper.getBlockState(center.east()).is(eastBlock), "east target must remain unchanged");
        helper.assertTrue(helper.getBlockState(center.west()).is(westBlock), "west target must remain unchanged");
    }
}
