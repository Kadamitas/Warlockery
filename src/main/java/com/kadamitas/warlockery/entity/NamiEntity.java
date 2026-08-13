package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.ritual.marriage.MarriageData;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class NamiEntity extends PathfinderMob {
    private static final double FOLLOW_DISTANCE = 9.0;
    private static final double TELEPORT_DISTANCE = 1024.0;

    public NamiEntity(final EntityType<? extends PathfinderMob> type, final Level level) {
        super(type, level);
        setCustomName(Component.translatable("entity.warlockery.nami"));
        setCustomNameVisible(true);
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        if (HazardEscapeRuntime.tick(this, level)) {
            return;
        }
        spouse(level).ifPresent(player -> {
            CreatureBehaviorState.bind(this, player.getUUID());
            defend(level, player);
            if (!SpouseAmbientRuntime.tick(this, level, player)) {
                follow(player);
            }
        });
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        return !(level() instanceof ServerLevel serverLevel
            && spouse(serverLevel).filter(player -> player == target).isPresent())
            && super.canAttack(target);
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        if (amount >= getHealth() && rescueAtSpouseBed(level)) {
            return true;
        }
        return super.hurtServer(level, source, amount);
    }

    public void acceptMarriage(final ServerPlayer player, final String spouseName) {
        CreatureBehaviorState.bind(this, player.getUUID());
        setCustomName(Component.literal(spouseName));
        setCustomNameVisible(true);
        setPersistenceRequired();
    }

    public void divorce() {
        if (level() instanceof ServerLevel level) {
            spouse(level).ifPresent(player -> SpouseAmbientRuntime.abort(this, level, player));
        }
        CreatureBehaviorState.unbind(this);
        setCustomName(Component.translatable("entity.warlockery.nami"));
        setTarget(null);
        getNavigation().stop();
    }

    private Optional<ServerPlayer> spouse(final ServerLevel level) {
        return MarriageData.get(level).ownerForNami(getUUID())
            .map(level.getServer().getPlayerList()::getPlayer);
    }

    private void follow(final ServerPlayer player) {
        if (player.level() != level()) {
            teleportTo(
                (ServerLevel) player.level(),
                player.getX() + 1.0,
                player.getY(),
                player.getZ() + 1.0,
                Set.<Relative>of(),
                getYRot(),
                getXRot(),
                false
            );
            return;
        }
        final double distance = distanceToSqr(player);
        if (distance > TELEPORT_DISTANCE) {
            teleportTo(player.getX() + 1.0, player.getY(), player.getZ() + 1.0);
        } else if (distance > FOLLOW_DISTANCE) {
            getNavigation().moveTo(player, 1.2);
        } else {
            getNavigation().stop();
        }
    }

    private void defend(final ServerLevel level, final ServerPlayer player) {
        if (tickCount % 20 == 0) {
            level.getEntitiesOfClass(
                    Monster.class,
                    new AABB(player.blockPosition()).inflate(18.0),
                    enemy -> enemy.isAlive() && enemy.getTarget() == player
                ).stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .ifPresent(this::setTarget);
        }
        final LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || distanceToSqr(target) > 576.0 || tickCount % 30 != 0) {
            return;
        }
        target.hurtServer(level, level.damageSources().indirectMagic(this, this), 6.0F);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getEyeY(), target.getZ(), 18, 0.35, 0.45, 0.35, 0.08);
        level.playSound(null, blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.NEUTRAL, 0.8F, 1.15F);
    }

    private boolean rescueAtSpouseBed(final ServerLevel currentLevel) {
        final Optional<ServerPlayer> spouse = spouse(currentLevel);
        if (spouse.isEmpty()) {
            return false;
        }
        final ServerPlayer player = spouse.orElseThrow();
        final ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
        final ServerLevel destination;
        final BlockPos position;
        if (respawn == null) {
            destination = currentLevel.getServer().overworld();
            position = destination.getRespawnData().pos();
        } else {
            destination = currentLevel.getServer().getLevel(respawn.respawnData().dimension());
            if (destination == null) {
                return false;
            }
            position = respawn.respawnData().pos();
        }
        setHealth(1.0F);
        clearFire();
        setTarget(null);
        teleportTo(
            destination,
            position.getX() + 0.5,
            position.getY() + 1.0,
            position.getZ() + 0.5,
            Set.<Relative>of(),
            getYRot(),
            getXRot(),
            false
        );
        return true;
    }
}
