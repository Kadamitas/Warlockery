package com.kadamitas.warlockery.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public final class ImpEntity extends WingedArcaneMob {
    private static final String STATE_KEY = "WarlockeryImpLife";
    private static final EntityDataAccessor<Byte> DATA_PRESENTATION_ACTION =
        SynchedEntityData.defineId(ImpEntity.class, EntityDataSerializers.BYTE);

    private final ImpLifeRuntime.Counters lifeCounters = new ImpLifeRuntime.Counters();
    private ImpLifeState lifeState;
    private int scoutChargedReads;
    private long progressTarget = Long.MIN_VALUE;
    private long progressSampleAt;
    private double progressDistance;

    public ImpEntity(final EntityType<? extends Monster> type, final Level level) {
        super(type, level, CreatureKind.IMP);
        lifeState = ImpLifeState.empty(getUUID(), level.getGameTime());
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PRESENTATION_ACTION,
            EntityPresentationSync.encode(ImpLifeRules.Action.NONE));
    }

    @Override
    protected void registerArcaneTargets() {
        targetPlayers();
    }

    public ImpLifeState lifeState() {
        return lifeState;
    }

    public void setLifeState(final ImpLifeState state) {
        lifeState = state == null ? ImpLifeState.empty(getUUID(), level().getGameTime()) : state;
        syncPresentationFromRuntime();
    }

    public ImpLifeRules.Action presentationAction() {
        return EntityPresentationSync.decode(entityData.get(DATA_PRESENTATION_ACTION),
            ImpLifeRules.Action.NONE);
    }

    private void syncPresentationFromRuntime() {
        final byte action = EntityPresentationSync.encode(lifeState.action());
        if (entityData.get(DATA_PRESENTATION_ACTION) != action) {
            entityData.set(DATA_PRESENTATION_ACTION, action);
        }
    }

    public ImpLifeRuntime.Counters lifeCounters() {
        return lifeCounters;
    }

    public int scoutChargedReads() {
        return scoutChargedReads;
    }

    public void chargeScoutReads(final int reads) {
        scoutChargedReads = Math.min(
            ImpLifeRules.SCOUT_TOTAL_READ_BUDGET,
            scoutChargedReads + Math.max(0, reads)
        );
    }

    public void resetScoutReads() {
        scoutChargedReads = 0;
    }

    public boolean progressStalled(final long now, final net.minecraft.core.BlockPos destination) {
        final double distance = Math.sqrt(blockPosition().distSqr(destination));
        if (progressTarget != destination.asLong()) {
            progressTarget = destination.asLong();
            progressSampleAt = now;
            progressDistance = distance;
            return false;
        }
        if (now - progressSampleAt < ImpLifeRules.STUCK_WINDOW_TICKS) {
            return false;
        }
        final boolean stalled = progressDistance - distance < ImpLifeRules.STUCK_MIN_PROGRESS;
        progressSampleAt = now;
        progressDistance = distance;
        return stalled;
    }

    @Override
    protected boolean ownsSpecializedWingedAi() {
        return true;
    }

    @Override
    protected void customWingedAiStep(final ServerLevel level) {
        ImpLifeRuntime.tick(this, level);
        syncPresentationFromRuntime();
    }

    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND
            && player.getItemInHand(hand).isEmpty()
            && level() instanceof ServerLevel serverLevel) {
            final InteractionResult command = ImpLifeRuntime.interactDutyCommand(this, serverLevel, player);
            if (command != InteractionResult.PASS) {
                return command;
            }
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        return super.canAttack(target) && ImpLifeRuntime.mayAttack(this, target);
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        if (ImpLifeRuntime.ignoresBoundDamage(this, source)) {
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public void performRangedAttack(final LivingEntity target, final float power) {
        final Vec3 direction = target.getEyePosition().subtract(getEyePosition()).normalize();
        final ImpFireball ember = new ImpFireball(level(), this, direction);
        ember.setPos(getX(), getEyeY() - 0.15, getZ());
        level().addFreshEntity(ember);
        level().playSound(null, blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.8F, 1.3F);
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        final CompoundTag tag = lifeState.write();
        tag.putInt("ScoutChargedReads", scoutChargedReads);
        output.store(STATE_KEY, CompoundTag.CODEC, tag);
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        final java.util.Optional<CompoundTag> stored = input.read(STATE_KEY, CompoundTag.CODEC);
        lifeState = stored
            .map(tag -> ImpLifeState.read(tag, getUUID(), level().getGameTime()))
            .orElse(ImpLifeState.empty(getUUID(), level().getGameTime()));
        scoutChargedReads = Math.clamp(
            stored.map(tag -> tag.getIntOr("ScoutChargedReads", 0)).orElse(0),
            0,
            ImpLifeRules.SCOUT_TOTAL_READ_BUDGET
        );
        progressTarget = Long.MIN_VALUE;
        syncPresentationFromRuntime();
    }
}
