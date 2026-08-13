package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.HazardEscapeRules.Hazard;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class HazardEscapeRuntime {
    private static final int SEARCH_RADIUS = 10;
    private static final TagKey<net.minecraft.world.level.block.Block> CONTACT_HAZARDS = TagKey.create(
        Registries.BLOCK,
        Identifier.fromNamespaceAndPath("warlockery", "ai/contact_hazards")
    );
    private static final List<BlockPos> ESCAPE_OFFSETS = IntStream.rangeClosed(2, SEARCH_RADIUS)
        .boxed()
        .flatMap(radius -> Stream.of(
            new BlockPos(radius, 0, 0),
            new BlockPos(-radius, 0, 0),
            new BlockPos(0, 0, radius),
            new BlockPos(0, 0, -radius),
            new BlockPos(radius, 0, radius),
            new BlockPos(radius, 0, -radius),
            new BlockPos(-radius, 0, radius),
            new BlockPos(-radius, 0, -radius)
        ))
        .toList();

    private HazardEscapeRuntime() {
    }

    public static boolean tick(
        final Mob creature,
        final ServerLevel level,
        final CreatureKind kind
    ) {
        return detectedHazards(creature, level)
            .filter(hazard -> HazardEscapeRules.shouldEscape(kind, hazard))
            .findFirst()
            .map(hazard -> escape(creature, level, hazard))
            .orElse(false);
    }

    public static boolean tick(final Mob creature, final ServerLevel level) {
        return detectedHazards(creature, level)
            .findFirst()
            .map(hazard -> escape(creature, level, hazard))
            .orElse(false);
    }

    static Optional<Hazard> currentHazard(final Mob creature, final ServerLevel level) {
        return detectedHazards(creature, level).findFirst();
    }

    private static Stream<Hazard> detectedHazards(final Mob creature, final ServerLevel level) {
        final Stream.Builder<Hazard> hazards = Stream.builder();
        if (creature.isInLava()) {
            hazards.add(Hazard.LAVA);
        }
        if (creature.isOnFire() || hazardousContact(level, creature.blockPosition(), Hazard.FIRE)) {
            hazards.add(Hazard.FIRE);
        }
        if (creature.isUnderWater() && creature.getAirSupply() < creature.getMaxAirSupply()) {
            hazards.add(Hazard.DROWNING);
        }
        if (hazardousContact(level, creature.blockPosition(), Hazard.CONTACT)) {
            hazards.add(Hazard.CONTACT);
        }
        return hazards.build();
    }

    static Optional<BlockPos> findSafeDestination(
        final Mob creature,
        final ServerLevel level,
        final Hazard hazard
    ) {
        final BlockPos origin = creature.blockPosition();
        return ESCAPE_OFFSETS.stream()
            .map(origin::offset)
            .map(position -> TacticalCombatRuntime.standableNear(level, position))
            .flatMap(Optional::stream)
            .distinct()
            .filter(position -> isSafe(level, position, hazard))
            .filter(position -> TacticalCombatRuntime.routeReaches(creature, position))
            .min(Comparator.comparingDouble(position -> position.distSqr(origin)));
    }

    static boolean isSafe(final ServerLevel level, final BlockPos position, final Hazard hazard) {
        if (!level.getFluidState(position).isEmpty() || !level.getFluidState(position.above()).isEmpty()) {
            return false;
        }
        final int proximity = hazard == Hazard.LAVA || hazard == Hazard.FIRE ? 2 : 1;
        return BlockPos.betweenClosedStream(
                position.offset(-proximity, -1, -proximity),
                position.offset(proximity, 2, proximity)
            )
            .noneMatch(candidate -> isUnsafeBlock(level.getBlockState(candidate))
                || level.getFluidState(candidate).is(FluidTags.LAVA));
    }

    private static boolean escape(final Mob creature, final ServerLevel level, final Hazard hazard) {
        if (creature.tickCount % HazardEscapeRules.reconsiderationTicks(hazard) == 0) {
            findSafeDestination(creature, level, hazard).ifPresentOrElse(
                destination -> creature.getNavigation().moveTo(
                    destination.getX() + 0.5,
                    destination.getY(),
                    destination.getZ() + 0.5,
                    HazardEscapeRules.movementSpeed(hazard)
                ),
                () -> moveAwayFromNearestHazard(creature, level, hazard)
            );
        }
        if (hazard == Hazard.LAVA || hazard == Hazard.DROWNING) {
            creature.getJumpControl().jump();
        }
        return true;
    }

    private static void moveAwayFromNearestHazard(
        final Mob creature,
        final ServerLevel level,
        final Hazard hazard
    ) {
        final Vec3 danger = nearestHazard(level, creature.blockPosition(), hazard)
            .map(Vec3::atCenterOf)
            .orElse(creature.position().subtract(0.0, 1.0, 0.0));
        Vec3 away = creature.position().subtract(danger).multiply(1.0, 0.0, 1.0);
        if (away.lengthSqr() < 1.0E-4) {
            away = new Vec3((creature.getId() & 1) == 0 ? 1.0 : -1.0, 0.0, 1.0);
        }
        final Vec3 destination = creature.position().add(away.normalize().scale(SEARCH_RADIUS));
        creature.getNavigation().moveTo(
            destination.x,
            Math.max(destination.y, creature.getY() + (hazard == Hazard.DROWNING ? 2.0 : 0.0)),
            destination.z,
            HazardEscapeRules.movementSpeed(hazard)
        );
    }

    private static Optional<BlockPos> nearestHazard(
        final ServerLevel level,
        final BlockPos origin,
        final Hazard hazard
    ) {
        return BlockPos.betweenClosedStream(origin.offset(-2, -2, -2), origin.offset(2, 2, 2))
            .filter(position -> hazardousBlock(level, position, hazard))
            .min(Comparator.comparingDouble(origin::distSqr));
    }

    private static boolean hazardousContact(
        final ServerLevel level,
        final BlockPos origin,
        final Hazard hazard
    ) {
        return BlockPos.betweenClosedStream(origin.offset(-1, -1, -1), origin.offset(1, 2, 1))
            .anyMatch(position -> hazardousBlock(level, position, hazard));
    }

    private static boolean hazardousBlock(
        final ServerLevel level,
        final BlockPos position,
        final Hazard hazard
    ) {
        final BlockState state = level.getBlockState(position);
        return switch (hazard) {
            case FIRE -> isFireBlock(state) || level.getFluidState(position).is(FluidTags.LAVA);
            case LAVA -> level.getFluidState(position).is(FluidTags.LAVA);
            case DROWNING -> level.getFluidState(position).is(FluidTags.WATER);
            case CONTACT -> isContactHazard(state);
        };
    }

    private static boolean isUnsafeBlock(final BlockState state) {
        return isFireBlock(state) || isContactHazard(state);
    }

    private static boolean isFireBlock(final BlockState state) {
        return state.is(Blocks.FIRE)
            || state.is(Blocks.SOUL_FIRE)
            || state.is(Blocks.CAMPFIRE)
            || state.is(Blocks.SOUL_CAMPFIRE)
            || state.is(Blocks.MAGMA_BLOCK);
    }

    private static boolean isContactHazard(final BlockState state) {
        return state.is(CONTACT_HAZARDS);
    }
}

