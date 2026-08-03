package com.kadamitas.warlockery.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.world.SettlementFortificationRules.LayoutPlan;
import com.kadamitas.warlockery.world.SettlementFortificationRules.Offset;
import com.kadamitas.warlockery.world.SettlementFortificationRules.SettlementKind;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SettlementFortificationRulesTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void bothSettlementPlansAreClosedBoxesWithFourTwoWideEntrances() {
        for (final SettlementKind kind : SettlementKind.values()) {
            final LayoutPlan plan = SettlementFortificationRules.plan(kind);
            assertTrue(SettlementFortificationRules.isClosedPerimeter(plan));
            assertEquals(SettlementFortificationRules.expectedPerimeterSize(plan.radius()), plan.perimeter().size());
            assertEquals(plan.perimeter().size(), Set.copyOf(plan.perimeter()).size());
            assertTrue(plan.perimeter().stream().allMatch(offset ->
                Math.abs(offset.x()) == plan.radius() || Math.abs(offset.z()) == plan.radius()
            ));
            assertEquals(8, plan.gates().size());
            assertEquals(
                EnumSet.allOf(Direction.class).stream().filter(Direction.Plane.HORIZONTAL).collect(Collectors.toSet()),
                plan.gates().stream().map(SettlementFortificationRules.GatePlacement::facing).collect(Collectors.toSet())
            );
            assertTrue(plan.gates().stream().allMatch(gate -> plan.perimeter().contains(gate.offset())));
        }
    }

    @Test
    void humanFortificationsUseStoneAndHobgoblinFortificationsUseWood() {
        final var human = SettlementFortificationRules.plan(SettlementKind.HUMAN);
        assertEquals(Blocks.COBBLESTONE, human.materials().foundation());
        assertEquals(Blocks.STONE_BRICKS, human.materials().wall());
        assertEquals(Blocks.STONE_BRICKS, human.materials().patrolDeck());
        assertEquals(Blocks.STONE_BRICK_STAIRS, human.materials().stairs());

        final var hobgoblin = SettlementFortificationRules.plan(SettlementKind.HOBGOBLIN);
        assertEquals(Blocks.OAK_LOG, hobgoblin.materials().foundation());
        assertEquals(Blocks.OAK_PLANKS, hobgoblin.materials().wall());
        assertEquals(Blocks.OAK_PLANKS, hobgoblin.materials().patrolDeck());
        assertEquals(Blocks.OAK_STAIRS, hobgoblin.materials().stairs());
        assertTrue(human.radius() > hobgoblin.radius());
    }

    @Test
    void patrolDeckIsTwoBlocksWideAndKeepsGolemsClearOfTheWall() {
        for (final SettlementKind kind : SettlementKind.values()) {
            final LayoutPlan plan = SettlementFortificationRules.plan(kind);
            final int outerDeckRadius = plan.radius() - 1;
            final int patrolRadius = plan.radius() - 2;
            assertEquals(
                SettlementFortificationRules.expectedPerimeterSize(outerDeckRadius)
                    + SettlementFortificationRules.expectedPerimeterSize(patrolRadius),
                plan.patrolDeck().size()
            );
            assertEquals(plan.patrolDeck().size(), Set.copyOf(plan.patrolDeck()).size());
            assertTrue(plan.patrolDeck().stream().allMatch(offset ->
                Math.max(Math.abs(offset.x()), Math.abs(offset.z())) == outerDeckRadius
                    || Math.max(Math.abs(offset.x()), Math.abs(offset.z())) == patrolRadius
            ));
            assertEquals(8, plan.patrolWaypoints().size());
            assertTrue(plan.patrolWaypoints().stream().allMatch(plan.patrolDeck()::contains));
            assertTrue(plan.patrolWaypoints().stream().allMatch(offset ->
                Math.max(Math.abs(offset.x()), Math.abs(offset.z())) == patrolRadius
            ));
        }
    }

    @Test
    void stairsProvideThreeStepAccessFromEveryWallSide() {
        for (final SettlementKind kind : SettlementKind.values()) {
            final LayoutPlan plan = SettlementFortificationRules.plan(kind);
            assertEquals(36, plan.stairs().size());
            assertEquals(
                EnumSet.allOf(Direction.class).stream().filter(Direction.Plane.HORIZONTAL).collect(Collectors.toSet()),
                plan.stairs().stream().map(SettlementFortificationRules.StairPlacement::facing).collect(Collectors.toSet())
            );
            for (final Direction direction : Direction.Plane.HORIZONTAL) {
                final Set<Integer> rises = plan.stairs().stream()
                    .filter(stair -> stair.facing() == direction)
                    .map(SettlementFortificationRules.StairPlacement::rise)
                    .collect(Collectors.toSet());
                assertEquals(Set.of(0, 1, 2), rises);
            }
        }
    }

    @Test
    void everySettlementHasGuardsAlreadyPositionedOnThePatrolRoute() {
        final LayoutPlan human = SettlementFortificationRules.plan(SettlementKind.HUMAN);
        final LayoutPlan hobgoblin = SettlementFortificationRules.plan(SettlementKind.HOBGOBLIN);
        assertEquals(4, human.guardSpawns().size());
        assertEquals(2, hobgoblin.guardSpawns().size());
        assertTrue(human.guardSpawns().stream().allMatch(human.patrolWaypoints()::contains));
        assertTrue(hobgoblin.guardSpawns().stream().allMatch(hobgoblin.patrolWaypoints()::contains));
        assertTrue(human.guardSpawns().stream().allMatch(offset ->
            Math.max(Math.abs(offset.x()), Math.abs(offset.z())) == human.radius() - 2
        ));
        assertTrue(hobgoblin.guardSpawns().stream().allMatch(offset ->
            Math.max(Math.abs(offset.x()), Math.abs(offset.z())) == hobgoblin.radius() - 2
        ));
    }

    @Test
    void humanWallRadiusAdaptsToVillagePoiExtentsWithAProtectedMargin() {
        assertEquals(
            SettlementFortificationRules.MIN_HUMAN_RADIUS,
            SettlementFortificationRules.humanRadiusForPoiOffsets(List.of(new Offset(2, 3)))
        );
        assertEquals(
            29,
            SettlementFortificationRules.humanRadiusForPoiOffsets(List.of(new Offset(20, -4)))
        );
        assertEquals(
            SettlementFortificationRules.MAX_HUMAN_RADIUS,
            SettlementFortificationRules.humanRadiusForPoiOffsets(List.of(new Offset(100, 0)))
        );
    }

    @Test
    void patrolDeckRejectsExcessiveReliefAndExcessiveRise() {
        final LayoutPlan plan = SettlementFortificationRules.plan(
            SettlementKind.HUMAN,
            SettlementFortificationRules.MIN_HUMAN_RADIUS
        );
        assertEquals(66, SettlementFortificationRules.boundedPatrolDeckY(plan, _ -> 64, 64).orElseThrow());
        final Offset highPoint = plan.perimeter().getFirst();
        assertTrue(SettlementFortificationRules.boundedPatrolDeckY(
            plan,
            offset -> offset.equals(highPoint) ? 71 : 64,
            64
        ).isEmpty());
        assertTrue(SettlementFortificationRules.boundedPatrolDeckY(plan, _ -> 72, 64).isEmpty());
    }

    @Test
    void everyGateHasAProtectedInteriorApproachCorridor() {
        final LayoutPlan plan = SettlementFortificationRules.plan(SettlementKind.HUMAN);
        final List<Offset> approaches = SettlementFortificationRules.gateApproachOffsets(plan);
        assertEquals(32, approaches.size());
        assertTrue(approaches.stream().allMatch(offset ->
            Math.abs(offset.x()) < plan.radius() && Math.abs(offset.z()) < plan.radius()
        ));
        assertTrue(SettlementFortificationRules.mutationFootprint(plan).containsAll(approaches));
        plan.gates().forEach(gate -> assertTrue(approaches.contains(new Offset(
            gate.offset().x() - gate.facing().getStepX(),
            gate.offset().z() - gate.facing().getStepZ()
        ))));
    }

    @Test
    void layoutCollectionsAreImmutable() {
        final LayoutPlan plan = SettlementFortificationRules.plan(SettlementKind.HUMAN);
        assertThrows(UnsupportedOperationException.class, () -> plan.perimeter().add(new Offset(0, 0)));
        assertThrows(UnsupportedOperationException.class, () -> plan.gates().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.stairs().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.guardSpawns().clear());
    }

    @Test
    void persistedLayoutsRejectRadiiThatCouldAllocateUnboundedGeometry() {
        assertThrows(IllegalArgumentException.class, () -> new SettlementFortificationData.Layout(
            0L,
            SettlementKind.HUMAN,
            SettlementFortificationRules.MAX_HUMAN_RADIUS + 1,
            64
        ));
        assertThrows(IllegalArgumentException.class, () -> new SettlementFortificationData.Layout(
            0L,
            SettlementKind.HOBGOBLIN,
            1_000_000,
            64
        ));
        assertEquals(1, new SettlementFortificationData.Layout(0L, SettlementKind.HUMAN, 1, 64).radius());
    }

    @Test
    void guardRulesClassifySilverFireAndPlayerRetaliationPrecisely() {
        assertTrue(VillageGuardRules.isSilverClassifiedAttack(true));
        assertFalse(VillageGuardRules.isSilverClassifiedAttack(false));
        assertTrue(VillageGuardRules.shouldRetaliate(true, true, false));
        assertFalse(VillageGuardRules.shouldRetaliate(false, true, false));
        assertFalse(VillageGuardRules.shouldRetaliate(true, false, false));
        assertFalse(VillageGuardRules.shouldRetaliate(true, true, true));
        assertTrue(VillageGuardRules.shouldFireSilverBolt(
            true, true, VillageGuardRules.MAX_RANGED_DISTANCE_SQUARED, VillageGuardRules.RANGED_COOLDOWN_TICKS
        ));
        assertFalse(VillageGuardRules.shouldFireSilverBolt(
            false, true, 1.0D, VillageGuardRules.RANGED_COOLDOWN_TICKS
        ));
        assertFalse(VillageGuardRules.shouldFireSilverBolt(
            true, true, VillageGuardRules.MAX_RANGED_DISTANCE_SQUARED + 1.0D,
            VillageGuardRules.RANGED_COOLDOWN_TICKS
        ));
        assertFalse(VillageGuardRules.shouldFireSilverBolt(
            true, true, 1.0D, VillageGuardRules.RANGED_COOLDOWN_TICKS - 1
        ));
    }
}
