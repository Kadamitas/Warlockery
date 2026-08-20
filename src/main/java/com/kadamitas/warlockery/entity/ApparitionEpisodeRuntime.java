package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ApparitionEpisodeRules.DestinationCandidate;
import com.kadamitas.warlockery.entity.ApparitionEpisodeRules.PlayerCandidate;
import com.kadamitas.warlockery.entity.ApparitionEpisodeRules.RouteLedger;
import com.kadamitas.warlockery.entity.ApparitionEpisodeRules.RouteResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The one bounded world-reading component shared by both F21 apparitions. Every hazard sample,
 * destination sweep, appointment sweep and path request either kind performs goes through exactly
 * one implementation here, so the recurring search, budget and cadence defects have exactly one
 * place to live and cannot be fixed in one kind while surviving in the other.
 *
 * <p>Nothing here decides anything an apparition wants. It never appoints a subject on its own,
 * never chooses a destination centre, never sets a combat target, never applies an effect, never
 * deals damage, never edits a block or an inventory, never writes another entity's persistent
 * state, never teleports, never forces a chunk and never enumerates entities globally. What to look
 * for and what to do about it belongs entirely to {@link EchoShadeRuntime} and
 * {@link SpectreRuntime}.</p>
 */
public final class ApparitionEpisodeRuntime {
    static final TagKey<Block> CONTACT_HAZARDS = TagKey.create(
        Registries.BLOCK,
        Identifier.fromNamespaceAndPath("warlockery", "ai/contact_hazards")
    );

    private ApparitionEpisodeRuntime() {
    }

    /**
     * Structural work counters proving the declared caps. Both kinds carry one instance. These are
     * pass-local diagnostics: they are never persisted and losing them can never change behavior.
     */
    public static final class Counters {
        long hazardSamples;
        long blockReads;
        long destinationSweeps;
        long destinationCandidateVisits;
        long navigationRequests;
        long unroutableSweeps;
        long appointmentSweeps;
        long appointmentCandidateVisits;
        long lineOfSightChecks;
        long appointmentFailures;
        long hazardInterruptions;
        long episodesStarted;
        long episodesEnded;

        public long hazardSamples() { return hazardSamples; }
        public long blockReads() { return blockReads; }
        public long destinationSweeps() { return destinationSweeps; }
        public long destinationCandidateVisits() { return destinationCandidateVisits; }
        public long navigationRequests() { return navigationRequests; }
        public long unroutableSweeps() { return unroutableSweeps; }
        public long appointmentSweeps() { return appointmentSweeps; }
        public long appointmentCandidateVisits() { return appointmentCandidateVisits; }
        public long lineOfSightChecks() { return lineOfSightChecks; }
        public long appointmentFailures() { return appointmentFailures; }
        public long hazardInterruptions() { return hazardInterruptions; }
        public long episodesStarted() { return episodesStarted; }
        public long episodesEnded() { return episodesEnded; }
    }

    /** What one route attempt produced, including the attempt a failed sweep never made. */
    public record RouteOutcome(boolean accepted, RouteLedger ledger, Optional<BlockPos> destination) {
    }

    // ---------------------------------------------------------------- world facts

    public static String dimensionOf(final ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    public static Optional<ServerPlayer> resolvePlayer(final ServerLevel level, final UUID id) {
        final var resolved = level.getPlayerByUUID(id);
        return resolved instanceof ServerPlayer player && player.level() == level && player.isAlive()
            ? Optional.of(player)
            : Optional.empty();
    }

    static boolean isHazardBlock(final BlockState state) {
        return state.is(Blocks.FIRE)
            || state.is(Blocks.SOUL_FIRE)
            || state.is(Blocks.CAMPFIRE)
            || state.is(Blocks.SOUL_CAMPFIRE)
            || state.is(Blocks.LAVA)
            || state.is(CONTACT_HAZARDS);
    }

    static boolean footprintLoaded(final ServerLevel level, final AABB box) {
        return level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.minZ))
            && level.hasChunkAt(BlockPos.containing(box.maxX, box.minY, box.maxZ))
            && level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.maxZ))
            && level.hasChunkAt(BlockPos.containing(box.maxX, box.minY, box.minZ));
    }

    /**
     * Bounded local hazard observation over the 3 x 3 x 3 contact neighbourhood. Reads stop at the
     * declared ceiling and an unloaded footprint is never forced: it simply reports no hazard.
     */
    public static boolean observeHazard(
        final Mob apparition,
        final ServerLevel level,
        final Counters counters
    ) {
        counters.hazardSamples++;
        counters.blockReads += ApparitionEpisodeRules.MAX_HAZARD_READS;
        if (apparition.isOnFire() || apparition.isInLava()) {
            return true;
        }
        if (apparition.isUnderWater()
            && apparition.getAirSupply() < apparition.getMaxAirSupply()) {
            return true;
        }
        if (!footprintLoaded(level, apparition.getBoundingBox().inflate(1.0D))) {
            return false;
        }
        final BlockPos center = apparition.blockPosition();
        int reads = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (reads >= ApparitionEpisodeRules.MAX_HAZARD_READS) {
                        return false;
                    }
                    reads++;
                    if (isHazardBlock(level.getBlockState(center.offset(dx, dy, dz)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** What one qualified destination turned out to be, so no caller reads the position twice. */
    record Qualification(boolean hazardFree) {
    }

    /**
     * Shared candidate qualification. Only a position whose entire entity footprint is already
     * loaded, inside the world border, collision free and not lava qualifies. Nothing here decides
     * preference: the shared comparator does.
     */
    static Optional<Qualification> qualifyDestination(
        final Mob apparition,
        final ServerLevel level,
        final BlockPos candidate,
        final boolean avoidHazards
    ) {
        final AABB box = apparition.getType().getDimensions()
            .makeBoundingBox(Vec3.atBottomCenterOf(candidate));
        if (!level.getWorldBorder().isWithinBounds(box) || !footprintLoaded(level, box)) {
            return Optional.empty();
        }
        final BlockState blockState = level.getBlockState(candidate);
        final var fluidState = level.getFluidState(candidate);
        final boolean hazardous = isHazardBlock(blockState) || !fluidState.isEmpty();
        if (avoidHazards && hazardous) {
            return Optional.empty();
        }
        if (blockState.is(Blocks.LAVA) || fluidState.is(FluidTags.LAVA)) {
            return Optional.empty();
        }
        return level.noCollision(apparition, box)
            ? Optional.of(new Qualification(!hazardous))
            : Optional.empty();
    }

    // ---------------------------------------------------------------- destination sweep

    /** The mutable sweep cursor an apparition carries so successive sweeps rotate its far page. */
    public static final class SweepCursor {
        private static final int UNSEEDED = Integer.MIN_VALUE;

        private int value = UNSEEDED;

        public void reset() {
            value = UNSEEDED;
        }

        private int current() {
            return value == UNSEEDED ? 0 : value;
        }

        int claim(final UUID id, final int envelopeSize, final int readCap) {
            if (value == UNSEEDED) {
                value = ApparitionEpisodeRules.seedCursor(id, envelopeSize, readCap);
            }
            return value;
        }

        void advance(final int envelopeSize, final int readCap) {
            value = ApparitionEpisodeRules.advanceCursor(envelopeSize, readCap, current());
        }
    }

    /**
     * The one bounded destination sweep. The centre-out envelope is evaluated through the shared
     * anchor-plus-rotating-page window, so the apparition's own block is considered on every sweep
     * and the far shell is reached within a small fixed number of successive sweeps. Every visited
     * candidate is charged its full worst-case read cost <em>before</em> any filter may reject it,
     * so the declared ceiling binds the real work rather than the accepted minority.
     */
    public static Optional<BlockPos> findDestination(
        final Mob apparition,
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRadius,
        final int verticalRadius,
        final SweepCursor cursor,
        final Optional<Vec3> awayFrom,
        final boolean avoidHazards,
        final Counters counters
    ) {
        counters.destinationSweeps++;
        final List<BlockPos> envelope =
            ApparitionEpisodeRules.envelope(horizontalRadius, verticalRadius);
        final int candidateCap =
            ApparitionEpisodeRules.MAX_DESTINATION_READS
                / ApparitionEpisodeRules.READS_PER_DESTINATION_CANDIDATE;
        final int claimed = cursor.claim(apparition.getUUID(), envelope.size(), candidateCap);
        final BlockPos origin = apparition.blockPosition();
        final Comparator<DestinationCandidate> preference =
            ApparitionEpisodeRules.destinationPreference();
        BlockPos best = null;
        DestinationCandidate bestFacts = null;
        int reads = 0;
        for (final BlockPos offset
            : ApparitionEpisodeRules.sweepWindow(envelope, candidateCap, claimed)) {
            if (reads + ApparitionEpisodeRules.READS_PER_DESTINATION_CANDIDATE
                > ApparitionEpisodeRules.MAX_DESTINATION_READS) {
                break;
            }
            final BlockPos candidate = center.offset(offset);
            if (candidate.equals(origin)) {
                continue;
            }
            counters.destinationCandidateVisits++;
            reads += ApparitionEpisodeRules.READS_PER_DESTINATION_CANDIDATE;
            counters.blockReads += ApparitionEpisodeRules.READS_PER_DESTINATION_CANDIDATE;
            final Optional<Qualification> qualified =
                qualifyDestination(apparition, level, candidate, avoidHazards);
            if (qualified.isEmpty()) {
                continue;
            }
            final DestinationCandidate facts = new DestinationCandidate(
                awayFrom.map(point -> Vec3.atCenterOf(candidate).distanceToSqr(point)).orElse(0.0D),
                qualified.orElseThrow().hazardFree(),
                candidate.distSqr(origin),
                candidate.asLong()
            );
            if (bestFacts == null || preference.compare(facts, bestFacts) < 0) {
                bestFacts = facts;
                best = candidate.immutable();
            }
        }
        cursor.advance(envelope.size(), candidateCap);
        return Optional.ofNullable(best);
    }

    // ---------------------------------------------------------------- routing

    /**
     * Strict route request. The third consecutive failure stops navigation and opens the backoff,
     * and the failure count is persisted at its observable maximum so the owning tick branch can
     * release on it. Only a release or a later success resets it.
     */
    public static RouteOutcome requestRoute(
        final Mob apparition,
        final RouteLedger ledger,
        final BlockPos destination,
        final double speed,
        final Counters counters
    ) {
        if (!ApparitionEpisodeRules.pathRequestAllowed(
            ledger.pathCooldownTicks(), ledger.routeRetryTicks()
        )) {
            return new RouteOutcome(false, ledger, Optional.empty());
        }
        counters.navigationRequests++;
        final Path path = apparition.getNavigation().createPath(destination, 0);
        final boolean reachable = path != null && path.canReach();
        final boolean accepted = reachable && apparition.getNavigation().moveTo(path, speed);
        final RouteLedger updated = ApparitionEpisodeRules.ledgerAfter(
            ledger, new RouteResult(path != null, reachable, accepted)
        );
        if (ApparitionEpisodeRules.routeExhausted(updated.routeFailures())) {
            apparition.getNavigation().stop();
            return new RouteOutcome(false, updated, Optional.empty());
        }
        return new RouteOutcome(
            accepted, updated, accepted ? Optional.of(destination.immutable()) : Optional.empty()
        );
    }

    /**
     * The one combined sweep-and-route primitive.
     *
     * <p>A sweep that qualified nothing spent the same real reads as one that did, so it arms the
     * same path cadence and counts the same route failure. Without that, a caller gated only by
     * {@code getNavigation().isDone()} would re-run the entire candidate sweep on every single tick
     * for as long as its surroundings stayed unusable, and the declared failure cap could never
     * bind.</p>
     */
    public static RouteOutcome sweepAndRoute(
        final Mob apparition,
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRadius,
        final int verticalRadius,
        final SweepCursor cursor,
        final RouteLedger ledger,
        final Optional<Vec3> awayFrom,
        final boolean avoidHazards,
        final double speed,
        final Counters counters
    ) {
        if (!ApparitionEpisodeRules.pathRequestAllowed(
            ledger.pathCooldownTicks(), ledger.routeRetryTicks()
        )) {
            return new RouteOutcome(false, ledger, Optional.empty());
        }
        final Optional<BlockPos> destination = findDestination(
            apparition, level, center, horizontalRadius, verticalRadius, cursor,
            awayFrom, avoidHazards, counters
        );
        if (destination.isEmpty()) {
            counters.unroutableSweeps++;
            final RouteLedger updated =
                ApparitionEpisodeRules.ledgerAfter(ledger, RouteResult.unroutable());
            if (ApparitionEpisodeRules.routeExhausted(updated.routeFailures())) {
                apparition.getNavigation().stop();
            }
            return new RouteOutcome(false, updated, Optional.empty());
        }
        return requestRoute(apparition, ledger, destination.orElseThrow(), speed, counters);
    }

    // ---------------------------------------------------------------- appointment sweep

    /**
     * The one bounded appointment sweep over loaded players of this level. Players outside the
     * requested range are skipped without being charged, so the candidate budget is always spent
     * on players actually in reach rather than on whoever happens to sit earliest in the level's
     * player list; every player still inside the range is charged a candidate visit before any
     * remaining eligibility filter may reject it, line-of-sight walks are separately capped
     * because a barrier walk is the expensive part, and the sweep never touches an unloaded
     * player, another dimension, a creative or spectator player, or a crowd larger than the
     * declared cap.
     *
     * <p>The caller supplies the species eligibility test. A Shade and a Spectre look for different
     * things in a player, and this method deliberately has no opinion about which.</p>
     */
    public static List<PlayerCandidate> observePlayers(
        final Mob apparition,
        final ServerLevel level,
        final double rangeSquared,
        final java.util.function.BiPredicate<ServerPlayer, Double> eligible,
        final Counters counters
    ) {
        counters.appointmentSweeps++;
        final List<PlayerCandidate> inspected = new ArrayList<>();
        int visited = 0;
        int lineOfSightChecks = 0;
        for (final ServerPlayer player : level.players()) {
            if (visited >= ApparitionEpisodeRules.MAX_PLAYER_CANDIDATES) {
                break;
            }
            final double distanceSquared = apparition.distanceToSqr(player);
            // Out of range is not a candidate. Charging the visit budget before the range test
            // would let players who merely joined the level earlier exhaust it before the ones
            // standing next to this apparition are examined at all.
            if (distanceSquared > rangeSquared) {
                continue;
            }
            visited++;
            counters.appointmentCandidateVisits++;
            final boolean qualifies = player.isAlive()
                && player.level() == level
                && !player.isCreative()
                && !player.isSpectator()
                && !player.isInvulnerable()
                && distanceSquared <= rangeSquared
                && eligible.test(player, distanceSquared);
            boolean visible = false;
            if (qualifies && lineOfSightChecks < ApparitionEpisodeRules.MAX_LINE_OF_SIGHT_CHECKS) {
                lineOfSightChecks++;
                counters.lineOfSightChecks++;
                visible = apparition.getSensing().hasLineOfSight(player);
            }
            inspected.add(new PlayerCandidate(
                player.getUUID(), qualifies, visible, distanceSquared
            ));
        }
        return List.copyOf(inspected);
    }
}
