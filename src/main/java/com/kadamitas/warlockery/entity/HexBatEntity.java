package com.kadamitas.warlockery.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Dedicated collision-enabled, no-gravity flying Hex Bat.
 * Not a Vex, SpiritMob, familiar, or spectral creature. Only
 * {@link HexBatRuntime} owns target selection and movement; the generic
 * behavior, tactical, ambient, and hazard runtimes are never dispatched.
 */
public final class HexBatEntity extends Monster implements ArcaneCreature {
    private static final String STATE_KEY = "WarlockeryHexBat";
    private static final EntityDataAccessor<Boolean> DATA_ROOSTING = SynchedEntityData.defineId(
        HexBatEntity.class, EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Boolean> DATA_SWOOPING = SynchedEntityData.defineId(
        HexBatEntity.class, EntityDataSerializers.BOOLEAN
    );

    private final HexBatRuntime.Counters batCounters = new HexBatRuntime.Counters();
    private HexBatState batState;
    // Transient sighting evidence behind the 80-tick unseen release.
    // Never saved, never synced, cleared on load.
    private java.util.@Nullable UUID lastSeenTargetId;
    private long lastSeenTargetAt;

    public HexBatEntity(final EntityType<? extends Monster> type, final Level level) {
        super(type, level);
        batState = HexBatState.empty(getUUID(), level.getGameTime());
        moveControl = new FlyingMoveControl<>(this, 20, true);
        setNoGravity(true);
        xpReward = 3;
        normalizeEquipment();
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.HEX_BAT;
    }

    public HexBatState batState() {
        return batState;
    }

    public void setBatState(final HexBatState state) {
        batState = state == null ? HexBatState.empty(getUUID(), level().getGameTime()) : state;
    }

    public HexBatRuntime.Counters batCounters() {
        return batCounters;
    }

    // ---- synchronized presentation facts ----

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ROOSTING, false);
        builder.define(DATA_SWOOPING, false);
    }

    public boolean isRoosting() {
        return entityData.get(DATA_ROOSTING);
    }

    public void setRoosting(final boolean roosting) {
        entityData.set(DATA_ROOSTING, roosting);
    }

    public boolean isSwooping() {
        return entityData.get(DATA_SWOOPING);
    }

    public void setSwooping(final boolean swooping) {
        entityData.set(DATA_SWOOPING, swooping);
    }

    // ---- flight chassis ----

    @Override
    protected PathNavigation createNavigation(final Level level) {
        final FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected void checkFallDamage(
        final double fallDistance, final boolean onGround,
        final net.minecraft.world.level.block.state.BlockState state,
        final net.minecraft.core.BlockPos position
    ) {
        // A flying bat takes no fall damage; collision itself stays enabled.
    }

    // ---- minimal non-movement goals ----

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    public int operationalTargetGoalCount() {
        return targetSelector.getAvailableGoals().size();
    }

    /** Records a confirmed line-of-sight sighting of the given target. */
    void noteTargetSeen(final java.util.UUID targetId, final long now) {
        lastSeenTargetId = targetId;
        lastSeenTargetAt = now;
    }

    /** Last confirmed sighting tick for the given target, or zero when none. */
    long targetLastSeenAt(final java.util.UUID targetId) {
        return targetId.equals(lastSeenTargetId) ? lastSeenTargetAt : 0L;
    }

    // ---- combat gates ----

    /** Final absolute safety gate; agrees with runtime acquisition even for external setTarget calls. */
    @Override
    public boolean canAttack(final LivingEntity target) {
        return super.canAttack(target) && HexBatRuntime.eligibleTarget(this, target);
    }

    /** Accepted attributed contact is the only jinx route; the bound action gates it. */
    @Override
    public boolean doHurtTarget(final ServerLevel level, final Entity target) {
        final boolean hurt = super.doHurtTarget(level, target);
        if (hurt) {
            HexBatRuntime.onContactAccepted(this, level, target);
        }
        return hurt;
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            HexBatRuntime.recordDirectAttack(this, level, source);
        }
        return hurt;
    }

    // ---- server dispatch ----

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        HexBatRuntime.tick(this, level);
    }

    // ---- equipment normalization ----

    private void normalizeEquipment() {
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        setDropChance(EquipmentSlot.OFFHAND, 0.0F);
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
        // The registry-owned 14/4/16 attribute baseline is exact; the generic
        // Mob random follow-range spawn bonus would make it nondeterministic.
        final AttributeInstance followRange = getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) followRange.removeModifier(RANDOM_SPAWN_BONUS_ID);
        setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        return result;
    }

    // ---- sounds (existing assets only) ----

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VEX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(final DamageSource source) {
        return SoundEvents.VEX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VEX_DEATH;
    }

    // ---- persistence ----

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(STATE_KEY, CompoundTag.CODEC, batState.write());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        batState = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(tag -> HexBatState.read(tag, getUUID(), level().getGameTime()))
            .orElse(HexBatState.empty(getUUID(), level().getGameTime()));
        setTarget(null);
        setSwooping(false);
        setRoosting(false);
        lastSeenTargetId = null;
        lastSeenTargetAt = 0L;
        normalizeEquipment();
    }
}
