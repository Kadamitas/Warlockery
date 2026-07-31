package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.ritual.ManifestationRuntime;
import com.kadamitas.warlockery.world.LegacyStructureRules;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class FetishRuntime {
    private static final String SENTINEL = "WarlockeryFetishSentinel";
    private static final String EXPIRATION = "WarlockeryFetishExpiration";

    private FetishRuntime() {
    }

    public static boolean tick(final ServerLevel level, final BlockPos center, final FetishMode mode) {
        return tick(level, center, mode, false);
    }

    public static boolean tick(
        final ServerLevel level,
        final BlockPos center,
        final FetishMode mode,
        final boolean silentAlarm
    ) {
        final AABB area = new AABB(center).inflate(FetishRules.RADIUS);
        final List<LivingEntity> threats = level.getEntitiesOfClass(
            LivingEntity.class,
            area,
            FetishRuntime::isThreat
        );
        expireSentinels(level, area);
        switch (mode) {
            case DISORIENTATION -> disorient(threats);
            case GHOST_WALKING -> ghostWalk(level, area);
            case SENTINEL -> summonSentinel(level, center, area, threats);
            case SHRIEKING -> shriek(level, center, !threats.isEmpty() && !silentAlarm);
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
        return StatueWardData.get(level).protectsHex(center) || BlockPos.betweenClosedStream(
                center.offset(-FetishRules.RADIUS, -FetishRules.RADIUS, -FetishRules.RADIUS),
                center.offset(FetishRules.RADIUS, FetishRules.RADIUS, FetishRules.RADIUS)
            )
            .filter(level::isLoaded)
            .filter(pos -> pos.distSqr(center) <= FetishRules.RADIUS * FetishRules.RADIUS)
            .map(level::getBlockState)
            .anyMatch(state -> state.getBlock() instanceof FetishBlock
                && state.getValue(FetishBlock.BOUND)
                && state.getValue(FetishBlock.ENABLED)
                && state.getValue(FetishBlock.MODE) == FetishMode.VOODOO_PROTECTION
                || WitchLadderBlock.isActiveProtection(state));
    }

    public static int attractZombies(final ServerLevel level, final BlockPos center) {
        final List<Zombie> attracted = level.getEntitiesOfClass(
            Zombie.class,
            new AABB(center).inflate(24.0),
            zombie -> LegacyStructureRules.attractsZombie(
                false,
                zombie.isAlive(),
                zombie.distanceToSqr(Vec3.atCenterOf(center))
            )
        );
        attracted.forEach(zombie -> {
            if (zombie.getTarget() == null) {
                zombie.getNavigation().moveTo(
                    center.getX() + 0.5,
                    center.getY(),
                    center.getZ() + 0.5,
                    1.0
                );
            }
        });
        return attracted.size();
    }

    private static boolean isThreat(final LivingEntity entity) {
        final boolean threateningMob = entity instanceof Mob mob
            && mob.getType().getCategory() == MobCategory.MONSTER;
        final boolean threateningPlayer = entity instanceof Player player && FetishRules.isPlayerThreat(
            !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty(),
            List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
                .stream()
                .map(player::getItemBySlot)
                .anyMatch(stack -> !stack.isEmpty())
        );
        return FetishRules.shouldAffect(
            true,
            entity.isAlive() && (threateningMob || threateningPlayer),
            entity.typeHolder().is(WitchcraftCompatibilityTags.FETISH_IMMUNE)
        );
    }

    private static void disorient(final List<LivingEntity> threats) {
        threats.forEach(entity -> {
            if (entity instanceof Mob mob) {
                mob.getNavigation().stop();
                mob.setTarget(null);
            }
            entity.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0, true, true));
            entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2, true, true));
        });
    }

    private static void ghostWalk(final ServerLevel level, final AABB area) {
        ManifestationRuntime.sustain(level, area, 80);
    }

    private static void summonSentinel(
        final ServerLevel level,
        final BlockPos center,
        final AABB area,
        final List<LivingEntity> threats
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
