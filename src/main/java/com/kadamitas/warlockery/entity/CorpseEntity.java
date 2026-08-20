package com.kadamitas.warlockery.entity;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Dedicated F17 Necromantic-Cohesion Body: a ground {@code Monster} shell with no
 * Zombie lifecycle, LOOK-only goals, an empty target selector, and one server
 * controller ({@link CorpseRuntime}) that owns every F17 decision and navigation call.
 */
public final class CorpseEntity extends Monster implements ArcaneCreature {
    public static final double BASE_MAX_HEALTH = 20.0D;
    public static final double BASE_FOLLOW_RANGE = 35.0D;
    public static final double BASE_MOVEMENT_SPEED = 0.23D;
    public static final double BASE_ATTACK_DAMAGE = 3.0D;
    public static final double BASE_ARMOR = 2.0D;
    public static final double BASE_REINFORCEMENT_CHANCE = 0.0D;

    private static final String STATE_KEY = "WarlockeryCorpseState";
    private static final String LEGACY_AMBIENT_COOLDOWN_KEY = "WarlockeryAmbientCooldownGRAVE_SCAVENGE";
    private static final Identifier BABY_MODIFIER = Identifier.withDefaultNamespace("baby");
    private static final Identifier ZOMBIE_RANDOM_SPAWN_BONUS = Identifier.withDefaultNamespace("zombie_random_spawn_bonus");
    private static final Identifier LEADER_ZOMBIE_BONUS = Identifier.withDefaultNamespace("leader_zombie_bonus");
    private static final Identifier REINFORCEMENT_CALLER_CHARGE = Identifier.withDefaultNamespace("reinforcement_caller_charge");
    private static final Identifier REINFORCEMENT_CALLEE_CHARGE = Identifier.withDefaultNamespace("reinforcement_callee_charge");
    private static final EntityDataAccessor<Boolean> DATA_DORMANT = SynchedEntityData.defineId(
        CorpseEntity.class,
        EntityDataSerializers.BOOLEAN
    );

    private final CorpseRuntime.Transient transientFacts = new CorpseRuntime.Transient();
    private final CorpseRuntime.Counters corpseCounters = new CorpseRuntime.Counters();
    private CorpseState corpseState = CorpseState.fresh();

    public CorpseEntity(final EntityType<? extends Monster> type, final Level level) {
        super(type, level);
        normalizeLifecycle();
    }

    public static Set<String> legacyModifierIds() {
        return CorpseRules.LEGACY_MODIFIER_IDS;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
            .add(Attributes.FOLLOW_RANGE, BASE_FOLLOW_RANGE)
            .add(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED)
            .add(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE)
            .add(Attributes.ARMOR, BASE_ARMOR)
            .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, BASE_REINFORCEMENT_CHANCE);
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.CORPSE;
    }

    public CorpseState corpseState() {
        return corpseState;
    }

    public void setCorpseState(final CorpseState state) {
        corpseState = state == null ? CorpseState.fresh() : state;
    }

    public CorpseRuntime.Transient transientFacts() {
        return transientFacts;
    }

    public CorpseRuntime.Counters corpseCounters() {
        return corpseCounters;
    }

    public boolean isDormant() {
        return entityData.get(DATA_DORMANT);
    }

    public void synchronizeDormant(final boolean dormant) {
        if (entityData.get(DATA_DORMANT) != dormant) {
            entityData.set(DATA_DORMANT, dormant);
        }
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DORMANT, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(9, new DormantAwareLookAtPlayerGoal());
        goalSelector.addGoal(10, new DormantAwareRandomLookGoal());
    }

    /** LOOK-only player glance; completely still while dormant. */
    private final class DormantAwareLookAtPlayerGoal extends LookAtPlayerGoal {
        private DormantAwareLookAtPlayerGoal() {
            super(CorpseEntity.this, Player.class, 8.0F);
        }

        @Override
        public boolean canUse() {
            return !isDormant() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !isDormant() && super.canContinueToUse();
        }
    }

    /** Idle glance stripped of the vanilla MOVE flag claim; completely still while dormant. */
    private final class DormantAwareRandomLookGoal extends RandomLookAroundGoal {
        private DormantAwareRandomLookGoal() {
            super(CorpseEntity.this);
            setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !isDormant() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !isDormant() && super.canContinueToUse();
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
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        CorpseRuntime.tick(this, level);
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        return super.canAttack(target) && CorpseRuntime.legalTarget(this, target);
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final float before = getHealth() + getAbsorptionAmount();
        final boolean hurt = super.hurtServer(level, source, amount);
        if (CorpseRules.effectiveDamage(hurt, before, getHealth() + getAbsorptionAmount())) {
            CorpseRuntime.recordAcceptedDamage(this, level, source);
        }
        return hurt;
    }

    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        final InteractionResult result = CorpseRuntime.manualFeed(this, player, hand);
        return result == InteractionResult.PASS ? super.mobInteract(player, hand) : result;
    }

    @Override
    public boolean isPreventingPlayerRest(final ServerLevel level, final Player player) {
        return !isDormant() && super.isPreventingPlayerRest(level, player);
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
     * Removes the exact legacy Zombie lifecycle: empties every equipment slot without
     * replacement drops, strips the six exact legacy modifier IDs, restores the declared
     * bases including reinforcement chance zero, then clamps health to the normalized maximum.
     */
    public void normalizeLifecycle() {
        setCanPickUpLoot(false);
        for (final EquipmentSlot slot : EquipmentSlot.values()) {
            if (!getItemBySlot(slot).isEmpty()) {
                setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
        restoreBase(Attributes.MAX_HEALTH, BASE_MAX_HEALTH);
        restoreBase(Attributes.FOLLOW_RANGE, BASE_FOLLOW_RANGE);
        restoreBase(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED);
        restoreBase(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE);
        restoreBase(Attributes.ARMOR, BASE_ARMOR);
        restoreBase(Attributes.SPAWN_REINFORCEMENTS_CHANCE, BASE_REINFORCEMENT_CHANCE);
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
        output.store(STATE_KEY, CompoundTag.CODEC, corpseState.write());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        corpseState = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(CorpseState::read)
            .orElseGet(this::migrateLegacyState);
        normalizeLifecycle();
        transientFacts.clearAll();
        synchronizeDormant(corpseState.dormant());
    }

    private CorpseState migrateLegacyState() {
        final CorpseState fresh = CorpseState.fresh();
        if (!getPersistentData().contains(LEGACY_AMBIENT_COOLDOWN_KEY)) {
            return fresh;
        }
        final long oldExpiry = getPersistentData().getLongOr(LEGACY_AMBIENT_COOLDOWN_KEY, 0L);
        getPersistentData().remove(LEGACY_AMBIENT_COOLDOWN_KEY);
        return fresh.withCooldown(CorpseRules.migrateLegacyCooldown(oldExpiry, level().getGameTime()));
    }
}
