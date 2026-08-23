package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.Intent;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.PhaseState;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.Rank;
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
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public final class InfernalHierarchyEntity extends ArcaneMob {
    public static final String STATE_KEY = "WarlockeryInfernalHierarchy";
    public static final String LEGACY_PHASE_KEY = "WarlockeryAbyssalTormentPhase";
    private static final EntityDataAccessor<Byte> DATA_PRESENTATION_INTENT =
        SynchedEntityData.defineId(InfernalHierarchyEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_PRESENTATION_PHASE =
        SynchedEntityData.defineId(InfernalHierarchyEntity.class, EntityDataSerializers.BYTE);

    private final Rank rank;
    private final InfernalHierarchyRuntime.Counters hierarchyCounters = new InfernalHierarchyRuntime.Counters();
    private InfernalHierarchyState hierarchyState;

    public InfernalHierarchyEntity(final EntityType<? extends Zombie> type, final Level level, final CreatureKind kind) {
        super(type, level, kind);
        rank = InfernalHierarchyRules.rankOf(kind).orElseThrow(() ->
            new IllegalArgumentException("Not an infernal hierarchy kind: " + kind));
        hierarchyState = InfernalHierarchyState.empty(rank, getUUID(), level.getGameTime());
        goalSelector.removeAllGoals(goal -> true);
        targetSelector.removeAllGoals(goal -> true);
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        normalizeHierarchyIdentity();
    }

    public Rank hierarchyRank() {
        return rank;
    }

    public InfernalHierarchyState hierarchyState() {
        return hierarchyState;
    }

    public void setHierarchyState(final InfernalHierarchyState state) {
        hierarchyState = state == null
            ? InfernalHierarchyState.empty(rank, getUUID(), level().getGameTime())
            : state;
        syncPresentationFromRuntime();
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PRESENTATION_INTENT, EntityPresentationSync.encode(Intent.IDLE));
        builder.define(DATA_PRESENTATION_PHASE, EntityPresentationSync.encode(PhaseState.NONE));
    }

    public Intent presentationIntent() {
        return EntityPresentationSync.decode(entityData.get(DATA_PRESENTATION_INTENT), Intent.IDLE);
    }

    public PhaseState presentationPhaseState() {
        return EntityPresentationSync.decode(entityData.get(DATA_PRESENTATION_PHASE), PhaseState.NONE);
    }

    private void syncPresentationFromRuntime() {
        final byte intent = EntityPresentationSync.encode(hierarchyState.intent());
        final byte phase = EntityPresentationSync.encode(hierarchyState.phaseState());
        if (entityData.get(DATA_PRESENTATION_INTENT) != intent) {
            entityData.set(DATA_PRESENTATION_INTENT, intent);
        }
        if (entityData.get(DATA_PRESENTATION_PHASE) != phase) {
            entityData.set(DATA_PRESENTATION_PHASE, phase);
        }
    }

    public InfernalHierarchyRuntime.Counters hierarchyCounters() {
        return hierarchyCounters;
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
    protected void tickSpecializedActivity(final ServerLevel level) {
        InfernalHierarchyRuntime.tick(this, level);
        syncPresentationFromRuntime();
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        return super.canAttack(target) && InfernalHierarchyRuntime.eligibleTarget(this, target);
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            InfernalHierarchyRuntime.recordDirectAttack(this, level, source, amount);
        }
        return hurt;
    }

    @Override
    public boolean doHurtTarget(final ServerLevel level, final net.minecraft.world.entity.Entity target) {
        final boolean hurt = super.doHurtTarget(level, target);
        if (hurt) {
            InfernalHierarchyRuntime.recordSuccessfulMelee(this, level);
        }
        return hurt;
    }

    @Override
    public void die(final DamageSource source) {
        final boolean announce = !isRemoved() && level() instanceof ServerLevel;
        super.die(source);
        if (announce && level() instanceof ServerLevel level) {
            InfernalHierarchyRuntime.recordAllyLoss(this, level);
            InfernalHierarchyRuntime.dissolveCommandOnLeaderDeath(this, level);
        }
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public boolean isUnderWaterConverting() {
        return false;
    }

    @Override
    protected void doUnderWaterConversion(final ServerLevel level) {
    }

    @Override
    public boolean convertVillagerToZombieVillager(final ServerLevel level, final Villager villager) {
        return false;
    }

    @Override
    protected void populateDefaultEquipmentSlots(final RandomSource random, final DifficultyInstance difficulty) {
    }

    @Override
    protected void randomizeReinforcementsChance() {
        final var reinforcements = getAttribute(
            net.minecraft.world.entity.ai.attributes.Attributes.SPAWN_REINFORCEMENTS_CHANCE
        );
        if (reinforcements != null) {
            reinforcements.setBaseValue(0.0D);
        }
    }

    @Override
    protected void handleAttributes(final float difficultyModifier, final EntitySpawnReason spawnReason) {
        randomizeReinforcementsChance();
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level,
        final DifficultyInstance difficulty,
        final EntitySpawnReason reason,
        final @Nullable SpawnGroupData groupData
    ) {
        final LivingEntity authoredTarget = getTargetUnchecked();
        final boolean authoredPersistence = isPersistenceRequired();
        final SpawnGroupData sanitized = groupData != null
            ? groupData
            : new Zombie.ZombieGroupData(false, false);
        final SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, sanitized);
        normalizeHierarchyIdentity();
        if (authoredTarget != null) setTarget(authoredTarget);
        if (authoredPersistence) setPersistenceRequired();
        return result;
    }

    @Override
    protected @Nullable LivingEntity asValidTarget(final @Nullable LivingEntity target) {
        return target instanceof Player player && (player.isCreative() || player.isSpectator())
            ? null
            : target;
    }

    @Override
    public @Nullable LivingEntity getTarget() {
        final LivingEntity target = getTargetUnchecked();
        return target != null && canAttack(target) ? target : null;
    }

    private void normalizeHierarchyIdentity() {
        setBaby(false);
        setCanBreakDoors(false);
        setCanPickUpLoot(false);
        randomizeReinforcementsChance();
        for (final EquipmentSlot slot : EquipmentSlot.values()) {
            if (!getItemBySlot(slot).isEmpty()) {
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        if (isPassenger()) {
            stopRiding();
        }
        setInWaterTime(-1);
        setConversionTime(-1);
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(STATE_KEY, CompoundTag.CODEC, hierarchyState.write());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        final boolean legacyPhaseTriggered = getPersistentData().getBooleanOr(LEGACY_PHASE_KEY, false);
        hierarchyState = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(tag -> InfernalHierarchyState.read(tag, rank, getUUID(), level().getGameTime(), legacyPhaseTriggered))
            .orElseGet(() -> InfernalHierarchyState.empty(rank, getUUID(), level().getGameTime())
                .withPhase(
                    legacyPhaseTriggered
                        ? InfernalHierarchyRules.PhaseState.DONE
                        : InfernalHierarchyRules.PhaseState.NONE,
                    legacyPhaseTriggered,
                    0L
                ));
        if (hierarchyState.phaseCompleted()) {
            getPersistentData().putBoolean(LEGACY_PHASE_KEY, true);
        }
        normalizeHierarchyIdentity();
        syncPresentationFromRuntime();
    }
}
