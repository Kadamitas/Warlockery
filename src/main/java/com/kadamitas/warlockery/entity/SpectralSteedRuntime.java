package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.SpectralSteedRules.Concern;
import com.kadamitas.warlockery.entity.SpectralSteedRules.Gait;
import com.kadamitas.warlockery.entity.SpectralSteedState.Phase;
import com.kadamitas.warlockery.entity.behavior.Cadence;
import com.kadamitas.warlockery.entity.behavior.ReadBudget;
import com.kadamitas.warlockery.entity.behavior.ScanEnvelope;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The one live tick of a spectral steed.
 *
 * <p>A ridden mount ticks constantly, so nothing on the ridden path opens a stream, sorts a list or
 * builds a pipeline. The concern chain is {@link SpectralSteedRules#chooseConcern}, the scans are
 * indexed loops, and the only allocating work sits behind a cadence that fires at most once every
 * twenty ticks and only while the steed is actually looking for somewhere to rest.</p>
 *
 * <p>Entry is {@link #tick}, called from {@link SpectralSteedEntity#tickSpecializedActivity}, which
 * is called from {@code ArcaneMob.customServerAiStep}, which is Minecraft's own server AI step. Every
 * other public member here is reached from {@link #tick} or from the entity body.</p>
 */
public final class SpectralSteedRuntime {

    /** Blocks a steed will stand down beside. A landmark only: nothing is eaten, broken or placed. */
    public static final TagKey<Block> REST_LANDMARKS = TagKey.create(
        Registries.BLOCK,
        Identifier.fromNamespaceAndPath("warlockery", "ai/spectral_steed_rest_landmarks")
    );

    /** The only entity types a bonded Nightmare's warning can ever reach. */
    public static final TagKey<EntityType<?>> FEAR_TARGETS = TagKey.create(
        Registries.ENTITY_TYPE,
        Identifier.fromNamespaceAndPath("warlockery", "ai/nightmare_fear_targets")
    );

    private static final Direction[] HORIZONTALS = {
        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private SpectralSteedRuntime() {
    }

    /** The owner riding and steering this steed, when that is who is on it. */
    public static Optional<Player> controllingOwner(final SpectralSteedEntity steed) {
        if (!(steed.getControllingPassenger() instanceof Player player)) {
            return Optional.empty();
        }
        return SpectralMountRules.canControl(
            steed.creatureKind(), CreatureBehaviorState.owner(steed), player.getUUID()
        ) ? Optional.of(player) : Optional.empty();
    }

    /** Whether the generic tactical and ambient layers must stand aside this tick. */
    public static boolean carryingOwner(final SpectralSteedEntity steed) {
        return controllingOwner(steed).isPresent();
    }

    /**
     * One server tick.
     *
     * <p>Order matters and is fixed: advance every countdown, end whatever ran out, gather facts,
     * choose one concern, act on exactly that concern, then apply the band. Ending an expired phase
     * happens before the concern is chosen precisely so a balk that has just run out cannot win the
     * ladder for another tick.</p>
     */
    public static void tick(final SpectralSteedEntity steed, final ServerLevel level) {
        final CreatureKind kind = steed.creatureKind();
        SpectralSteedState state = steed.steedState().step();
        state = endExpiredPhase(state);

        final Optional<Player> rider = controllingOwner(steed);
        final boolean carrying = rider.isPresent();
        final boolean hazard = hazardPresent(steed, level, state, kind);
        state = state.withHazardScan(armedScan(state));
        final boolean balking = state.balking();
        final boolean seekingRest =
            SpectralSteedRules.seeksRest(state.fatigue(), state.restCooldown(), carrying);

        final Concern concern = SpectralSteedRules.chooseConcern(hazard, balking, carrying, seekingRest);
        final Gait ceiling = SpectralSteedRules.ceilingGait(kind, state.bond(), state.fatigue());
        Gait desired = Gait.HALT;
        boolean urgent = false;

        switch (concern) {
            case HAZARD -> {
                if (!balking) {
                    state = state.startingBalk(SpectralSteedRules.balkTicks(kind, state.bond()));
                }
                urgent = true;
            }
            case BALK -> urgent = true;
            case CARRY -> {
                final Player controller = rider.orElseThrow();
                steed.setTarget(null);
                desired = SpectralSteedRules.desiredGait(kind, controller.zza, controller.xxa);
                state = creditBond(state, true, false);
                state = warnIfThreatened(steed, level, state, kind, controller);
            }
            case REST -> {
                state = pursueRest(steed, level, state);
                desired = state.resting() ? Gait.HALT : Gait.WALK;
                urgent = state.resting();
            }
            case IDLE -> desired = Gait.HALT;
        }

        state = applyFatigue(state, kind, concern);
        final Gait capped = SpectralSteedRules.capped(desired, ceiling);
        final Gait next = SpectralSteedRules.nextGait(state.gait(), capped, state.gaitHold(), urgent);
        steed.setSteedState(state.withGait(next));
    }

    /**
     * Ends whichever phase ran out, and only here. The balk ending releases steering; the rest
     * ending is the second and last way a steed earns bond, and it arms the rest cooldown.
     */
    private static SpectralSteedState endExpiredPhase(final SpectralSteedState state) {
        final Optional<Phase> expired = state.phase().expiredPhase();
        if (expired.isEmpty()) {
            return state;
        }
        return switch (expired.orElseThrow()) {
            case BALK -> state.withPhase(state.phase().endExpired());
            case RESTING -> creditBond(state.withRestCompleted(), false, true);
        };
    }

    private static SpectralSteedState creditBond(
        final SpectralSteedState state,
        final boolean carryingOwner,
        final boolean completedRest
    ) {
        return state.withBondGain(
            SpectralSteedRules.bondGain(carryingOwner, completedRest, state.bondThisEpisode()),
            carryingOwner
        );
    }

    private static SpectralSteedState applyFatigue(
        final SpectralSteedState state,
        final CreatureKind kind,
        final Concern concern
    ) {
        final int delta = switch (concern) {
            case HAZARD, BALK, IDLE -> SpectralSteedRules.fatigueDelta(kind, Gait.HALT);
            case REST -> state.resting()
                ? SpectralSteedRules.restFatigueDelta()
                : SpectralSteedRules.fatigueDelta(kind, Gait.WALK);
            case CARRY -> SpectralSteedRules.fatigueDelta(kind, state.gait());
        };
        return delta == 0 ? state : state.withFatigue(state.fatigue() + delta);
    }

    /**
     * A bounded local hazard look, behind its own cadence so a ridden steed does not pay for one
     * every tick. The cadence is armed whether or not a hazard was found.
     */
    private static boolean hazardPresent(
        final SpectralSteedEntity steed,
        final ServerLevel level,
        final SpectralSteedState state,
        final CreatureKind kind
    ) {
        if (!state.hazardScan().due()) {
            return false;
        }
        return HazardEscapeRuntime.currentHazard(steed, level)
            .filter(found -> HazardEscapeRules.shouldEscape(kind, found))
            .isPresent();
    }

    /** Arming records that the look ran, not that it found anything. */
    private static Cadence armedScan(final SpectralSteedState state) {
        return state.hazardScan().due() ? state.hazardScan().arm() : state.hazardScan();
    }

    /**
     * Looks for somewhere to stand down, walks there, and settles on arrival.
     *
     * <p>The request cadence is consulted before the search runs, and the search is charged as a
     * failure whether it found nothing, could not build a path, or was refused. A steed standing in
     * unusable terrain therefore backs off instead of re-sweeping every tick.</p>
     */
    private static SpectralSteedState pursueRest(
        final SpectralSteedEntity steed,
        final ServerLevel level,
        final SpectralSteedState state
    ) {
        if (state.resting()) {
            return state;
        }
        final Optional<BlockPos> held = state.rest();
        if (held.isPresent()) {
            final BlockPos site = held.orElseThrow();
            // The re-check is a bounded, charged read like every other world look this family
            // takes. It runs on every tick the steed is walking to its site, so leaving it outside
            // the budget left up to six reads per tick unpaid for and unobservable. The charge is
            // recorded before the answer is judged, so a site that has just been lost costs exactly
            // what a site that survived costs.
            final ReadBudget validation = ReadBudget.of(SpectralSteedRules.MAX_REST_VALIDATION_READS);
            final boolean valid = stillValidRestSite(steed, level, site, validation);
            final SpectralSteedState checked = state.withRestValidationCharged(validation.spent());
            if (!valid) {
                return checked.withRest(Optional.empty(), Optional.empty());
            }
            if (steed.distanceToSqr(site.getX() + 0.5, site.getY(), site.getZ() + 0.5)
                <= SpectralSteedRules.REST_ARRIVAL_DISTANCE_SQUARED) {
                return checked.startingRest(SpectralSteedRules.REST_SETTLE_TICKS);
            }
            if (!checked.restRequest().mayRequest()) {
                return checked;
            }
            final boolean routeStarted = steed.getNavigation().moveTo(
                site.getX() + 0.5, site.getY(), site.getZ() + 0.5, 1.0
            );
            final SpectralSteedState requested = checked.withRestNavigationStarted();
            if (routeStarted) {
                return requested;
            }
            // A site the navigator will not route to is not a site. Releasing it and charging the
            // request is what stops a steed re-asking for the same unreachable place every tick.
            return requested.withRest(Optional.empty(), Optional.empty())
                .withRestRequest(checked.restRequest().failed(SpectralSteedRules.REST_BACKOFF));
        }
        if (!state.restRequest().mayRequest()) {
            return state;
        }
        final ReadBudget budget = ReadBudget.of(SpectralSteedRules.MAX_REST_BLOCK_READS);
        final ScanEnvelope envelope =
            ScanEnvelope.of(SpectralSteedRules.REST_HORIZONTAL_RADIUS, SpectralSteedRules.REST_VERTICAL_RADIUS);
        final RestSearch search = searchRestSite(steed, level, state, envelope, budget);
        final int nextCursor =
            envelope.advanceCursor(SpectralSteedRules.MAX_REST_CANDIDATES, state.restCursor());
        final SpectralSteedState charged = state.withRestSearchCharged(budget.spent(), nextCursor)
            .withRestRequest(
                SpectralSteedRules.afterRestSearch(state.restRequest(), search.outcome())
            );
        return switch (search.outcome()) {
            // The search succeeded the moment it qualified a site. Whether the navigator will route
            // to it is a separate question, answered on the next tick by the held-site branch
            // above, which is also where an unroutable site is released.
            case FOUND -> charged.withRest(
                search.site().map(BlockPos::immutable),
                Optional.of(level.dimension().identifier().toString())
            );
            // Neither empty outcome holds a site, and the two are already told apart above by what
            // they did to the request rather than by anything here.
            case NOTHING_QUALIFIED, BUDGET_EXHAUSTED -> charged;
        };
    }

    /**
     * What one bounded look produced, kept as an outcome rather than an {@code Optional} so that
     * "there is nothing here" and "I ran out of reads before I could tell" cannot collapse into the
     * same empty answer on the way back to the caller.
     */
    private record RestSearch(SpectralSteedRules.RestSearchOutcome outcome, Optional<BlockPos> site) {

        private static final RestSearch NOTHING =
            new RestSearch(SpectralSteedRules.RestSearchOutcome.NOTHING_QUALIFIED, Optional.empty());
        private static final RestSearch EXHAUSTED =
            new RestSearch(SpectralSteedRules.RestSearchOutcome.BUDGET_EXHAUSTED, Optional.empty());

        private static RestSearch found(final BlockPos site) {
            return new RestSearch(SpectralSteedRules.RestSearchOutcome.FOUND, Optional.of(site));
        }

        private boolean qualifiedNothing() {
            return outcome == SpectralSteedRules.RestSearchOutcome.NOTHING_QUALIFIED;
        }
    }

    /**
     * The bounded search itself.
     *
     * <p>Every read is charged before its value can be judged, so a rejected candidate costs exactly
     * what an accepted one costs. The window rotates by one page per search, so a steed surrounded by
     * unusable near blocks still reaches the far envelope instead of re-reading the innermost ring
     * forever.</p>
     *
     * <p>A refused charge stops the search and says so. It is never reported as an evaluated empty
     * window, because the window was not evaluated: the loop stopped part way through one.</p>
     */
    private static RestSearch searchRestSite(
        final SpectralSteedEntity steed,
        final ServerLevel level,
        final SpectralSteedState state,
        final ScanEnvelope envelope,
        final ReadBudget budget
    ) {
        final List<BlockPos> window =
            envelope.window(SpectralSteedRules.MAX_REST_CANDIDATES, state.restCursor());
        final BlockPos origin = steed.blockPosition();
        for (int index = 0; index < window.size(); index++) {
            final BlockPos landmark = origin.offset(window.get(index));
            if (!budget.charge()) {
                return RestSearch.EXHAUSTED;
            }
            if (!level.getBlockState(landmark).is(REST_LANDMARKS)) {
                continue;
            }
            final RestSearch stance = standingSpotBeside(steed, level, landmark, budget);
            if (!stance.qualifiedNothing()) {
                return stance;
            }
        }
        return RestSearch.NOTHING;
    }

    private static RestSearch standingSpotBeside(
        final SpectralSteedEntity steed,
        final ServerLevel level,
        final BlockPos landmark,
        final ReadBudget budget
    ) {
        for (int index = 0; index < HORIZONTALS.length; index++) {
            final BlockPos stand = landmark.relative(HORIZONTALS[index], 2);
            if (!validStance(steed, level, stand, budget)) {
                if (budget.exhausted()) {
                    return RestSearch.EXHAUSTED;
                }
                continue;
            }
            return RestSearch.found(stand.immutable());
        }
        return RestSearch.NOTHING;
    }

    /**
     * A site survives only while its landmark is still there and the stance is still clear.
     *
     * <p>Every read is charged first, so a refused charge and a failed look are the same answer: a
     * site this steed cannot currently afford to re-prove is released rather than kept on trust.
     * That merges the exhausted branch into an outcome the caller already handles instead of
     * leaving an arm no input can reach.</p>
     */
    private static boolean stillValidRestSite(
        final SpectralSteedEntity steed,
        final ServerLevel level,
        final BlockPos site,
        final ReadBudget budget
    ) {
        if (!validStance(steed, level, site, budget)) {
            return false;
        }
        for (int index = 0; index < HORIZONTALS.length; index++) {
            if (!budget.charge()) {
                return false;
            }
            if (level.getBlockState(site.relative(HORIZONTALS[index], 2)).is(REST_LANDMARKS)) {
                return true;
            }
        }
        return false;
    }

    private static boolean validStance(
        final SpectralSteedEntity steed,
        final ServerLevel level,
        final BlockPos site,
        final ReadBudget budget
    ) {
        final AABB footprint = steed.getBoundingBox().move(
            site.getX() + 0.5 - steed.getX(),
            site.getY() - steed.getY(),
            site.getZ() + 0.5 - steed.getZ()
        );
        if (!budget.accepts(
            () -> level.getWorldBorder().isWithinBounds(footprint), Boolean.TRUE::equals
        ) || !footprintLoaded(level, footprint, budget)) {
            return false;
        }
        final Optional<BlockState> feetRead = budget.read(() -> level.getBlockState(site));
        if (feetRead.isEmpty()) return false;
        final BlockState feet = feetRead.orElseThrow();
        final Optional<BlockState> headRead = budget.read(() -> level.getBlockState(site.above()));
        if (headRead.isEmpty()) return false;
        final BlockState head = headRead.orElseThrow();
        final BlockPos supportPos = site.below();
        final Optional<BlockState> supportRead = budget.read(() -> level.getBlockState(supportPos));
        if (supportRead.isEmpty()) return false;
        final BlockState support = supportRead.orElseThrow();
        return feet.isAir()
            && feet.getFluidState().isEmpty()
            && head.isAir()
            && head.getFluidState().isEmpty()
            && support.getFluidState().isEmpty()
            && support.isFaceSturdy(level, supportPos, Direction.UP)
            && !unsafeContact(feet)
            && !unsafeContact(head)
            && !unsafeContact(support)
            // The stance box rests on the support plane. Raising and deflating only the collision
            // probe by a sub-pixel epsilon keeps full body/head clearance while avoiding a numeric
            // overlap with that already-proved sturdy support face.
            && budget.accepts(
                () -> level.getBlockCollisions(
                    steed, footprint.move(0.0, 1.0E-3, 0.0).deflate(1.0E-4)
                ).iterator().hasNext(), hasCollision -> !hasCollision
            )
            && budget.accepts(
                () -> level.getEntityCollisions(steed, footprint.deflate(1.0E-4)).isEmpty(),
                Boolean.TRUE::equals
            );
    }

    private static boolean unsafeContact(final BlockState state) {
        return state.is(Blocks.FIRE)
            || state.is(Blocks.SOUL_FIRE)
            || state.is(Blocks.CAMPFIRE)
            || state.is(Blocks.SOUL_CAMPFIRE)
            || state.is(Blocks.MAGMA_BLOCK)
            || state.is(Blocks.CACTUS)
            || state.is(Blocks.SWEET_BERRY_BUSH)
            || state.is(Blocks.POWDER_SNOW)
            || state.getFluidState().is(FluidTags.LAVA);
    }

    private static boolean footprintLoaded(
        final ServerLevel level,
        final AABB footprint,
        final ReadBudget budget
    ) {
        final BlockPos[] corners = {
            BlockPos.containing(footprint.minX, footprint.minY, footprint.minZ),
            BlockPos.containing(footprint.maxX, footprint.minY, footprint.minZ),
            BlockPos.containing(footprint.minX, footprint.minY, footprint.maxZ),
            BlockPos.containing(footprint.maxX, footprint.minY, footprint.maxZ)
        };
        for (final BlockPos corner : corners) {
            if (!budget.accepts(() -> level.hasChunkAt(corner), Boolean.TRUE::equals)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The Nightmare's one telegraphed warning.
     *
     * <p>It is issued only when something is actually threatening the pair, only to entities the tag
     * admits, and only once per cooldown. It applies no damage, no control and no chaining; every
     * visited entity is counted whether or not it qualified, so the visit bound is falsifiable.</p>
     */
    private static SpectralSteedState warnIfThreatened(
        final SpectralSteedEntity steed,
        final ServerLevel level,
        final SpectralSteedState state,
        final CreatureKind kind,
        final Player controller
    ) {
        final LivingEntity threat = threatOf(steed, controller);
        if (!SpectralSteedRules.warningWarranted(
            kind, threat != null, state.bond(), state.fearCooldown()
        )) {
            return state;
        }
        final Optional<UUID> owner = CreatureBehaviorState.owner(steed);
        final List<LivingEntity> nearby = level.getEntitiesOfClass(
            LivingEntity.class, steed.getBoundingBox().inflate(SpectralSteedRules.FEAR_RADIUS)
        );
        int visits = 0;
        final List<LivingEntity> recipients = new ArrayList<>(SpectralSteedRules.MAX_FEAR_RECIPIENTS);
        for (int index = 0; index < nearby.size(); index++) {
            if (visits >= SpectralSteedRules.MAX_FEAR_VISITS
                || recipients.size() >= SpectralSteedRules.MAX_FEAR_RECIPIENTS) {
                break;
            }
            final LivingEntity candidate = nearby.get(index);
            if (candidate == steed) {
                continue;
            }
            visits++;
            if (!SpectralSteedRules.warningReaches(
                candidate.isAlive(),
                candidate.typeHolder().is(FEAR_TARGETS),
                owner.filter(candidate.getUUID()::equals).isPresent(),
                sharesOwner(candidate, owner),
                candidate instanceof ArcaneCreature,
                candidate instanceof Player player && !player.canBeSeenAsEnemy()
            )) {
                continue;
            }
            recipients.add(candidate);
        }
        if (!recipients.isEmpty()) {
            level.playSound(null, steed.getX(), steed.getY(), steed.getZ(),
                SoundEvents.HORSE_ANGRY, SoundSource.HOSTILE, 1.0F, 0.65F);
            level.gameEvent(steed, GameEvent.ENTITY_ACTION, steed.position());
            for (int index = 0; index < recipients.size(); index++) {
                recipients.get(index).addEffect(new MobEffectInstance(
                    MobEffects.SLOWNESS, SpectralSteedRules.FEAR_EFFECT_TICKS, 0, true, false
                ));
            }
        }
        return state.withWarningIssued(visits, recipients.size());
    }

    private static boolean sharesOwner(final Entity candidate, final Optional<UUID> owner) {
        return owner.isPresent() && CreatureBehaviorState.owner(candidate).equals(owner);
    }

    private static LivingEntity threatOf(final SpectralSteedEntity steed, final Player controller) {
        final LivingEntity own = steed.getLastHurtByMob();
        if (own != null && own.isAlive()) {
            return own;
        }
        final LivingEntity riders = controller.getLastHurtByMob();
        return riders != null && riders.isAlive() ? riders : null;
    }

    /**
     * Where a dismounting rider is put down.
     *
     * <p>The vanilla answer is the top of the mount's own box, which drops a rider straight back into
     * whatever the mount was standing in. This prefers a clear supported neighbour and charges a
     * bounded number of reads to find one, falling back to the mount's own position rather than to a
     * position it never checked.</p>
     */
    public static Vec3 dismountLocation(final SpectralSteedEntity steed, final ServerLevel level) {
        final BlockPos origin = steed.blockPosition();
        final ReadBudget budget = ReadBudget.of(3 * HORIZONTALS.length);
        for (int index = 0; index < HORIZONTALS.length; index++) {
            final BlockPos candidate = origin.relative(HORIZONTALS[index]);
            if (!budget.charge()) {
                break;
            }
            final BlockState feet = level.getBlockState(candidate);
            if (!feet.isAir() || !feet.getFluidState().isEmpty()) {
                continue;
            }
            if (!budget.charge()) {
                break;
            }
            if (!level.getBlockState(candidate.above()).isAir()) {
                continue;
            }
            if (!budget.charge()) {
                break;
            }
            if (level.getBlockState(candidate.below()).isAir()) {
                continue;
            }
            return new Vec3(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
        }
        return new Vec3(steed.getX(), steed.getBoundingBox().maxY, steed.getZ());
    }
}
