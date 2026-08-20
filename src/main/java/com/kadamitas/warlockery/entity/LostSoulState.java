package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.LostSoulRules.Phase;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Versioned, fixed-cardinality Lost Soul semantics. Exactly one memorial anchor and one episode
 * may exist. Every duration is a remaining loaded-active tick count, never an absolute world
 * deadline, so unloading pauses meaning instead of expiring it. No path, live entity reference,
 * collection, line-of-sight cache, owner copy, or client fact is ever stored: the one owner
 * authority stays the generic {@link CreatureBehaviorState} UUID.
 */
public record LostSoulState(
    int schemaVersion,
    Phase phase,
    Anchor anchor,
    Episode episode,
    Cadence cadence
) {
    public static final int SCHEMA_VERSION = 1;

    public LostSoulState {
        phase = Objects.requireNonNull(phase, "phase");
        anchor = Objects.requireNonNull(anchor, "anchor");
        episode = Objects.requireNonNull(episode, "episode");
        cadence = Objects.requireNonNull(cadence, "cadence");
    }

    public record Anchor(Optional<BlockPos> position, Optional<String> dimension) {
        public Anchor {
            position = Objects.requireNonNull(position, "position").map(BlockPos::immutable);
            dimension = Objects.requireNonNull(dimension, "dimension").filter(value -> !value.isBlank());
            if (position.isEmpty() || dimension.isEmpty()) {
                position = Optional.empty();
                dimension = Optional.empty();
            }
        }

        public static Anchor none() {
            return new Anchor(Optional.empty(), Optional.empty());
        }

        public static Anchor at(final BlockPos position, final String dimension) {
            return new Anchor(Optional.of(position), Optional.of(dimension));
        }

        public boolean present() {
            return position.isPresent() && dimension.isPresent();
        }
    }

    public record Episode(
        int remainingTicks,
        int petitionRemainingTicks,
        int settleRemainingTicks,
        int pulseRemainingTicks,
        int pulsesEmitted
    ) {
        public Episode {
            remainingTicks = LostSoulRules.clampRemaining(remainingTicks, LostSoulRules.EPISODE_TICKS);
            petitionRemainingTicks =
                LostSoulRules.clampRemaining(petitionRemainingTicks, LostSoulRules.PETITION_TICKS);
            settleRemainingTicks =
                LostSoulRules.clampRemaining(settleRemainingTicks, LostSoulRules.SETTLE_TICKS);
            pulseRemainingTicks = LostSoulRules.clampRemaining(
                pulseRemainingTicks, LostSoulRules.PETITION_PULSE_INTERVAL_TICKS
            );
            pulsesEmitted = Math.clamp(pulsesEmitted, 0, LostSoulRules.MAX_PETITION_PULSES);
            if (remainingTicks <= 0) {
                petitionRemainingTicks = 0;
                settleRemainingTicks = 0;
                pulseRemainingTicks = 0;
                pulsesEmitted = 0;
            }
        }

        public static Episode none() {
            return new Episode(0, 0, 0, 0, 0);
        }

        public static Episode started() {
            return new Episode(LostSoulRules.EPISODE_TICKS, 0, 0,
                LostSoulRules.PETITION_PULSE_INTERVAL_TICKS, 0);
        }

        public boolean active() {
            return remainingTicks > 0;
        }
    }

    public record Cadence(
        int cooldownTicks,
        int routeFailures,
        int routeRetryTicks
    ) {
        public Cadence {
            cooldownTicks = LostSoulRules.clampRemaining(cooldownTicks, LostSoulRules.COOLDOWN_TICKS);
            routeFailures = Math.clamp(routeFailures, 0, LostSoulRules.MAX_ROUTE_FAILURES);
            routeRetryTicks =
                LostSoulRules.clampRemaining(routeRetryTicks, LostSoulRules.ROUTE_BACKOFF_TICKS);
        }

        public static Cadence none() {
            return new Cadence(0, 0, 0);
        }
    }

    public static LostSoulState empty() {
        return new LostSoulState(SCHEMA_VERSION, Phase.WANDER, Anchor.none(),
            Episode.none(), Cadence.none());
    }

    /**
     * The canonical constructor deliberately performs no phase reconciliation. Every phase here
     * is ended by exactly one tick branch in {@link LostSoulRuntime}, and a constructor that
     * silently rewrote a phase would race those branches: an expired episode would leave WANDER
     * before {@code endEpisodeIfRequired} could observe the expiry, so the anchor would never be
     * released and the cooldown would never be armed. Structural nonsense is still safe, because
     * every attention branch runs {@code endEpisodeIfRequired} before it dereferences an anchor,
     * and an absent anchor ends the episode through {@code EpisodeEnd.ANCHOR_LOST}.
     */
    public LostSoulState withPhase(final Phase updated) {
        return new LostSoulState(schemaVersion, updated, anchor, episode, cadence);
    }

    public LostSoulState withAnchor(final Anchor updated) {
        return new LostSoulState(schemaVersion, phase, updated, episode, cadence);
    }

    public LostSoulState withEpisode(final Episode updated) {
        return new LostSoulState(schemaVersion, phase, anchor, updated, cadence);
    }

    public LostSoulState withCadence(final Cadence updated) {
        return new LostSoulState(schemaVersion, phase, anchor, episode, updated);
    }

    /** Ends the episode, clears the anchor and starts the cooldown without touching ownership. */
    public LostSoulState endEpisode() {
        return withAnchor(Anchor.none())
            .withEpisode(Episode.none())
            .withCadence(new Cadence(LostSoulRules.COOLDOWN_TICKS, 0, cadence.routeRetryTicks()))
            .withPhase(Phase.COOLDOWN);
    }

    /**
     * Atomic binding transition. The episode, anchor, petition feedback and route accounting are
     * dropped in the same state write that enters quiet bound attendance, so no petition can
     * survive a binding and no half-cancelled episode is ever observable.
     */
    public LostSoulState bind() {
        return new LostSoulState(schemaVersion, LostSoulRules.phaseAfterBinding(), Anchor.none(),
            Episode.none(), new Cadence(0, 0, 0));
    }

    public LostSoulState unbind() {
        return new LostSoulState(schemaVersion, Phase.WANDER, Anchor.none(), Episode.none(),
            new Cadence(LostSoulRules.COOLDOWN_TICKS, 0, 0));
    }

    /**
     * Compact fixed-cardinality encoding. Representative populated states must encode below
     * {@link LostSoulRules#MAX_STATE_BYTES}.
     */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", schemaVersion);
        tag.putString("Phase", phase.name().toLowerCase(Locale.ROOT));
        anchor.position().ifPresent(position -> tag.putLong("AnchorPos", position.asLong()));
        anchor.dimension().ifPresent(dimension -> tag.putString("AnchorDim", dimension));
        tag.putInt("Episode", episode.remainingTicks());
        tag.putInt("Petition", episode.petitionRemainingTicks());
        tag.putInt("Settle", episode.settleRemainingTicks());
        tag.putInt("Pulse", episode.pulseRemainingTicks());
        tag.putInt("PulseCount", episode.pulsesEmitted());
        tag.putInt("Cooldown", cadence.cooldownTicks());
        tag.putInt("RouteFail", cadence.routeFailures());
        tag.putInt("RouteRetry", cadence.routeRetryTicks());
        return tag;
    }

    /**
     * Reads version 1. A missing or unknown schema resets to a safe wander. Every remaining
     * duration is clamped without consulting elapsed world time, the petition pulse interval is
     * restored when it was persisted as zero so no feedback replays on load, and an anchor from
     * another dimension is dropped rather than chased.
     */
    public static LostSoulState read(final CompoundTag tag, final String currentDimension) {
        if (tag == null || tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return empty();
        }
        final Anchor anchor = new Anchor(
            readPosition(tag, "AnchorPos"),
            readDimension(tag, "AnchorDim").filter(dimension -> dimension.equals(currentDimension))
        );
        final Episode episode = new Episode(
            tag.getIntOr("Episode", 0),
            tag.getIntOr("Petition", 0),
            tag.getIntOr("Settle", 0),
            LostSoulRules.resetPulseIntervalOnLoad(
                tag.getIntOr("Pulse", 0), LostSoulRules.PETITION_PULSE_INTERVAL_TICKS
            ),
            tag.getIntOr("PulseCount", 0)
        );
        final Cadence cadence = new Cadence(
            tag.getIntOr("Cooldown", 0),
            tag.getIntOr("RouteFail", 0),
            tag.getIntOr("RouteRetry", 0)
        );
        final Phase stored = parsePhase(tag.getStringOr("Phase", ""));
        return new LostSoulState(
            SCHEMA_VERSION,
            stored == Phase.PETITION || stored == Phase.SETTLE ? Phase.APPROACH : stored,
            anchor,
            episode,
            cadence
        );
    }

    private static Phase parsePhase(final String value) {
        for (final Phase candidate : Phase.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        return Phase.WANDER;
    }

    private static Optional<BlockPos> readPosition(final CompoundTag tag, final String key) {
        final long stored = tag.getLongOr(key, Long.MIN_VALUE);
        return stored == Long.MIN_VALUE ? Optional.empty() : Optional.of(BlockPos.of(stored));
    }

    private static Optional<String> readDimension(final CompoundTag tag, final String key) {
        final String stored = tag.getStringOr(key, "");
        return stored.isBlank() ? Optional.empty() : Optional.of(stored);
    }
}
