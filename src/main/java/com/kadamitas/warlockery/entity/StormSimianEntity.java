package com.kadamitas.warlockery.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The bound canopy scout. Its registry id, displayed name, spawn egg, model, texture, renderer,
 * dimensions, loot, summon rite, companion binder offering, bound waystone travel, owner protection,
 * owner Slow Falling pulse, environmental immunity, hostile only targeting and owned wind charge are
 * all exactly what they were, and every one of them still arrives through the shared
 * {@link WingedArcaneMob} pipeline.
 *
 * <p>What is new is a species arbiter. {@link StormSimianRuntime} runs from
 * {@link #customServerAiStep} <em>after</em> {@code super}, so the companion follow, the tactical
 * runtime and the ambient lightning rod interest all still run and the arbiter is simply the last
 * writer of the tick. That is deliberate rather than incidental: the frozen support contracts stay
 * reachable and unmodified, and the species owns movement only on the ticks it has an intent.</p>
 *
 * <p>The {@code ownsSpecializedWingedAi} seam is deliberately not claimed. It belongs to the Imp,
 * and {@code ImpCompatibilityTest} asserts that this class does not declare it.</p>
 *
 * <p>The one removal is the old idle behaviour: a ten block box scanned every ten ticks for the
 * nearest live {@code ItemEntity}, followed by a navigation write toward it, with
 * {@code setCanPickUpLoot(true)} so contact then took the stack. Curiosity replaced it: bounded,
 * capped, line of sight filtered, and incapable of taking anything.</p>
 */
public final class StormSimianEntity extends WingedArcaneMob {
    static final String STATE_KEY = "WarlockeryStormSimian";
    private static final EntityDataAccessor<Integer> DATA_PRESENTATION_CHARGE =
        SynchedEntityData.defineId(StormSimianEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_PRESENTATION_HAS_GRIP =
        SynchedEntityData.defineId(StormSimianEntity.class, EntityDataSerializers.BOOLEAN);

    private final StormSimianRuntime.Counters counters = new StormSimianRuntime.Counters();
    private final StormSimianRuntime.TransientState scratch =
        new StormSimianRuntime.TransientState();
    private StormSimianState state = StormSimianState.empty();

    public StormSimianEntity(final EntityType<? extends Monster> type, final Level level) {
        super(type, level, CreatureKind.STORM_SIMIAN);
        // Curiosity inspects and never takes. Vanilla Mob picks up whatever it brushes past while
        // this is true, which is exactly the item theft the design forbids.
        setCanPickUpLoot(false);
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PRESENTATION_CHARGE, 0);
        builder.define(DATA_PRESENTATION_HAS_GRIP, false);
    }

    @Override
    protected void registerArcaneTargets() {
        targetHostileMobs();
    }

    public StormSimianState stormSimianState() {
        return state;
    }

    public void setStormSimianState(final StormSimianState updated) {
        state = updated == null ? StormSimianState.empty() : updated;
        syncPresentationFromRuntime();
    }

    public int presentationCharge() {
        return entityData.get(DATA_PRESENTATION_CHARGE);
    }

    public boolean presentationHasGrip() {
        return entityData.get(DATA_PRESENTATION_HAS_GRIP);
    }

    private void syncPresentationFromRuntime() {
        final int charge = state.charge();
        final boolean hasGrip = state.grip().isPresent();
        if (entityData.get(DATA_PRESENTATION_CHARGE) != charge) {
            entityData.set(DATA_PRESENTATION_CHARGE, charge);
        }
        if (entityData.get(DATA_PRESENTATION_HAS_GRIP) != hasGrip) {
            entityData.set(DATA_PRESENTATION_HAS_GRIP, hasGrip);
        }
    }

    public StormSimianRuntime.Counters stormSimianCounters() {
        return counters;
    }

    public StormSimianRuntime.TransientState stormSimianTransient() {
        return scratch;
    }

    /**
     * The arbiter runs last so it owns the final navigation write of the tick, while every frozen
     * generic writer above it keeps running and keeps its behaviour.
     */
    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        StormSimianRuntime.tick(this, level);
        syncPresentationFromRuntime();
    }

    /**
     * Nothing ever rides a Storm Simian. Found by the first execution of the exclusion fixture:
     * the vanilla default accepts any first passenger, so a bare {@code startRiding} succeeded and
     * the species quietly carried a player, which is the one Spectral Steed semantic it must never
     * have. No game reachable path led there, and now no path can.
     */
    @Override
    protected boolean canAddPassenger(final net.minecraft.world.entity.Entity passenger) {
        return false;
    }

    @Override
    public boolean hurtServer(
        final ServerLevel level,
        final DamageSource source,
        final float amount
    ) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && amount > 0.0F) {
            StormSimianRuntime.onAcceptedDamage(this, source);
        }
        return hurt;
    }

    /**
     * The same single owned wind charge against the same already legal target. Charge changes only
     * how it looks and how hard it is thrown, and is consumed at most once per attack.
     */
    @Override
    public void performRangedAttack(final LivingEntity target, final float power) {
        final float gustPower = StormSimianRuntime.consumeGustCharge(this);
        final boolean charged = gustPower > StormSimianRules.BASE_GUST_POWER;
        final Vec3 origin = getEyePosition();
        final Vec3 direction = target.getEyePosition().subtract(origin).normalize().scale(gustPower);
        final WindCharge gust = new WindCharge(level(), origin.x, origin.y, origin.z, direction);
        gust.setOwner(this);
        level().addFreshEntity(gust);
        level().playSound(null, blockPosition(), SoundEvents.WIND_CHARGE_THROW, SoundSource.NEUTRAL,
            0.7F, charged ? 1.5F : 1.2F);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level,
        final DifficultyInstance difficulty,
        final EntitySpawnReason reason,
        final @Nullable SpawnGroupData groupData
    ) {
        final SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);
        // The registered winged attribute baseline is exact; the generic Mob random follow range
        // spawn bonus would make it nondeterministic and break exact attribute assertions.
        final AttributeInstance followRange = getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.removeModifier(RANDOM_SPAWN_BONUS_ID);
        }
        return result;
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(STATE_KEY, CompoundTag.CODEC, state.write());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        state = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(StormSimianState::read)
            .orElse(StormSimianState.empty());
        scratch.resetForLoad();
        setCanPickUpLoot(false);
        syncPresentationFromRuntime();
    }
}
