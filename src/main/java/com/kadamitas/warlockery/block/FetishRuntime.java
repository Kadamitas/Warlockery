package com.kadamitas.warlockery.block;

import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public final class FetishRuntime {
    private static final String SENTINEL = "WarlockeryFetishSentinel";
    private static final String EXPIRATION = "WarlockeryFetishExpiration";

    private FetishRuntime() {
    }

    public static boolean tick(final ServerLevel level, final BlockPos center, final FetishMode mode) {
        final AABB area = new AABB(center).inflate(FetishRules.RADIUS);
        final List<Mob> threats = level.getEntitiesOfClass(Mob.class, area, mob -> FetishRules.shouldAffect(
            true,
            mob.isAlive() && mob.getType().getCategory() == MobCategory.MONSTER,
            mob.typeHolder().is(WitchcraftCompatibilityTags.FETISH_IMMUNE)
        ));
        expireSentinels(level, area);
        switch (mode) {
            case DISORIENTATION -> disorient(threats);
            case GHOST_WALKING -> ghostWalk(level, area);
            case SENTINEL -> summonSentinel(level, center, area, threats);
            case SHRIEKING -> shriek(level, center, !threats.isEmpty());
            case VOODOO_PROTECTION -> level.sendParticles(
                ParticleTypes.ENCHANT,
                center.getX() + 0.5,
                center.getY() + 1.0,
                center.getZ() + 0.5,
                3,
                0.4,
                0.5,
                0.4,
                0.01
            );
        }
        return mode == FetishMode.SHRIEKING && !threats.isEmpty();
    }

    public static boolean protects(final LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) {
            return false;
        }
        final BlockPos center = target.blockPosition();
        return BlockPos.betweenClosedStream(
                center.offset(-FetishRules.RADIUS, -FetishRules.RADIUS, -FetishRules.RADIUS),
                center.offset(FetishRules.RADIUS, FetishRules.RADIUS, FetishRules.RADIUS)
            )
            .filter(level::isLoaded)
            .filter(pos -> pos.distSqr(center) <= FetishRules.RADIUS * FetishRules.RADIUS)
            .map(level::getBlockState)
            .anyMatch(state -> state.getBlock() instanceof FetishBlock
                && state.getValue(FetishBlock.BOUND)
                && state.getValue(FetishBlock.ENABLED)
                && state.getValue(FetishBlock.MODE) == FetishMode.VOODOO_PROTECTION);
    }

    private static void disorient(final List<Mob> threats) {
        threats.forEach(mob -> {
            mob.getNavigation().stop();
            mob.setTarget(null);
            mob.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0, true, true));
            mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2, true, true));
        });
    }

    private static void ghostWalk(final ServerLevel level, final AABB area) {
        level.getEntitiesOfClass(Player.class, area, player -> player.isAlive() && !player.isSpectator())
            .forEach(player -> {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false));
            });
    }

    private static void summonSentinel(
        final ServerLevel level,
        final BlockPos center,
        final AABB area,
        final List<Mob> threats
    ) {
        if (threats.isEmpty() || hasSentinel(level, area)) {
            return;
        }
        final IronGolem sentinel = EntityTypes.IRON_GOLEM.create(
            level,
            EntitySpawnReason.EVENT
        );
        if (sentinel == null) {
            return;
        }
        sentinel.snapTo(center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5);
        if (!level.noCollision(sentinel)) {
            return;
        }
        sentinel.setPlayerCreated(true);
        sentinel.setPersistenceRequired();
        sentinel.setHomeTo(center, FetishRules.RADIUS);
        sentinel.setTarget(threats.getFirst());
        sentinel.setCustomName(Component.translatable("entity.warlockery.fetish_sentinel"));
        sentinel.getPersistentData().putBoolean(SENTINEL, true);
        sentinel.getPersistentData().putLong(EXPIRATION, level.getGameTime() + FetishRules.SENTINEL_LIFETIME);
        level.addFreshEntity(sentinel);
    }

    private static boolean hasSentinel(final ServerLevel level, final AABB area) {
        return level.getEntitiesOfClass(
            IronGolem.class,
            area,
            entity -> entity.getPersistentData().getBooleanOr(SENTINEL, false)
        ).stream().findAny().isPresent();
    }

    private static void expireSentinels(final ServerLevel level, final AABB area) {
        level.getEntitiesOfClass(
            IronGolem.class,
            area,
            entity -> entity.getPersistentData().getBooleanOr(SENTINEL, false)
        ).stream()
            .filter(entity -> level.getGameTime() >= entity.getPersistentData().getLongOr(EXPIRATION, 0L))
            .sorted(Comparator.comparingInt(IronGolem::getId))
            .forEach(IronGolem::discard);
    }

    private static void shriek(final ServerLevel level, final BlockPos center, final boolean alarm) {
        if (!alarm) {
            return;
        }
        level.playSound(
            null,
            center,
            SoundEvents.SCULK_SHRIEKER_SHRIEK,
            SoundSource.BLOCKS,
            0.8F,
            1.2F
        );
    }
}
