package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.HobgoblinEntity;
import com.kadamitas.warlockery.fabric.event.LivingDamageContext;
import com.kadamitas.warlockery.entity.GoblinEntity;
import com.kadamitas.warlockery.entity.ArcaneCreature;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.world.SettlementFortificationRules.LayoutPlan;
import com.kadamitas.warlockery.world.SettlementFortificationRules.Offset;
import com.kadamitas.warlockery.world.SettlementFortificationRules.SettlementKind;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public final class VillageGuardRuntime {
    private static final ConcurrentHashMap<UUID, Long> LAST_SHOT = new ConcurrentHashMap<>();

    private VillageGuardRuntime() {
    }

    public static void tick(final ServerLevel level) {
        if (level.getGameTime() % 5L != 0L || level.players().isEmpty()) {
            return;
        }
        final Set<UUID> visited = new HashSet<>();
        level.players().forEach(player -> level.getEntitiesOfClass(
            IronGolem.class,
            new AABB(player.blockPosition()).inflate(96.0, 48.0, 96.0),
            VillageGuardRuntime::isSettlementGuard
        ).stream()
            .filter(guard -> visited.add(guard.getUUID()))
            .forEach(guard -> tickGuard(level, guard)));
        LAST_SHOT.keySet().removeIf(uuid -> !visited.contains(uuid) && level.getGameTime() % 1_200L == 0L);
    }

    public static boolean isSettlementGuard(final Entity entity) {
        return entity.entityTags().contains(SettlementFortificationRuntime.GUARD_TAG);
    }

    public static boolean isHumanSettlementGuard(final Entity entity) {
        return isSettlementGuard(entity)
            && entity.entityTags().contains(SettlementFortificationRuntime.HUMAN_GUARD_TAG);
    }

    public static boolean isHobgoblinSettlementGuard(final Entity entity) {
        return isSettlementGuard(entity)
            && entity.entityTags().contains(SettlementFortificationRuntime.HOBGOBLIN_GUARD_TAG);
    }

    public static boolean handleInteract(final ServerPlayer player, final Entity target, final ItemStack heldItem) {
        if (!(target instanceof Villager villager) || !isCommissionableTarget(villager)) {
            return false;
        }
        final boolean eligible = VillageGuardRules.canCommission(
            player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE),
            player.level().isVillage(villager.blockPosition()),
            !villager.isBaby(),
            heldItem.is(Items.LEATHER_CHESTPLATE)
        );
        if (!eligible) {
            return false;
        }
        final IronGolem guard = EntityTypes.IRON_GOLEM.create(player.level(), EntitySpawnReason.CONVERSION);
        if (guard == null) {
            return false;
        }
        guard.snapTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), villager.getXRot());
        guard.setPlayerCreated(true);
        guard.setPersistenceRequired();
        if (villager.hasCustomName()) {
            guard.setCustomName(villager.getCustomName());
        }
        player.level().addFreshEntity(guard);
        villager.discard();
        if (!player.hasInfiniteMaterials()) {
            heldItem.shrink(1);
        }
        player.sendOverlayMessage(Component.translatable("message.warlockery.village.guard_commissioned")
            .withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static void handleSettlementAttack(final LivingDamageContext event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        final Entity attacker = event.getSource().getEntity();
        if (attacker instanceof ServerPlayer player) {
            protectedSettlement(event.getEntity()).filter(kind -> VillageGuardRules.shouldRetaliate(
                true,
                true,
                player.isCreative() || player.isSpectator()
            )).ifPresent(kind -> alertGuards(level, event.getEntity().blockPosition(), kind, player));
            return;
        }
        if (event.getEntity() instanceof ServerPlayer protectedPlayer
            && protectedPlayer.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)
            && attacker instanceof LivingEntity threat) {
            alertGuards(level, protectedPlayer.blockPosition(), SettlementKind.HUMAN, threat);
        }
    }

    private static Optional<SettlementKind> protectedSettlement(final LivingEntity entity) {
        // Kind first, class second. A concrete-class test alone silently stops recognising exact
        // Hobgoblin residents the moment the exact species moves to its own dedicated body.
        if (entity instanceof ArcaneCreature resident
            && resident.creatureKind() == CreatureKind.HOBGOBLIN) {
            return Optional.of(SettlementKind.HOBGOBLIN);
        }
        if (entity instanceof Villager villager && villager.getType() == EntityTypes.VILLAGER) {
            return Optional.of(SettlementKind.HUMAN);
        }
        if (isHumanSettlementGuard(entity)) {
            return Optional.of(SettlementKind.HUMAN);
        }
        return isHobgoblinSettlementGuard(entity)
            ? Optional.of(SettlementKind.HOBGOBLIN)
            : Optional.empty();
    }

    private static void alertGuards(
        final ServerLevel level,
        final BlockPos attackedPosition,
        final SettlementKind kind,
        final LivingEntity attacker
    ) {
        level.getEntitiesOfClass(
            IronGolem.class,
            new AABB(attackedPosition).inflate(48.0, 24.0, 48.0),
            guard -> kind == SettlementKind.HUMAN
                ? isHumanSettlementGuard(guard)
                : isHobgoblinSettlementGuard(guard)
        ).forEach(guard -> guard.setTarget(attacker));
    }

    private static void tickGuard(final ServerLevel level, final IronGolem guard) {
        LivingEntity target = guard.getTarget();
        if (target == null || !target.isAlive() || guard.distanceToSqr(target) > 48.0 * 48.0) {
            target = nearestThreat(level, guard).orElse(null);
            guard.setTarget(target);
        }
        if (target == null) {
            patrol(level, guard);
            return;
        }
        final long lastShot = LAST_SHOT.getOrDefault(guard.getUUID(), Long.MIN_VALUE / 2L);
        final long elapsed = level.getGameTime() - lastShot;
        if (VillageGuardRules.shouldFireSilverBolt(
            true,
            target.isAlive(),
            guard.distanceToSqr(target),
            elapsed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) elapsed
        )) {
            fireSilverBolt(level, guard, target);
            LAST_SHOT.put(guard.getUUID(), level.getGameTime());
        }
    }

    private static Optional<LivingEntity> nearestThreat(final ServerLevel level, final IronGolem guard) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                guard.getBoundingBox().inflate(VillageGuardRules.TARGET_RADIUS),
                candidate -> candidate != guard && isThreat(candidate)
            ).stream()
            .min(Comparator.comparingDouble(guard::distanceToSqr));
    }

    private static boolean isThreat(final LivingEntity candidate) {
        // The dedicated F10 body is the exact Goblin; a human settlement guard treats every one of
        // them as hostile, marked assault member or not, exactly as it did in 1.4.
        if (candidate instanceof GoblinEntity) {
            return true;
        }
        return candidate instanceof Monster;
    }

    private static void fireSilverBolt(
        final ServerLevel level,
        final IronGolem guard,
        final LivingEntity target
    ) {
        final ItemStack bolt = new ItemStack(ModItems.ALL.get("ingredient_bolt_silver").get());
        final Arrow arrow = new Arrow(level, guard, bolt, null);
        arrow.setBaseDamage(4.0);
        final double x = target.getX() - guard.getX();
        final double z = target.getZ() - guard.getZ();
        final double arc = Math.sqrt(x * x + z * z) * 0.12;
        Projectile.spawnProjectile(arrow, level, bolt, projectile -> projectile.shoot(
            x,
            target.getEyeY() - projectile.getY() + arc,
            z,
            1.8F,
            2.0F
        ));
        guard.playSound(SoundEvents.CROSSBOW_SHOOT, 0.9F, 0.9F + guard.getRandom().nextFloat() * 0.2F);
    }

    private static void patrol(final ServerLevel level, final IronGolem guard) {
        if (!guard.getNavigation().isDone() && guard.tickCount % 80 != 0) {
            return;
        }
        final Optional<BlockPos> center = guardCenter(guard);
        if (center.isEmpty()) {
            return;
        }
        final SettlementKind kind = isHumanSettlementGuard(guard)
            ? SettlementKind.HUMAN
            : SettlementKind.HOBGOBLIN;
        final int radius = guardRadius(guard).orElseGet(() -> SettlementFortificationRules.plan(kind).radius());
        final LayoutPlan plan = radius == 1
            ? SettlementFortificationRules.compactPlan(kind)
            : SettlementFortificationRules.plan(kind, radius);
        final int index = Math.floorMod(guard.getUUID().hashCode() + guard.tickCount / 80, plan.patrolWaypoints().size());
        final Offset offset = plan.patrolWaypoints().get(index);
        final BlockPos horizontal = center.orElseThrow().offset(offset.x(), 0, offset.z());
        final BlockPos destination = new BlockPos(
            horizontal.getX(),
            level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, horizontal.getX(), horizontal.getZ()),
            horizontal.getZ()
        );
        guard.getNavigation().moveTo(
            destination.getX() + 0.5,
            destination.getY(),
            destination.getZ() + 0.5,
            0.8
        );
    }

    private static Optional<BlockPos> guardCenter(final Entity guard) {
        return guard.entityTags().stream()
            .filter(tag -> tag.startsWith(SettlementFortificationRuntime.GUARD_CENTER_PREFIX))
            .map(tag -> tag.substring(SettlementFortificationRuntime.GUARD_CENTER_PREFIX.length()))
            .mapToLong(VillageGuardRuntime::parseLongOrSentinel)
            .filter(value -> value != Long.MIN_VALUE)
            .mapToObj(BlockPos::of)
            .findFirst();
    }

    private static Optional<Integer> guardRadius(final Entity guard) {
        return guard.entityTags().stream()
            .filter(tag -> tag.startsWith(SettlementFortificationRuntime.GUARD_RADIUS_PREFIX))
            .map(tag -> tag.substring(SettlementFortificationRuntime.GUARD_RADIUS_PREFIX.length()))
            .map(VillageGuardRuntime::parseIntOrSentinel)
            .filter(value -> value >= 0)
            .findFirst();
    }

    private static long parseLongOrSentinel(final String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static int parseIntOrSentinel(final String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static boolean isCommissionableTarget(final Villager villager) {
        return villager.getType() == EntityTypes.VILLAGER;
    }
}
