package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ApparitionEpisodeRules.RouteLedger;
import com.kadamitas.warlockery.entity.EchoShadeRules.Phase;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * Versioned, fixed-cardinality Echo Shade semantics. Exactly one mark and one echo may exist. Every
 * duration is a remaining loaded-active tick count, never an absolute world deadline, so unloading
 * pauses meaning instead of expiring it. No path, live entity reference, collection, line-of-sight
 * cache, player copy or client fact is ever stored: the mark is a bare UUID and a dimension key.
 *
 * <p>Nothing about the marked player other than that identity is recorded. There is no inventory,
 * armor, effect, attribute, name, skin, pose, input or NBT field here, and there is deliberately
 * nowhere for one to be added without changing this record's shape.</p>
 */
public record EchoShadeState(
    int schemaVersion,
    Phase phase,
    Mark mark,
    Echo echo,
    RouteLedger route,
    int cooldownTicks
) {
    public static final int SCHEMA_VERSION = 1;

    public EchoShadeState {
        phase = Objects.requireNonNull(phase, "phase");
        mark = Objects.requireNonNull(mark, "mark");
        echo = Objects.requireNonNull(echo, "echo");
        route = Objects.requireNonNull(route, "route");
        cooldownTicks =
            ApparitionEpisodeRules.clampRemaining(cooldownTicks, EchoShadeRules.COOLDOWN_TICKS);
    }

    /**
     * The one marked player, or none. Never a live entity and never anything the player owns.
     *
     * <p>The compact constructor collapses a half-written mark to none. That is the identity shape
     * of reconciliation, not the timer shape: it asserts that the two halves of one identity cannot
     * disagree, which is the type's job, rather than deciding that something ended, which belongs
     * to a tick branch. No duration is touched here, so no branch can be robbed of its ending.</p>
     */
    public record Mark(Optional<UUID> id, Optional<String> dimension) {
        public Mark {
            id = Objects.requireNonNull(id, "id");
            dimension = Objects.requireNonNull(dimension, "dimension").filter(key -> !key.isBlank());
            if (id.isEmpty() || dimension.isEmpty()) {
                id = Optional.empty();
                dimension = Optional.empty();
            }
        }

        public static Mark none() {
            return new Mark(Optional.empty(), Optional.empty());
        }

        public static Mark of(final UUID id, final String dimension) {
            return new Mark(Optional.of(id), Optional.of(dimension));
        }

        public boolean present() {
            return id.isPresent() && dimension.isPresent();
        }
    }

    /**
     * The one echo in flight.
     *
     * <p>The compact constructor deliberately clamps each field independently and reconciles
     * nothing across fields. Every phase of an echo is ended by exactly one tick branch in
     * {@link EchoShadeRuntime}, and a constructor that quietly zeroed a sibling timer when the
     * episode budget ran out would race those branches: the branch that owns ending the strike
     * window would never observe its own expiry, so the recovery it must arm and the mark it must
     * release would simply never happen and the shade would strand on a spent echo forever. Tick
     * dispatch is the single exit. Structurally impossible combinations stay safe because every
     * attention branch runs {@code endEchoIfRequired} before it dereferences the mark, and an
     * absent mark ends the echo through {@code EchoEnd.MARK_LOST}.</p>
     */
    public record Echo(
        int remainingTicks,
        int recordRemainingTicks,
        int sampleRemainingTicks,
        int answerRemainingTicks,
        int strikeRemainingTicks,
        int recoverRemainingTicks,
        int recordedMillisX,
        int recordedMillisZ,
        int samples,
        int strikes
    ) {
        public Echo {
            remainingTicks =
                ApparitionEpisodeRules.clampRemaining(remainingTicks, EchoShadeRules.EPISODE_TICKS);
            recordRemainingTicks = ApparitionEpisodeRules.clampRemaining(
                recordRemainingTicks, EchoShadeRules.RECORD_TICKS);
            sampleRemainingTicks = ApparitionEpisodeRules.clampRemaining(
                sampleRemainingTicks, EchoShadeRules.SAMPLE_INTERVAL_TICKS);
            answerRemainingTicks = ApparitionEpisodeRules.clampRemaining(
                answerRemainingTicks, EchoShadeRules.ANSWER_TICKS);
            strikeRemainingTicks = ApparitionEpisodeRules.clampRemaining(
                strikeRemainingTicks, EchoShadeRules.STRIKE_TICKS);
            recoverRemainingTicks = ApparitionEpisodeRules.clampRemaining(
                recoverRemainingTicks, EchoShadeRules.RECOVER_TICKS);
            recordedMillisX = Math.clamp(recordedMillisX,
                -EchoShadeRules.MAX_RECORDED_MILLIS, EchoShadeRules.MAX_RECORDED_MILLIS);
            recordedMillisZ = Math.clamp(recordedMillisZ,
                -EchoShadeRules.MAX_RECORDED_MILLIS, EchoShadeRules.MAX_RECORDED_MILLIS);
            samples = Math.clamp(samples, 0, EchoShadeRules.MAX_SAMPLES);
            strikes = Math.clamp(strikes, 0, EchoShadeRules.MAX_STRIKES);
        }

        public static Echo none() {
            return new Echo(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        public static Echo started() {
            return new Echo(
                EchoShadeRules.EPISODE_TICKS,
                EchoShadeRules.RECORD_TICKS,
                EchoShadeRules.SAMPLE_INTERVAL_TICKS,
                0, 0, 0, 0, 0, 0, 0
            );
        }

        public boolean active() {
            return remainingTicks > 0;
        }

        public Echo withRemaining(final int updated) {
            return new Echo(updated, recordRemainingTicks, sampleRemainingTicks,
                answerRemainingTicks, strikeRemainingTicks, recoverRemainingTicks,
                recordedMillisX, recordedMillisZ, samples, strikes);
        }

        public Echo withRecord(final int record, final int sample) {
            return new Echo(remainingTicks, record, sample, answerRemainingTicks,
                strikeRemainingTicks, recoverRemainingTicks, recordedMillisX, recordedMillisZ,
                samples, strikes);
        }

        public Echo withSampled(final int millisX, final int millisZ, final int updatedSamples) {
            return new Echo(remainingTicks, recordRemainingTicks,
                EchoShadeRules.SAMPLE_INTERVAL_TICKS, answerRemainingTicks, strikeRemainingTicks,
                recoverRemainingTicks, millisX, millisZ, updatedSamples, strikes);
        }

        public Echo withAnswer(final int answer) {
            return new Echo(remainingTicks, 0, 0, answer, strikeRemainingTicks,
                recoverRemainingTicks, recordedMillisX, recordedMillisZ, samples, strikes);
        }

        public Echo withStrike(final int strike) {
            return new Echo(remainingTicks, 0, 0, 0, strike, recoverRemainingTicks,
                recordedMillisX, recordedMillisZ, samples, strikes);
        }

        public Echo withStrikes(final int updated) {
            return new Echo(remainingTicks, recordRemainingTicks, sampleRemainingTicks,
                answerRemainingTicks, strikeRemainingTicks, recoverRemainingTicks,
                recordedMillisX, recordedMillisZ, samples, updated);
        }

        public Echo withRecover(final int recover) {
            return new Echo(remainingTicks, 0, 0, 0, 0, recover,
                recordedMillisX, recordedMillisZ, samples, strikes);
        }
    }

    public static EchoShadeState empty() {
        return new EchoShadeState(SCHEMA_VERSION, Phase.WATCH, Mark.none(), Echo.none(),
            new RouteLedger(0, 0, 0), 0);
    }

    public EchoShadeState withPhase(final Phase updated) {
        return new EchoShadeState(schemaVersion, updated, mark, echo, route, cooldownTicks);
    }

    public EchoShadeState withMark(final Mark updated) {
        return new EchoShadeState(schemaVersion, phase, updated, echo, route, cooldownTicks);
    }

    public EchoShadeState withEcho(final Echo updated) {
        return new EchoShadeState(schemaVersion, phase, mark, updated, route, cooldownTicks);
    }

    public EchoShadeState withRoute(final RouteLedger updated) {
        return new EchoShadeState(schemaVersion, phase, mark, echo, updated, cooldownTicks);
    }

    public EchoShadeState withCooldown(final int updated) {
        return new EchoShadeState(schemaVersion, phase, mark, echo, route, updated);
    }

    /**
     * Ends the echo, releases the mark, arms the cadence and clears the route accounting in one
     * write, so no half-cancelled echo is ever observable.
     */
    public EchoShadeState endEcho() {
        return new EchoShadeState(schemaVersion, Phase.WATCH, Mark.none(), Echo.none(),
            new RouteLedger(0, 0, route.routeRetryTicks()), EchoShadeRules.COOLDOWN_TICKS);
    }

    /** Compact fixed-cardinality encoding, asserted below the shared state-size ceiling. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", schemaVersion);
        tag.putString("Phase", phase.name().toLowerCase(Locale.ROOT));
        mark.id().ifPresent(id -> tag.putString("MarkId", id.toString()));
        mark.dimension().ifPresent(dimension -> tag.putString("MarkDim", dimension));
        tag.putInt("Echo", echo.remainingTicks());
        tag.putInt("Record", echo.recordRemainingTicks());
        tag.putInt("Sample", echo.sampleRemainingTicks());
        tag.putInt("Answer", echo.answerRemainingTicks());
        tag.putInt("Strike", echo.strikeRemainingTicks());
        tag.putInt("Recover", echo.recoverRemainingTicks());
        tag.putInt("MillisX", echo.recordedMillisX());
        tag.putInt("MillisZ", echo.recordedMillisZ());
        tag.putInt("Samples", echo.samples());
        tag.putInt("Strikes", echo.strikes());
        tag.putInt("PathCooldown", route.pathCooldownTicks());
        tag.putInt("RouteFail", route.routeFailures());
        tag.putInt("RouteRetry", route.routeRetryTicks());
        tag.putInt("Cooldown", cooldownTicks);
        return tag;
    }

    /**
     * Reads version 1. A missing or unknown schema resets to a safe watch. Every remaining duration
     * is clamped without consulting elapsed world time, a mark from another dimension is dropped
     * rather than chased, and a reload never lands inside an open strike window: a saved STRIKE is
     * restored as ANSWER so the shade must earn its single attempt again, while the spent strike
     * count survives so no reload can grant a second one.
     */
    public static EchoShadeState read(final CompoundTag tag, final String currentDimension) {
        if (tag == null || tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return empty();
        }
        final Mark mark = new Mark(
            readUuid(tag, "MarkId"),
            readText(tag, "MarkDim").filter(dimension -> dimension.equals(currentDimension))
        );
        final Echo echo = new Echo(
            tag.getIntOr("Echo", 0),
            tag.getIntOr("Record", 0),
            tag.getIntOr("Sample", 0),
            tag.getIntOr("Answer", 0),
            tag.getIntOr("Strike", 0),
            tag.getIntOr("Recover", 0),
            tag.getIntOr("MillisX", 0),
            tag.getIntOr("MillisZ", 0),
            tag.getIntOr("Samples", 0),
            tag.getIntOr("Strikes", 0)
        );
        final Phase stored = parsePhase(tag.getStringOr("Phase", ""));
        return new EchoShadeState(
            SCHEMA_VERSION,
            stored == Phase.STRIKE ? Phase.ANSWER : stored,
            mark,
            echo,
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
        return Phase.WATCH;
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
