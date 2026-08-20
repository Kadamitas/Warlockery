package com.kadamitas.warlockery.entity;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The body of {@code warlockery:spectral_familiar}.
 *
 * <p>It extends {@link SpiritMob} rather than {@code AnimalFamiliarMob}, and that is structural
 * rather than stylistic. {@code CreatureVisualProfile} gives {@code CreatureKind.FAMILIAR} the
 * {@code SPIRIT} archetype, so {@code ModEntities.createArcaneType} routes it through
 * {@code createSpirit} and it has always been built as a {@code SpiritMob extends Vex}. Any cast or
 * {@code instanceof ArcaneMob} against a spectral familiar throws. The three animal familiars' rules
 * are shared with this family by call; their <em>body</em> cannot be, because it is a
 * {@code Zombie}.</p>
 *
 * <p>Extending {@link SpiritMob} rather than {@code Vex} directly is also deliberate: the companion
 * binder acquisition path and the ore-sample interaction both arrive through
 * {@code SpiritMob.mobInteract -> CreatureBehavior.interact}, and both are frozen public
 * contracts.</p>
 *
 * <h2>Three vanilla hazards this chassis carries, and what is done about each</h2>
 *
 * <ol>
 *   <li>{@code Vex.registerGoals} installs a {@code NearestAttackableTargetGoal<Player>}, a
 *       {@code VexCopyOwnerTargetGoal} and a {@code HurtByTargetGoal}. {@link #registerGoals()} is
 *       overridden so none of them is ever installed; this runtime is the sole target authority and
 *       a bound familiar that hunts passing players on its own is not one.</li>
 *   <li>{@code Mob.checkDespawn} discards any body whose type is not allowed in peaceful
 *       <em>before</em> it consults persistence, and {@code createSpirit} builds this type with
 *       {@code notInPeaceful()}, so a difficulty change deletes a bound familiar outright. That
 *       sweep is <em>not</em> fixed here. The peaceful-despawn package fixes it once for every
 *       affected body, keyed on ownership and with the difficulty compare first, and it covers
 *       {@link SpiritMob}, so this body inherits the fix rather than declaring a fourteenth
 *       override of it. What this class owes that fix is {@link #isPersistenceRequired()}, which is
 *       what makes an owned spectral familiar report as persistent at all.</li>
 *   <li>{@code ArcaneMob} declares {@code CreatureKind.FAMILIAR} environmentally immune, but a
 *       spectral familiar is never an {@code ArcaneMob}, so that clause has never run for it. See
 *       {@link #hurtServer}.</li>
 * </ol>
 */
public class SpectralFamiliarEntity extends SpiritMob {

    /** The single durable state key. */
    public static final String STATE_KEY = "WarlockerySpectralFamiliar";

    /** Sentinel meaning "seed me from this entity's identity on the next survey". */
    public static final int UNSEEDED_CURSOR = -1;

    private final SpectralFamiliarRuntime.Counters spectralCounters =
        new SpectralFamiliarRuntime.Counters();
    private SpectralFamiliarState spectralState;

    /**
     * The rotating survey cursor. Transient, never saved, and reset to the unseeded sentinel on load
     * rather than to zero: a cursor reloaded as zero restarts the far tail from the beginning every
     * time, so a familiar that unloads more often than one full rotation would never evaluate the
     * far envelope at all.
     */
    private transient int surveyCursor = UNSEEDED_CURSOR;

    private transient SpectralFamiliarRules.Decision lastSpectralDecision =
        SpectralFamiliarRules.UNBOUND;

    /**
     * Set only while the shared contract binding runs; never persisted, never read elsewhere.
     *
     * <p>{@code CreatureBehaviorRuntime.bindCompanion} ends in the one-way
     * {@code Mob.setPersistenceRequired()}, which has no clearing setter at 26.2, so a familiar that
     * was ever bound would never despawn again even after it stopped being owned. The F10 review
     * REJECTED overriding {@code isPersistenceRequired()} to a derived predicate, because that
     * discards every legitimate vanilla latch and a name-tagged familiar would then despawn. The
     * accepted shape, reproduced here and in {@code AnimalFamiliarMob}, is to refuse exactly the one
     * write made inside this window and to honour every other one.</p>
     */
    private transient boolean suppressContractPersistenceLatch;

    public SpectralFamiliarEntity(final EntityType<? extends Vex> type, final Level level) {
        super(type, level, CreatureKind.FAMILIAR);
        spectralState = SpectralFamiliarState.empty(getUUID(), level.getGameTime());
        // SpiritMob's constructor adds a player-targeting goal for six kinds; FAMILIAR is not one of
        // them, so nothing to remove there. Vex's own goal set is refused at registerGoals instead.
        xpReward = 1;
    }

    /**
     * The Vex goal set is never installed.
     *
     * <p>{@code Mob}'s constructor calls {@code registerGoals()} on the server, so overriding here
     * is what prevents the goals from existing at all rather than removing them afterwards. Every
     * goal {@code Vex.registerGoals} adds is a movement or a target writer that
     * {@link SpectralFamiliarRuntime} owns: {@code VexChargeAttackGoal} and
     * {@code VexRandomMoveGoal} steer the move control, {@code HurtByTargetGoal} and
     * {@code VexCopyOwnerTargetGoal} write targets, and {@code NearestAttackableTargetGoal<Player>}
     * makes an idle bound familiar attack whichever player wanders closest. Float and look are
     * presentation and stay.</p>
     */
    @Override
    protected final void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    public final SpectralFamiliarState spectralState() {
        return spectralState;
    }

    public final void setSpectralState(final SpectralFamiliarState state) {
        spectralState = state == null
            ? SpectralFamiliarState.empty(getUUID(), level().getGameTime())
            : state;
    }

    public final SpectralFamiliarRuntime.Counters spectralCounters() {
        return spectralCounters;
    }

    final int surveyCursor() {
        return surveyCursor;
    }

    final void setSurveyCursor(final int cursor) {
        surveyCursor = cursor;
    }

    // ---- the one tick authority ----

    @Override
    protected final void customServerAiStep(final ServerLevel level) {
        // super still runs, and its two generic layers are declined below rather than skipped, so
        // the decline is observable instead of implicit.
        super.customServerAiStep(level);
        SpectralFamiliarRuntime.tick(this, level);
    }

    @Override
    protected final void tickProfiledBehavior(final ServerLevel level) {
        // Declined on purpose. The generic companion layer writes owner follow, the emergency owner
        // teleport, the owner aura and the owner's attacker as a target, and it also runs the 1.4
        // tickOreGuidance, which streams 10,625 positions through Level.getBlockState. This family's
        // runtime owns every one of those under its own tether, cadence, budget and lease.
        spectralCounters.genericLayersDeclined++;
    }

    @Override
    protected final void tickSpecializedActivity(final ServerLevel level) {
        // Declined on purpose, same reason: TacticalCombatRuntime and AmbientActivityRuntime are
        // both movement writers, and FAMILIAR_HOME ambient movement competed with ore guidance at
        // 1.4 for the same tick.
        spectralCounters.genericLayersDeclined++;
    }

    // ---- interaction: the single binding path, and the latch guard around it ----

    @Override
    protected final InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        suppressContractPersistenceLatch = true;
        try {
            return super.mobInteract(player, hand);
        } finally {
            suppressContractPersistenceLatch = false;
        }
    }

    /**
     * Refuses exactly one write: the one {@code CreatureBehaviorRuntime.finishBinding} makes while
     * the shared contract binding is running. A name tag, the equipment-slot container behind
     * {@code /item replace entity}, a hopper, a dropper and {@code GameTestEntityBuilder} all latch
     * normally, because none of them runs inside the window.
     */
    @Override
    public final void setPersistenceRequired() {
        if (suppressContractPersistenceLatch) {
            return;
        }
        super.setPersistenceRequired();
    }

    /**
     * The vanilla latch is consulted, not discarded. A bound familiar persists because it is owned,
     * and an unbound one goes back to ordinary despawn.
     */
    @Override
    public final boolean isPersistenceRequired() {
        return super.isPersistenceRequired() || CreatureBehaviorState.owner(this).isPresent();
    }

    @Override
    public final boolean canAttack(final LivingEntity target) {
        return SpectralFamiliarRuntime.canAttack(this, target) && super.canAttack(target);
    }

    /**
     * Familiar environmental immunity, made reachable.
     *
     * <p>{@code ArcaneMob.isEnvironmentallyImmuneFamiliar} reads
     * {@code FamiliarBondRules.isClassicFamiliar(kind) || kind == CreatureKind.FAMILIAR}, so the
     * declared contract has always included the spectral familiar. It has never once run for it: a
     * spectral familiar is a {@code SpiritMob}, never an {@code ArcaneMob}, so that clause is
     * unreachable for {@code FAMILIAR} and always has been. The predicate is reused verbatim rather
     * than restated, so the two bodies cannot drift apart.</p>
     */
    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        if (FamiliarBondRules.ignoresEnvironmentalDamage(source)
            || source.getEntity() == null && source.getDirectEntity() == null) {
            return false;
        }
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            SpectralFamiliarRuntime.onAcceptedDamage(this, level, source);
        }
        return hurt;
    }

    // ---- persistence ----

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(STATE_KEY, CompoundTag.CODEC, spectralState.write());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        final long now = level().getGameTime();
        spectralState = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(tag -> SpectralFamiliarState.read(tag, getUUID(), now))
            .orElseGet(() -> SpectralFamiliarState.empty(getUUID(), now));
        // Volatile facts never cross the seam.
        setTarget(null);
        getNavigation().stop();
        getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0.0D);
        surveyCursor = UNSEEDED_CURSOR;
    }

    // ---- declared test seams ----
    //
    // Honestly labelled: these exist so the live fixtures can observe a contract that has no other
    // observable surface. They are not production callers of anything.

    /** The last decision this body executed, so a fixture can assert the rung rather than a guess. */
    public final SpectralFamiliarRules.Decision lastSpectralDecision() {
        return lastSpectralDecision;
    }

    final void recordSpectralDecision(final SpectralFamiliarRules.Decision decision) {
        lastSpectralDecision = decision;
    }

    /**
     * Makes the bounded survey due on the next tick and clears the post-episode cooldown. A fixture
     * cannot wait out a two hundred tick cadence plus a six hundred tick cooldown inside a four
     * hundred tick arena, and shortening the real cadence for tests would mean the tested cadence is
     * not the shipped one.
     */
    public final void makeSurveyDue() {
        final long now = level().getGameTime();
        spectralState = spectralState
            .withSurvey(new AnimalFamiliarRules.SearchOutcome(
                now, spectralState.survey().consecutiveFailures()), 0L)
            .withGuideCooldown(0L)
            .withDrift(0L);
    }

    /** Test seam: the vanilla latch alone, with this family's own reason excluded. */
    public final boolean vanillaPersistenceLatched() {
        return super.isPersistenceRequired();
    }

    /** Test seam: whether the contract-latch suppression window is currently open. */
    public final boolean contractLatchSuppressed() {
        return suppressContractPersistenceLatch;
    }

    /** The exact operational goal names, so a fixture can prove the goal set is what it claims. */
    public final List<String> operationalGoalNames() {
        return goalSelector.getAvailableGoals().stream()
            .map(goal -> goal.getGoal().getClass().getSimpleName())
            .toList();
    }

    public final int operationalTargetGoalCount() {
        return targetSelector.getAvailableGoals().size();
    }
}

