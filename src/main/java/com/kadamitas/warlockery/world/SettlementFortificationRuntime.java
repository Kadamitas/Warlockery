package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.config.WarlockeryConfig;
import com.kadamitas.warlockery.world.SettlementFortificationRules.GatePlacement;
import com.kadamitas.warlockery.world.SettlementFortificationRules.LayoutPlan;
import com.kadamitas.warlockery.world.SettlementFortificationRules.Offset;
import com.kadamitas.warlockery.world.SettlementFortificationRules.SettlementKind;
import com.kadamitas.warlockery.world.SettlementFortificationRules.StairPlacement;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public final class SettlementFortificationRuntime {
    public static final String GUARD_TAG = "warlockery_settlement_guard";
    public static final String HUMAN_GUARD_TAG = "warlockery_human_guard";
    public static final String HOBGOBLIN_GUARD_TAG = "warlockery_hobgoblin_guard";
    public static final String GUARD_CENTER_PREFIX = "warlockery_guard_center_";
    public static final String GUARD_RADIUS_PREFIX = "warlockery_guard_radius_";

    private SettlementFortificationRuntime() {
    }

    public static FortificationResult fortifyHumanVillage(final ServerLevel level, final BlockPos center) {
        if (!WarlockeryConfig.settlementFortifications()) {
            final LayoutPlan fallback = SettlementFortificationRules.plan(SettlementKind.HUMAN);
            return new FortificationResult(0, 0, fallback.gates().size(), fallback.stairs().size());
        }
        final SettlementFortificationData data = SettlementFortificationData.get(level);
        final Optional<SettlementFortificationData.Layout> saved = data.layout(center);
        if (saved.isPresent()) {
            final LayoutPlan plan = planFor(saved.orElseThrow());
            return new FortificationResult(
                0,
                spawnMissingGuards(level, center, plan),
                plan.gates().size(),
                plan.stairs().size()
            );
        }
        return adaptiveHumanLayout(level, center)
            .map(layout -> build(level, center, layout.plan(), layout.terrain(), layout.deckY()))
            .orElseGet(() -> new FortificationResult(0, 0, 0, 0));
    }

    public static FortificationResult fortifyHobgoblinSettlement(final ServerLevel level, final BlockPos center) {
        return fortifyIfEnabled(level, center, SettlementFortificationRules.plan(SettlementKind.HOBGOBLIN));
    }

    private static FortificationResult fortifyIfEnabled(
        final ServerLevel level,
        final BlockPos center,
        final LayoutPlan plan
    ) {
        return WarlockeryConfig.settlementFortifications()
            ? fortify(level, center, plan)
            : new FortificationResult(0, 0, plan.gates().size(), plan.stairs().size());
    }

    static FortificationResult fortify(
        final ServerLevel level,
        final BlockPos center,
        final LayoutPlan plan
    ) {
        final SettlementFortificationData data = SettlementFortificationData.get(level);
        if (data.contains(center)) {
            final int guards = spawnMissingGuards(level, center, plan);
            return new FortificationResult(0, guards, plan.gates().size(), plan.stairs().size());
        }
        final Map<Offset, Integer> terrain = terrainSnapshot(level, center, plan);
        final OptionalInt boundedDeck = SettlementFortificationRules.boundedPatrolDeckY(
            plan,
            terrain::get,
            surfaceY(level, center.getX(), center.getZ())
        );
        if (boundedDeck.isEmpty()) {
            return new FortificationResult(0, 0, plan.gates().size(), plan.stairs().size());
        }
        return build(level, center, plan, terrain, boundedDeck.orElseThrow());
    }

    private static FortificationResult build(
        final ServerLevel level,
        final BlockPos center,
        final LayoutPlan plan,
        final Map<Offset, Integer> terrain,
        final int deckY
    ) {
        int placed = 0;
        for (Offset offset : plan.perimeter()) {
            final BlockPos base = absolute(center, offset, terrain.get(offset));
            final GatePlacement gate = plan.gates().stream()
                .filter(candidate -> candidate.offset().equals(offset))
                .findFirst()
                .orElse(null);
            placed += gate == null
                ? buildWallColumn(level, base, offset, deckY, plan)
                : buildGateColumn(level, base, gate, deckY, plan);
        }
        final Set<Offset> approaches = Set.copyOf(SettlementFortificationRules.gateApproachOffsets(plan));
        for (Offset offset : plan.patrolDeck()) {
            final BlockPos ground = absolute(center, offset, terrain.get(offset));
            if (!approaches.contains(offset)) {
                placed += fillColumn(level, ground, deckY, plan.materials().foundation());
            }
            final BlockPos deck = new BlockPos(ground.getX(), deckY, ground.getZ());
            placed += place(level, deck, plan.materials().patrolDeck().defaultBlockState());
            placed += clearHeadroom(level, deck.above(), 3);
        }
        for (StairPlacement stair : plan.stairs()) {
            final BlockPos ground = absolute(center, stair.offset(), terrain.get(stair.offset()));
            final int stepY = deckY - (plan.wallHeight() - stair.rise() - 1);
            placed += fillColumn(level, ground, stepY - 1, plan.materials().foundation());
            final BlockPos step = new BlockPos(ground.getX(), Math.max(ground.getY(), stepY), ground.getZ());
            placed += place(level, step, plan.materials().stairs().defaultBlockState()
                .setValue(StairBlock.FACING, stair.facing()));
            placed += clearHeadroom(level, step.above(), 3);
        }
        placed += extendAccessStairs(level, center, terrain, deckY, plan);
        if (plan.radius() > 1) {
            placed += buildGateApproaches(level, center, terrain, deckY, plan);
        }
        final int guards = spawnMissingGuards(level, center, plan);
        SettlementFortificationData.get(level).mark(center, plan.kind(), plan.radius(), deckY);
        return new FortificationResult(placed, guards, plan.gates().size(), plan.stairs().size());
    }

    public static int approachRadius(
        final ServerLevel level,
        final BlockPos center,
        final SettlementKind kind
    ) {
        return SettlementFortificationData.get(level).layout(center)
            .filter(layout -> layout.kind() == kind)
            .map(SettlementFortificationData.Layout::radius)
            .orElseGet(() -> SettlementFortificationRules.plan(kind).radius());
    }

    public static Optional<SettlementFortificationData.Layout> registeredLayout(
        final ServerLevel level,
        final BlockPos center
    ) {
        return SettlementFortificationData.get(level).layout(center);
    }

    public static Optional<BlockPos> findRegisteredCenter(
        final ServerLevel level,
        final BlockPos origin,
        final SettlementKind kind,
        final double radius
    ) {
        final double radiusSquared = radius * radius;
        return SettlementFortificationData.get(level).layouts().stream()
            .filter(layout -> layout.kind() == kind)
            .map(SettlementFortificationData.Layout::center)
            .filter(center -> center.distSqr(origin) <= radiusSquared)
            .min(Comparator.comparingDouble(origin::distSqr));
    }

    public static List<BlockPos> gatePositions(
        final ServerLevel level,
        final BlockPos center
    ) {
        return registeredLayout(level, center).stream()
            .flatMap(layout -> {
                final LayoutPlan plan = planFor(layout);
                return plan.gates().stream().map(gate -> findGateBlock(level, center, layout, gate)
                    .orElseGet(() -> surface(level, center.offset(gate.offset().x(), 0, gate.offset().z()))));
            })
            .toList();
    }

    public static Optional<BlockPos> nearestGate(
        final ServerLevel level,
        final BlockPos center,
        final BlockPos approachFrom
    ) {
        return gatePositions(level, center).stream()
            .min(Comparator.comparingDouble(approachFrom::distSqr));
    }

    public static void setGatesOpen(
        final ServerLevel level,
        final BlockPos center,
        final boolean open
    ) {
        SettlementFortificationData.get(level).layout(center).ifPresent(layout -> {
            final LayoutPlan plan = planFor(layout);
            plan.gates().forEach(gate -> {
                findGateBlock(level, center, layout, gate).ifPresent(position -> {
                    final var state = level.getBlockState(position);
                    if (state.getValue(FenceGateBlock.OPEN) != open) {
                        level.setBlockAndUpdate(position, state.setValue(FenceGateBlock.OPEN, open));
                    }
                });
            });
        });
    }

    private static Optional<BlockPos> findGateBlock(
        final ServerLevel level,
        final BlockPos center,
        final SettlementFortificationData.Layout layout,
        final GatePlacement gate
    ) {
        final int x = center.getX() + gate.offset().x();
        final int z = center.getZ() + gate.offset().z();
        return java.util.stream.IntStream.iterate(layout.deckY(), y -> y >= layout.deckY()
                - SettlementFortificationRules.MAX_DECK_RISE - 4, y -> y - 1)
            .mapToObj(y -> new BlockPos(x, y, z))
            .filter(position -> level.getBlockState(position).getBlock() instanceof FenceGateBlock)
            .findFirst();
    }

    private static LayoutPlan planFor(final SettlementFortificationData.Layout layout) {
        return layout.radius() == 1
            ? SettlementFortificationRules.compactPlan(layout.kind())
            : SettlementFortificationRules.plan(layout.kind(), layout.radius());
    }

    private static Optional<PreparedLayout> adaptiveHumanLayout(
        final ServerLevel level,
        final BlockPos center
    ) {
        final List<Offset> poiOffsets = level.getPoiManager()
            .getInSquare(
                holder -> holder.is(PoiTypeTags.VILLAGE),
                center,
                SettlementFortificationRules.MAX_HUMAN_RADIUS,
                PoiManager.Occupancy.ANY
            )
            .map(record -> record.getPos().subtract(center))
            .map(position -> new Offset(position.getX(), position.getZ()))
            .toList();
        final int initialRadius = SettlementFortificationRules.humanRadiusForPoiOffsets(poiOffsets);
        final int centerSurface = surfaceY(level, center.getX(), center.getZ());
        for (int radius = initialRadius; radius <= SettlementFortificationRules.MAX_HUMAN_RADIUS; radius++) {
            final LayoutPlan plan = SettlementFortificationRules.plan(SettlementKind.HUMAN, radius);
            final Map<Offset, Integer> terrain = terrainSnapshot(level, center, plan);
            final OptionalInt deckY = SettlementFortificationRules.boundedPatrolDeckY(plan, terrain::get, centerSurface);
            if (deckY.isPresent() && safePlacement(level, center, plan, terrain, deckY.orElseThrow())) {
                return Optional.of(new PreparedLayout(plan, terrain, deckY.orElseThrow()));
            }
        }
        return Optional.empty();
    }

    private static boolean safePlacement(
        final ServerLevel level,
        final BlockPos center,
        final LayoutPlan plan,
        final Map<Offset, Integer> terrain,
        final int deckY
    ) {
        return SettlementFortificationRules.mutationFootprint(plan).stream().noneMatch(offset -> {
            final int surface = terrain.computeIfAbsent(
                offset,
                key -> surfaceY(level, center.getX() + key.x(), center.getZ() + key.z())
            );
            return java.util.stream.IntStream.rangeClosed(surface - 1, Math.max(surface + 3, deckY + 3))
                .mapToObj(y -> absolute(center, offset, y))
                .anyMatch(position -> isProtectedBlock(level, position));
        });
    }

    public static boolean isProtectedBlock(final ServerLevel level, final BlockPos position) {
        final var state = level.getBlockState(position);
        return level.getBlockEntity(position) != null
            || state.getBlock() instanceof BedBlock
            || state.getBlock() instanceof DoorBlock
            || level.getPoiManager().exists(position, holder -> true);
    }

    private static Map<Offset, Integer> terrainSnapshot(
        final ServerLevel level,
        final BlockPos center,
        final LayoutPlan plan
    ) {
        final Map<Offset, Integer> result = new HashMap<>();
        Stream.of(
                plan.perimeter().stream(),
                plan.patrolDeck().stream(),
                plan.stairs().stream().map(StairPlacement::offset),
                plan.guardSpawns().stream()
            )
            .flatMap(stream -> stream)
            .distinct()
            .forEach(offset -> result.put(offset, surfaceY(level, center.getX() + offset.x(), center.getZ() + offset.z())));
        return result;
    }

    private static int buildWallColumn(
        final ServerLevel level,
        final BlockPos base,
        final Offset offset,
        final int deckY,
        final LayoutPlan plan
    ) {
        int placed = place(level, base.below(), plan.materials().foundation().defaultBlockState());
        placed += fillColumn(level, base, deckY, plan.materials().wall());
        if (plan.radius() > 1 && (Math.abs(offset.x()) + Math.abs(offset.z())) % 2 == 0) {
            placed += place(level, new BlockPos(base.getX(), deckY + 1, base.getZ()),
                plan.materials().wall().defaultBlockState());
        }
        return placed;
    }

    private static int buildGateColumn(
        final ServerLevel level,
        final BlockPos base,
        final GatePlacement gate,
        final int deckY,
        final LayoutPlan plan
    ) {
        int placed = place(level, base.below(), plan.materials().foundation().defaultBlockState());
        placed += place(level, base, plan.materials().gate().defaultBlockState()
            .setValue(FenceGateBlock.FACING, gate.facing()));
        placed += place(level, base.above(), Blocks.AIR.defaultBlockState());
        placed += fillColumn(level, base.above(2), deckY, plan.materials().wall());
        return placed;
    }

    private static int extendAccessStairs(
        final ServerLevel level,
        final BlockPos center,
        final Map<Offset, Integer> terrain,
        final int deckY,
        final LayoutPlan plan
    ) {
        int placed = 0;
        for (StairPlacement bottom : plan.stairs().stream().filter(stair -> stair.rise() == 0).toList()) {
            Offset offset = bottom.offset();
            for (int depth = plan.wallHeight() + 1; depth < plan.radius() - 2; depth++) {
                offset = new Offset(
                    offset.x() - bottom.facing().getStepX(),
                    offset.z() - bottom.facing().getStepZ()
                );
                final int groundY = terrain.computeIfAbsent(
                    offset,
                    key -> surfaceY(level, center.getX() + key.x(), center.getZ() + key.z())
                );
                final int desiredY = deckY - depth + 1;
                if (desiredY < groundY) {
                    break;
                }
                final BlockPos ground = absolute(center, offset, groundY);
                placed += fillColumn(level, ground, desiredY - 1, plan.materials().foundation());
                final BlockPos step = new BlockPos(ground.getX(), desiredY, ground.getZ());
                placed += place(level, step, plan.materials().stairs().defaultBlockState()
                    .setValue(StairBlock.FACING, bottom.facing()));
                placed += clearHeadroom(level, step.above(), 3);
                if (desiredY == groundY) {
                    break;
                }
            }
        }
        return placed;
    }

    private static int buildGateApproaches(
        final ServerLevel level,
        final BlockPos center,
        final Map<Offset, Integer> terrain,
        final int deckY,
        final LayoutPlan plan
    ) {
        int placed = 0;
        for (Offset offset : SettlementFortificationRules.gateApproachOffsets(plan)) {
            final int surfaceY = terrain.computeIfAbsent(
                offset,
                key -> surfaceY(level, center.getX() + key.x(), center.getZ() + key.z())
            );
            final BlockPos firstAir = absolute(center, offset, surfaceY);
            placed += place(level, firstAir.below(), plan.materials().foundation().defaultBlockState());
            placed += clearHeadroom(level, firstAir, Math.max(2, deckY - surfaceY));
        }
        return placed;
    }

    private static int fillColumn(
        final ServerLevel level,
        final BlockPos start,
        final int inclusiveTopY,
        final net.minecraft.world.level.block.Block material
    ) {
        if (start.getY() > inclusiveTopY) {
            return 0;
        }
        int placed = 0;
        for (int y = start.getY(); y <= inclusiveTopY; y++) {
            final BlockPos position = new BlockPos(start.getX(), y, start.getZ());
            if (level.getBlockEntity(position) == null) {
                placed += place(level, position, material.defaultBlockState());
            }
        }
        return placed;
    }

    private static int clearHeadroom(final ServerLevel level, final BlockPos first, final int height) {
        int changed = 0;
        for (int offset = 0; offset < height; offset++) {
            final BlockPos position = first.above(offset);
            if (level.getBlockEntity(position) == null) {
                changed += place(level, position, Blocks.AIR.defaultBlockState());
            }
        }
        return changed;
    }

    private static int spawnMissingGuards(
        final ServerLevel level,
        final BlockPos center,
        final LayoutPlan plan
    ) {
        final String centerTag = GUARD_CENTER_PREFIX + center.asLong();
        final int present = level.getEntitiesOfClass(
            IronGolem.class,
            new AABB(center).inflate(plan.radius() + 8.0, 24.0, plan.radius() + 8.0),
            guard -> guard.entityTags().contains(GUARD_TAG) && guard.entityTags().contains(centerTag)
        ).size();
        int spawned = 0;
        for (int index = present; index < plan.guardSpawns().size(); index++) {
            final Offset offset = plan.guardSpawns().get(index);
            final BlockPos position = surface(level, center.offset(offset.x(), 0, offset.z()));
            final IronGolem guard = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.STRUCTURE);
            if (guard == null) {
                continue;
            }
            guard.snapTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0.0F, 0.0F);
            guard.setPlayerCreated(false);
            guard.setPersistenceRequired();
            guard.addTag(GUARD_TAG);
            guard.addTag(plan.kind() == SettlementKind.HUMAN ? HUMAN_GUARD_TAG : HOBGOBLIN_GUARD_TAG);
            guard.addTag(centerTag);
            guard.addTag(GUARD_RADIUS_PREFIX + plan.radius());
            if (level.addFreshEntity(guard)) {
                spawned++;
            }
        }
        return spawned;
    }

    private static int place(
        final ServerLevel level,
        final BlockPos position,
        final net.minecraft.world.level.block.state.BlockState state
    ) {
        if (level.getBlockState(position).equals(state)) {
            return 0;
        }
        if (isProtectedBlock(level, position)) {
            return 0;
        }
        level.setBlockAndUpdate(position, state);
        return 1;
    }

    private static BlockPos absolute(final BlockPos center, final Offset offset, final int y) {
        return new BlockPos(center.getX() + offset.x(), y, center.getZ() + offset.z());
    }

    private static BlockPos surface(final ServerLevel level, final BlockPos position) {
        return new BlockPos(position.getX(), surfaceY(level, position.getX(), position.getZ()), position.getZ());
    }

    private static int surfaceY(final ServerLevel level, final int x, final int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private record PreparedLayout(LayoutPlan plan, Map<Offset, Integer> terrain, int deckY) {
    }

    public record FortificationResult(int blocksPlaced, int guardsSpawned, int gateColumns, int stairBlocks) {
    }
}
