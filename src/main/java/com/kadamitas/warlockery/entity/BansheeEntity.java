package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.BansheeRules.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Dedicated collision-enabled flying Banshee. Deliberately not an {@code Enemy}, Vex, Zombie,
 * SpiritMob, WingedArcaneMob, familiar, or caster: it never targets, never attacks proactively,
 * and its only behavior authority is {@link BansheeRuntime}. Public category, dimensions,
 * attributes, XP, sounds, and Peaceful removal stay exactly as registered.
 */
public final class BansheeEntity extends PathfinderMob implements ArcaneCreature {
    static final String STATE_KEY = "WarlockeryBanshee";
    private static final EntityDataAccessor<Byte> DATA_ACTIVITY =
        SynchedEntityData.defineId(BansheeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_PULSE_SEQUENCE =
        SynchedEntityData.defineId(BansheeEntity.class, EntityDataSerializers.INT);

    private final CreatureBehavior dustBehavior = CreatureBehaviorFactory.create(CreatureKind.BANSHEE);
    private final BansheeRuntime.Counters bansheeCounters = new BansheeRuntime.Counters();
    private final BansheeRuntime.TransientState bansheeTransient = new BansheeRuntime.TransientState();
    private BansheeState bansheeState = BansheeState.empty();

    public BansheeEntity(final EntityType<? extends PathfinderMob> type, final Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 20, true);
        this.xpReward = 3;
        setNoGravity(true);
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.BANSHEE;
    }

    public BansheeState bansheeState() {
        return bansheeState;
    }

    public void setBansheeState(final BansheeState state) {
        bansheeState = state == null ? BansheeState.empty() : state;
    }

    public BansheeRuntime.Counters bansheeCounters() {
        return bansheeCounters;
    }

    public BansheeRuntime.TransientState bansheeTransient() {
        return bansheeTransient;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(10, new LookOnlyRandomLookGoal(this));
    }

    /**
     * Vanilla {@link RandomLookAroundGoal} declares MOVE and LOOK; this variant redeclares LOOK
     * only so the goal selector can never contest the runtime's movement authority.
     */
    private static final class LookOnlyRandomLookGoal extends RandomLookAroundGoal {
        private LookOnlyRandomLookGoal(final net.minecraft.world.entity.Mob mob) {
            super(mob);
            setFlags(java.util.EnumSet.of(Flag.LOOK));
        }
    }

    public int operationalTargetGoalCount() {
        return targetSelector.getAvailableGoals().size();
    }

    public java.util.List<String> operationalGoalNames() {
        return goalSelector.getAvailableGoals().stream()
            .map(goal -> goal.getGoal().getClass().getSimpleName())
            .toList();
    }

    @Override
    protected PathNavigation createNavigation(final Level level) {
        final FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        return false;
    }

    @Override
    protected void checkFallDamage(
        final double ya,
        final boolean onGround,
        final BlockState onState,
        final BlockPos pos
    ) {
    }

    @Override
    protected void playStepSound(final BlockPos pos, final BlockState blockState) {
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        BansheeRuntime.tick(this, level);
    }

    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        final InteractionResult result = dustBehavior.interact(this, player, hand);
        return result == InteractionResult.PASS ? super.mobInteract(player, hand) : result;
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && amount > 0.0F) {
            BansheeRuntime.onAcceptedDamage(this, level, source);
        }
        return hurt;
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_ACTIVITY, (byte) Mode.VIGIL.ordinal());
        entityData.define(DATA_PULSE_SEQUENCE, 0);
    }

    public Mode presentationActivity() {
        final int stored = entityData.get(DATA_ACTIVITY);
        final Mode[] modes = Mode.values();
        return stored >= 0 && stored < modes.length ? modes[stored] : Mode.VIGIL;
    }

    public int presentationPulseSequence() {
        return entityData.get(DATA_PULSE_SEQUENCE);
    }

    public void syncPresentation(final Mode mode) {
        final byte encoded = (byte) mode.ordinal();
        if (entityData.get(DATA_ACTIVITY) != encoded) {
            entityData.set(DATA_ACTIVITY, encoded);
        }
    }

    public void syncPulse() {
        entityData.set(DATA_PULSE_SEQUENCE, entityData.get(DATA_PULSE_SEQUENCE) + 1);
    }

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

    @Override
    protected void populateDefaultEquipmentSlots(
        final net.minecraft.util.RandomSource random,
        final DifficultyInstance difficulty
    ) {
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
        final net.minecraft.world.entity.ai.attributes.AttributeInstance followRange =
            getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.removeModifier(RANDOM_SPAWN_BONUS_ID);
        }
        setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        return result;
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
        output.store(STATE_KEY, CompoundTag.CODEC, bansheeState.write());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        final String dimension = level().dimension().identifier().toString();
        bansheeState = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(tag -> BansheeState.read(tag, dimension))
            .orElse(BansheeState.empty());
        bansheeTransient.resetForLoad();
        normalizeEquipment();
        setNoGravity(true);
    }
}
