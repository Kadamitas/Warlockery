package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.EldritchWatcherRules.Mode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public final class EldritchWatcherEntity extends Vex implements ArcaneCreature {
    private static final String STATE_KEY = "WarlockeryEldritchWatcher";
    private static final EntityDataAccessor<Byte> DATA_PRESENTATION_MODE =
        SynchedEntityData.defineId(EldritchWatcherEntity.class, EntityDataSerializers.BYTE);

    private final EldritchWatcherRuntime.Counters watcherCounters = new EldritchWatcherRuntime.Counters();
    private EldritchWatcherState watcherState;

    public EldritchWatcherEntity(final EntityType<? extends Vex> type, final Level level) {
        super(type, level);
        watcherState = EldritchWatcherState.empty(getUUID(), level.getGameTime());
        normalizeEquipment();
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.ELDRITCH_WATCHER;
    }

    public EldritchWatcherState watcherState() {
        return watcherState;
    }

    public void setWatcherState(final EldritchWatcherState state) {
        watcherState = state == null
            ? EldritchWatcherState.empty(getUUID(), level().getGameTime())
            : state;
        syncPresentation(watcherState.mode());
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_PRESENTATION_MODE, (byte) Mode.QUIET_VIGIL.ordinal());
    }

    public Mode presentationMode() {
        final int stored = entityData.get(DATA_PRESENTATION_MODE);
        final Mode[] modes = Mode.values();
        return stored >= 0 && stored < modes.length ? modes[stored] : Mode.QUIET_VIGIL;
    }

    private void syncPresentation(final Mode mode) {
        final byte encoded = (byte) mode.ordinal();
        if (entityData.get(DATA_PRESENTATION_MODE) != encoded) {
            entityData.set(DATA_PRESENTATION_MODE, encoded);
        }
    }

    public EldritchWatcherRuntime.Counters watcherCounters() {
        return watcherCounters;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F, 1.0F));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
    }

    public int operationalTargetGoalCount() {
        return targetSelector.getAvailableGoals().size();
    }

    public List<String> operationalGoalNames() {
        return goalSelector.getAvailableGoals().stream()
            .map(goal -> goal.getGoal().getClass().getSimpleName())
            .toList();
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        EldritchWatcherRuntime.tick(this, level);
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        return super.canAttack(target) && EldritchWatcherRuntime.eligibleTarget(this, target);
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            EldritchWatcherRuntime.recordDirectHarm(this, level, source);
        }
        return hurt;
    }

    public boolean acceptExternalLure(final ServerLevel level, final BlockPos lurePosition) {
        return EldritchWatcherRuntime.acceptExternalLure(this, level, lurePosition);
    }

    public Optional<UUID> warlockeryOwner() {
        return CreatureBehaviorState.owner(this);
    }

    public Optional<UUID> vanillaOwner() {
        final LivingEntity owner = getOwner();
        return owner == null ? Optional.empty() : Optional.of(owner.getUUID());
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level,
        final DifficultyInstance difficulty,
        final EntitySpawnReason reason,
        final @Nullable SpawnGroupData groupData
    ) {
        final SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);
        normalizeEquipment();
        return result;
    }

    @Override
    protected void populateDefaultEquipmentSlots(final RandomSource random, final DifficultyInstance difficulty) {
        normalizeEquipment();
    }

    private void normalizeEquipment() {
        for (final EquipmentSlot slot : EquipmentSlot.values()) {
            if (!getItemBySlot(slot).isEmpty()) {
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(STATE_KEY, CompoundTag.CODEC, watcherState.write());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        final long now = level().getGameTime();
        watcherState = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(tag -> EldritchWatcherState.read(
                tag, getUUID(), level().dimension().identifier().toString(), now
            ))
            .orElse(EldritchWatcherState.empty(getUUID(), now));
        syncPresentation(watcherState.mode());
        setIsCharging(false);
        normalizeEquipment();
    }
}
