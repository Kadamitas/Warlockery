package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.HedgeCroneRules.Mode;
import java.util.EnumSet;
import java.util.List;
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
 * Dedicated F13 Hedge Crone: an independent boundary-anchored encounter built on a ground
 * {@code Monster} shell rather than the vanilla Zombie lifecycle. It installs only a float goal,
 * bounded LOOK-only goals, and one attack-only close-defense goal; every target, destination, and
 * navigation call belongs to {@link HedgeCroneRuntime}. The registered ID, display name,
 * dimensions, category, attributes, XP, loot, model, and texture are untouched.
 *
 * <p>Deliberately not a Circle Mage, a shared practitioner, or a social variant of one: the Crone
 * has no owner, no roster, no aura, no peer report, and no conclave.</p>
 */
public final class HedgeCroneEntity extends Monster implements ArcaneCreature {
    /** The exact registry-owned baseline. Declared here only so normalization can restore it. */
    public static final double BASE_MAX_HEALTH = 60.0D;
    public static final double BASE_ATTACK_DAMAGE = 9.0D;
    public static final double BASE_ARMOR = 6.0D;
    public static final double BASE_FOLLOW_RANGE = 35.0D;
    public static final double BASE_MOVEMENT_SPEED = 0.23D;

    static final String STATE_KEY = "WarlockeryHedgeCroneState";
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
        SynchedEntityData.defineId(HedgeCroneEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_WARD_PREPARED =
        SynchedEntityData.defineId(HedgeCroneEntity.class, EntityDataSerializers.BOOLEAN);

    private final HedgeCroneRuntime.Counters croneCounters = new HedgeCroneRuntime.Counters();
    private final HedgeCroneRuntime.TransientState croneTransient = new HedgeCroneRuntime.TransientState();
    private HedgeCroneState croneState = HedgeCroneState.empty();

    public HedgeCroneEntity(final EntityType<? extends Monster> type, final Level level) {
        super(type, level);
        this.xpReward = 10;
        normalizeLifecycle();
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.HEDGE_CRONE;
    }

    public HedgeCroneState croneState() {
        return croneState;
    }

    public void setCroneState(final HedgeCroneState state) {
        croneState = state == null ? HedgeCroneState.empty() : state;
    }

    public HedgeCroneRuntime.Counters croneCounters() {
        return croneCounters;
    }

    public HedgeCroneRuntime.TransientState croneTransient() {
        return croneTransient;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new CloseDefenseGoal());
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(10, new LookOnlyRandomLookGoal(this));
    }

    /**
     * Attack-only close defense. It declares no goal flag at all, so it can never contest the
     * runtime's single MOVE and LOOK authority, and it never creates, moves, or stops a path: it
     * only lands the retained primary hit once the runtime has already placed a legal target
     * inside actual melee reach with line of sight.
     */
    private final class CloseDefenseGoal extends Goal {
        private int cooldownTicks;

        private CloseDefenseGoal() {
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
                croneCounters.closeDefenseHits++;
            }
        }
    }

    static final int ATTACK_COOLDOWN_TICKS = 20;

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
        HedgeCroneRuntime.tick(this, level);
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        return super.canAttack(target) && HedgeCroneRuntime.legalTarget(this, target);
    }

    /**
     * Enforces the immutable action target. During a warning or hex windup no closer candidate and
     * no external {@code setTarget} call may replace the frozen identity; an explicit clear is
     * always allowed because cancellation is the runtime's own decision.
     */
    @Override
    public void setTarget(final @Nullable LivingEntity target) {
        if (target != null && !HedgeCroneRules.mayRetarget(
            croneState.action().action(),
            croneState.action().targetId().orElse(null),
            target.getUUID()
        )) {
            return;
        }
        super.setTarget(target);
    }

    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        // The Hedge Crone has no recruitment, binding, offering, or trade surface at all.
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final float before = getHealth() + getAbsorptionAmount();
        final boolean hurt = super.hurtServer(level, source, amount);
        final float accepted = before - (getHealth() + getAbsorptionAmount());
        if (hurt && accepted > 0.0F) {
            HedgeCroneRuntime.onAcceptedDamage(this, level, source, accepted);
        }
        return hurt;
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTIVITY, (byte) Mode.IDLE.ordinal());
        builder.define(DATA_WARD_PREPARED, false);
    }

    public Mode presentationActivity() {
        final int stored = entityData.get(DATA_ACTIVITY);
        final Mode[] modes = Mode.values();
        return stored >= 0 && stored < modes.length ? modes[stored] : Mode.IDLE;
    }

    public boolean presentationWardPrepared() {
        return entityData.get(DATA_WARD_PREPARED);
    }

    /** Server-derived presentation only. No client fact is ever accepted. */
    public void syncPresentation(final Mode mode, final boolean wardPrepared) {
        final byte encoded = (byte) mode.ordinal();
        if (entityData.get(DATA_ACTIVITY) != encoded) {
            entityData.set(DATA_ACTIVITY, encoded);
        }
        if (entityData.get(DATA_WARD_PREPARED) != wardPrepared) {
            entityData.set(DATA_WARD_PREPARED, wardPrepared);
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
     * Removes the legacy Zombie lifecycle: empties every equipment slot without a replacement
     * drop, strips the baby and random/leader/reinforcement spawn modifiers including the generic
     * {@code Mob} follow-range bonus so exact attribute assertions stay deterministic, restores the
     * declared registry bases, and clamps health to the normalized maximum.
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
        output.store(STATE_KEY, CompoundTag.CODEC, croneState.write());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        final String dimension = level().dimension().identifier().toString();
        croneState = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(tag -> HedgeCroneState.read(tag, dimension))
            .orElse(HedgeCroneState.empty());
        croneTransient.resetForLoad();
        normalizeLifecycle();
        setTarget(null);
        getNavigation().stop();
        syncPresentation(croneState.mode(), croneState.work().wardPrepared());
    }
}
