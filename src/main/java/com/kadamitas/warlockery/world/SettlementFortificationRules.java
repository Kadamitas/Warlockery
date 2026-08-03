package com.kadamitas.warlockery.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;

public final class SettlementFortificationRules {
    public static final int MIN_HUMAN_RADIUS = 16;
    public static final int MAX_HUMAN_RADIUS = 44;
    public static final int HUMAN_BUILDING_MARGIN = 9;
    public static final int MAX_TERRAIN_RELIEF = 6;
    public static final int MAX_DECK_RISE = 8;
    public static final int GATE_CORRIDOR_DEPTH = 4;
    public static final int HOBGOBLIN_RADIUS = 10;
    private static final int HUMAN_RADIUS = 24;
    private static final int WALL_HEIGHT = 3;

    private SettlementFortificationRules() {
    }

    public static LayoutPlan plan(final SettlementKind kind) {
        final int radius = kind == SettlementKind.HUMAN ? HUMAN_RADIUS : HOBGOBLIN_RADIUS;
        return plan(kind, radius);
    }

    public static LayoutPlan plan(final SettlementKind kind, final int radius) {
        final int boundedRadius = kind == SettlementKind.HUMAN
            ? Math.clamp(radius, MIN_HUMAN_RADIUS, MAX_HUMAN_RADIUS)
            : radius;
        return new LayoutPlan(
            kind,
            boundedRadius,
            WALL_HEIGHT,
            materials(kind),
            perimeter(boundedRadius),
            gates(boundedRadius),
            patrolDeck(boundedRadius),
            stairs(boundedRadius, WALL_HEIGHT),
            patrolWaypoints(boundedRadius),
            guardSpawns(boundedRadius, kind == SettlementKind.HUMAN ? 4 : 2)
        );
    }

    public static int humanRadiusForPoiOffsets(final Collection<Offset> poiOffsets) {
        final int extent = poiOffsets.stream()
            .mapToInt(offset -> Math.max(Math.abs(offset.x()), Math.abs(offset.z())))
            .max()
            .orElse(MIN_HUMAN_RADIUS - HUMAN_BUILDING_MARGIN);
        return Math.clamp(extent + HUMAN_BUILDING_MARGIN, MIN_HUMAN_RADIUS, MAX_HUMAN_RADIUS);
    }

    public static OptionalInt boundedPatrolDeckY(
        final LayoutPlan plan,
        final ToIntFunction<Offset> terrainY,
        final int centerSurfaceY
    ) {
        final var statistics = Stream.concat(plan.perimeter().stream(), plan.patrolDeck().stream())
            .mapToInt(terrainY)
            .summaryStatistics();
        if (statistics.getCount() == 0L
            || statistics.getMax() - statistics.getMin() > MAX_TERRAIN_RELIEF) {
            return OptionalInt.empty();
        }
        final int deckY = statistics.getMax() + plan.wallHeight() - 1;
        return deckY - centerSurfaceY <= MAX_DECK_RISE
            ? OptionalInt.of(deckY)
            : OptionalInt.empty();
    }

    public static List<Offset> gateApproachOffsets(final LayoutPlan plan) {
        return plan.gates().stream()
            .flatMap(gate -> java.util.stream.IntStream.rangeClosed(1, GATE_CORRIDOR_DEPTH)
                .mapToObj(depth -> new Offset(
                    gate.offset().x() - gate.facing().getStepX() * depth,
                    gate.offset().z() - gate.facing().getStepZ() * depth
                )))
            .distinct()
            .toList();
    }

    public static Set<Offset> mutationFootprint(final LayoutPlan plan) {
        final Set<Offset> result = new LinkedHashSet<>();
        result.addAll(plan.perimeter());
        result.addAll(plan.patrolDeck());
        plan.stairs().stream().map(StairPlacement::offset).forEach(result::add);
        result.addAll(gateApproachOffsets(plan));
        return Set.copyOf(result);
    }

    static LayoutPlan compactPlan(final SettlementKind kind) {
        final int radius = 1;
        return new LayoutPlan(
            kind,
            radius,
            2,
            materials(kind),
            perimeter(radius),
            List.of(
                new GatePlacement(new Offset(0, -radius), Direction.NORTH),
                new GatePlacement(new Offset(0, radius), Direction.SOUTH),
                new GatePlacement(new Offset(-radius, 0), Direction.WEST),
                new GatePlacement(new Offset(radius, 0), Direction.EAST)
            ),
            List.of(new Offset(0, 0)),
            List.of(),
            List.of(new Offset(0, 0)),
            List.of(new Offset(0, 0))
        );
    }

    public static int patrolDeckY(final LayoutPlan plan, final ToIntFunction<Offset> terrainY) {
        return Stream.concat(plan.perimeter().stream(), plan.patrolDeck().stream())
            .mapToInt(terrainY)
            .max()
            .orElseThrow() + plan.wallHeight() - 1;
    }

    public static VerticalSpan wallSpan(final int localSurfaceY, final int patrolDeckY) {
        return new VerticalSpan(localSurfaceY, Math.max(localSurfaceY, patrolDeckY));
    }

    public static int stairY(final LayoutPlan plan, final StairPlacement stair, final int patrolDeckY) {
        return patrolDeckY - (plan.wallHeight() - stair.rise() - 1);
    }

    private static MaterialProfile materials(final SettlementKind kind) {
        return kind == SettlementKind.HUMAN
            ? new MaterialProfile(
                Blocks.COBBLESTONE,
                Blocks.STONE_BRICKS,
                Blocks.STONE_BRICKS,
                (StairBlock) Blocks.STONE_BRICK_STAIRS,
                (FenceGateBlock) Blocks.DARK_OAK_FENCE_GATE
            )
            : new MaterialProfile(
                Blocks.OAK_LOG,
                Blocks.OAK_PLANKS,
                Blocks.OAK_PLANKS,
                (StairBlock) Blocks.OAK_STAIRS,
                (FenceGateBlock) Blocks.OAK_FENCE_GATE
            );
    }

    public static boolean isClosedPerimeter(final LayoutPlan plan) {
        final Set<Offset> perimeter = Set.copyOf(plan.perimeter());
        return plan.gates().size() >= 4
            && plan.gates().stream().map(GatePlacement::offset).allMatch(perimeter::contains)
            && expectedPerimeterSize(plan.radius()) == perimeter.size();
    }

    public static int expectedPerimeterSize(final int radius) {
        return radius * 8;
    }

    private static List<Offset> perimeter(final int radius) {
        final Set<Offset> result = new LinkedHashSet<>();
        for (int axis = -radius; axis <= radius; axis++) {
            result.add(new Offset(axis, -radius));
            result.add(new Offset(axis, radius));
            result.add(new Offset(-radius, axis));
            result.add(new Offset(radius, axis));
        }
        return List.copyOf(result);
    }

    private static List<GatePlacement> gates(final int radius) {
        return List.of(
            new GatePlacement(new Offset(-1, -radius), Direction.NORTH),
            new GatePlacement(new Offset(0, -radius), Direction.NORTH),
            new GatePlacement(new Offset(0, radius), Direction.SOUTH),
            new GatePlacement(new Offset(1, radius), Direction.SOUTH),
            new GatePlacement(new Offset(-radius, 0), Direction.WEST),
            new GatePlacement(new Offset(-radius, 1), Direction.WEST),
            new GatePlacement(new Offset(radius, -1), Direction.EAST),
            new GatePlacement(new Offset(radius, 0), Direction.EAST)
        );
    }

    private static List<Offset> patrolDeck(final int radius) {
        final Set<Offset> result = new LinkedHashSet<>(perimeter(radius - 1));
        result.addAll(perimeter(radius - 2));
        return List.copyOf(result);
    }

    private static List<StairPlacement> stairs(final int radius, final int wallHeight) {
        final List<StairPlacement> result = new ArrayList<>();
        final int tangent = Math.max(4, radius / 2);
        for (int width = -1; width <= 1; width++) {
            for (int depth = 1; depth <= wallHeight; depth++) {
                final int rise = wallHeight - depth;
                result.add(new StairPlacement(
                    new Offset(tangent + width, -radius + depth), Direction.NORTH, rise
                ));
                result.add(new StairPlacement(
                    new Offset(-tangent + width, radius - depth), Direction.SOUTH, rise
                ));
                result.add(new StairPlacement(
                    new Offset(-radius + depth, -tangent + width), Direction.WEST, rise
                ));
                result.add(new StairPlacement(
                    new Offset(radius - depth, tangent + width), Direction.EAST, rise
                ));
            }
        }
        return List.copyOf(result);
    }

    private static List<Offset> patrolWaypoints(final int radius) {
        final int deck = radius - 2;
        return List.of(
            new Offset(0, -deck),
            new Offset(deck, -deck),
            new Offset(deck, 0),
            new Offset(deck, deck),
            new Offset(0, deck),
            new Offset(-deck, deck),
            new Offset(-deck, 0),
            new Offset(-deck, -deck)
        );
    }

    private static List<Offset> guardSpawns(final int radius, final int count) {
        final int deck = radius - 2;
        return List.of(
            new Offset(0, -deck),
            new Offset(deck, 0),
            new Offset(0, deck),
            new Offset(-deck, 0)
        ).subList(0, count);
    }

    public enum SettlementKind {
        HUMAN,
        HOBGOBLIN
    }

    public record Offset(int x, int z) {
    }

    public record GatePlacement(Offset offset, Direction facing) {
    }

    public record StairPlacement(Offset offset, Direction facing, int rise) {
        public StairPlacement {
            if (rise < 0) {
                throw new IllegalArgumentException("Stair rise cannot be negative");
            }
        }
    }

    public record VerticalSpan(int bottomY, int topY) {
        public VerticalSpan {
            if (topY < bottomY) {
                throw new IllegalArgumentException("A vertical span cannot end below its beginning");
            }
        }

        public boolean contains(final int y) {
            return y >= bottomY && y <= topY;
        }
    }

    public record MaterialProfile(
        Block foundation,
        Block wall,
        Block patrolDeck,
        StairBlock stairs,
        FenceGateBlock gate
    ) {
    }

    public record LayoutPlan(
        SettlementKind kind,
        int radius,
        int wallHeight,
        MaterialProfile materials,
        List<Offset> perimeter,
        List<GatePlacement> gates,
        List<Offset> patrolDeck,
        List<StairPlacement> stairs,
        List<Offset> patrolWaypoints,
        List<Offset> guardSpawns
    ) {
        public LayoutPlan {
            if (radius < 1 || wallHeight < 2) {
                throw new IllegalArgumentException("A fortification requires a practical radius and wall height");
            }
            perimeter = List.copyOf(perimeter);
            gates = List.copyOf(gates);
            patrolDeck = List.copyOf(patrolDeck);
            stairs = List.copyOf(stairs);
            patrolWaypoints = List.copyOf(patrolWaypoints);
            guardSpawns = List.copyOf(guardSpawns);
        }
    }
}
