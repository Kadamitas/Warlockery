package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.world.SettlementFortificationRules.LayoutPlan;
import com.kadamitas.warlockery.world.SettlementFortificationRules.Offset;
import com.kadamitas.warlockery.world.SettlementFortificationRules.SettlementKind;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

public final class SettlementFortificationGameTests {
    private static final BlockPos RELATIVE_CENTER = new BlockPos(1, 0, 1);

    private SettlementFortificationGameTests() {
    }

    public static void humanVillageBuildsAClosedStoneDefense(final GameTestHelper helper) {
        final LayoutPlan plan = SettlementFortificationRules.compactPlan(SettlementKind.HUMAN);
        final BlockPos center = helper.absolutePos(RELATIVE_CENTER);
        final var result = SettlementFortificationRuntime.fortify(helper.getLevel(), center, plan);
        assertFortificationApplied(helper, center, plan, result);
        final int wallBaseY = SettlementFortificationRuntime.registeredLayout(helper.getLevel(), center)
            .orElseThrow().deckY() - plan.wallHeight() + 1;
        helper.assertTrue(plan.perimeter().stream().allMatch(offset -> {
            final BlockPos base = new BlockPos(center.getX() + offset.x(), wallBaseY, center.getZ() + offset.z());
            return plan.gates().stream().anyMatch(gate -> gate.offset().equals(offset))
                || helper.getLevel().getBlockState(base).is(Blocks.STONE_BRICKS);
        }), "every non-gate human wall column must use stone bricks");
        helper.succeed();
    }

    public static void hobgoblinVillageBuildsAClosedWoodDefense(final GameTestHelper helper) {
        final LayoutPlan plan = SettlementFortificationRules.compactPlan(SettlementKind.HOBGOBLIN);
        final BlockPos center = helper.absolutePos(RELATIVE_CENTER);
        final var result = SettlementFortificationRuntime.fortify(helper.getLevel(), center, plan);
        assertFortificationApplied(helper, center, plan, result);
        final int wallBaseY = SettlementFortificationRuntime.registeredLayout(helper.getLevel(), center)
            .orElseThrow().deckY() - plan.wallHeight() + 1;
        helper.assertTrue(plan.perimeter().stream().allMatch(offset -> {
            final BlockPos base = new BlockPos(center.getX() + offset.x(), wallBaseY, center.getZ() + offset.z());
            return plan.gates().stream().anyMatch(gate -> gate.offset().equals(offset))
                || helper.getLevel().getBlockState(base).is(Blocks.OAK_PLANKS);
        }), "every non-gate hobgoblin wall column must use oak planks");
        helper.succeed();
    }

    public static void variedTerrainKeepsLevelPatrolDeckAndSupportedWalls(final GameTestHelper helper) {
        final LayoutPlan plan = SettlementFortificationRules.plan(SettlementKind.HUMAN);
        final int deckY = SettlementFortificationRules.patrolDeckY(
            plan,
            offset -> 64 + Math.floorMod(offset.x() * 3 + offset.z() * 5, 7)
        );
        helper.assertValueEqual(deckY, 72, "shared patrol deck elevation");
        helper.assertTrue(plan.perimeter().stream().allMatch(offset -> {
            final int localSurface = 64 + Math.floorMod(offset.x() * 3 + offset.z() * 5, 7);
            final var span = SettlementFortificationRules.wallSpan(localSurface, deckY);
            return span.bottomY() == localSurface
                && span.topY() == deckY
                && java.util.stream.IntStream.rangeClosed(localSurface, deckY).allMatch(span::contains);
        }), "every varied-terrain wall column must remain supported through the common deck height");
        helper.assertTrue(plan.stairs().stream().allMatch(stair ->
            SettlementFortificationRules.stairY(plan, stair, deckY) <= deckY
        ), "all planned stairs must connect upward to the common patrol deck");
        helper.succeed();
    }

    public static void repeatedFortificationDoesNotStackOrDuplicateGuards(final GameTestHelper helper) {
        final LayoutPlan plan = SettlementFortificationRules.compactPlan(SettlementKind.HOBGOBLIN);
        final BlockPos center = helper.absolutePos(RELATIVE_CENTER);
        final var first = SettlementFortificationRuntime.fortify(helper.getLevel(), center, plan);
        final Set<BlockPos> occupiedAfterFirst = snapshotOccupied(helper, center);
        final var second = SettlementFortificationRuntime.fortify(helper.getLevel(), center, plan);
        final Set<BlockPos> occupiedAfterSecond = snapshotOccupied(helper, center);

        helper.assertValueEqual(first.guardsSpawned(), plan.guardSpawns().size(), "initial guard complement");
        helper.assertValueEqual(second.guardsSpawned(), 0, "duplicate guards spawned");
        helper.assertValueEqual(occupiedAfterSecond, occupiedAfterFirst, "repeated fortification changed its silhouette");
        helper.assertValueEqual(settlementGuards(helper, center, plan).size(), plan.guardSpawns().size(),
            "guard complement after repeated fortification");
        helper.succeed();
    }

    public static void protectedVillageBlocksSurviveFortification(final GameTestHelper helper) {
        final LayoutPlan plan = SettlementFortificationRules.compactPlan(SettlementKind.HUMAN);
        final BlockPos center = helper.absolutePos(RELATIVE_CENTER);
        final BlockPos bedFoot = helper.absolutePos(new BlockPos(0, 0, 0));
        final BlockPos bedHead = helper.absolutePos(new BlockPos(1, 0, 0));
        final BlockPos doorLower = helper.absolutePos(new BlockPos(2, 0, 0));
        final BlockPos doorUpper = helper.absolutePos(new BlockPos(2, 1, 0));
        final BlockPos workstation = helper.absolutePos(new BlockPos(2, 0, 2));

        final var redBed = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("red_bed"));
        helper.setBlock(new BlockPos(0, 0, 0), redBed.defaultBlockState()
            .setValue(BedBlock.FACING, Direction.EAST)
            .setValue(BedBlock.PART, BedPart.FOOT));
        helper.setBlock(new BlockPos(1, 0, 0), redBed.defaultBlockState()
            .setValue(BedBlock.FACING, Direction.EAST)
            .setValue(BedBlock.PART, BedPart.HEAD));
        helper.setBlock(new BlockPos(2, 0, 0), Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(new BlockPos(2, 1, 0), Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.SMITHING_TABLE);

        final Map<BlockPos, BlockState> protectedStates = new LinkedHashMap<>();
        Set.of(bedFoot, bedHead, doorLower, doorUpper, workstation)
            .forEach(position -> protectedStates.put(position, helper.getLevel().getBlockState(position)));
        helper.assertTrue(Set.of(bedFoot, bedHead, doorLower, doorUpper, workstation).stream()
            .allMatch(position -> SettlementFortificationRuntime.isProtectedBlock(helper.getLevel(), position)),
            "beds, doors, and village workstations must be protected fortification terrain");

        SettlementFortificationRuntime.fortify(helper.getLevel(), center, plan);

        protectedStates.forEach((position, state) -> helper.assertValueEqual(
            helper.getLevel().getBlockState(position),
            state,
            "protected village block at " + position
        ));
        helper.succeed();
    }

    private static void assertFortificationApplied(
        final GameTestHelper helper,
        final BlockPos center,
        final LayoutPlan plan,
        final SettlementFortificationRuntime.FortificationResult result
    ) {
        final Set<Offset> gateOffsets = plan.gates().stream()
            .map(SettlementFortificationRules.GatePlacement::offset)
            .collect(Collectors.toSet());
        helper.assertTrue(SettlementFortificationRules.isClosedPerimeter(plan),
            "fortification plan must describe a closed perimeter");
        helper.assertTrue(plan.gates().size() >= 4, "fortification must have at least two entrances");
        helper.assertValueEqual(result.gateColumns(), plan.gates().size(), "gate column count");
        helper.assertValueEqual(result.stairBlocks(), plan.stairs().size(), "stair block count");
        helper.assertValueEqual(result.guardsSpawned(), plan.guardSpawns().size(), "spawned settlement guards");
        final int wallBaseY = SettlementFortificationRuntime.registeredLayout(helper.getLevel(), center)
            .orElseThrow().deckY() - plan.wallHeight() + 1;
        helper.assertTrue(plan.perimeter().stream().allMatch(offset -> {
            final BlockPos base = new BlockPos(center.getX() + offset.x(), wallBaseY, center.getZ() + offset.z());
            if (gateOffsets.contains(offset)) {
                return helper.getLevel().getBlockState(base).is(plan.materials().gate())
                    && helper.getLevel().getBlockState(base).getValue(FenceGateBlock.FACING)
                    == plan.gates().stream().filter(gate -> gate.offset().equals(offset)).findFirst().orElseThrow().facing();
            }
            return java.util.stream.IntStream.range(0, plan.wallHeight())
                .allMatch(height -> helper.getLevel().getBlockState(base.above(height)).is(plan.materials().wall()));
        }), "the built perimeter must be closed except for its registered gate columns");
        helper.assertValueEqual(settlementGuards(helper, center, plan).size(), plan.guardSpawns().size(),
            "tagged guard complement");
    }

    private static java.util.List<IronGolem> settlementGuards(
        final GameTestHelper helper,
        final BlockPos center,
        final LayoutPlan plan
    ) {
        return helper.getLevel().getEntitiesOfClass(
            IronGolem.class,
            new AABB(center).inflate(plan.radius() + 8.0D, 16.0D, plan.radius() + 8.0D),
            guard -> guard.entityTags().contains(SettlementFortificationRuntime.GUARD_TAG)
                && guard.entityTags().contains(SettlementFortificationRuntime.GUARD_CENTER_PREFIX + center.asLong())
        );
    }

    private static Set<BlockPos> snapshotOccupied(
        final GameTestHelper helper,
        final BlockPos center
    ) {
        return BlockPos.betweenClosedStream(
                center.offset(-1, 0, -1),
                center.offset(1, 2, 1)
            )
            .filter(position -> !helper.getLevel().getBlockState(position).isAir())
            .map(BlockPos::immutable)
            .collect(Collectors.toUnmodifiableSet());
    }
}
