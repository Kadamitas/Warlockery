package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.HellhoundLifeRules.PackOrigin;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Dedicated final adapter for only {@code warlockery:hellhound}. It clears inherited Zombie
 * selectors, installs float/look plus controller-gated attack execution, owns the specialized
 * activity seam, normalizes every acquisition route, disables reinforcement, villager and Drowned
 * conversion, egg breaking, equipment, babies, jockeys, doors, and pickup, records the damage and
 * bite lifecycle, persists semantic state, and performs terminal cleanup.
 */
public final class HellhoundEntity extends ArcaneMob {
    private static final String STATE_KEY = "WarlockeryHellhoundLife";

    private final HellhoundLifeRuntime.Counters lifeCounters = new HellhoundLifeRuntime.Counters();
    private HellhoundLifeState lifeState;
    /**
     * Transient pack-cadence snapshot: never persisted, refreshed only by the bounded 40-tick
     * pack queries so tiny decisions perform no spatial work of their own.
     */
    private int loadedPackCountSnapshot = 1;
    private java.util.Optional<net.minecraft.core.BlockPos> nearestPackmateSnapshot =
        java.util.Optional.empty();

    public HellhoundEntity(final EntityType<? extends Zombie> type, final Level level) {
        super(type, level, CreatureKind.HELLHOUND);
        lifeState = HellhoundLifeState.solitary(getUUID(), PackOrigin.SOLITARY, level.getGameTime());
        goalSelector.removeAllGoals(goal -> true);
        targetSelector.removeAllGoals(goal -> true);
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        normalizeZombieState();
    }

    public HellhoundLifeState lifeState() {
        return lifeState;
    }

    public void setLifeState(final HellhoundLifeState state) {
        lifeState = state == null
            ? HellhoundLifeState.solitary(getUUID(), PackOrigin.SOLITARY, level().getGameTime())
            : state;
    }

    public HellhoundLifeRuntime.Counters lifeCounters() {
        return lifeCounters;
    }

    int loadedPackCountSnapshot() {
        return loadedPackCountSnapshot;
    }

    java.util.Optional<net.minecraft.core.BlockPos> nearestPackmateSnapshot() {
        return nearestPackmateSnapshot;
    }

    void updatePackSnapshot(
        final int loadedCount,
        final java.util.Optional<net.minecraft.core.BlockPos> nearestPackmate
    ) {
        loadedPackCountSnapshot = Math.max(1, loadedCount);
        nearestPackmateSnapshot = nearestPackmate;
    }

    public int operationalTargetGoalCount() {
        return targetSelector.getAvailableGoals().size();
    }

    public List<String> operationalGoalNames() {
        return goalSelector.getAvailableGoals().stream()
            .map(goal -> goal.getGoal().getClass().getSimpleName())
            .toList();
    }

    /** F09 replaces the generic tactical and ambient layers with one dedicated runtime. */
    @Override
    protected void tickSpecializedActivity(final ServerLevel level) {
        HellhoundLifeRuntime.tick(this, level);
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        return super.canAttack(target) && HellhoundLifeRuntime.eligibleTarget(this, target);
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            HellhoundLifeRuntime.recordDirectAttack(this, level, source);
        }
        return hurt;
    }

    /** Hard damage never creates Hellhound reinforcement and villagers are never converted. */
    @Override
    public boolean killedEntity(final ServerLevel level, final LivingEntity killed, final DamageSource source) {
        return true;
    }

    /** Water conversion is disabled while drowning damage and water escape remain ordinary. */
    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public boolean isBaby() {
        return false;
    }

    @Override
    public void setBaby(final boolean baby) {
        super.setBaby(false);
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public boolean wantsToPickUp(final ServerLevel level, final ItemStack stack) {
        return false;
    }

    @Override
    public boolean canBreakDoors() {
        return false;
    }

    @Override
    protected void populateDefaultEquipmentSlots(
        final net.minecraft.util.RandomSource random,
        final DifficultyInstance difficulty
    ) {
        // Hellhounds never spawn with equipment.
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level,
        final DifficultyInstance difficulty,
        final EntitySpawnReason reason,
        final @Nullable SpawnGroupData groupData
    ) {
        super.finalizeSpawn(level, difficulty, reason, groupData);
        normalizeZombieState();
        setDeltaMovement(Vec3.ZERO);
        final long now = level.getLevel().getGameTime();
        final String dimension = level.getLevel().dimension().identifier().toString();
        if (isNaturalGroupSpawn(reason)) {
            final HellhoundPackSpawnData pack = groupData instanceof HellhoundPackSpawnData shared
                ? shared
                : new HellhoundPackSpawnData(UUID.randomUUID(), dimension, blockPosition().asLong());
            if (pack.dimension().equals(dimension)) {
                setLifeState(HellhoundLifeState.naturalGroup(
                    getUUID(),
                    pack.packId(),
                    pack.dimension(),
                    net.minecraft.core.BlockPos.of(pack.packedAnchor()),
                    now
                ));
            } else {
                setLifeState(HellhoundLifeState.solitary(getUUID(), PackOrigin.SOLITARY, now));
            }
            return pack;
        }
        setLifeState(HellhoundLifeState.solitary(getUUID(), PackOrigin.SOLITARY, now));
        return null;
    }

    private static boolean isNaturalGroupSpawn(final EntitySpawnReason reason) {
        return reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION;
    }

    /** Removes every inherited random Zombie variant regardless of the acquisition route. */
    private void normalizeZombieState() {
        setBaby(false);
        setCanPickUpLoot(false);
        setCanBreakDoors(false);
        if (isVehicle()) {
            ejectPassengers();
        }
        for (final EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ANIMAL_ARMOR && !getItemBySlot(slot).isEmpty()) {
                setItemSlot(slot, ItemStack.EMPTY);
            }
            setDropChance(slot, 0.0F);
        }
        clearRandomModifiers(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
        clearRandomModifiers(Attributes.FOLLOW_RANGE);
        clearRandomModifiers(Attributes.KNOCKBACK_RESISTANCE);
        clearRandomModifiers(Attributes.MAX_HEALTH);
        final AttributeInstance reinforcements = getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
        if (reinforcements != null) {
            reinforcements.setBaseValue(0.0D);
        }
    }

    private void clearRandomModifiers(
        final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute
    ) {
        final AttributeInstance instance = getAttribute(attribute);
        if (instance == null) {
            return;
        }
        List.copyOf(instance.getModifiers()).forEach(modifier -> instance.removeModifier(modifier.id()));
    }

    /** Terminal cleanup releases every active claim including the exact legacy hearth. */
    @Override
    public void remove(final RemovalReason reason) {
        if (reason.shouldDestroy() && level() instanceof ServerLevel serverLevel) {
            HellhoundLifeRuntime.releaseAll(this, serverLevel, true);
        }
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(STATE_KEY, CompoundTag.CODEC, lifeState.write());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        final long now = level().getGameTime();
        lifeState = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(tag -> HellhoundLifeState.read(tag, getUUID(), now))
            .orElseGet(() -> HellhoundLifeState.solitary(getUUID(), PackOrigin.LEGACY_SOLITARY, now));
        normalizeZombieState();
    }

    /** One shared natural-group datum: exact pack identity, origin dimension, first position. */
    public record HellhoundPackSpawnData(
        UUID packId,
        String dimension,
        long packedAnchor
    ) implements SpawnGroupData {
    }
}
