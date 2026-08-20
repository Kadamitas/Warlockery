package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.CircleMageRules.Mode;
import com.kadamitas.warlockery.entity.CircleMageRules.RecruitmentResult;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Dedicated F13 Circle Mage: an approachable, recruitable practitioner on a ground {@code Monster}
 * shell with no Zombie lifecycle. It keeps the exact familiar/offering/six-member recruitment
 * contract, follows and regenerates one owner, defends through a telegraphed attributed bolt, and
 * performs bounded solo or three-member study conclaves, all owned by {@link CircleMageRuntime}.
 *
 * <p>Deliberately not a Hedge Crone or a variant of one: the Mage has no anchor boundary, no
 * warning escalation, no ward, and no contextual hex.</p>
 */
public final class CircleMageEntity extends Monster implements ArcaneCreature {
    /** The exact registry-owned baseline. Declared here only so normalization can restore it. */
    public static final double BASE_MAX_HEALTH = 20.0D;
    public static final double BASE_ATTACK_DAMAGE = 3.0D;
    public static final double BASE_ARMOR = 2.0D;
    public static final double BASE_FOLLOW_RANGE = 35.0D;
    public static final double BASE_MOVEMENT_SPEED = 0.23D;

    static final String STATE_KEY = "WarlockeryCircleMageState";
    static final int ATTACK_COOLDOWN_TICKS = 20;
    private static final Identifier BABY_MODIFIER = Identifier.withDefaultNamespace("baby");
    private static final Identifier ZOMBIE_RANDOM_SPAWN_BONUS =
        Identifier.withDefaultNamespace("zombie_random_spawn_bonus");
    private static final Identifier LEADER_ZOMBIE_BONUS =
        Identifier.withDefaultNamespace("leader_zombie_bonus");
    private static final Identifier REINFORCEMENT_CALLER_CHARGE =
        Identifier.withDefaultNamespace("reinforcement_caller_charge");
    private static final Identifier REINFORCEMENT_CALLEE_CHARGE =
        Identifier.withDefaultNamespace("reinforcement_callee_charge");

    private static final EntityDataAccessor<Byte> DATA_ACTIVITY =
        SynchedEntityData.defineId(CircleMageEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_FOCUS_PREPARED =
        SynchedEntityData.defineId(CircleMageEntity.class, EntityDataSerializers.BOOLEAN);

    private final CreatureBehavior covenBehavior = CreatureBehaviorFactory.create(CreatureKind.CIRCLE_MAGE);
    private final CircleMageRuntime.Counters mageCounters = new CircleMageRuntime.Counters();
    private final CircleMageRuntime.TransientState mageTransient = new CircleMageRuntime.TransientState();
    private CircleMageState mageState = CircleMageState.empty();

    public CircleMageEntity(final EntityType<? extends Monster> type, final Level level) {
        super(type, level);
        this.xpReward = 5;
        normalizeLifecycle();
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.CIRCLE_MAGE;
    }

    public CircleMageState mageState() {
        return mageState;
    }

    public void setMageState(final CircleMageState state) {
        mageState = state == null ? CircleMageState.empty() : state;
    }

    public CircleMageRuntime.Counters mageCounters() {
        return mageCounters;
    }

    public CircleMageRuntime.TransientState mageTransient() {
        return mageTransient;
    }

    public Optional<UUID> warlockeryOwner() {
        return CreatureBehaviorState.owner(this);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new EmergencyDefenceGoal());
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(10, new LookOnlyRandomLookGoal(this));
    }

    /**
     * Attack-only emergency execution with no goal flag and no path ownership: it lands the
     * retained primary hit only once {@link CircleMageRuntime} has already placed a legal target
     * inside actual melee reach with line of sight.
     */
    private final class EmergencyDefenceGoal extends Goal {
        private int cooldownTicks;

        private EmergencyDefenceGoal() {
            setFlags(EnumSet.noneOf(Flag.class));
        }

        @Override
        public boolean canUse() {
            final LivingEntity target = getTarget();
            return target != null && target.isAlive() && isWithinMeleeAttackRange(target)
                && getSensing().hasLineOfSight(target);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            cooldownTicks = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            cooldownTicks = Math.max(0, cooldownTicks - 1);
            final LivingEntity target = getTarget();
            if (cooldownTicks > 0 || target == null || !(level() instanceof ServerLevel server)) {
                return;
            }
            cooldownTicks = ATTACK_COOLDOWN_TICKS;
            swing(InteractionHand.MAIN_HAND);
            if (doHurtTarget(server, target)) {
                mageCounters.emergencyHits++;
            }
        }
    }

    /** Redeclares LOOK only so the goal selector can never claim MOVE. */
    private static final class LookOnlyRandomLookGoal extends RandomLookAroundGoal {
        private LookOnlyRandomLookGoal(final net.minecraft.world.entity.Mob mob) {
            super(mob);
            setFlags(EnumSet.of(Flag.LOOK));
        }
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
        CircleMageRuntime.tick(this, level);
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        return super.canAttack(target)
            && covenBehavior.canAttack(this, target)
            && CircleMageRuntime.legalTarget(this, target);
    }

    /**
     * Enforces the immutable action target. During a bolt windup no closer candidate and no
     * external {@code setTarget} call may replace the frozen identity; an explicit clear is always
     * allowed because cancellation is the runtime's own decision.
     */
    @Override
    public void setTarget(final @Nullable LivingEntity target) {
        if (target != null && !CircleMageRules.mayRetarget(
            mageState.action().action(),
            mageState.action().targetId().orElse(null),
            target.getUUID()
        )) {
            return;
        }
        super.setTarget(target);
    }

    /**
     * Recruitment keeps the exact existing interaction surface. The dedicated rules decide the
     * outcome first so the audited defect is fixed at the source: a same-owner repeat is idempotent
     * success that consumes nothing and never reaches the shared binding path, and a conflicting
     * owner is rejected without spending an offering. Every other branch delegates unchanged.
     */
    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        if (!(level() instanceof ServerLevel server)) {
            return super.mobInteract(player, hand);
        }
        final RecruitmentResult decision = CircleMageRuntime.recruitmentDecision(this, server, player, hand);
        if (decision == RecruitmentResult.ALREADY_BOUND_TO_PLAYER) {
            return CircleMageRuntime.acknowledgeExistingBinding(this, server, player);
        }
        if (decision == RecruitmentResult.BOUND_ELSEWHERE) {
            // Short circuit before the shared binding path so a conflicting owner can neither
            // steal the Mage nor spend an offering trying.
            return CircleMageRuntime.rejectConflictingOwner(this, server, player);
        }
        final InteractionResult result = covenBehavior.interact(this, player, hand);
        // Only a genuine new admission may consume the offering; every other outcome keeps the
        // exact existing shared feedback and consumes nothing.
        if (result == InteractionResult.SUCCESS && decision.consumesOffering()) {
            CircleMageRuntime.onRecruited(this, server);
        }
        return result == InteractionResult.PASS ? super.mobInteract(player, hand) : result;
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final float before = getHealth() + getAbsorptionAmount();
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && before - (getHealth() + getAbsorptionAmount()) > 0.0F) {
            CircleMageRuntime.onAcceptedDamage(this, level, source);
        }
        return hurt;
    }

    /** Cancels bolt, study, conclave, report, and destination state after an explicit Seer call. */
    public void onSeerRecall(final ServerLevel level, final BlockPos circleCenter) {
        CircleMageRuntime.onSeerRecall(this, level, circleCenter);
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTIVITY, (byte) Mode.IDLE.ordinal());
        builder.define(DATA_FOCUS_PREPARED, false);
    }

    public Mode presentationActivity() {
        final int stored = entityData.get(DATA_ACTIVITY);
        final Mode[] modes = Mode.values();
        return stored >= 0 && stored < modes.length ? modes[stored] : Mode.IDLE;
    }

    public boolean presentationFocusPrepared() {
        return entityData.get(DATA_FOCUS_PREPARED);
    }

    public void syncPresentation(final Mode mode, final boolean focusPrepared) {
        final byte encoded = (byte) mode.ordinal();
        if (entityData.get(DATA_ACTIVITY) != encoded) {
            entityData.set(DATA_ACTIVITY, encoded);
        }
        if (entityData.get(DATA_FOCUS_PREPARED) != focusPrepared) {
            entityData.set(DATA_FOCUS_PREPARED, focusPrepared);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(final DamageSource source) {
        return SoundEvents.ZOMBIE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_DEATH;
    }

    @Override
    protected void playStepSound(final BlockPos position, final BlockState state) {
        playSound(SoundEvents.ZOMBIE_STEP, 0.15F, 1.0F);
    }

    @Override
    protected void populateDefaultEquipmentSlots(
        final RandomSource random,
        final DifficultyInstance difficulty
    ) {
        normalizeLifecycle();
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level,
        final DifficultyInstance difficulty,
        final EntitySpawnReason reason,
        final @Nullable SpawnGroupData groupData
    ) {
        final SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);
        normalizeLifecycle();
        return result;
    }

    /**
     * Removes the legacy Zombie lifecycle and the audited route-dependent finalization difference:
     * ritual creation, structure gathering, and spawn eggs now all produce the identical
     * empty-handed Mage with the declared registry bases and no arbitrary loot pickup.
     */
    public void normalizeLifecycle() {
        setCanPickUpLoot(false);
        for (final EquipmentSlot slot : EquipmentSlot.values()) {
            if (!getItemBySlot(slot).isEmpty()) {
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        restoreBase(Attributes.MAX_HEALTH, BASE_MAX_HEALTH);
        restoreBase(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE);
        restoreBase(Attributes.ARMOR, BASE_ARMOR);
        restoreBase(Attributes.FOLLOW_RANGE, BASE_FOLLOW_RANGE);
        restoreBase(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED);
        restoreBase(Attributes.KNOCKBACK_RESISTANCE, null);
        setHealth(Math.min(getHealth(), getMaxHealth()));
    }

    private void restoreBase(
        final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
        final @Nullable Double base
    ) {
        final AttributeInstance instance = getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(BABY_MODIFIER);
        instance.removeModifier(RANDOM_SPAWN_BONUS_ID);
        instance.removeModifier(ZOMBIE_RANDOM_SPAWN_BONUS);
        instance.removeModifier(LEADER_ZOMBIE_BONUS);
        instance.removeModifier(REINFORCEMENT_CALLER_CHARGE);
        instance.removeModifier(REINFORCEMENT_CALLEE_CHARGE);
        if (base != null) {
            instance.setBaseValue(base);
        }
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(STATE_KEY, CompoundTag.CODEC, mageState.write());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        final String dimension = level().dimension().identifier().toString();
        mageState = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(tag -> CircleMageState.read(tag, dimension))
            .orElse(CircleMageState.empty());
        mageTransient.resetForLoad();
        normalizeLifecycle();
        setTarget(null);
        getNavigation().stop();
        syncPresentation(mageState.mode(), mageState.study().focusPrepared());
    }
}
