package com.kadamitas.warlockery.entity;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The dedicated F31 body: a termed single-host tenant.
 *
 * <p>It keeps every public surface exactly as shipped, including the {@code warlockery:parasytic_louse}
 * id, the displayed name, the hostile monster category with Peaceful exclusion, the absence of any
 * natural spawn, the 0.45 by 0.35 dimensions, the {@code ARTHROPOD} archetype with its
 * {@code louse_shell}, {@code feeding_core} and {@code hooked_mandible} model parts, the
 * {@code summon_parasytic_louse} rite, the spawn egg, the loot table, the {@code warlockery:louse}
 * item round trip and the {@code louse_redirecting_armor} tag contract. What it drops is the
 * inherited Zombie goal set, target set and lifecycle, the second unarbitrated navigation writer,
 * and the rangeless recencyless redirect.</p>
 *
 * <p>Deliberately not a {@link ArcaneMob}, not a Zombie and not ageable: as a plain {@link Monster}
 * there is no baby state, no jockey, no equipment randomization, no loot pickup, no door breaking,
 * no villager conversion, no reinforcement call, no sunlight combustion, no Drowned conversion and
 * no underwater timer to inherit in the first place. The attribute bases below are declared
 * explicitly rather than inherited so the values are examined rather than assumed, and they match
 * the Zombie baseline this mob effectively had, exactly as {@link CorpseEntity} already declares it
 * for the other former {@code ArcaneMob} ground kind.</p>
 */
public final class ParasyticLouseEntity extends Monster implements ArcaneCreature {
    public static final double BASE_MAX_HEALTH = 20.0D;
    public static final double BASE_FOLLOW_RANGE = 35.0D;
    public static final double BASE_MOVEMENT_SPEED = 0.23D;
    public static final double BASE_ATTACK_DAMAGE = 3.0D;
    public static final double BASE_ARMOR = 2.0D;
    public static final double BASE_REINFORCEMENT_CHANCE = 0.0D;

    static final String STATE_KEY = "WarlockeryParasyticLouseState";
    private static final EntityDataAccessor<Byte> DATA_PRESENTATION_PHASE =
        SynchedEntityData.defineId(ParasyticLouseEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_PRESENTATION_NOURISHMENT =
        SynchedEntityData.defineId(ParasyticLouseEntity.class, EntityDataSerializers.INT);

    private static final Identifier BABY_MODIFIER = Identifier.withDefaultNamespace("baby");
    private static final Identifier ZOMBIE_RANDOM_SPAWN_BONUS =
        Identifier.withDefaultNamespace("zombie_random_spawn_bonus");
    private static final Identifier LEADER_ZOMBIE_BONUS =
        Identifier.withDefaultNamespace("leader_zombie_bonus");
    private static final Identifier REINFORCEMENT_CALLER_CHARGE =
        Identifier.withDefaultNamespace("reinforcement_caller_charge");
    private static final Identifier REINFORCEMENT_CALLEE_CHARGE =
        Identifier.withDefaultNamespace("reinforcement_callee_charge");

    private final ParasyticLouseRuntime.Counters counters = new ParasyticLouseRuntime.Counters();
    private final ParasyticLouseRuntime.Tenancy tenancy = new ParasyticLouseRuntime.Tenancy();
    private ParasyticLouseState state = ParasyticLouseState.empty();

    public ParasyticLouseEntity(final EntityType<? extends Monster> type, final Level level) {
        super(type, level);
        normalizeLifecycle();
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PRESENTATION_PHASE,
            EntityPresentationSync.encode(ParasyticLouseTenancyRules.Phase.FREE));
        builder.define(DATA_PRESENTATION_NOURISHMENT, 0);
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
        return CreatureKind.LOUSE;
    }

    public ParasyticLouseState louseState() {
        return state;
    }

    public void setLouseState(final ParasyticLouseState updated) {
        state = updated == null ? ParasyticLouseState.empty() : updated;
        syncPresentationFromRuntime();
    }

    public ParasyticLouseTenancyRules.Phase presentationPhase() {
        return EntityPresentationSync.decode(entityData.get(DATA_PRESENTATION_PHASE),
            ParasyticLouseTenancyRules.Phase.FREE);
    }

    public int presentationNourishment() {
        return entityData.get(DATA_PRESENTATION_NOURISHMENT);
    }

    private void syncPresentationFromRuntime() {
        final byte phase = EntityPresentationSync.encode(tenancy.phase());
        final int nourishment = state.nourishment();
        if (entityData.get(DATA_PRESENTATION_PHASE) != phase) {
            entityData.set(DATA_PRESENTATION_PHASE, phase);
        }
        if (entityData.get(DATA_PRESENTATION_NOURISHMENT) != nourishment) {
            entityData.set(DATA_PRESENTATION_NOURISHMENT, nourishment);
        }
    }

    public ParasyticLouseRuntime.Counters louseCounters() {
        return counters;
    }

    public ParasyticLouseRuntime.Tenancy tenancy() {
        return tenancy;
    }

    /**
     * LOOK only, and the idle glance is stripped of the vanilla MOVE flag claim. Movement authority
     * belongs exclusively to {@link ParasyticLouseRuntime}, and no target goal is ever registered,
     * so no goal can select a host, start an attack or issue a competing path request.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(10, new LookOnlyRandomLookGoal());
    }

    private final class LookOnlyRandomLookGoal extends RandomLookAroundGoal {
        private LookOnlyRandomLookGoal() {
            super(ParasyticLouseEntity.this);
            setFlags(EnumSet.of(Goal.Flag.LOOK));
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
        ParasyticLouseRuntime.tick(this, level);
        syncPresentationFromRuntime();
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        return super.canAttack(target) && ParasyticLouseRuntime.legalHost(this, target);
    }

    /**
     * Snapshots health plus absorption, calls super, and mints an attribution only when super
     * returned true and the total is strictly lower. A truthy call with no effective positive loss
     * mints no attribution and no withdrawal.
     */
    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final float before = getHealth() + getAbsorptionAmount();
        final boolean hurt = super.hurtServer(level, source, amount);
        if (ParasyticLouseTenancyRules.effectiveFeed(hurt, before, getHealth() + getAbsorptionAmount())) {
            ParasyticLouseRuntime.recordAcceptedDamage(this, level, source);
        }
        return hurt;
    }

    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        final InteractionResult result = ParasyticLouseRuntime.interact(this, player, hand);
        return result == InteractionResult.PASS ? super.mobInteract(player, hand) : result;
    }

    /**
     * A household nuisance with a free-hand counter must never be able to block the night. This is a
     * deliberate departure from ordinary {@link Monster} behavior: a louse that both prevented rest
     * and released sleeping hosts would deadlock the player it is attached to.
     */
    @Override
    public boolean isPreventingPlayerRest(final ServerLevel level, final Player player) {
        return false;
    }

    @Override
    public void remove(final RemovalReason reason) {
        // Full teardown on removal for any reason: no final delivery, no sound, no particle, and
        // nothing left written on the former host.
        tenancy.clearAll();
        super.remove(reason);
    }

    // ---------------------------------------------------------------- preserved identity

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SPIDER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(final DamageSource source) {
        return SoundEvents.SPIDER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SPIDER_DEATH;
    }

    @Override
    protected void playStepSound(final BlockPos position, final BlockState blockState) {
        playSound(SoundEvents.SPIDER_STEP, 0.1F, 1.4F);
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
        normalizeLifecycle();
        return result;
    }

    /**
     * Removes the exact legacy lifecycle this kind used to inherit: empties every equipment slot
     * without spawning replacement drops, disables loot pickup permanently, strips the five legacy
     * permanent modifier IDs plus the generic random spawn bonus, restores every declared base
     * including reinforcement chance zero, then clamps current health to the normalized maximum.
     *
     * <p>The generic {@code Mob} random follow-range spawn bonus is removed here rather than only in
     * {@code finalizeSpawn} so that an exact attribute assertion holds on every construction path.</p>
     */
    public void normalizeLifecycle() {
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
        restoreBase(Attributes.SPAWN_REINFORCEMENTS_CHANCE, BASE_REINFORCEMENT_CHANCE);
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
        instance.removeModifier(BABY_MODIFIER);
        instance.removeModifier(RANDOM_SPAWN_BONUS_ID);
        instance.removeModifier(ZOMBIE_RANDOM_SPAWN_BONUS);
        instance.removeModifier(LEADER_ZOMBIE_BONUS);
        instance.removeModifier(REINFORCEMENT_CALLER_CHARGE);
        instance.removeModifier(REINFORCEMENT_CALLEE_CHARGE);
        instance.setBaseValue(base);
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(STATE_KEY, CompoundTag.CODEC, state.write());
    }

    /**
     * Load reconciliation. The durable record parses and clamps independently and already carries
     * its own load cooldown floor; every transient field is cleared, so the phase normalizes to
     * FREE, the residence term is gone and no mark, delivery, feed, sound, particle or route can
     * replay. The three preserved Warlockery persistent key groups, the payload, the owner and the
     * empowerment, are untouched here and are read read-only by the runtime when it needs them.
     */
    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        state = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(ParasyticLouseState::read)
            .orElseGet(() -> ParasyticLouseState.empty().withSeekCooldown(
                ParasyticLouseTenancyRules.seekCooldownOnLoad(0)
            ));
        tenancy.clearAll();
        normalizeLifecycle();
        getNavigation().stop();
        setTarget(null);
        setDeltaMovement(getDeltaMovement().x * 0.0D, getDeltaMovement().y, 0.0D);
        syncPresentationFromRuntime();
    }
}
