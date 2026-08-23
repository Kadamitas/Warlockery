package com.kadamitas.warlockery.transformation;

import com.kadamitas.warlockery.entity.ArcaneCreature;
import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.world.VillageAssaultRuntime;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;

public final class WerewolfPreyDriveRuntime {
    public static final TagKey<net.minecraft.world.entity.EntityType<?>> PREY = TagKey.create(
        Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("warlockery", "werewolf_prey")
    );
    private static final Map<UUID, Episode> EPISODES = new ConcurrentHashMap<>();
    private static final Map<UUID, Cooldown> COOLDOWNS = new ConcurrentHashMap<>();

    private WerewolfPreyDriveRuntime() {
    }

    public static void tick(final ServerPlayer player) {
        tick(player, bound -> player.getRandom().nextInt(bound));
    }

    static void tick(final ServerPlayer player, final IntUnaryOperator randomRoll) {
        final long now = player.level().getGameTime();
        Cooldown cooldown = COOLDOWNS.get(player.getUUID());
        if (player.isDeadOrDying()
            || cooldown != null && !cooldown.dimension().equals(player.level().dimension())) {
            release(player);
            return;
        }
        if (cooldown != null && cooldown.expiresAt() <= now) {
            COOLDOWNS.remove(player.getUUID(), cooldown);
            cooldown = null;
        }
        final Episode episode = EPISODES.get(player.getUUID());
        if (episode != null) {
            final Entity resolved = player.level().getEntity(episode.targetId());
            if (!episode.dimension().equals(player.level().dimension())) {
                release(player);
                return;
            }
            if (!(resolved instanceof LivingEntity target)
                || now >= episode.expiresAt() || !valid(player, target)
                || target.isDeadOrDying()
                || SupernaturalState.getForm(player) != SupernaturalForm.WEREWOLF
                || !WerewolfPreyDriveRules.shapeEligible(SupernaturalProgression.werewolfShape(player))
                || WerewolfPreyDriveRules.cancelsEpisode(condition(player))) {
                releaseEpisode(player);
                return;
            }
            if (player.distanceToSqr(target) <= 4.0 && player.hasLineOfSight(target)) {
                target.hurtServer(player.level(), target.damageSources().playerAttack(player), 4.0F);
                if (!target.isAlive()) {
                    releaseEpisode(player);
                }
            } else {
                pursue(player, target);
            }
            return;
        }
        if (player.tickCount % WerewolfPreyDriveRules.CHECK_INTERVAL_TICKS != 0
            || !WerewolfPreyDriveRules.triggered(randomRoll.applyAsInt(WerewolfPreyDriveRules.TRIGGER_BOUND))) {
            return;
        }
        tryStartEpisode(player);
    }

    static boolean tryStartEpisode(final ServerPlayer player) {
        final long now = player.level().getGameTime();
        final Cooldown cooldown = COOLDOWNS.get(player.getUUID());
        if (player.isDeadOrDying()
            || SupernaturalState.getForm(player) != SupernaturalForm.WEREWOLF
            || !WerewolfPreyDriveRules.shapeEligible(SupernaturalProgression.werewolfShape(player))
            || WerewolfPreyDriveRules.cancelsEpisode(condition(player))
            || cooldown != null && cooldown.dimension().equals(player.level().dimension())
                && cooldown.expiresAt() > now) {
            return false;
        }
        final LivingEntity target = player.level().getEntitiesOfClass(
            LivingEntity.class,
            player.getBoundingBox().inflate(WerewolfPreyDriveRules.RANGE),
            candidate -> coarseValid(player, candidate)
        ).stream().filter(player::hasLineOfSight)
            .sorted(Comparator.comparingDouble((LivingEntity candidate) -> player.distanceToSqr(candidate))
                .thenComparing(Entity::getUUID)).limit(64).findFirst().orElse(null);
        if (target == null) {
            return false;
        }
        EPISODES.put(player.getUUID(), new Episode(
            target.getId(), now + WerewolfPreyDriveRules.TIMEOUT_TICKS, player.level().dimension()
        ));
        COOLDOWNS.put(player.getUUID(), new Cooldown(
            now + WerewolfPreyDriveRules.COOLDOWN_TICKS, player.level().dimension()
        ));
        return true;
    }

    public static int targetEntityId(final Player player) {
        final Episode episode = EPISODES.get(player.getUUID());
        return episode == null ? -1 : episode.targetId();
    }

    public static void release(final Player player) {
        EPISODES.remove(player.getUUID());
        COOLDOWNS.remove(player.getUUID());
    }

    private static void releaseEpisode(final Player player) {
        EPISODES.remove(player.getUUID());
    }

    static boolean coolingDown(final Player player) {
        return COOLDOWNS.containsKey(player.getUUID());
    }

    private static boolean valid(final ServerPlayer player, final LivingEntity target) {
        return coarseValid(player, target) && player.hasLineOfSight(target);
    }

    private static boolean coarseValid(final ServerPlayer player, final LivingEntity target) {
        final boolean protectedIdentity = protectedIdentity(target) || target.isAlliedTo(player);
        return WerewolfPreyDriveRules.eligible(new WerewolfPreyDriveRules.Candidate(
            target.typeHolder().is(PREY), target.isAlive(), juvenile(target),
            protectedIdentity, target instanceof ArcaneCreature, target.isPassenger() || target.isVehicle(),
            target.level() != player.level() || target.isRemoved(),
            VillageAssaultRuntime.protectsFromPreyDrive(target),
            player.distanceToSqr(target) > WerewolfPreyDriveRules.RANGE * WerewolfPreyDriveRules.RANGE,
            false
        )) && player.canAttack(target);
    }

    static boolean protectedIdentity(final LivingEntity target) {
        return target.hasCustomName()
            || target instanceof Leashable leashable && leashable.isLeashed()
            || target instanceof TamableAnimal tamable && tamable.isTame()
            || target instanceof OwnableEntity ownable && ownable.getOwnerReference() != null
            || target instanceof AbstractHorse horse && horse.isTamed()
            || CreatureBehaviorState.owner(target).isPresent();
    }

    static boolean juvenile(final LivingEntity target) {
        return target.isBaby();
    }

    private static void pursue(final ServerPlayer player, final LivingEntity target) {
        final net.minecraft.world.phys.Vec3 movement = player.getDeltaMovement();
        final net.minecraft.world.phys.Vec3 direction = target.position().subtract(player.position());
        final WerewolfPreyDriveRules.PursuitMotion pursuit = WerewolfPreyDriveRules.pursuitMotion(
            movement.x, movement.z, direction.x, direction.z, movement.y
        );
        player.setDeltaMovement(pursuit.x(), pursuit.vertical(), pursuit.z());
        player.hurtMarked = true;
        player.setSprinting(true);
    }

    private static WerewolfPreyDriveRules.PlayerCondition condition(final ServerPlayer player) {
        return new WerewolfPreyDriveRules.PlayerCondition(
            player.isPassenger(),
            player.getAbilities().flying,
            player.isFallFlying(),
            player.isInWater(),
            player.getAirSupply() < player.getMaxAirSupply(),
            player.isInLava() || player.isOnFire(),
            player.isInPowderSnow,
            player.isFreezing()
        );
    }

    private record Episode(
        int targetId,
        long expiresAt,
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension
    ) {
    }

    private record Cooldown(
        long expiresAt,
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension
    ) {
    }
}
