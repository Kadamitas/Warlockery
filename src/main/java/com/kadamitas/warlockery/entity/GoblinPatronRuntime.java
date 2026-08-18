package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.GoblinPatronRules.Action;
import com.kadamitas.warlockery.entity.GoblinPatronRules.Candidate;
import com.kadamitas.warlockery.entity.GoblinPatronRules.CombatFacts;
import com.kadamitas.warlockery.entity.GoblinPatronRules.CounterpartCandidate;
import com.kadamitas.warlockery.entity.GoblinPatronRules.Decision;
import com.kadamitas.warlockery.entity.GoblinPatronRules.OfferingEvent;
import com.kadamitas.warlockery.entity.GoblinPatronRules.OfferingResult;
import com.kadamitas.warlockery.entity.GoblinPatronRules.Phase;
import com.kadamitas.warlockery.entity.GoblinPatronRules.Reason;
import com.kadamitas.warlockery.entity.GoblinPatronRules.ReleaseReason;
import com.kadamitas.warlockery.entity.GoblinPatronRules.RouteFailure;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The only server-side F12 patron behavior controller and the sole owner of ordinary patron
 * navigation, shared by Stonebroker and Forgewarden.
 *
 * <p>One controller serves both bodies deliberately. The priority ladder, the perception budgets,
 * the navigation lease, the accord, the offering window, the trade guard, and the feedback cadence
 * are identical machinery; only the executors and the scanned subject differ. Two controllers would
 * have been the same fifteen members twice, and the same search defect twice.</p>
 *
 * <p>No method here enumerates a dimension's entity list, forces or creates a chunk ticket, calls a
 * chunk-loading accessor, retains a live entity beyond its own tick, edits a block, opens a
 * container, or writes another family's data. Patron physical world-edit count is always zero.</p>
 */
public final class GoblinPatronRuntime {
    private static final double APPROACH_SPEED = 1.0D;
    private static final double URGENT_SPEED = 1.25D;
    private static final double WITHDRAW_SPEED = 1.2D;
    private static final double ARROW_BASE_DAMAGE = 6.0D;
    private static final float ARROW_VELOCITY = 1.8F;
    private static final float ARROW_INACCURACY = 3.0F;
    private static final float ARROW_PITCH = 1.15F;
    private static final float SURGE_DAMAGE = 6.0F;
    /**
     * Declared test seam: package-private rather than private so the live fixture can assert that
     * exactly this much vertical impulse reaches the victim, and never twice this much. An
     * unasserted launch magnitude is how the 1.4 paired push composed with this one undetected.
     */
    static final float HAMMER_LAUNCH = 0.45F;
    private static final int HAMMER_FIRE_SECONDS = 4;

    /**
     * The enumerated reposition ring for {@code CLAIM_SHIFT}, ordered so a nearer safe stand is
     * always preferred. These are candidate offsets only: every one is charged and revalidated
     * against the loaded world before it can be chosen.
     */
    private static final List<BlockPos> SHIFT_OFFSETS = List.of(
        new BlockPos(7, 0, 0), new BlockPos(-7, 0, 0), new BlockPos(0, 0, 7), new BlockPos(0, 0, -7),
        new BlockPos(5, 0, 5), new BlockPos(-5, 0, 5), new BlockPos(5, 0, -5), new BlockPos(-5, 0, -5),
        new BlockPos(9, 0, 0), new BlockPos(-9, 0, 0), new BlockPos(0, 0, 9), new BlockPos(0, 0, -9),
        new BlockPos(11, 0, 0), new BlockPos(-11, 0, 0), new BlockPos(0, 0, 11), new BlockPos(0, 0, -11)
    );

    private GoblinPatronRuntime() {
    }

    // ================================================================ body contract

    /**
     * The narrow contract both patron bodies expose to this controller.
     *
     * <p>It carries one accessor for the body and one for the shared {@link Core}. Every durable
     * field, every counter, every scratch value, and the persistence-latch guard live in that one
     * holder, so neither entity has to restate them. That is the whole anti-duplication device: the
     * two bodies differ by constructor, kind, sounds, boss-bar style, and nothing else.</p>
     */
    public interface PatronBody {
        AbstractGoblinMerchantEntity body();

        Core patronCore();

        CreatureKind patronKind();
    }

    /** Structural work counters proving the exact caps. Pass-local and never persisted. */
    public static final class Counters {
        long challengerVisits;
        long counterpartVisits;
        long directiveVisits;
        long surgeVisits;
        long chargedBlockReads;
        long candidatesRetained;
        long navigationRequests;
        long navigationFailures;
        long actionsStarted;
        long actionsCommitted;
        long actionsCancelled;
        long arrowsFired;
        long surgesCommitted;
        long feedbackPulses;
        long worldEdits;

        public long challengerVisits() { return challengerVisits; }
        public long counterpartVisits() { return counterpartVisits; }
        public long directiveVisits() { return directiveVisits; }
        public long surgeVisits() { return surgeVisits; }
        public long chargedBlockReads() { return chargedBlockReads; }
        public long candidatesRetained() { return candidatesRetained; }
        public long navigationRequests() { return navigationRequests; }
        public long navigationFailures() { return navigationFailures; }
        public long actionsStarted() { return actionsStarted; }
        public long actionsCommitted() { return actionsCommitted; }
        public long actionsCancelled() { return actionsCancelled; }
        public long arrowsFired() { return arrowsFired; }
        public long surgesCommitted() { return surgesCommitted; }
        public long feedbackPulses() { return feedbackPulses; }
        public long worldEdits() { return worldEdits; }
    }

    /**
     * Execution scratch rebuilt after every load. Nothing here is meaning: losing it can delay a
     * scan by at most one cadence but can never replay an arrow, a surge, a trade, or an effect.
     */
    public static final class TransientState {
        boolean reconciled;
        int perceptionCooldownTicks;
        int counterpartCooldownTicks;
        int directiveCooldownTicks;
        int blockScanCooldownTicks;
        int hazardCooldownTicks;
        int actionCooldownTicks;
        int navigationCooldownTicks;
        int feedbackCooldownTicks;
        int tradeCooldownTicks;
        boolean hazardActive;
        boolean accordSubjectThreatened;
        int scanCursor = UNSEEDED_CURSOR;
        Optional<BlockPos> scannedContext = Optional.empty();

        public void resetForLoad() {
            reconciled = false;
            perceptionCooldownTicks = 0;
            counterpartCooldownTicks = 0;
            directiveCooldownTicks = 0;
            blockScanCooldownTicks = 0;
            hazardCooldownTicks = 0;
            actionCooldownTicks = 0;
            navigationCooldownTicks = 0;
            feedbackCooldownTicks = 0;
            tradeCooldownTicks = 0;
            hazardActive = false;
            accordSubjectThreatened = false;
            scannedContext = Optional.empty();
            // Not zero. A patron that unloads more often than one full rotation would restart the
            // far tail at index 0 every time and never reach the far envelope, which is a weaker
            // form of the search defect the envelope exists to prevent. The cursor is reseeded from
            // the stable identity offset on first use instead.
            scanCursor = UNSEEDED_CURSOR;
        }

        public boolean hazardActive() {
            return hazardActive;
        }

        public Optional<BlockPos> scannedContext() {
            return scannedContext;
        }

        /**
         * Declared fixture seam, package private on purpose.
         *
         * <p>{@link #resetForLoad} is deliberately not enough to make a fixture's next tick do any
         * work, and that is a real trap rather than a detail: it clears {@code reconciled}, so the
         * very next {@link GoblinPatronRuntime#reconcileOnLoad} reseeds every cadence from the
         * body's own UUID hash and the tick then does nothing at all. A fixture that reset and
         * ticked would observe an unscanned, unacquired, unaccorded patron and would assert against
         * stale state while looking perfectly correct.</p>
         */
        void makeEveryCadenceDue() {
            reconciled = true;
            perceptionCooldownTicks = 0;
            counterpartCooldownTicks = 0;
            directiveCooldownTicks = 0;
            blockScanCooldownTicks = 0;
            hazardCooldownTicks = 0;
            actionCooldownTicks = 0;
            navigationCooldownTicks = 0;
            feedbackCooldownTicks = 0;
            tradeCooldownTicks = 0;
        }
    }

    private static final int UNSEEDED_CURSOR = -1;

    /**
     * Every mutable per-patron field in exactly one place. Both bodies hold one of these and
     * nothing else, which is what keeps {@code StonebrokerEntity} and {@code ForgewardenEntity}
     * from becoming two copies of the same class.
     */
    public static final class Core {
        private final Counters counters = new Counters();
        private final TransientState scratch = new TransientState();
        private final ServerBossEvent bossEvent;
        private GoblinPatronState state;
        /** Set only while the shared contract binding runs; never persisted, never read elsewhere. */
        private boolean suppressContractPersistenceLatch;

        public Core(final CreatureKind kind, final ServerBossEvent bossEvent) {
            this.state = GoblinPatronState.empty(kind);
            this.bossEvent = bossEvent;
        }

        public GoblinPatronState state() {
            return state;
        }

        public void setState(final GoblinPatronState updated) {
            state = updated == null ? state : updated;
        }

        public Counters counters() {
            return counters;
        }

        public TransientState scratch() {
            return scratch;
        }

        public ServerBossEvent bossEvent() {
            return bossEvent;
        }

        public boolean contractLatchSuppressed() {
            return suppressContractPersistenceLatch;
        }

        public void setContractLatchSuppressed(final boolean suppressed) {
            suppressContractPersistenceLatch = suppressed;
        }
    }

    // ================================================================ shorthand

    private static GoblinPatronState state(final PatronBody patron) {
        return patron.patronCore().state();
    }

    private static void setState(final PatronBody patron, final GoblinPatronState updated) {
        patron.patronCore().setState(updated);
    }

    private static TransientState scratch(final PatronBody patron) {
        return patron.patronCore().scratch();
    }

    private static Counters counters(final PatronBody patron) {
        return patron.patronCore().counters();
    }

    public static String dimensionOf(final ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    // ================================================================ entry point

    /**
     * The one live entry point, called from each patron body's {@code customServerAiStep} on every
     * loaded server tick.
     */
    public static void tick(final PatronBody patron, final ServerLevel level) {
        final AbstractGoblinMerchantEntity body = patron.body();
        if (!GoblinPatronRules.isPatron(patron.patronKind()) || body.isNoAi() || !body.isAlive()) {
            return;
        }
        reconcileOnLoad(patron, level);
        advanceLoadedTimers(patron, level);
        updateBossBar(patron);
        if (body.isTrading()) {
            holdForTrade(patron);
            return;
        }
        if (tickHazard(patron, level)) {
            return;
        }
        perceive(patron, level);
        decide(patron, level);
        execute(patron, level);
        publishDirective(patron, level);
        emitFeedback(patron, level);
    }

    // ================================================================ lifecycle

    private static void reconcileOnLoad(final PatronBody patron, final ServerLevel level) {
        final TransientState scratch = scratch(patron);
        if (scratch.reconciled) {
            return;
        }
        scratch.reconciled = true;
        // Stagger every cadence by a stable identity offset so a reloaded batch never spikes.
        final int offset = GoblinPatronRules.stableOffset(
            patron.body().getUUID(), GoblinPatronRules.MAX_SCHEDULE_OFFSET_TICKS + 1
        );
        scratch.perceptionCooldownTicks = offset % GoblinPatronRules.PERCEPTION_INTERVAL_TICKS;
        scratch.counterpartCooldownTicks = offset % GoblinPatronRules.COUNTERPART_INTERVAL_TICKS;
        scratch.blockScanCooldownTicks = offset % GoblinPatronRules.BLOCK_SCAN_INTERVAL_TICKS;
        scratch.directiveCooldownTicks = offset % GoblinPatronRules.DIRECTIVE_INTERVAL_TICKS;

        GoblinPatronState updated = state(patron);
        final String dimension = dimensionOf(level);
        // A committed action, its arrows, and its navigation lease never survive an unload: an
        // action cannot span a tick, so it cannot span a save either.
        updated = updated.withRoute(GoblinPatronState.Route.none());
        if (updated.anchor().present() && updated.anchor().dimension()
            .map(stored -> !stored.equals(dimension)).orElse(true)) {
            updated = updated.withAnchor(GoblinPatronState.Anchor.none());
        }
        if (updated.anchor().position()
            .map(anchor -> !level.getWorldBorder().isWithinBounds(anchor)).orElse(false)) {
            updated = updated.withAnchor(GoblinPatronState.Anchor.none());
        }
        if (updated.accord().counterpart().isPresent() && updated.accord().dimension()
            .map(stored -> !stored.equals(dimension)).orElse(true)) {
            updated = updated.withAccord(GoblinPatronState.Accord.none());
        }
        setState(patron, updated);
        patron.body().getNavigation().stop();
    }

    /**
     * Advances every remaining-tick counter by exactly one loaded tick, then runs the transitions
     * those counters own.
     *
     * <p>This method is the single exit for every timed phase. The state records deliberately do
     * not end anything in their own constructors: if {@code Combat} cleared its action when the
     * commit timer reached zero, this branch would never see the transition, the recovery it must
     * arm would never be armed, and the completed action would never be recorded.</p>
     */
    private static void advanceLoadedTimers(final PatronBody patron, final ServerLevel level) {
        final TransientState scratch = scratch(patron);
        scratch.perceptionCooldownTicks = Math.max(0, scratch.perceptionCooldownTicks - 1);
        scratch.counterpartCooldownTicks = Math.max(0, scratch.counterpartCooldownTicks - 1);
        scratch.directiveCooldownTicks = Math.max(0, scratch.directiveCooldownTicks - 1);
        scratch.blockScanCooldownTicks = Math.max(0, scratch.blockScanCooldownTicks - 1);
        scratch.hazardCooldownTicks = Math.max(0, scratch.hazardCooldownTicks - 1);
        scratch.actionCooldownTicks = Math.max(0, scratch.actionCooldownTicks - 1);
        scratch.navigationCooldownTicks = Math.max(0, scratch.navigationCooldownTicks - 1);
        scratch.feedbackCooldownTicks = Math.max(0, scratch.feedbackCooldownTicks - 1);
        scratch.tradeCooldownTicks = Math.max(0, scratch.tradeCooldownTicks - 1);

        final GoblinPatronState before = state(patron);
        final GoblinPatronState.Merchant merchant = before.merchant();
        GoblinPatronState updated = before
            .withCombat(before.combat().tick())
            .withAnchor(before.anchor().tick())
            .withEngagement(before.engagement().tick())
            .withAccord(before.accord().tick())
            .withPublished(before.published().tick())
            .withRoute(before.route().tick())
            .withMerchant(new GoblinPatronState.Merchant(
                merchant.level(), merchant.xp(), merchant.restocksToday(),
                Math.max(0, merchant.restockSpacingTicks() - 1), merchant.restockEpoch()
            ));
        setState(patron, updated);

        // Every transition below is owned by this branch and by nothing else.
        if (before.combat().committed() && updated.combat().actionElapsed()) {
            completeAction(patron, level, before.combat().action());
        }
        if (before.engagement().player().isPresent() && updated.engagement().windowElapsed()) {
            closeEngagement(patron, Reason.WINDOW_EXPIRED);
        }
        if (before.anchor().present() && updated.anchor().expired()) {
            setState(patron, state(patron).withAnchor(GoblinPatronState.Anchor.none()));
        }
        if (before.accord().counterpart().isPresent() && updated.accord().expired()) {
            releaseAccord(patron);
        }
        if (before.published().resultKind().isPresent() && updated.published().expired()) {
            setState(patron, state(patron).withPublished(GoblinPatronState.Published.none()));
        }
    }

    private static void holdForTrade(final PatronBody patron) {
        patron.body().getNavigation().stop();
        patron.body().setTarget(null);
        final GoblinPatronState current = state(patron);
        if (current.combat().committed()) {
            cancelAction(patron, Reason.TRADING);
        }
    }

    // ================================================================ hazard

    /**
     * Immediate entity-contact facts preempt in one tick. The charged local block observation runs
     * no faster than {@link GoblinPatronRules#HAZARD_INTERVAL_TICKS}, and a new explicit fluid or
     * fire contact marks the observation dirty rather than raising the cadence.
     */
    private static boolean tickHazard(final PatronBody patron, final ServerLevel level) {
        final AbstractGoblinMerchantEntity body = patron.body();
        final TransientState scratch = scratch(patron);
        final boolean fireImmune = GoblinPatronRules.immuneToFireHazard(patron.patronKind());
        final boolean burning = !fireImmune && (body.isOnFire() || body.isInLava());
        final boolean contact = body.getAirSupply() <= 0 || body.isFreezing();
        final boolean exposed = burning || contact;
        if (!exposed) {
            scratch.hazardActive = false;
            return false;
        }
        scratch.hazardActive = true;
        if (!GoblinPatronRules.isDue(scratch.hazardCooldownTicks)) {
            return true;
        }
        scratch.hazardCooldownTicks = GoblinPatronRules.HAZARD_INTERVAL_TICKS;
        cancelAction(patron, Reason.HAZARD_PREEMPTS);
        state(patron).anchor().position().ifPresent(anchor ->
            requestNavigation(patron, level, anchor, URGENT_SPEED));
        return true;
    }

    // ================================================================ perception

    private static void perceive(final PatronBody patron, final ServerLevel level) {
        acquireChallenger(patron, level);
        reconcileCounterpart(patron, level);
        scanContext(patron, level);
        reconcileMerchant(patron, level);
    }

    /**
     * Stable challenger acquisition. Candidate traversal, not merely the retained result, is capped
     * at {@link GoblinPatronRules#MAX_CHALLENGER_INSPECTIONS}, and the recent attacker plus the
     * current stable challenger are preseeded so a crowd cannot hide them behind the cap.
     */
    private static void acquireChallenger(final PatronBody patron, final ServerLevel level) {
        final TransientState scratch = scratch(patron);
        if (!GoblinPatronRules.isDue(scratch.perceptionCooldownTicks)) {
            return;
        }
        // Armed before anything can qualify or fail, so a pass that selects nobody still costs one
        // interval instead of retrying on every single tick forever.
        scratch.perceptionCooldownTicks = GoblinPatronRules.nextScanCadence(
            false, GoblinPatronRules.PERCEPTION_INTERVAL_TICKS
        );
        final AbstractGoblinMerchantEntity body = patron.body();
        final GoblinPatronState current = state(patron);
        final Optional<UUID> engaged = current.engagement().open()
            ? current.engagement().player()
            : Optional.empty();
        final List<Candidate> candidates = new ArrayList<>();
        for (final LivingEntity living : level.getEntitiesOfClass(
            LivingEntity.class,
            body.getBoundingBox().inflate(GoblinPatronRules.CHALLENGER_RADIUS),
            living -> living != body
        )) {
            candidates.add(new Candidate(
                living.getUUID(),
                living.isAlive(),
                isProtectedTarget(living) || engaged.filter(living.getUUID()::equals).isPresent(),
                current.combat().recentAttacker().filter(living.getUUID()::equals).isPresent(),
                current.combat().challenger().filter(living.getUUID()::equals).isPresent(),
                body.distanceToSqr(living)
            ));
        }
        final GoblinPatronRules.Selection selection = GoblinPatronRules.selectChallenger(
            candidates, GoblinPatronRules.MAX_CHALLENGER_INSPECTIONS
        );
        counters(patron).challengerVisits += selection.inspected();
        final Optional<UUID> chosen = selection.challenger();
        if (chosen.isEmpty()) {
            if (current.combat().challenger().isPresent()) {
                setState(patron, state(patron).withCombat(state(patron).combat()
                    .withChallenger(Optional.empty(), ReleaseReason.INVALID)));
                body.setTarget(null);
            }
            return;
        }
        setState(patron, state(patron).withCombat(state(patron).combat()
            .withChallenger(chosen, ReleaseReason.NONE)));
        resolveLiving(level, chosen).ifPresent(body::setTarget);
    }

    private static boolean isProtectedTarget(final LivingEntity living) {
        if (living instanceof Player player) {
            return player.isCreative() || player.isSpectator();
        }
        return living.isInvulnerable()
            || (living instanceof ArcaneCreature arcane && GoblinPatronRules.isPatron(arcane.creatureKind()));
    }

    /**
     * Mutual, one-to-one accord maintenance. Each patron scans at most eight exact counterpart
     * candidates per cadence and forms an accord only when both loaded patrons choose each other.
     */
    private static void reconcileCounterpart(final PatronBody patron, final ServerLevel level) {
        final TransientState scratch = scratch(patron);
        if (!GoblinPatronRules.isDue(scratch.counterpartCooldownTicks)) {
            return;
        }
        scratch.counterpartCooldownTicks = GoblinPatronRules.nextScanCadence(
            false, GoblinPatronRules.COUNTERPART_INTERVAL_TICKS
        );
        final AbstractGoblinMerchantEntity body = patron.body();
        final GoblinPatronState current = state(patron);
        final String dimension = dimensionOf(level);
        final List<CounterpartCandidate> candidates = new ArrayList<>();
        for (final AbstractGoblinMerchantEntity other : level.getEntitiesOfClass(
            AbstractGoblinMerchantEntity.class,
            body.getBoundingBox().inflate(GoblinPatronRules.COUNTERPART_RADIUS),
            other -> other != body && other instanceof PatronBody
        )) {
            final PatronBody counterpart = (PatronBody) other;
            candidates.add(new CounterpartCandidate(
                other.getUUID(),
                counterpart.patronKind(),
                other.isAlive(),
                dimensionOf(level).equals(dimension),
                body.distanceToSqr(other),
                state(counterpart).accord().counterpart()
                    .or(() -> preferredCounterpart(counterpart, body.getUUID()))
            ));
        }
        final GoblinPatronRules.AccordSelection selection = GoblinPatronRules.selectCounterpart(
            body.getUUID(),
            patron.patronKind(),
            current.accord().counterpart(),
            candidates,
            GoblinPatronRules.MAX_COUNTERPART_INSPECTIONS
        );
        counters(patron).counterpartVisits += selection.inspected();
        if (selection.counterpart().isEmpty()) {
            if (current.accord().counterpart().isPresent()) {
                releaseAccord(patron);
            }
            return;
        }
        final UUID chosen = selection.counterpart().orElseThrow();
        if (current.accord().counterpart().filter(chosen::equals).isPresent()) {
            setState(patron, state(patron).withAccord(state(patron).accord().refreshed()));
            return;
        }
        counterpartKind(level, chosen).ifPresent(kind -> setState(patron, state(patron).withAccord(
            GoblinPatronState.Accord.formed(
                chosen, kind, dimension, current.authorityEpoch(), current.authorityEpoch() + 1L
            )
        )));
    }

    /**
     * The candidate's own current choice, or this patron when the candidate has none yet. This is
     * what makes the accord genuinely mutual rather than one-sided: an unbonded counterpart is
     * treated as choosing the patron that is looking at it, and the counterpart's own cadence then
     * confirms or refuses the same pairing from its side.
     */
    private static Optional<UUID> preferredCounterpart(final PatronBody counterpart, final UUID self) {
        return state(counterpart).accord().counterpart().isPresent()
            ? state(counterpart).accord().counterpart()
            : Optional.of(self);
    }

    private static Optional<CreatureKind> counterpartKind(final ServerLevel level, final UUID id) {
        final Entity entity = level.getEntity(id);
        return entity instanceof ArcaneCreature arcane
            ? Optional.of(arcane.creatureKind())
            : Optional.empty();
    }

    private static void releaseAccord(final PatronBody patron) {
        setState(patron, state(patron)
            .withAccord(GoblinPatronState.Accord.none())
            .withNextAuthorityEpoch());
    }

    // ================================================================ block perception

    /**
     * The one charged patron block scan.
     *
     * <p>The read budget of 256 is far below the 25 x 9 x 25 envelope volume of 5,625, so a naive
     * raster would spend the whole budget on one corner and never reach the patron's own level or
     * the opposite quadrant. The envelope, the near anchor, and the rotating far page are the exact
     * primitives {@link GoblinEnclaveRuntime} already proved for F10; they are reused here rather
     * than re-derived, because re-deriving that search is how five families broke it.</p>
     *
     * <p>Every position inside the window is charged before the predicate can reject it, so a scan
     * over hostile terrain costs exactly what a scan over friendly terrain costs and the declared
     * cap actually binds.</p>
     */
    private static void scanContext(final PatronBody patron, final ServerLevel level) {
        final TransientState scratch = scratch(patron);
        if (!GoblinPatronRules.isDue(scratch.blockScanCooldownTicks)) {
            return;
        }
        final AbstractGoblinMerchantEntity body = patron.body();
        final BlockPos origin = body.blockPosition();
        final List<BlockPos> offsets = GoblinEnclaveRuntime.envelope(
            GoblinPatronRules.SCAN_HORIZONTAL, GoblinPatronRules.SCAN_VERTICAL
        );
        final int readCap = GoblinPatronRules.scanReadCap();
        final int tail = offsets.size() - GoblinEnclaveRuntime.anchorSize(offsets.size(), readCap);
        if (scratch.scanCursor == UNSEEDED_CURSOR) {
            scratch.scanCursor = tail == 0
                ? 0
                : GoblinPatronRules.stableOffset(body.getUUID(), tail);
        }
        final Predicate<BlockPos> accepts = contextPredicate(patron, level);
        final List<BlockPos> hits = new ArrayList<>();
        int reads = 0;
        for (final BlockPos offset : GoblinEnclaveRuntime.scanWindow(offsets, readCap, scratch.scanCursor)) {
            final BlockPos candidate = origin.offset(offset);
            if (!level.getWorldBorder().isWithinBounds(candidate) || !level.isLoaded(candidate)) {
                // A candidate whose chunk section is not already loaded is rejected before any read
                // and is therefore not charged, because no read happened.
                continue;
            }
            reads++;
            if (hits.size() < GoblinPatronRules.retentionCap() && accepts.test(candidate)) {
                hits.add(candidate.immutable());
            }
        }
        if (tail > 0) {
            scratch.scanCursor = Math.floorMod(
                scratch.scanCursor + GoblinEnclaveRuntime.pageSize(offsets.size(), readCap), tail
            );
        }
        counters(patron).chargedBlockReads += reads;
        counters(patron).candidatesRetained += hits.size();
        final Optional<BlockPos> chosen = hits.stream().min(Comparator.comparingDouble(
            position -> body.distanceToSqr(Vec3.atCenterOf(position))
        ));
        scratch.scannedContext = chosen;
        // Armed for both outcomes. A scan that qualified nothing must still cost its cadence and
        // record the miss, or the patron retries the same failed scan on every tick forever.
        scratch.blockScanCooldownTicks = GoblinPatronRules.nextScanCadence(
            chosen.isPresent(), GoblinPatronRules.BLOCK_SCAN_INTERVAL_TICKS
        );
    }

    /**
     * The two patrons scan genuinely different subjects. Stonebroker appraises worked mineral and
     * storage contexts; Forgewarden inspects forge and workstation contexts. Neither opens a block
     * entity, reads arbitrary contents, or changes anything.
     */
    private static Predicate<BlockPos> contextPredicate(final PatronBody patron, final ServerLevel level) {
        if (patron.patronKind() == CreatureKind.STONEBROKER) {
            return position -> {
                final BlockState state = level.getBlockState(position);
                return state.is(WarlockeryTags.Blocks.HOBGOBLIN_MINEABLES)
                    || state.is(CreatureBehaviorTags.Blocks.HOBGOBLIN_DEPOSIT_CONTAINERS);
            };
        }
        return position -> {
            final BlockState state = level.getBlockState(position);
            return state.is(BlockTags.ANVIL)
                || state.is(WarlockeryTags.Blocks.MACHINE_HEAT_SOURCES)
                || state.is(Blocks.BLAST_FURNACE)
                || state.is(Blocks.FURNACE)
                || state.is(Blocks.SMITHING_TABLE)
                || state.is(Blocks.GRINDSTONE);
        };
    }

    // ================================================================ merchant

    private static void reconcileMerchant(final PatronBody patron, final ServerLevel level) {
        final TransientState scratch = scratch(patron);
        if (!GoblinPatronRules.isDue(scratch.tradeCooldownTicks)) {
            return;
        }
        scratch.tradeCooldownTicks = GoblinPatronRules.TRADE_INTERVAL_TICKS;
        final GoblinPatronState current = state(patron);
        // A new game day resets the restock counter without replaying every missed restock.
        final long day = level.getGameTime() / 24_000L;
        if (day != current.merchant().restockEpoch() / 8L && current.merchant().restocksToday() > 0) {
            setState(patron, current.withMerchant(current.merchant().onNewDay()));
        }
        final GoblinPatronState refreshed = state(patron);
        final Reason eligibility = GoblinPatronRules.restockEligibility(
            refreshed.merchant().restocksToday(),
            refreshed.merchant().restockSpacingTicks(),
            !scratch.hazardActive(),
            patron.body().isTrading(),
            GoblinPatronRules.blocksTrade(refreshed.combat().action())
        );
        if (eligibility != Reason.OK || patron.body().getOffers().isEmpty()) {
            return;
        }
        setState(patron, refreshed.withMerchant(refreshed.merchant().afterRestock()));
        patron.body().getOffers().forEach(offer -> offer.resetUses());
    }

    /** Consulted from each body's {@code safeToTrade}, itself reached from merchant interaction. */
    public static boolean safeToTrade(final PatronBody patron) {
        final GoblinPatronState current = state(patron);
        return GoblinPatronRules.tradeEligibility(
            patron.body().isAlive(),
            false,
            scratch(patron).hazardActive(),
            GoblinPatronRules.blocksTrade(current.combat().action()),
            current.combat().withdrawing()
        ) == Reason.OK;
    }

    /** Called from each body's {@code mobInteract} the moment a trade window actually opens. */
    public static void onTradeOpened(final PatronBody patron) {
        patron.body().getNavigation().stop();
        patron.body().setTarget(null);
        cancelAction(patron, Reason.TRADING);
        setState(patron, state(patron).withCombat(
            state(patron).combat().withChallenger(Optional.empty(), ReleaseReason.TRADE)
        ));
    }

    // ================================================================ decision

    private static void decide(final PatronBody patron, final ServerLevel level) {
        final TransientState scratch = scratch(patron);
        final GoblinPatronState current = state(patron);
        if (current.combat().committed() || current.combat().recovering()) {
            return;
        }
        if (!GoblinPatronRules.isDue(scratch.actionCooldownTicks)) {
            return;
        }
        scratch.actionCooldownTicks = GoblinPatronRules.ACTION_INTERVAL_TICKS;
        final AbstractGoblinMerchantEntity body = patron.body();
        final Optional<LivingEntity> challenger = resolveLiving(level, current.combat().challenger());
        final boolean safePoint = current.anchor().present();
        final boolean withdrawing = GoblinPatronRules.withdrawing(
            current.combat().withdrawing(), body.getHealth(), body.getMaxHealth(), safePoint
        );
        if (withdrawing != current.combat().withdrawing()) {
            setState(patron, state(patron).withCombat(state(patron).combat().withWithdrawing(withdrawing)));
        }
        if (challenger.isEmpty()) {
            selectAmbient(patron, level);
            return;
        }
        final LivingEntity target = challenger.orElseThrow();
        final Decision decision = GoblinPatronRules.nextAction(new CombatFacts(
            patron.patronKind(),
            GoblinPatronRules.phase(body.getHealth(), body.getMaxHealth()),
            true,
            body.getSensing().hasLineOfSight(target),
            body.distanceToSqr(target),
            body.isWithinMeleeAttackRange(target),
            state(patron).combat().signatureGapTicks(),
            state(patron).combat().secondaryGapTicks(),
            withdrawing,
            scratch.accordSubjectThreatened
        ));
        decision.action().ifPresent(action -> startAction(patron, action, Optional.of(target.getUUID())));
        if (decision.action().isEmpty()) {
            // Ordinary approach keeps a real melee window open rather than deadlocking on a
            // refused signature action.
            approach(patron, level, target);
        }
    }

    /**
     * The ambient half of the ladder. Both patrons run one bounded vocation action derived from
     * their own scanned context, and neither ever runs the other's.
     */
    private static void selectAmbient(final PatronBody patron, final ServerLevel level) {
        final GoblinPatronState current = state(patron);
        if (current.engagement().open()) {
            startAction(patron, GoblinPatronRules.windowAction(patron.patronKind()), Optional.empty());
            return;
        }
        final Optional<BlockPos> context = scratch(patron).scannedContext;
        if (context.isPresent() && !current.anchor().present()) {
            startAction(
                patron,
                patron.patronKind() == CreatureKind.STONEBROKER
                    ? Action.APPRAISE_CONTEXT
                    : Action.INSPECT_FORGE,
                Optional.empty()
            );
            return;
        }
        if (current.anchor().present()) {
            startAction(
                patron,
                patron.patronKind() == CreatureKind.STONEBROKER ? Action.WATCH_CLAIM : Action.WARD_STANCE,
                Optional.empty()
            );
            return;
        }
        if (patron.patronKind() == CreatureKind.STONEBROKER && current.empowerment().level() > 0) {
            startAction(patron, Action.QUIET_LEDGER, Optional.empty());
        }
    }

    private static void startAction(
        final PatronBody patron,
        final Action action,
        final Optional<UUID> target
    ) {
        if (!GoblinPatronRules.permits(patron.patronKind(), action)) {
            return;
        }
        final GoblinPatronState current = state(patron).withNextActionEpoch();
        final int tell = GoblinPatronRules.tellTicks(action);
        final int commit = tell + Math.max(20, GoblinPatronRules.recoveryTicks(action));
        final GoblinPatronState.Combat combat = current.combat();
        setState(patron, current.withCombat(new GoblinPatronState.Combat(
            action, target, tell, commit, combat.recoveryRemainingTicks(), combat.lastCompleted(),
            combat.signatureGapTicks(), combat.secondaryGapTicks(),
            action == Action.WARD_STANCE ? GoblinPatronRules.WARD_STANCE_TICKS : combat.stanceRemainingTicks(),
            combat.challenger(), combat.recentAttacker(), combat.recentAttackerDimension(),
            combat.recentAttackerTicks(), combat.lastRelease(), combat.withdrawing(),
            action == Action.LEDGER_VOLLEY
                ? GoblinPatronRules.volleyArrows(GoblinPatronRules.phase(
                    patron.body().getHealth(), patron.body().getMaxHealth()))
                : 0
        )));
        counters(patron).actionsStarted++;
        emitTell(patron, action);
    }

    /** Ends a completed action and arms its recovery plus its own phase gap. Tick-owned only. */
    private static void completeAction(final PatronBody patron, final ServerLevel level, final Action action) {
        final GoblinPatronState current = state(patron);
        final int signatureGap = isSignature(patron.patronKind(), action)
            ? GoblinPatronRules.signatureGapTicks(
                patron.patronKind(),
                GoblinPatronRules.phase(patron.body().getHealth(), patron.body().getMaxHealth()))
            : 0;
        setState(patron, current.withCombat(current.combat().completed(
            action, GoblinPatronRules.recoveryTicks(action), signatureGap
        )));
        releaseNavigationLease(patron);
        counters(patron).actionsCommitted++;
    }

    private static boolean isSignature(final CreatureKind kind, final Action action) {
        return kind == CreatureKind.STONEBROKER
            ? action == Action.LEDGER_VOLLEY
            : action == Action.FORGE_SURGE;
    }

    /**
     * Cancels the current action before any later commit. Cancellation clears navigation, the
     * pending arrows or surge, the tell state, and the action epoch, so no effect from the
     * cancelled epoch can ever land.
     */
    private static void cancelAction(final PatronBody patron, final Reason reason) {
        final GoblinPatronState current = state(patron);
        if (!current.combat().committed()) {
            return;
        }
        setState(patron, current
            .withCombat(current.combat().completed(Action.IDLE, 0, 0))
            .withNextActionEpoch());
        releaseNavigationLease(patron);
        counters(patron).actionsCancelled++;
    }

    // ================================================================ execution

    private static void execute(final PatronBody patron, final ServerLevel level) {
        final GoblinPatronState current = state(patron);
        final Action action = current.combat().action();
        if (action == Action.IDLE || action == Action.TRADE_HOLD) {
            return;
        }
        switch (action) {
            case LEDGER_VOLLEY -> executeVolley(patron, level);
            case CLAIM_SHIFT -> executeShift(patron, level);
            case ORDERLY_WITHDRAWAL -> executeWithdrawal(patron, level);
            case APPRAISE_CONTEXT, INSPECT_FORGE -> executeContextInspection(patron, level);
            case WATCH_CLAIM, REGROUP -> executeHoldAnchor(patron, level);
            case QUIET_LEDGER -> executeQuietLedger(patron, level);
            case PARLEY, COMMISSION -> executeWindow(patron, level);
            case WARD_STANCE -> executeWardStance(patron);
            case INTERPOSE -> executeInterpose(patron, level);
            case HAMMER_COMMIT -> executeHammer(patron, level);
            case FORGE_SURGE -> executeSurge(patron, level);
            default -> {
            }
        }
    }

    /**
     * The Stonebroker signature. The tell runs first and no arrow exists until it has elapsed;
     * every commit revalidates loaded state, eligibility, line of sight, and the action epoch.
     */
    private static void executeVolley(final PatronBody patron, final ServerLevel level) {
        final GoblinPatronState current = state(patron);
        if (current.combat().telling()) {
            state(patron).combat().actionTarget()
                .flatMap(id -> resolveLiving(level, Optional.of(id)))
                .ifPresent(target -> patron.body().getLookControl().setLookAt(target, 30.0F, 30.0F));
            return;
        }
        final Optional<LivingEntity> target = resolveLiving(level, current.combat().actionTarget());
        if (target.isEmpty() || !target.orElseThrow().isAlive()
            || isProtectedTarget(target.orElseThrow())) {
            cancelAction(patron, Reason.TARGET_INVALID);
            return;
        }
        final LivingEntity victim = target.orElseThrow();
        if (!patron.body().getSensing().hasLineOfSight(victim)) {
            cancelAction(patron, Reason.NO_LINE_OF_SIGHT);
            return;
        }
        if (current.combat().arrowsRemaining() <= 0) {
            completeAction(patron, level, Action.LEDGER_VOLLEY);
            return;
        }
        if (current.combat().commitRemainingTicks() % GoblinPatronRules.VOLLEY_ARROW_SPACING_TICKS != 0) {
            return;
        }
        fireArrow(patron.body(), victim, level);
        counters(patron).arrowsFired++;
        final GoblinPatronState.Combat combat = state(patron).combat();
        setState(patron, state(patron).withCombat(new GoblinPatronState.Combat(
            combat.action(), combat.actionTarget(), combat.tellRemainingTicks(),
            combat.commitRemainingTicks(), combat.recoveryRemainingTicks(), combat.lastCompleted(),
            combat.signatureGapTicks(), combat.secondaryGapTicks(), combat.stanceRemainingTicks(),
            combat.challenger(), combat.recentAttacker(), combat.recentAttackerDimension(),
            combat.recentAttackerTicks(), combat.lastRelease(), combat.withdrawing(),
            combat.arrowsRemaining() - 1
        )));
    }

    /**
     * Path movement, never teleportation. Every candidate stand is charged and validated against
     * the loaded world, and a rejected path releases the lease in the same tick.
     */
    private static void executeShift(final PatronBody patron, final ServerLevel level) {
        if (state(patron).combat().telling()) {
            return;
        }
        final Optional<BlockPos> stand = safeShiftPosition(patron, level);
        if (stand.isEmpty()) {
            cancelAction(patron, Reason.NO_CANDIDATE);
            return;
        }
        requestNavigation(patron, level, stand.orElseThrow(), APPROACH_SPEED);
        completeAction(patron, level, Action.CLAIM_SHIFT);
    }

    private static Optional<BlockPos> safeShiftPosition(final PatronBody patron, final ServerLevel level) {
        final AbstractGoblinMerchantEntity body = patron.body();
        final BlockPos origin = body.blockPosition();
        int reads = 0;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (final BlockPos offset : SHIFT_OFFSETS) {
            final BlockPos candidate = origin.offset(offset);
            if (!level.isLoaded(candidate) || !level.getWorldBorder().isWithinBounds(candidate)) {
                continue;
            }
            // Charged before the safety filter, so rejected stands cost exactly what accepted ones do.
            reads += 3;
            if (!isSafeStand(level, body, candidate)) {
                continue;
            }
            final double distance = body.distanceToSqr(Vec3.atCenterOf(candidate));
            if (distance >= GoblinPatronRules.SHIFT_MIN_DISTANCE * GoblinPatronRules.SHIFT_MIN_DISTANCE
                && distance <= GoblinPatronRules.SHIFT_MAX_DISTANCE * GoblinPatronRules.SHIFT_MAX_DISTANCE
                && distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        counters(patron).chargedBlockReads += reads;
        return Optional.ofNullable(best);
    }

    private static boolean isSafeStand(
        final ServerLevel level,
        final AbstractGoblinMerchantEntity body,
        final BlockPos candidate
    ) {
        final BlockPos below = candidate.below();
        final BlockPos above = candidate.above();
        if (!level.isLoaded(below) || !level.isLoaded(above)) {
            return false;
        }
        if (!level.getFluidState(candidate).isEmpty()) {
            return false;
        }
        if (level.getBlockState(below).isAir()) {
            return false;
        }
        final AABB footprint = body.getBoundingBox().move(
            candidate.getX() + 0.5D - body.getX(),
            candidate.getY() - body.getY(),
            candidate.getZ() + 0.5D - body.getZ()
        );
        return level.noCollision(body, footprint);
    }

    private static void executeWithdrawal(final PatronBody patron, final ServerLevel level) {
        final Optional<BlockPos> refuge = state(patron).anchor().position();
        if (refuge.isEmpty()) {
            cancelAction(patron, Reason.NO_CANDIDATE);
            return;
        }
        requestNavigation(patron, level, refuge.orElseThrow(), WITHDRAW_SPEED);
        if (patron.body().distanceToSqr(Vec3.atCenterOf(refuge.orElseThrow())) <= 9.0D) {
            completeAction(patron, level, Action.ORDERLY_WITHDRAWAL);
        }
    }

    /**
     * Identifies one loaded tagged context and stores an expiring anchor. It never opens the block
     * entity, reads arbitrary contents, withdraws items, mines, places, breaks, or claims anything.
     */
    private static void executeContextInspection(final PatronBody patron, final ServerLevel level) {
        final Optional<BlockPos> context = scratch(patron).scannedContext;
        if (context.isEmpty() || !level.isLoaded(context.orElseThrow())) {
            cancelAction(patron, Reason.TARGET_INVALID);
            return;
        }
        final BlockPos position = context.orElseThrow();
        patron.body().getLookControl().setLookAt(Vec3.atCenterOf(position));
        setState(patron, state(patron).withAnchor(
            GoblinPatronState.Anchor.at(position, dimensionOf(level))
        ));
        completeAction(patron, level, state(patron).combat().action());
    }

    private static void executeHoldAnchor(final PatronBody patron, final ServerLevel level) {
        final Optional<BlockPos> anchor = state(patron).anchor().position();
        if (anchor.isEmpty()) {
            cancelAction(patron, Reason.TARGET_INVALID);
            return;
        }
        final BlockPos position = anchor.orElseThrow();
        if (patron.body().distanceToSqr(Vec3.atCenterOf(position))
            > GoblinPatronRules.WATCH_RADIUS * GoblinPatronRules.WATCH_RADIUS) {
            requestNavigation(patron, level, position, APPROACH_SPEED);
            return;
        }
        patron.body().getLookControl().setLookAt(Vec3.atCenterOf(position));
    }

    private static void executeQuietLedger(final PatronBody patron, final ServerLevel level) {
        state(patron).engagement().player()
            .flatMap(id -> resolveLiving(level, Optional.of(id)))
            .ifPresent(counterparty -> patron.body().getLookControl()
                .setLookAt(counterparty, 30.0F, 30.0F));
    }

    private static void executeWindow(final PatronBody patron, final ServerLevel level) {
        // The window suspends challenge movement against its holder and permits trade. It creates
        // no immunity, no ownership, no follow, and no permanent peace.
        patron.body().getNavigation().stop();
        state(patron).engagement().player()
            .flatMap(id -> resolveLiving(level, Optional.of(id)))
            .ifPresent(holder -> patron.body().getLookControl().setLookAt(holder, 30.0F, 30.0F));
        if (!state(patron).engagement().open()) {
            completeAction(patron, level, state(patron).combat().action());
        }
    }

    private static void executeWardStance(final PatronBody patron) {
        patron.body().getNavigation().stop();
        if (!state(patron).combat().stanceActive()) {
            final GoblinPatronState current = state(patron);
            setState(patron, current.withCombat(current.combat().completed(
                Action.WARD_STANCE, GoblinPatronRules.WARD_STANCE_GAP_TICKS, 0
            )));
        }
    }

    /**
     * Chooses only the loaded direct attacker of the accorded Stonebroker and requests a bounded
     * position between that attacker and the ward subject. It never inherits a remote target.
     */
    private static void executeInterpose(final PatronBody patron, final ServerLevel level) {
        final Optional<LivingEntity> subject = resolveLiving(level, state(patron).accord().counterpart());
        final Optional<LivingEntity> attacker = resolveLiving(level, state(patron).combat().challenger());
        if (subject.isEmpty() || attacker.isEmpty()) {
            cancelAction(patron, Reason.TARGET_INVALID);
            return;
        }
        final Vec3 midpoint = subject.orElseThrow().position()
            .add(attacker.orElseThrow().position()).scale(0.5D);
        requestNavigation(patron, level, BlockPos.containing(midpoint), URGENT_SPEED);
    }

    /**
     * The Forgewarden signature melee. The runtime owns the approach until actual melee reach and
     * line of sight; the attack-only executor on the body commits the hit and the fire and launch
     * riders land only after accepted damage.
     */
    private static void executeHammer(final PatronBody patron, final ServerLevel level) {
        if (state(patron).combat().telling()) {
            return;
        }
        final Optional<LivingEntity> target = resolveLiving(level, state(patron).combat().actionTarget());
        if (target.isEmpty() || isProtectedTarget(target.orElseThrow())) {
            cancelAction(patron, Reason.TARGET_INVALID);
            return;
        }
        final LivingEntity victim = target.orElseThrow();
        // Being geometrically inside attack range without line of sight is not a successful
        // handoff: the runtime keeps the approach rather than letting the executor stall.
        if (!patron.body().isWithinMeleeAttackRange(victim)
            || !patron.body().getSensing().hasLineOfSight(victim)) {
            approach(patron, level, victim);
            return;
        }
        patron.body().setTarget(victim);
        completeAction(patron, level, Action.HAMMER_COMMIT);
    }

    /**
     * The Forgewarden area signature. It inspects at most sixteen living candidates inside one
     * already-loaded radius-4 box at commit, attributes its damage, applies knockback and fire only
     * after accepted damage, and performs zero block ignition, break, placement, fluid, or
     * block-entity operations.
     */
    private static void executeSurge(final PatronBody patron, final ServerLevel level) {
        if (state(patron).combat().telling()) {
            patron.body().getNavigation().stop();
            return;
        }
        final AbstractGoblinMerchantEntity body = patron.body();
        int inspected = 0;
        for (final LivingEntity living : level.getEntitiesOfClass(
            LivingEntity.class,
            body.getBoundingBox().inflate(GoblinPatronRules.SURGE_RADIUS),
            living -> living != body
        )) {
            if (inspected >= GoblinPatronRules.MAX_SURGE_INSPECTIONS) {
                break;
            }
            // Charged before any eligibility filter can reject the candidate.
            inspected++;
            if (!living.isAlive() || isProtectedTarget(living)
                || !body.getSensing().hasLineOfSight(living)) {
                continue;
            }
            final DamageSource surge = body.damageSources().mobAttack(body);
            final boolean accepted = living.hurtServer(level, surge, SURGE_DAMAGE);
            if (accepted) {
                living.knockback(
                    0.4D, body.getX() - living.getX(), body.getZ() - living.getZ(), surge, SURGE_DAMAGE
                );
                living.igniteForSeconds(HAMMER_FIRE_SECONDS);
            }
        }
        counters(patron).surgeVisits += inspected;
        counters(patron).surgesCommitted++;
        level.sendParticles(ParticleTypes.LAVA, body.getX(), body.getY() + 1.0D, body.getZ(),
            8, 0.4D, 0.4D, 0.4D, 0.02D);
        completeAction(patron, level, Action.FORGE_SURGE);
    }

    private static void approach(
        final PatronBody patron,
        final ServerLevel level,
        final LivingEntity target
    ) {
        patron.body().getLookControl().setLookAt(target, 30.0F, 30.0F);
        requestNavigation(patron, level, target.blockPosition(), APPROACH_SPEED);
    }

    // ================================================================ navigation

    /**
     * The only place an ordinary patron path is created. One lease records the owning action epoch
     * and is released in the same tick on success, rejection, or the third classified failure.
     */
    private static void requestNavigation(
        final PatronBody patron,
        final ServerLevel level,
        final BlockPos destination,
        final double speed
    ) {
        final TransientState scratch = scratch(patron);
        final GoblinPatronState current = state(patron);
        if (GoblinPatronRules.routeEligibility(
            scratch.navigationCooldownTicks, current.route().retryRemainingTicks()
        ) != Reason.OK) {
            return;
        }
        if (!level.getWorldBorder().isWithinBounds(destination) || !level.isLoaded(destination)) {
            recordRouteFailure(patron, RouteFailure.UNREACHABLE);
            return;
        }
        scratch.navigationCooldownTicks = GoblinPatronRules.NAVIGATION_INTERVAL_TICKS;
        counters(patron).navigationRequests++;
        final var path = patron.body().getNavigation().createPath(destination, 0);
        if (path == null || !path.canReach()) {
            recordRouteFailure(patron, RouteFailure.NO_PATH);
            return;
        }
        if (!patron.body().getNavigation().moveTo(path, speed)) {
            recordRouteFailure(patron, RouteFailure.REJECTED);
            return;
        }
        // A successful movement clears the prior classified failures before the action finishes.
        setState(patron, state(patron).withRoute(new GoblinPatronState.Route(
            Optional.of(destination), Optional.of(dimensionOf(level)),
            state(patron).actionEpoch(), RouteFailure.NONE, 0, 0, false
        ).succeeded()));
    }

    private static void recordRouteFailure(final PatronBody patron, final RouteFailure failure) {
        counters(patron).navigationFailures++;
        setState(patron, state(patron).withRoute(state(patron).route().failed(failure)));
        if (state(patron).route().failureCount() >= GoblinPatronRules.MAX_ROUTE_FAILURES) {
            patron.body().getNavigation().stop();
            cancelAction(patron, Reason.ROUTE_BACKOFF);
        }
    }

    private static void releaseNavigationLease(final PatronBody patron) {
        patron.body().getNavigation().stop();
        final GoblinPatronState.Route route = state(patron).route();
        setState(patron, state(patron).withRoute(new GoblinPatronState.Route(
            Optional.empty(), Optional.empty(), 0L, route.lastFailure(), route.failureCount(),
            route.retryRemainingTicks(), route.stuck()
        )));
    }

    // ================================================================ directives

    /**
     * Publishes at most one current immutable local result. F10 and F11 query it on their own
     * cadence and are free to ignore it; nothing here reaches into their navigation or state.
     */
    private static void publishDirective(final PatronBody patron, final ServerLevel level) {
        final TransientState scratch = scratch(patron);
        if (!GoblinPatronRules.isDue(scratch.directiveCooldownTicks)) {
            return;
        }
        scratch.directiveCooldownTicks = GoblinPatronRules.nextScanCadence(
            false, GoblinPatronRules.DIRECTIVE_INTERVAL_TICKS
        );
        final GoblinPatronState current = state(patron);
        if (!current.anchor().present()) {
            if (current.published().resultKind().isPresent()) {
                setState(patron, current.withPublished(GoblinPatronState.Published.none()));
            }
            return;
        }
        setState(patron, state(patron).withPublished(new GoblinPatronState.Published(
            current.published().resultEpoch() + 1L,
            Optional.of(GoblinPatronRules.directiveKind(patron.patronKind())),
            current.anchor().position(),
            Optional.of(dimensionOf(level)),
            current.combat().challenger(),
            GoblinPatronRules.DIRECTIVE_EXPIRY_TICKS
        )));
    }

    /**
     * The exact read side of the F10/F11 boundary. A recipient calls this for one loaded local
     * patron it has already found on its own cadence, and receives an immutable value or nothing.
     */
    public static Optional<GoblinPatronDirective> directiveOf(
        final PatronBody patron,
        final ServerLevel level
    ) {
        final GoblinPatronState current = state(patron);
        final GoblinPatronState.Published published = current.published();
        if (!published.present() || published.anchor().isEmpty() || published.dimension().isEmpty()) {
            return Optional.empty();
        }
        final long now = level.getGameTime();
        return Optional.of(new GoblinPatronDirective(
            patron.body().getUUID(),
            patron.patronKind(),
            current.authorityEpoch(),
            published.resultEpoch(),
            published.resultKind().orElseThrow(),
            published.dimension().orElseThrow(),
            published.anchor().orElseThrow(),
            published.challenger(),
            now,
            now + published.remainingTicks()
        ));
    }

    /**
     * The bounded local query F10 and F11 use. It inspects at most sixteen already-loaded exact
     * patrons inside one radius and charges every one of them before any filter runs.
     */
    public static List<GoblinPatronDirective> localDirectives(
        final Mob recipient,
        final ServerLevel level
    ) {
        final List<GoblinPatronDirective> directives = new ArrayList<>();
        int inspected = 0;
        for (final AbstractGoblinMerchantEntity candidate : level.getEntitiesOfClass(
            AbstractGoblinMerchantEntity.class,
            recipient.getBoundingBox().inflate(GoblinPatronRules.DIRECTIVE_RADIUS),
            candidate -> candidate != recipient && candidate instanceof PatronBody
        )) {
            if (inspected >= GoblinPatronRules.MAX_DIRECTIVE_INSPECTIONS) {
                break;
            }
            inspected++;
            if (!candidate.isAlive()) {
                continue;
            }
            directiveOf((PatronBody) candidate, level).ifPresent(directives::add);
        }
        return List.copyOf(directives);
    }

    // ================================================================ interaction

    /**
     * The server-authoritative heart offering, which precedes ordinary trade.
     *
     * <p>The shared contract behavior is still the empowerment mechanic, because its exact caps,
     * attribute deltas, item consumption, and localized messages are public surface. It runs inside
     * the contract-latch guard the two bodies own: {@code CreatureBehaviorRuntime.bindCompanion}
     * ends in the one-way {@code setPersistenceRequired()} and there is no clearing setter, so any
     * binding path reached from here has to refuse that one write or the unclearable latch returns
     * silently. It fails quietly rather than at compile time, so the guard is established here as
     * well as at the two call sites.</p>
     */
    public static InteractionResult interact(
        final PatronBody patron,
        final CreatureBehavior contractBehavior,
        final Player player,
        final InteractionHand hand
    ) {
        final AbstractGoblinMerchantEntity body = patron.body();
        if (!(body.level() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }
        final ItemStack held = player.getItemInHand(hand);
        if (!held.is(CreatureBehaviorTags.Items.HEART_OFFERINGS)) {
            return InteractionResult.PASS;
        }
        final OfferingResult outcome = GoblinPatronRules.offerHeart(
            patron.patronKind(), state(patron).empowerment().level()
        );
        final InteractionResult contractResult;
        patron.patronCore().setContractLatchSuppressed(true);
        try {
            contractResult = contractBehavior.interact(body, player, hand);
        } finally {
            patron.patronCore().setContractLatchSuppressed(false);
        }
        if (!outcome.accepted() || contractResult == InteractionResult.PASS) {
            return contractResult;
        }
        final GoblinPatronState current = state(patron);
        setState(patron, current
            .withEmpowerment(new GoblinPatronState.Empowerment(
                outcome.empowermentAfter(),
                GoblinPatronRules.recordFact(
                    current.empowerment().facts(), player.getUUID(), OfferingEvent.OFFERED
                )
            ))
            .withEngagement(GoblinPatronState.Engagement.opened(
                player.getUUID(), outcome.windowTicks()
            )));
        // The window holder stops being a challenge subject for as long as the window is open.
        if (current.combat().challenger().filter(player.getUUID()::equals).isPresent()) {
            setState(patron, state(patron).withCombat(state(patron).combat()
                .withChallenger(Optional.empty(), ReleaseReason.PARLEY)));
            body.setTarget(null);
        }
        startAction(patron, GoblinPatronRules.windowAction(patron.patronKind()), Optional.empty());
        return contractResult;
    }

    private static void closeEngagement(final PatronBody patron, final Reason reason) {
        setState(patron, state(patron).withEngagement(GoblinPatronState.Engagement.none()));
        if (state(patron).combat().action() == GoblinPatronRules.windowAction(patron.patronKind())) {
            cancelAction(patron, reason);
        }
    }

    // ================================================================ damage hooks

    /**
     * Records the direct attacker, breaches an open window in the same tick, and applies the ward
     * reduction when this patron is a protected Stonebroker.
     */
    public static void onAcceptedDamage(
        final PatronBody patron,
        final ServerLevel level,
        final DamageSource source
    ) {
        if (!(source.getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        final GoblinPatronState current = state(patron);
        setState(patron, current.withCombat(new GoblinPatronState.Combat(
            current.combat().action(), current.combat().actionTarget(),
            current.combat().tellRemainingTicks(), current.combat().commitRemainingTicks(),
            current.combat().recoveryRemainingTicks(), current.combat().lastCompleted(),
            current.combat().signatureGapTicks(), current.combat().secondaryGapTicks(),
            current.combat().stanceRemainingTicks(), current.combat().challenger(),
            Optional.of(attacker.getUUID()), Optional.of(dimensionOf(level)),
            GoblinPatronRules.FACT_EXPIRY_TICKS / 10, current.combat().lastRelease(),
            current.combat().withdrawing(), current.combat().arrowsRemaining()
        )));
        if (GoblinPatronRules.breaches(current.engagement().player(), attacker.getUUID())) {
            setState(patron, state(patron).withEmpowerment(new GoblinPatronState.Empowerment(
                state(patron).empowerment().level(),
                GoblinPatronRules.recordFact(
                    state(patron).empowerment().facts(), attacker.getUUID(), OfferingEvent.BREACHED
                )
            )));
            closeEngagement(patron, Reason.BREACHED);
            if (patron.body().isTrading()) {
                patron.body().setTradingPlayer(null);
            }
        }
        // Publishing the shared mark is a Stonebroker privilege; Forgewarden only consumes it.
        if (patron.patronKind() == CreatureKind.STONEBROKER && state(patron).accord().present()) {
            setState(patron, state(patron).withAccord(
                state(patron).accord().withSharedChallenger(Optional.of(attacker.getUUID()))
            ));
        }
        markAccordSubjectThreatened(patron, level, attacker);
    }

    /**
     * Tells an accorded Forgewarden that its ward subject is under attack. This is the only inbound
     * signal the accord carries, and it supplies no target, path, or effect by itself.
     */
    private static void markAccordSubjectThreatened(
        final PatronBody patron,
        final ServerLevel level,
        final LivingEntity attacker
    ) {
        state(patron).accord().counterpart().ifPresent(counterpartId -> {
            final Entity counterpart = level.getEntity(counterpartId);
            if (counterpart instanceof PatronBody other
                && other.patronKind() == CreatureKind.FORGEWARDEN
                && state(other).accord().counterpart()
                    .filter(patron.body().getUUID()::equals).isPresent()) {
                scratch(other).accordSubjectThreatened = true;
                setState(other, state(other).withAccord(
                    state(other).accord().withSharedChallenger(Optional.of(attacker.getUUID()))
                ));
            }
        });
    }

    /**
     * The exact damage a ward stance removes from an accorded Stonebroker. Reads only, so the
     * caller keeps ownership of the event it is adjusting.
     */
    public static float wardReductionFor(
        final PatronBody subject,
        final ServerLevel level,
        final DamageSource source
    ) {
        final GoblinPatronState current = state(subject);
        final Optional<UUID> counterpartId = current.accord().counterpart();
        if (counterpartId.isEmpty() || !(source.getEntity() instanceof LivingEntity)) {
            return 0.0F;
        }
        final Entity counterpart = level.getEntity(counterpartId.orElseThrow());
        if (!(counterpart instanceof PatronBody warden)
            || warden.patronKind() != CreatureKind.FORGEWARDEN) {
            return 0.0F;
        }
        final Reason accordState = GoblinPatronRules.accordUsable(
            counterpart.isAlive() && subject.body().isAlive(),
            dimensionOf(level).equals(current.accord().dimension().orElse("")),
            state(warden).accord().counterpart().filter(subject.body().getUUID()::equals).isPresent(),
            current.accord().remainingTicks(),
            subject.body().distanceToSqr(counterpart),
            GoblinPatronRules.WARD_PROTECTION_DISTANCE
        );
        return GoblinPatronRules.wardReduction(
            subject.patronKind(),
            state(warden).combat().stanceActive(),
            accordState,
            true
        );
    }

    /** The transient shared-challenger bonus, applied only through the primary attack modifier. */
    public static float attackDamageBonus(
        final PatronBody patron,
        final ServerLevel level,
        final Entity target
    ) {
        final GoblinPatronState current = state(patron);
        if (!current.accord().present()) {
            return 0.0F;
        }
        final Optional<UUID> counterpartId = current.accord().counterpart();
        final Entity counterpart = counterpartId.map(level::getEntity).orElse(null);
        final Reason accordState = GoblinPatronRules.accordUsable(
            counterpart != null && counterpart.isAlive(),
            dimensionOf(level).equals(current.accord().dimension().orElse("")),
            counterpart instanceof PatronBody other
                && state(other).accord().counterpart().filter(patron.body().getUUID()::equals).isPresent(),
            current.accord().remainingTicks(),
            counterpart == null ? Double.MAX_VALUE : patron.body().distanceToSqr(counterpart),
            GoblinPatronRules.ACCORD_EFFECT_DISTANCE
        );
        return GoblinPatronRules.sharedChallengerBonus(
            patron.patronKind(), current.accord().sharedChallenger(), target.getUUID(), accordState
        );
    }

    /**
     * The Forgewarden hammer riders, applied only after the melee hit was actually accepted. A
     * rejected hit adds no fire and no launch.
     */
    public static void afterAttack(final PatronBody patron, final Entity target) {
        if (patron.patronKind() != CreatureKind.FORGEWARDEN || !(target instanceof LivingEntity living)) {
            return;
        }
        living.igniteForSeconds(HAMMER_FIRE_SECONDS);
        living.push(0.0D, HAMMER_LAUNCH, 0.0D);
    }

    /** Every patron refuses a target the rules classify as protected. */
    public static boolean canAttack(final PatronBody patron, final LivingEntity target) {
        if (isProtectedTarget(target)) {
            return false;
        }
        return state(patron).engagement().player()
            .filter(target.getUUID()::equals)
            .isEmpty();
    }

    // ================================================================ lifecycle hooks

    /** Called from each body's {@code remove}: the local link never outlives the patron. */
    public static void onRemoved(final PatronBody patron) {
        setState(patron, state(patron).releasedLocalState());
        patron.patronCore().bossEvent().removeAllPlayers();
    }

    private static void updateBossBar(final PatronBody patron) {
        final AbstractGoblinMerchantEntity body = patron.body();
        final ServerBossEvent event = patron.patronCore().bossEvent();
        event.setProgress(Math.clamp(body.getHealth() / Math.max(1.0F, body.getMaxHealth()), 0.0F, 1.0F));
        event.setVisible(body.isAlive());
    }

    // ================================================================ feedback

    private static void emitTell(final PatronBody patron, final Action action) {
        if (!GoblinPatronRules.isTelegraphed(action) || !(patron.body().level() instanceof ServerLevel level)) {
            return;
        }
        final AbstractGoblinMerchantEntity body = patron.body();
        if (patron.patronKind() == CreatureKind.STONEBROKER) {
            body.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.1F);
            level.sendParticles(ParticleTypes.ENCHANT, body.getX(), body.getY() + 1.6D, body.getZ(),
                6, 0.3D, 0.3D, 0.3D, 0.01D);
            return;
        }
        body.playSound(SoundEvents.ANVIL_LAND, 0.6F, 1.4F);
        level.sendParticles(ParticleTypes.CRIT, body.getX(), body.getY() + 1.6D, body.getZ(),
            6, 0.3D, 0.3D, 0.3D, 0.01D);
    }

    private static void emitFeedback(final PatronBody patron, final ServerLevel level) {
        final TransientState scratch = scratch(patron);
        if (!GoblinPatronRules.isDue(scratch.feedbackCooldownTicks)) {
            return;
        }
        scratch.feedbackCooldownTicks = GoblinPatronRules.FEEDBACK_INTERVAL_TICKS;
        counters(patron).feedbackPulses++;
        if (!state(patron).anchor().present()) {
            return;
        }
        final AbstractGoblinMerchantEntity body = patron.body();
        level.sendParticles(
            patron.patronKind() == CreatureKind.STONEBROKER ? ParticleTypes.ENCHANT : ParticleTypes.SMOKE,
            body.getX(), body.getY() + 2.0D, body.getZ(), 2, 0.2D, 0.2D, 0.2D, 0.0D
        );
    }

    // ================================================================ shared helpers

    private static Optional<LivingEntity> resolveLiving(final ServerLevel level, final Optional<UUID> id) {
        return id.map(level::getEntity)
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .filter(Entity::isAlive);
    }

    // ================================================================ shared body members

    /**
     * The exact executor set both patron bodies register: float, an attack-only melee commit, a
     * player look, and a LOOK-only redeclared random look. Not one of them declares {@code MOVE},
     * so nothing can ever contend with this runtime for navigation.
     *
     * <p>It lives here so the two bodies register one implementation rather than two copies. A
     * copy-pasted executor set is how a two-entity family shipped one search defect twice.</p>
     */
    public static <T extends AbstractGoblinMerchantEntity & PatronBody> void registerPatronGoals(
        final T patron,
        final GoalSelector goalSelector
    ) {
        goalSelector.addGoal(0, new FloatGoal(patron));
        goalSelector.addGoal(1, new AttackOnlyMeleeGoal(patron));
        goalSelector.addGoal(8, new LookAtPlayerGoal(patron, Player.class, 12.0F));
        goalSelector.addGoal(9, new LookOnlyRandomLookGoal(patron));
    }

    private static final class LookOnlyRandomLookGoal extends RandomLookAroundGoal {
        private LookOnlyRandomLookGoal(final Mob mob) {
            super(mob);
            setFlags(EnumSet.of(Flag.LOOK));
        }
    }

    /**
     * Commits melee only. It declares LOOK, never MOVE, and never touches navigation: establishing
     * reach and line of sight is the runtime's job. The target is revalidated at windup and again
     * immediately before the commit, so a stale or newly protected target receives no hit and no
     * rider effect.
     */
    private static final class AttackOnlyMeleeGoal extends Goal {
        private static final int COOLDOWN_TICKS = 20;
        private final AbstractGoblinMerchantEntity body;
        private int cooldown;

        private <T extends AbstractGoblinMerchantEntity & PatronBody> AttackOnlyMeleeGoal(final T patron) {
            this.body = patron;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            final LivingEntity target = body.getTarget();
            return target != null && target.isAlive() && body.canAttack(target);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            cooldown = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            final LivingEntity target = body.getTarget();
            if (target == null || !(body.level() instanceof ServerLevel level)) {
                return;
            }
            body.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            if (!body.isWithinMeleeAttackRange(target)) {
                return;
            }
            if (!target.isAlive() || !body.canAttack(target)) {
                body.setTarget(null);
                return;
            }
            cooldown = COOLDOWN_TICKS;
            body.swing(InteractionHand.MAIN_HAND);
            body.doHurtTarget(level, target);
        }
    }

    /**
     * The shared player-interaction order: the heart offering is server-authoritative and precedes
     * ordinary trade, and a trade that actually opens suspends scans and navigation in the same
     * tick.
     */
    public static InteractionResult mobInteract(
        final PatronBody patron,
        final CreatureBehavior contractBehavior,
        final Player player,
        final InteractionHand hand,
        final java.util.function.Supplier<InteractionResult> ordinaryInteraction
    ) {
        final InteractionResult offering = interact(patron, contractBehavior, player, hand);
        if (offering != InteractionResult.PASS) {
            return offering;
        }
        final InteractionResult result = ordinaryInteraction.get();
        if (patron.body().isTrading()) {
            onTradeOpened(patron);
        }
        return result;
    }

    /**
     * Persistence is the vanilla latch plus the one explicit patron reason. The vanilla latch is
     * deliberately still honoured, because every other {@code setPersistenceRequired()} site in
     * 26.2 is a real player or system intent a patron must respect exactly like any other mob: a
     * name tag, the equipment-slot container behind {@code /item replace entity}, a dispenser, and
     * the GameTest entity builder that keeps a test-spawned mob alive for its own test. Only the
     * shared contract binding's write is refused, and it is refused at the source.
     */
    public static boolean persistenceRequired(final PatronBody patron, final boolean vanillaLatch) {
        return vanillaLatch || patron.body().isAlive();
    }

    public static void writeSaveData(
        final PatronBody patron,
        final ValueOutput output,
        final String key
    ) {
        output.store(key, CompoundTag.CODEC, state(patron).write());
    }

    public static void readSaveData(
        final PatronBody patron,
        final ValueInput input,
        final String key,
        final String dimension,
        final int legacyEmpowerment,
        final int legacyVillagerXp
    ) {
        setState(patron, input.read(key, CompoundTag.CODEC)
            .map(tag -> GoblinPatronState.read(tag, patron.patronKind(), dimension))
            .orElseGet(() -> GoblinPatronState.migrateLegacy(
                patron.patronKind(), legacyEmpowerment, legacyVillagerXp
            )));
        scratch(patron).resetForLoad();
    }

    /** The exact patron offer list: a kind-specific catalog seeded from identity, kind, and level. */
    public static List<net.minecraft.world.item.trading.MerchantOffer> createOffers(final PatronBody patron) {
        final GoblinPatronState current = state(patron);
        return GoblinTradeCatalog.createPatronOffers(
            patron.patronKind(),
            GoblinPatronRules.offerSeed(
                patron.body().getUUID(),
                patron.patronKind(),
                current.merchant().level(),
                current.merchant().restockEpoch()
            ),
            current.merchant().level()
        );
    }

    public static void awardMerchantXp(final PatronBody patron, final int xp) {
        final GoblinPatronState current = state(patron);
        setState(patron, current.withMerchant(
            current.merchant().withXp(current.merchant().xp() + Math.max(0, xp))
        ));
    }

    private static void fireArrow(
        final AbstractGoblinMerchantEntity body,
        final LivingEntity target,
        final ServerLevel level
    ) {
        final ItemStack projectileStack = new ItemStack(Items.ARROW);
        final Arrow arrow = new Arrow(level, body, projectileStack, null);
        arrow.setBaseDamage(ARROW_BASE_DAMAGE);
        final double x = target.getX() - body.getX();
        final double z = target.getZ() - body.getZ();
        final double arc = Math.sqrt(x * x + z * z) * 0.12D;
        Projectile.spawnProjectile(arrow, level, projectileStack, projectile -> projectile.shoot(
            x, target.getEyeY() - projectile.getY() + arc, z, ARROW_VELOCITY, ARROW_INACCURACY
        ));
        body.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, ARROW_PITCH);
    }
}
