package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.DeathRules.Phase;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Versioned, fixed-cardinality Death semantics. Exactly one subject, one phase, one set of
 * remaining loaded-tick durations, and a zero to three failure counter exist. Every duration is a
 * remaining loaded-active tick count, never an absolute world deadline, so unloading pauses the
 * appointment instead of expiring or replaying it. No path, live entity reference, collection,
 * line-of-sight cache, victim history, or client fact is ever stored.
 */
public record DeathState(
    int schemaVersion,
    Phase phase,
    Appointment appointment,
    Cadence cadence
) {
    public static final int SCHEMA_VERSION = 1;

    public DeathState {
        phase = Objects.requireNonNull(phase, "phase");
        appointment = Objects.requireNonNull(appointment, "appointment");
        cadence = Objects.requireNonNull(cadence, "cadence");
        phase = reconcile(phase, appointment);
    }

    /**
     * The single appointment. {@code reaped} is the only fact that survives a reload with the
     * power to forbid work: a completed attempt can never be replayed by any load path.
     */
    public record Appointment(
        Optional<UUID> subject,
        Optional<String> dimension,
        Optional<BlockPos> lastSeen,
        int approachRemainingTicks,
        int telegraphRemainingTicks,
        int recoverRemainingTicks,
        boolean reaped
    ) {
        public Appointment {
            subject = Objects.requireNonNull(subject, "subject");
            dimension = Objects.requireNonNull(dimension, "dimension").filter(value -> !value.isBlank());
            lastSeen = Objects.requireNonNull(lastSeen, "lastSeen").map(BlockPos::immutable);
            if (subject.isEmpty() || dimension.isEmpty()) {
                subject = Optional.empty();
                dimension = Optional.empty();
                lastSeen = Optional.empty();
                approachRemainingTicks = 0;
                telegraphRemainingTicks = 0;
                reaped = false;
            }
            approachRemainingTicks =
                DeathRules.clampRemaining(approachRemainingTicks, DeathRules.APPROACH_DEADLINE_TICKS);
            telegraphRemainingTicks =
                DeathRules.clampRemaining(telegraphRemainingTicks, DeathRules.TELEGRAPH_TICKS);
            recoverRemainingTicks =
                DeathRules.clampRemaining(recoverRemainingTicks, DeathRules.RECOVER_TICKS);
        }

        public static Appointment none() {
            return new Appointment(Optional.empty(), Optional.empty(), Optional.empty(), 0, 0, 0, false);
        }

        public static Appointment appointed(final UUID subject, final String dimension) {
            return new Appointment(
                Optional.of(subject), Optional.of(dimension), Optional.empty(),
                DeathRules.APPROACH_DEADLINE_TICKS, DeathRules.TELEGRAPH_TICKS, 0, false
            );
        }

        /**
         * Direct-attacker defense reuses the one episode shape on a deliberately shorter leash:
         * it answers an attacker rather than keeping an appointment, so it gets
         * {@link DeathRules#DIRECT_ATTACKER_TICKS} instead of the full approach deadline.
         */
        public static Appointment retaliation(final UUID attacker, final String dimension) {
            return new Appointment(
                Optional.of(attacker), Optional.of(dimension), Optional.empty(),
                DeathRules.DIRECT_ATTACKER_TICKS, DeathRules.TELEGRAPH_TICKS, 0, false
            );
        }

        public boolean present() {
            return subject.isPresent() && dimension.isPresent();
        }
    }

    public record Cadence(int reappointCooldownTicks, int routeFailures, int routeRetryTicks) {
        public Cadence {
            reappointCooldownTicks =
                DeathRules.clampRemaining(reappointCooldownTicks, DeathRules.REAPPOINT_COOLDOWN_TICKS);
            routeFailures = Math.clamp(routeFailures, 0, DeathRules.MAX_ROUTE_FAILURES);
            routeRetryTicks = DeathRules.clampRemaining(routeRetryTicks, DeathRules.ROUTE_BACKOFF_TICKS);
        }

        public static Cadence none() {
            return new Cadence(0, 0, 0);
        }
    }

    public static DeathState empty() {
        return new DeathState(SCHEMA_VERSION, Phase.QUIESCENT, Appointment.none(), Cadence.none());
    }

    /**
     * A phase that names an appointment cannot survive without one. {@code RECOVER} deliberately
     * survives an exhausted recovery: the runtime's own recovery decision is the single transition
     * that releases the appointment, counts the release, and starts the reappointment backoff, so
     * this constructor must never take that transition away from it.
     */
    private static Phase reconcile(final Phase phase, final Appointment appointment) {
        return switch (phase) {
            case APPOINTED, APPROACH, TELEGRAPH, REAP ->
                appointment.present() ? phase : Phase.QUIESCENT;
            case RECOVER, RELEASE, QUIESCENT -> phase;
        };
    }

    public DeathState withPhase(final Phase updated) {
        return new DeathState(schemaVersion, updated, appointment, cadence);
    }

    public DeathState withAppointment(final Appointment updated) {
        return new DeathState(schemaVersion, phase, updated, cadence);
    }

    public DeathState withCadence(final Cadence updated) {
        return new DeathState(schemaVersion, phase, appointment, updated);
    }

    /**
     * Clears the subject, resets the failure counter after it was observed, and starts the
     * reappointment backoff. A recovery still owing loaded ticks keeps running so a completed
     * attempt is always followed by its full recovery before anything else may be appointed.
     */
    public DeathState releaseAppointment() {
        final int recover = appointment.recoverRemainingTicks();
        return new DeathState(
            schemaVersion,
            recover > 0 ? Phase.RECOVER : Phase.RELEASE,
            new Appointment(Optional.empty(), Optional.empty(), Optional.empty(), 0, 0, recover, false),
            new Cadence(DeathRules.REAPPOINT_COOLDOWN_TICKS, 0, cadence.routeRetryTicks())
        );
    }

    /**
     * Compact fixed-cardinality encoding. No path, victim history, soul ledger, world death
     * record, or cross-family fact is representable. Representative populated states must encode
     * below {@link DeathRules#MAX_STATE_BYTES}.
     */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", schemaVersion);
        tag.putString("Phase", phase.name().toLowerCase(Locale.ROOT));
        appointment.subject().ifPresent(subject -> tag.putString("SubjId", subject.toString()));
        appointment.dimension().ifPresent(dimension -> tag.putString("SubjDim", dimension));
        appointment.lastSeen().ifPresent(position -> tag.putLong("SubjSeen", position.asLong()));
        tag.putInt("Approach", appointment.approachRemainingTicks());
        tag.putInt("Telegraph", appointment.telegraphRemainingTicks());
        tag.putInt("Recover", appointment.recoverRemainingTicks());
        tag.putBoolean("Reaped", appointment.reaped());
        tag.putInt("Reappoint", cadence.reappointCooldownTicks());
        tag.putInt("RouteFail", cadence.routeFailures());
        tag.putInt("RouteRetry", cadence.routeRetryTicks());
        return tag;
    }

    /**
     * Reads version 1. A missing, malformed, or unknown schema resets to a safe quiescent state.
     * Every duration is clamped without consulting elapsed world time, a subject recorded in
     * another dimension is dropped, and a stored {@code REAP} phase always resumes as a completed
     * attempt so no load path can ever produce a second reaping call for the same episode.
     */
    public static DeathState read(final CompoundTag tag, final String currentDimension) {
        if (tag == null || tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return empty();
        }
        final Phase stored = parsePhase(tag.getStringOr("Phase", ""));
        final Optional<String> dimension = readDimension(tag, "SubjDim")
            .filter(value -> value.equals(currentDimension));
        final Appointment appointment = new Appointment(
            readUuid(tag, "SubjId"),
            dimension,
            readPosition(tag, "SubjSeen"),
            tag.getIntOr("Approach", 0),
            tag.getIntOr("Telegraph", 0),
            tag.getIntOr("Recover", 0),
            tag.getBooleanOr("Reaped", false) || stored == Phase.REAP
        );
        final Cadence cadence = new Cadence(
            tag.getIntOr("Reappoint", 0),
            tag.getIntOr("RouteFail", 0),
            tag.getIntOr("RouteRetry", 0)
        );
        return new DeathState(SCHEMA_VERSION, resumePhase(stored), appointment, cadence);
    }

    /**
     * Reload reconstructs navigation from a plain approach. A telegraph is never resumed
     * mid-hold and a reaping phase is never re-entered: both restart as an approach whose
     * completed-attempt flag already forbids a second call.
     */
    private static Phase resumePhase(final Phase stored) {
        return switch (stored) {
            case APPOINTED, APPROACH, TELEGRAPH, REAP -> Phase.APPROACH;
            case RECOVER, RELEASE, QUIESCENT -> stored;
        };
    }

    private static Phase parsePhase(final String value) {
        for (final Phase candidate : Phase.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        return Phase.QUIESCENT;
    }

    /** Key presence is the authority, so no encodable position is mistaken for an absent one. */
    private static Optional<BlockPos> readPosition(final CompoundTag tag, final String key) {
        return tag.contains(key) ? Optional.of(BlockPos.of(tag.getLongOr(key, 0L))) : Optional.empty();
    }

    private static Optional<String> readDimension(final CompoundTag tag, final String key) {
        final String stored = tag.getStringOr(key, "");
        return stored.isBlank() ? Optional.empty() : Optional.of(stored);
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
}
