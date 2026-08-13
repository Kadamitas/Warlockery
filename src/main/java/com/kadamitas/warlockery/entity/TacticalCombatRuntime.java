package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.TacticalCombatRules.Maneuver;
import com.kadamitas.warlockery.entity.TacticalCombatRules.Profile;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class TacticalCombatRuntime {
    private static final long DIRECTED_THREAT_MEMORY_TICKS = 120L;
    private static final Map<Mob, Long> DIRECTED_THREATS = new WeakHashMap<>();
    private static final TagKey<Item> RANGED_WEAPONS = TagKey.create(
        Registries.ITEM,
        Identifier.fromNamespaceAndPath("warlockery", "ai/ranged_weapons")
    );
    private static final List<Integer> VERTICAL_OFFSETS = List.of(0, 1, -1, 2, -2);
    private static final List<BlockPos> COVER_OFFSETS = IntStream.rangeClosed(2, 8)
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

    private TacticalCombatRuntime() {
    }

    public static void tick(final Mob creature, final ServerLevel level, final CreatureKind kind) {
        if (HazardEscapeRuntime.tick(creature, level, kind)) {
            return;
        }
        final LivingEntity target = creature.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        final Profile profile = TacticalCombatRules.profile(kind);
        if (!TacticalCombatRules.shouldReconsider(creature.tickCount, creature.getId(), profile.cadenceTicks())) {
            return;
        }
        final double distance = creature.distanceTo(target);
        final boolean visible = target.hasLineOfSight(creature);
        final boolean reachable = routeReaches(creature, target);
        final Maneuver maneuver = TacticalCombatRules.choose(
            profile,
            hasCombatThreat(creature, target, level.getGameTime()),
            visible,
            reachable,
            distance,
            creature.getHealth(),
            creature.getMaxHealth()
        );
        execute(creature, level, target, profile, maneuver);
    }

    public static void rememberIncomingThreat(
        final Mob creature,
        final ServerLevel level,
        final DamageSource source
    ) {
        if (isDirectedCombatDamage(source)) {
            DIRECTED_THREATS.put(creature, level.getGameTime() + DIRECTED_THREAT_MEMORY_TICKS);
        }
    }

    static boolean hasCombatThreat(final Mob creature, final LivingEntity target, final long gameTime) {
        return isRangedThreat(target) || hasRecentDirectedThreat(creature, gameTime);
    }

    static boolean hasRecentDirectedThreat(final Mob creature, final long gameTime) {
        final Long expiresAt = DIRECTED_THREATS.get(creature);
        if (expiresAt == null) {
            return false;
        }
        if (gameTime < expiresAt) {
            return true;
        }
        DIRECTED_THREATS.remove(creature);
        return false;
    }

    static boolean isDirectedCombatDamage(final DamageSource source) {
        return playerAttribution(source.getEntity()) || playerAttribution(source.getDirectEntity());
    }

    static void execute(
        final Mob creature,
        final ServerLevel level,
        final LivingEntity target,
        final Profile profile,
        final Maneuver maneuver
    ) {
        switch (maneuver) {
            case COVER -> findCover(creature, level, target, profile.coverSearchRadius())
                .ifPresentOrElse(
                    position -> moveTo(creature, Vec3.atBottomCenterOf(position), profile.movementSpeed()),
                    () -> disengage(creature, target, profile.movementSpeed())
                );
            case DISENGAGE -> disengage(creature, target, profile.movementSpeed());
            case FLANK -> flank(creature, level, target, profile);
            case PRESS -> creature.getNavigation().moveTo(target, profile.movementSpeed());
            case HOLD -> creature.getNavigation().stop();
        }
    }

    static Optional<BlockPos> findCover(
        final Mob creature,
        final ServerLevel level,
        final LivingEntity target,
        final int searchRadius
    ) {
        final BlockPos origin = creature.blockPosition();
        return COVER_OFFSETS.stream()
            .filter(offset -> Math.max(Math.abs(offset.getX()), Math.abs(offset.getZ())) <= searchRadius)
            .map(origin::offset)
            .map(position -> standableNear(level, position))
            .flatMap(Optional::stream)
            .distinct()
            .filter(position -> concealedFrom(level, creature, target, position))
            .filter(position -> routeReaches(creature, position))
            .min(Comparator.comparingDouble(position -> position.distSqr(origin)));
    }

    private static void flank(
        final Mob creature,
        final ServerLevel level,
        final LivingEntity target,
        final Profile profile
    ) {
        Vec3 outward = creature.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
        if (outward.lengthSqr() < 1.0E-4) {
            outward = new Vec3(1.0, 0.0, 0.0);
        }
        outward = outward.normalize();
        final int side = TacticalCombatRules.flankSide(creature.getId());
        final Vec3 lateral = new Vec3(-outward.z, 0.0, outward.x).scale(side * Math.max(3.0, profile.preferredDistance()));
        final Vec3 intended = target.position()
            .add(outward.scale(profile.preferredDistance()))
            .add(lateral)
            .add(0.0, profile.doctrine() == TacticalCombatRules.Doctrine.AERIAL ? 3.0 : 0.0, 0.0);
        final Optional<BlockPos> destination = profile.doctrine() == TacticalCombatRules.Doctrine.AERIAL
            ? Optional.of(BlockPos.containing(intended))
            : standableNear(level, BlockPos.containing(intended));
        if (destination.filter(position -> routeReaches(creature, position)).isPresent()) {
            moveTo(creature, Vec3.atBottomCenterOf(destination.orElseThrow()), profile.movementSpeed());
        } else {
            disengage(creature, target, profile.movementSpeed());
        }
    }

    private static void disengage(final Mob creature, final LivingEntity target, final double speed) {
        final Vec3 randomRetreat = creature instanceof PathfinderMob pathfinder
            ? DefaultRandomPos.getPosAway(pathfinder, 10, 5, target.position())
            : null;
        if (randomRetreat != null) {
            moveTo(creature, randomRetreat, speed);
            return;
        }
        Vec3 away = creature.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
        if (away.lengthSqr() < 1.0E-4) {
            away = new Vec3(TacticalCombatRules.flankSide(creature.getId()), 0.0, 0.0);
        }
        moveTo(creature, creature.position().add(away.normalize().scale(8.0)), speed);
    }

    static Optional<BlockPos> standableNear(final ServerLevel level, final BlockPos origin) {
        return VERTICAL_OFFSETS.stream()
            .map(offset -> origin.offset(0, offset, 0))
            .filter(position -> isStandable(level, position))
            .findFirst();
    }

    private static boolean isStandable(final ServerLevel level, final BlockPos position) {
        return level.getBlockState(position).getCollisionShape(level, position).isEmpty()
            && level.getBlockState(position.above()).getCollisionShape(level, position.above()).isEmpty()
            && level.getBlockState(position.below()).isFaceSturdy(level, position.below(), Direction.UP);
    }

    static boolean concealedFrom(
        final ServerLevel level,
        final Mob creature,
        final LivingEntity target,
        final BlockPos position
    ) {
        final Vec3 destination = Vec3.atBottomCenterOf(position).add(0.0, creature.getEyeHeight(), 0.0);
        return level.clip(new ClipContext(
            target.getEyePosition(),
            destination,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            target
        )).getType() == HitResult.Type.BLOCK;
    }

    static boolean routeReaches(final Mob creature, final LivingEntity target) {
        final Path path = creature.getNavigation().createPath(target, 1);
        return path != null && path.canReach();
    }

    static boolean routeReaches(final Mob creature, final BlockPos destination) {
        final Path path = creature.getNavigation().createPath(destination, 0);
        return path != null && (path.canReach() || path.getDistToTarget() <= 1.0F);
    }

    static boolean isRangedThreat(final LivingEntity target) {
        return target instanceof Player player
            && Stream.of(player.getMainHandItem(), player.getOffhandItem()).anyMatch(TacticalCombatRuntime::isRangedWeapon);
    }

    private static boolean isRangedWeapon(final ItemStack stack) {
        return stack.is(RANGED_WEAPONS);
    }

    private static boolean playerAttribution(final Entity entity) {
        if (entity instanceof Player) {
            return true;
        }
        return entity instanceof Projectile projectile && projectile.getOwner() instanceof Player;
    }

    private static void moveTo(final Mob creature, final Vec3 destination, final double speed) {
        creature.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
    }
}

