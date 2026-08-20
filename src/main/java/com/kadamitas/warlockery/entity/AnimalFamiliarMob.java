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
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The one body every bound animal familiar shares.
 *
 * <p>The three concrete bodies below this class are deliberately thin. Everything that is the same
 * for a cat, an owl and a toad -- clearing the inherited generic goals, declining the generic
 * profiled companion layer, declining the generic tactical and ambient layers, the contract
 * persistence latch guard, the persistence predicate, the save and load seam, the damage seam, the
 * target legality seam and the runtime dispatch -- lives here exactly once. What a subclass adds is
 * only what constitutes identity: its species, its chassis (walk, fly or hop) and its own
 * presentation.</p>
 *
 * <p>It still extends {@link ArcaneMob}, which is not an accident. Classic familiar environmental
 * immunity and classic familiar damage sharing are inherited from {@code ArcaneMob.hurtServer} and
 * from {@code CreatureCombat}, the companion binder acquisition path is inherited through
 * {@code ArcaneMob.mobInteract}, and every one of those is a frozen public contract. Re-implementing
 * them on a fresh base class would have been three chances to get a frozen contract subtly wrong.</p>
 */
public abstract class AnimalFamiliarMob extends ArcaneMob {

    /** The single durable state key. All three species share it; the payload carries the species. */
    public static final String STATE_KEY = "WarlockeryAnimalFamiliar";

    private final AnimalFamiliarRuntime.Counters familiarCounters = new AnimalFamiliarRuntime.Counters();
    private AnimalFamiliarState familiarState;

    /**
     * The rotating home-scan cursor. Transient, never saved, and reset to the unseeded sentinel on
     * load rather than to zero: a cursor reloaded as zero restarts the far tail from the beginning
     * every time, so a familiar that unloads more often than one full rotation would never evaluate
     * the far envelope at all. The sentinel makes the next scan re-seed from the mixing hash.
     */
    private transient int homeScanCursor = UNSEEDED_CURSOR;
    private transient boolean homeValidity;
    private transient long homeValidityCheckedAt;
    private transient AnimalFamiliarRules.Decision lastFamiliarDecision = new AnimalFamiliarRules.Decision(
        AnimalFamiliarRules.Action.IDLE, AnimalFamiliarRules.Reason.NOTHING_TO_DO);

    /** Sentinel meaning "seed me from this entity's identity on the next scan". */
    public static final int UNSEEDED_CURSOR = -1;

    /**
     * Set only while the shared contract binding runs; never persisted, never read elsewhere.
     *
     * <p>This is the inherited constraint from F10, honoured at its source rather than at the
     * predicate. {@code CreatureBehaviorRuntime.bindCompanion} ends in the one-way
     * {@code Mob.setPersistenceRequired()}, which has no clearing setter at 26.2, so a familiar
     * that was ever bound would never despawn again even after it stopped being owned. The F10
     * review REJECTED overriding {@code isPersistenceRequired()} to a derived predicate, because
     * that discarded every legitimate vanilla latch -- a name-tagged familiar would have despawned.
     * The accepted shape, reproduced here, is to refuse exactly the one write made inside this
     * window and to honour every other one.</p>
     */
    private transient boolean suppressContractPersistenceLatch;

    protected AnimalFamiliarMob(
        final EntityType<? extends Zombie> type,
        final Level level,
        final AnimalFamiliarSpecies species
    ) {
        super(type, level, species.kind());
        familiarState = AnimalFamiliarState.empty(species, getUUID(), level.getGameTime());
        // ArcaneMob's constructor installed the shared 1.4 goal set for CAT, OWL and TOAD: a float
        // goal, a water-avoiding stroll, a look goal and a melee goal. The stroll and the melee goal
        // are movement and target writers, and this family's runtime is the sole authority for
        // both, so they come out. Float and look are presentation and stay.
        goalSelector.removeAllGoals(goal -> true);
        targetSelector.removeAllGoals(goal -> true);
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        xpReward = 1;
    }

    /**
     * The zombie goal set is never installed.
     *
     * <p>Two reasons, and the second one is fatal rather than merely wrong. Every goal in it is a
     * movement or target writer that {@link AnimalFamiliarRuntime} owns, and
     * {@code MoveThroughVillageGoal} refuses at construction any body that does not navigate on the
     * ground -- which the Owl does not. Overriding here rather than removing afterwards is the
     * difference between an owl that exists and an owl that throws inside its own constructor.</p>
     */
    @Override
    protected final void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    public abstract AnimalFamiliarSpecies species();

    public final AnimalFamiliarState familiarState() {
        return familiarState;
    }

    public final void setFamiliarState(final AnimalFamiliarState state) {
        familiarState = state == null
            ? AnimalFamiliarState.empty(species(), getUUID(), level().getGameTime())
            : state;
    }

    public final AnimalFamiliarRuntime.Counters familiarCounters() {
        return familiarCounters;
    }

    final int homeScanCursor() {
        return homeScanCursor;
    }

    final void setHomeScanCursor(final int cursor) {
        homeScanCursor = cursor;
    }

    final boolean homeValidity() {
        return homeValidity;
    }

    final long homeValidityCheckedAt() {
        return homeValidityCheckedAt;
    }

    final void setHomeValidity(final boolean valid, final long checkedAt) {
        homeValidity = valid;
        homeValidityCheckedAt = checkedAt;
    }

    // ---- the one tick authority ----

    @Override
    protected final void customServerAiStep(final ServerLevel level) {
        // super still runs, and its two generic layers are declined below rather than skipped, so
        // the decline is observable instead of implicit.
        super.customServerAiStep(level);
        AnimalFamiliarRuntime.tick(this, level);
    }

    @Override
    protected final void tickProfiledBehavior(final ServerLevel level) {
        // Declined on purpose. The generic companion layer writes owner follow, the emergency owner
        // teleport, the owner aura and the owner's attacker as a target; AnimalFamiliarRuntime owns
        // all four under this family's own tether, cadence and lease, and two writers per tick is
        // the defect this family exists to remove. Counted so a fixture asserts the decline
        // happened rather than trusting this comment.
        familiarCounters.genericLayersDeclined++;
    }

    @Override
    protected final void tickSpecializedActivity(final ServerLevel level) {
        // Declined on purpose, same reason: TacticalCombatRuntime and AmbientActivityRuntime are
        // both movement writers.
        familiarCounters.genericLayersDeclined++;
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
     * {@code /item replace entity}, a hopper, a dropper, {@code EquipmentDispenseItemBehavior} and
     * {@code GameTestEntityBuilder} all latch normally, because none of them runs inside the
     * window.
     */
    @Override
    public final void setPersistenceRequired() {
        if (suppressContractPersistenceLatch) {
            return;
        }
        super.setPersistenceRequired();
    }

    /**
     * The vanilla latch is consulted, not discarded. The explicit reason has to be visible here as
     * well, because {@code Mob.checkDespawn} short-circuits on this method before it consults
     * anything else: a bound familiar persists because it is owned.
     */
    @Override
    public final boolean isPersistenceRequired() {
        return super.isPersistenceRequired() || CreatureBehaviorState.owner(this).isPresent();
    }

    /**
     * An unbound familiar stays in the world. Owner ruling: "dont despawn unbound familiars".
     *
     * <p>Established against the decompiled 26.2 sources rather than against any description of
     * them, because the obvious-sounding hook does not exist. {@code Mob.shouldDespawnInPeaceful}
     * is not a member at this version. {@code Mob.checkDespawn} is:</p>
     *
     * <pre>
     * if (peaceful &amp;&amp; !getType().isAllowedInPeaceful())      discard();
     * else if (!isPersistenceRequired() &amp;&amp; !requiresCustomPersistence()) {
     *     ... if (distSqr &gt; despawnDistance^2 &amp;&amp; removeWhenFarAway(distSqr)) discard();
     *     ... if (noActionTime &gt; 600 &amp;&amp; random(800)==0 &amp;&amp; distSqr &gt; 32^2
     *             &amp;&amp; removeWhenFarAway(distSqr))                discard();
     * } else noActionTime = 0;
     * </pre>
     *
     * <p>All three of these ids are registered {@code MobCategory.CREATURE} with no
     * {@code notInPeaceful()}, so {@code isAllowedInPeaceful()} is true and the peaceful arm never
     * reached them; both remaining {@code discard()} calls are guarded by this method, so refusing
     * here is the whole gate and nothing else has to change.</p>
     *
     * <p><strong>Why this hook and not {@code requiresCustomPersistence()}</strong>, which sits in
     * the same condition and would also stop the despawn: {@code NaturalSpawner.createState} counts
     * a mob toward its category's spawn cap only when it is neither
     * {@code isPersistenceRequired()} nor {@code requiresCustomPersistence()}. Taking that hook
     * would make every accumulated owl invisible to the CREATURE cap, so the spawner would keep
     * spawning more forever. This hook leaves the count alone, which means an accumulating
     * population throttles its own further spawning at the vanilla cap. It is also the exact
     * mechanism vanilla uses for the animals this ruling is comparing them to:
     * {@code Animal.removeWhenFarAway} returns false, which is why cows and chickens stay.</p>
     *
     * <p>The two things this must not break, and does not: a bound familiar is already persistent
     * through {@link #isPersistenceRequired()}, which short-circuits before this method is ever
     * consulted, so fixture 2's binding behaviour is untouched; and the vanilla name-tag latch is
     * neither read nor written here, so the F10 rejection -- a remedy that discarded every
     * legitimate latch and would have despawned a name-tagged mob -- is not repeated.</p>
     */
    @Override
    public final boolean removeWhenFarAway(final double distanceToClosestPlayerSquared) {
        return false;
    }

    /**
     * A familiar never breaks a door, and refusing the write is the only way to be sure of it.
     *
     * <p>{@code Zombie.finalizeSpawn} calls {@code setCanBreakDoors(random.nextFloat() &lt;
     * difficulty * 0.1F)} and calls it again unconditionally for a leader zombie, and
     * {@code Zombie.readAdditionalSaveData} calls it from the {@code CanBreakDoors} tag. The setter
     * adds {@code breakDoorGoal} at priority 1, and it is guarded only by
     * {@code navigation.canNavigateGround()} -- which the Cat and the Toad satisfy. The Owl escaped
     * by the accident of flying navigation, not by design.</p>
     *
     * <p>The direct fixtures never saw it because they construct bodies and never call
     * {@code finalizeSpawn}, while the real acquisition path for a brew-summoned toad or owl is
     * {@code EntityType.spawn}, which does. Refusing here covers all three call sites at once, and
     * {@code canBreakDoors()} stays false so the saved tag stays false too.</p>
     */
    @Override
    public final void setCanBreakDoors(final boolean canBreakDoors) {
    }

    // ---- declared test seams ----
    //
    // Honestly labelled: these five exist so the live fixtures can observe a contract that has no
    // other observable surface. They are not production callers of anything.

    /** The last decision this body executed, so a fixture can assert the rung rather than a guess. */
    public final AnimalFamiliarRules.Decision lastFamiliarDecision() {
        return lastFamiliarDecision;
    }

    final void recordFamiliarAction(final AnimalFamiliarRules.Decision decision) {
        lastFamiliarDecision = decision;
    }

    /**
     * Makes both bounded searches due on the next tick. A fixture cannot wait out a three hundred
     * to five hundred tick cadence inside a four hundred tick arena, and shortening the real
     * cadence for tests would mean the tested cadence is not the shipped one.
     */
    public final void makeFamiliarSearchesDue() {
        final long now = level().getGameTime();
        familiarState = familiarState
            .withHomeSearch(new AnimalFamiliarRules.SearchOutcome(
                now, familiarState.homeSearch().consecutiveFailures()))
            .withPreySearch(new AnimalFamiliarRules.SearchOutcome(
                now, familiarState.preySearch().consecutiveFailures()))
            .withSignatureCooldown(0L)
            .withRoute(0L, 0L, 0);
        homeValidityCheckedAt = 0L;
    }

    /** Test seam: the vanilla latch alone, with this family's own reason excluded. */
    public final boolean vanillaPersistenceLatched() {
        return super.isPersistenceRequired();
    }

    /** Test seam: whether the contract-latch suppression window is currently open. */
    public final boolean contractLatchSuppressed() {
        return suppressContractPersistenceLatch;
    }

    // ---- frozen familiar contracts, and one deliberate correction ----

    /**
     * A bound familiar is not a zombie and must not drown into a Drowned.
     *
     * <p>Deliberate in-family correction, recorded as a deviation. {@code ArcaneMob} extends
     * {@code Zombie}, so at HEAD a Toad that its own {@code POND_REST} ambient activity walked into
     * water was on a thirty-second timer to being replaced by a {@code minecraft:drowned}, taking
     * the owner binding, the aura and the mob itself with it. Nothing about the familiar concept
     * survives that conversion, and it is unreachable from any design intent.</p>
     */
    @Override
    public final boolean convertsInWater() {
        return false;
    }

    @Override
    public final boolean canAttack(final LivingEntity target) {
        return AnimalFamiliarRuntime.canAttack(this, target) && super.canAttack(target);
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            AnimalFamiliarRuntime.onAcceptedDamage(this, level, source);
        }
        return hurt;
    }

    // ---- persistence ----

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(STATE_KEY, CompoundTag.CODEC, familiarState.write());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        final long now = level().getGameTime();
        familiarState = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(tag -> AnimalFamiliarState.read(tag, species(), getUUID(), now))
            .orElseGet(() -> AnimalFamiliarState.empty(species(), getUUID(), now));
        // Volatile facts never cross the seam.
        setTarget(null);
        getNavigation().stop();
        homeScanCursor = UNSEEDED_CURSOR;
        homeValidity = false;
        homeValidityCheckedAt = 0L;
    }

    // ---- test seams for the live fixtures ----

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
