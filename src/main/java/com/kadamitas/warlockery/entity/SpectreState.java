package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ApparitionEpisodeRules.RouteLedger;
import com.kadamitas.warlockery.entity.SpectreRules.Phase;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * Versioned, fixed-cardinality Spectre semantics. Exactly one witness and one haunting may exist.
 * Every duration is a remaining loaded-active tick count, never an absolute world deadline, so
 * unloading pauses meaning instead of expiring it. No path, live entity reference, collection,
 * line-of-sight cache, player copy or client fact is ever stored.
 *
 * <p>A delivered dread is recorded as a count rather than as a timestamp, so it survives a reload
 * and no save cycle can ever grant a second delivery against the same witness.</p>
 */
public record SpectreState(
    int schemaVersion,
    Phase phase,
    Witness witness,
    Haunt haunt,
    RouteLedger route,
    int cooldownTicks
) {
    public static final int SCHEMA_VERSION = 1;

    public SpectreState {
        phase = Objects.requireNonNull(phase, "phase");
        witness = Objects.requireNonNull(witness, "witness");
        haunt = Objects.requireNonNull(haunt, "haunt");
        route = Objects.requireNonNull(route, "route");
        cooldownTicks =
            ApparitionEpisodeRules.clampRemaining(cooldownTicks, SpectreRules.COOLDOWN_TICKS);
    }

    /**
     * The one appointed witness, or none. Never a live entity and never anything it carries.
     *
     * <p>The compact constructor collapses a half-written witness to none. That is the identity
     * shape of reconciliation, not the timer shape: it asserts that the two halves of one identity
     * cannot disagree, which is the type's job, rather than deciding that something ended, which
     * belongs to a tick branch. No duration is touched here, so no branch loses its ending.</p>
     */
    public record Witness(Optional<UUID> id, Optional<String> dimension) {
        public Witness {
            id = Objects.requireNonNull(id, "id");
            dimension = Objects.requireNonNull(dimension, "dimension").filter(key -> !key.isBlank());
            if (id.isEmpty() || dimension.isEmpty()) {
                id = Optional.empty();
                dimension = Optional.empty();
            }
        }

        public static Witness none() {
            return new Witness(Optional.empty(), Optional.empty());
        }

        public static Witness of(final UUID id, final String dimension) {
            return new Witness(Optional.of(id), Optional.of(dimension));
        }

        public boolean present() {
            return id.isPresent() && dimension.isPresent();
        }
    }

    /**
     * The one haunting in flight.
     *
     * <p>The compact constructor deliberately clamps each field independently and reconciles
     * nothing across fields. Every phase of a haunting is ended by exactly one tick branch in
     * {@link SpectreRuntime}, and a constructor that quietly zeroed a sibling timer when the
     * episode budget ran out would race those branches: the branch that owns ending the dread
     * window would never observe its own expiry, so the fade it must arm and the witness it must
     * release would simply never happen and the Spectre would strand on a spent haunting forever.
     * Tick dispatch is the single exit. Structurally impossible combinations stay safe because
     * every attention branch runs {@code endHauntIfRequired} before it dereferences the witness,
     * and an absent witness ends the haunting through {@code HauntEnd.WITNESS_LOST}.</p>
     */
    public record Haunt(
        int remainingTicks,
        int manifestRemainingTicks,
        int telegraphRemainingTicks,
        int dreadRemainingTicks,
        int fadeRemainingTicks,
        int telegraphs,
        int dreads
    ) {
        public Haunt {
            remainingTicks =
                ApparitionEpisodeRules.clampRemaining(remainingTicks, SpectreRules.EPISODE_TICKS);
            manifestRemainingTicks = ApparitionEpisodeRules.clampRemaining(
                manifestRemainingTicks, SpectreRules.MANIFEST_TICKS);
            telegraphRemainingTicks = ApparitionEpisodeRules.clampRemaining(
                telegraphRemainingTicks, SpectreRules.TELEGRAPH_INTERVAL_TICKS);
            dreadRemainingTicks = ApparitionEpisodeRules.clampRemaining(
                dreadRemainingTicks, SpectreRules.DREAD_TICKS);
            fadeRemainingTicks = ApparitionEpisodeRules.clampRemaining(
                fadeRemainingTicks, SpectreRules.FADE_TICKS);
            telegraphs = Math.clamp(telegraphs, 0, SpectreRules.MAX_TELEGRAPHS);
            dreads = Math.clamp(dreads, 0, SpectreRules.MAX_DREADS);
        }

        public static Haunt none() {
            return new Haunt(0, 0, 0, 0, 0, 0, 0);
        }

        public static Haunt started() {
            return new Haunt(
                SpectreRules.EPISODE_TICKS,
                SpectreRules.MANIFEST_TICKS,
                SpectreRules.TELEGRAPH_INTERVAL_TICKS,
                0, 0, 0, 0
            );
        }

        public boolean active() {
            return remainingTicks > 0;
        }

        public Haunt withTelegraph(final int interval, final int emitted) {
            return new Haunt(remainingTicks, manifestRemainingTicks, interval, dreadRemainingTicks,
                fadeRemainingTicks, emitted, dreads);
        }

        public Haunt withDread(final int dread) {
            return new Haunt(remainingTicks, 0, 0, dread, fadeRemainingTicks, telegraphs, dreads);
        }

        public Haunt withDreads(final int updated) {
            return new Haunt(remainingTicks, manifestRemainingTicks, telegraphRemainingTicks,
                dreadRemainingTicks, fadeRemainingTicks, telegraphs, updated);
        }

        public Haunt withFade(final int fade) {
            return new Haunt(remainingTicks, 0, 0, 0, fade, telegraphs, dreads);
        }
    }

    public static SpectreState empty() {
        return new SpectreState(SCHEMA_VERSION, Phase.DRIFT, Witness.none(), Haunt.none(),
            new RouteLedger(0, 0, 0), 0);
    }

    public SpectreState withPhase(final Phase updated) {
        return new SpectreState(schemaVersion, updated, witness, haunt, route, cooldownTicks);
    }

    public SpectreState withWitness(final Witness updated) {
        return new SpectreState(schemaVersion, phase, updated, haunt, route, cooldownTicks);
    }

    public SpectreState withHaunt(final Haunt updated) {
        return new SpectreState(schemaVersion, phase, witness, updated, route, cooldownTicks);
    }

    public SpectreState withRoute(final RouteLedger updated) {
        return new SpectreState(schemaVersion, phase, witness, haunt, updated, cooldownTicks);
    }

    public SpectreState withCooldown(final int updated) {
        return new SpectreState(schemaVersion, phase, witness, haunt, route, updated);
    }

    /**
     * Ends the haunting, releases the witness, arms the cadence and clears the route accounting in
     * one write, so no half-cancelled haunting is ever observable.
     */
    public SpectreState endHaunt() {
        return new SpectreState(schemaVersion, Phase.DRIFT, Witness.none(), Haunt.none(),
            new RouteLedger(0, 0, route.routeRetryTicks()), SpectreRules.COOLDOWN_TICKS);
    }

    /** Compact fixed-cardinality encoding, asserted below the shared state-size ceiling. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", schemaVersion);
        tag.putString("Phase", phase.name().toLowerCase(Locale.ROOT));
        witness.id().ifPresent(id -> tag.putString("WitnessId", id.toString()));
        witness.dimension().ifPresent(dimension -> tag.putString("WitnessDim", dimension));
        tag.putInt("Haunt", haunt.remainingTicks());
        tag.putInt("Manifest", haunt.manifestRemainingTicks());
        tag.putInt("Telegraph", haunt.telegraphRemainingTicks());
        tag.putInt("Dread", haunt.dreadRemainingTicks());
        tag.putInt("Fade", haunt.fadeRemainingTicks());
        tag.putInt("Telegraphs", haunt.telegraphs());
        tag.putInt("Dreads", haunt.dreads());
        tag.putInt("PathCooldown", route.pathCooldownTicks());
        tag.putInt("RouteFail", route.routeFailures());
        tag.putInt("RouteRetry", route.routeRetryTicks());
        tag.putInt("Cooldown", cooldownTicks);
        return tag;
    }

    /**
     * Reads version 1. A missing or unknown schema resets to a safe drift. Every remaining duration
     * is clamped without consulting elapsed world time, a witness from another dimension is dropped
     * rather than pursued, the telegraph interval is restored when it was persisted as zero so no
     * feedback replays on load, and a reload never lands inside an open dread window: a saved DREAD
     * is restored as MANIFEST so the Spectre must telegraph again, while the delivered dread count
     * survives so no reload can grant a second delivery.
     */
    public static SpectreState read(final CompoundTag tag, final String currentDimension) {
        if (tag == null || tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return empty();
        }
        final Witness witness = new Witness(
            readUuid(tag, "WitnessId"),
            readText(tag, "WitnessDim").filter(dimension -> dimension.equals(currentDimension))
        );
        final Haunt haunt = new Haunt(
            tag.getIntOr("Haunt", 0),
            tag.getIntOr("Manifest", 0),
            SpectreRules.resetTelegraphIntervalOnLoad(tag.getIntOr("Telegraph", 0)),
            tag.getIntOr("Dread", 0),
            tag.getIntOr("Fade", 0),
            tag.getIntOr("Telegraphs", 0),
            tag.getIntOr("Dreads", 0)
        );
        final Phase stored = parsePhase(tag.getStringOr("Phase", ""));
        return new SpectreState(
            SCHEMA_VERSION,
            stored == Phase.DREAD ? Phase.MANIFEST : stored,
            witness,
            haunt,
            new RouteLedger(
                tag.getIntOr("PathCooldown", 0),
                tag.getIntOr("RouteFail", 0),
                tag.getIntOr("RouteRetry", 0)
            ),
            tag.getIntOr("Cooldown", 0)
        );
    }

    private static Phase parsePhase(final String value) {
        for (final Phase candidate : Phase.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        return Phase.DRIFT;
    }

    private static Optional<UUID> readUuid(final CompoundTag tag, final String key) {
        final String stored = tag.getStringOr(key, "");
        if (stored.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(stored));
        } catch (final IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static Optional<String> readText(final CompoundTag tag, final String key) {
        final String stored = tag.getStringOr(key, "");
        return stored.isBlank() ? Optional.empty() : Optional.of(stored);
    }
}
