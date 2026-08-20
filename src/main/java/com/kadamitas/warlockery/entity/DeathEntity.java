package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.DeathRules.Phase;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * Dedicated F18 Death: a ground {@code Monster} shell with no Zombie lifecycle, LOOK-only goals,
 * an empty target selector, and one server controller ({@link DeathRuntime}) that owns every
 * appointment decision and navigation call. Death is a mob with an appointment-keeping identity,
 * never a death handler: it changes no player death mechanic, drop, or respawn, listens to no
 * global death event, and keeps no victim history. Registry ID, displayed name, category,
 * dimensions, attributes, XP, loot, spawn egg, ritual summon and redirection are unchanged.
 */
public final class DeathEntity extends Monster implements ArcaneCreature {
    public static final double BASE_MAX_HEALTH = DeathCombatRules.MAX_HEALTH;
    public static final double BASE_FOLLOW_RANGE = 35.0D;
    public static final double BASE_MOVEMENT_SPEED = 0.23D;
    public static final double BASE_ATTACK_DAMAGE = 14.0D;
    public static final double BASE_ARMOR = 12.0D;

    static final String STATE_KEY = "WarlockeryDeathState";
    private static final EntityDataAccessor<Byte> DATA_PHASE =
        SynchedEntityData.defineId(DeathEntity.class, EntityDataSerializers.BYTE);

    private final DeathRuntime.Counters deathCounters = new DeathRuntime.Counters();
    private final DeathRuntime.TransientState deathTransient = new DeathRuntime.TransientState();
    private DeathState deathState = DeathState.empty();

    public DeathEntity(final EntityType<? extends Monster> type, final Level level) {
        super(type, level);
        this.xpReward = 20;
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
            .add(Attributes.FOLLOW_RANGE, BASE_FOLLOW_RANGE)
            .add(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED)
            .add(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE)
            .add(Attributes.ARMOR, BASE_ARMOR);
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.DEATH;
    }

    public DeathState deathState() {
        return deathState;
    }

    public void setDeathState(final DeathState state) {
        deathState = state == null ? DeathState.empty() : state;
    }

    public DeathRuntime.Counters deathCounters() {
        return deathCounters;
    }

    public DeathRuntime.TransientState deathTransient() {
        return deathTransient;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(10, new LookOnlyRandomLookGoal(this));
    }

    /**
     * Vanilla {@link RandomLookAroundGoal} declares MOVE and LOOK; this variant redeclares LOOK
     * only so the goal selector can never contest the runtime's sole movement authority.
     */
    private static final class LookOnlyRandomLookGoal extends RandomLookAroundGoal {
        private LookOnlyRandomLookGoal(final net.minecraft.world.entity.Mob mob) {
            super(mob);
            setFlags(EnumSet.of(Goal.Flag.LOOK));
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
        DeathRuntime.tick(this, level);
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        return super.canAttack(target) && DeathRuntime.legalTarget(this, target);
    }

    /**
     * The established primary melee path. The preserved fifteen percent maximum-health shape is a
     * transient attack-attribute bonus around the ordinary vanilla hit, so armor, effects,
     * invulnerability, shields, death-protection mechanics, event cancellation, and attribution
     * all stay exactly where vanilla and the existing Forge event order put them.
     */
    @Override
    public boolean doHurtTarget(final ServerLevel level, final Entity target) {
        final float bonus = target instanceof LivingEntity living
            ? DeathRules.primaryMeleeBonus(living.getMaxHealth(), getAttributeValue(Attributes.ATTACK_DAMAGE))
            : 0.0F;
        final boolean hurt = PrimaryAttackModifier.withDamageBonus(
            this,
            bonus,
            () -> super.doHurtTarget(level, target)
        );
        if (hurt && target instanceof LivingEntity living) {
            living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.WITHER,
                DeathRules.WITHER_DURATION_TICKS,
                DeathRules.WITHER_AMPLIFIER
            ));
        }
        return hurt;
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && amount > 0.0F) {
            DeathRuntime.onAcceptedDamage(this, level, source);
        }
        return hurt;
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_PHASE, (byte) Phase.QUIESCENT.ordinal());
    }

    public Phase presentationPhase() {
        final int stored = entityData.get(DATA_PHASE);
        final Phase[] phases = Phase.values();
        return stored >= 0 && stored < phases.length ? phases[stored] : Phase.QUIESCENT;
    }

    public void syncPresentation(final Phase phase) {
        final byte encoded = (byte) phase.ordinal();
        if (entityData.get(DATA_PHASE) != encoded) {
            entityData.set(DATA_PHASE, encoded);
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
        normalizeIdentity();
        return result;
    }

    /**
     * Restores the exact registered bases and empties every equipment slot without replacement
     * drops. The generic Mob random follow-range spawn bonus is removed so the declared
     * attribute baseline stays exact and deterministic.
     */
    public void normalizeIdentity() {
        setCanPickUpLoot(false);
        for (final EquipmentSlot slot : EquipmentSlot.values()) {
            if (!getItemBySlot(slot).isEmpty()) {
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        restoreBase(Attributes.MAX_HEALTH, BASE_MAX_HEALTH);
        restoreBase(Attributes.FOLLOW_RANGE, BASE_FOLLOW_RANGE);
        restoreBase(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED);
        restoreBase(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE);
        restoreBase(Attributes.ARMOR, BASE_ARMOR);
        setHealth(Math.min(getHealth(), getMaxHealth()));
    }

    private void restoreBase(
        final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
        final double base
    ) {
        final AttributeInstance instance = getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(RANDOM_SPAWN_BONUS_ID);
        instance.setBaseValue(base);
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(STATE_KEY, CompoundTag.CODEC, deathState.write());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        final String dimension = level().dimension().identifier().toString();
        deathState = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(tag -> DeathState.read(tag, dimension))
            .orElse(DeathState.empty());
        deathTransient.resetForLoad();
        normalizeIdentity();
    }

    /**
     * Terminal cleanup only. An unload, a player-scoped unload, or a dimension change must pause
     * the appointment rather than end it, which is the entire reason every duration in
     * {@link DeathState} is a remaining loaded-tick count rather than a world deadline. Execution
     * scratch is rebuilt on every load, so dropping it is safe for any removal reason.
     */
    @Override
    public void remove(final RemovalReason reason) {
        if (reason.shouldDestroy()) {
            DeathRuntime.onRemoved(this);
        } else {
            deathTransient.resetForLoad();
        }
        super.remove(reason);
    }
}
