package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.SpiritRules.Phase;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Versioned, fixed-cardinality Spirit semantics. Exactly one attendance anchor, one wary
 * reaction, and one guard subject may exist. Every duration is a remaining loaded-active tick
 * count, never an absolute world deadline, so unloading pauses meaning instead of expiring it.
 * No path, live entity reference, collection, line-of-sight cache, owner copy, or client fact is
 * ever stored: the one owner authority stays the generic {@link CreatureBehaviorState} UUID.
 */
public record SpiritState(
    int schemaVersion,
    Phase phase,
    Anchor anchor,
    Wary wary,
    Attendance attendance,
    Guard guard,
    Cadence cadence
) {
    public static final int SCHEMA_VERSION = 1;

    public SpiritState {
        phase = Objects.requireNonNull(phase, "phase");
        anchor = Objects.requireNonNull(anchor, "anchor");
        wary = Objects.requireNonNull(wary, "wary");
        attendance = Objects.requireNonNull(attendance, "attendance");
        guard = Objects.requireNonNull(guard, "guard");
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

    public record Wary(int remainingTicks, int cooldownTicks) {
        public Wary {
            remainingTicks = SpiritRules.clampRemaining(remainingTicks, SpiritRules.WARY_TICKS);
            cooldownTicks = SpiritRules.clampRemaining(cooldownTicks, SpiritRules.WARY_COOLDOWN_TICKS);
        }

        public static Wary none() {
            return new Wary(0, 0);
        }

        public static Wary started() {
            return new Wary(SpiritRules.WARY_TICKS, 0);
        }

        public boolean active() {
            return remainingTicks > 0;
        }
    }

    public record Attendance(int remainingTicks, int pulseRemainingTicks, int pulsesEmitted) {
        public Attendance {
            remainingTicks = SpiritRules.clampRemaining(remainingTicks, SpiritRules.ATTEND_TICKS);
            pulseRemainingTicks = SpiritRules.clampRemaining(
                pulseRemainingTicks, SpiritRules.ATTEND_PULSE_INTERVAL_TICKS
            );
            pulsesEmitted = Math.clamp(pulsesEmitted, 0, SpiritRules.MAX_ATTEND_PULSES);
            if (remainingTicks <= 0) {
                pulseRemainingTicks = 0;
                pulsesEmitted = 0;
            }
        }

        public static Attendance none() {
            return new Attendance(0, 0, 0);
        }

        public static Attendance started() {
            return new Attendance(SpiritRules.ATTEND_TICKS,
                SpiritRules.ATTEND_PULSE_INTERVAL_TICKS, 0);
        }

        public boolean active() {
            return remainingTicks > 0;
        }
    }

    /**
     * The one accepted direct attacker plus the exact bounded warn, defend, strike and recovery
     * accounting. A strike count that has reached its cap is what makes the defence finite: it is
     * persisted so a reload can never grant a second attributed strike.
     */
    public record Guard(
        Optional<UUID> attackerId,
        Optional<String> dimension,
        int warnRemainingTicks,
        int warnPulseRemainingTicks,
        int warnPulsesEmitted,
        int defendRemainingTicks,
        int strikes,
        int recoverRemainingTicks
    ) {
        public Guard {
            attackerId = Objects.requireNonNull(attackerId, "attackerId");
            dimension = Objects.requireNonNull(dimension, "dimension").filter(value -> !value.isBlank());
            if (attackerId.isEmpty() || dimension.isEmpty()) {
                attackerId = Optional.empty();
                dimension = Optional.empty();
                warnRemainingTicks = 0;
                warnPulseRemainingTicks = 0;
                warnPulsesEmitted = 0;
                defendRemainingTicks = 0;
                strikes = 0;
            }
            warnRemainingTicks = SpiritRules.clampRemaining(warnRemainingTicks, SpiritRules.WARN_TICKS);
            warnPulseRemainingTicks = SpiritRules.clampRemaining(
                warnPulseRemainingTicks, SpiritRules.WARN_PULSE_INTERVAL_TICKS
            );
            warnPulsesEmitted = Math.clamp(warnPulsesEmitted, 0, SpiritRules.MAX_WARN_PULSES);
            defendRemainingTicks =
                SpiritRules.clampRemaining(defendRemainingTicks, SpiritRules.DEFEND_TICKS);
            strikes = Math.clamp(strikes, 0, SpiritRules.MAX_DEFENCE_STRIKES);
            recoverRemainingTicks =
                SpiritRules.clampRemaining(recoverRemainingTicks, SpiritRules.RECOVER_TICKS);
        }

        public static Guard none() {
            return new Guard(Optional.empty(), Optional.empty(), 0, 0, 0, 0, 0, 0);
        }

        public static Guard recovering(final int recoverRemainingTicks) {
            return new Guard(Optional.empty(), Optional.empty(), 0, 0, 0, 0, 0,
                recoverRemainingTicks);
        }

        public static Guard warning(final UUID attackerId, final String dimension, final int recover) {
            return new Guard(Optional.of(attackerId), Optional.of(dimension),
                SpiritRules.WARN_TICKS, SpiritRules.WARN_PULSE_INTERVAL_TICKS, 0, 0, 0, recover);
        }

        public boolean present() {
            return attackerId.isPresent() && dimension.isPresent();
        }
    }

    public record Cadence(int routeFailures, int routeRetryTicks, int attendCooldownTicks) {
        public Cadence {
            routeFailures = Math.clamp(routeFailures, 0, SpiritRules.MAX_ROUTE_FAILURES);
            routeRetryTicks =
                SpiritRules.clampRemaining(routeRetryTicks, SpiritRules.ROUTE_BACKOFF_TICKS);
            attendCooldownTicks =
                SpiritRules.clampRemaining(attendCooldownTicks, SpiritRules.ATTEND_COOLDOWN_TICKS);
        }

        public static Cadence none() {
            return new Cadence(0, 0, 0);
        }
    }

    public static SpiritState empty() {
        return new SpiritState(SCHEMA_VERSION, Phase.WANDER, Anchor.none(), Wary.none(),
            Attendance.none(), Guard.none(), Cadence.none());
    }

    /**
     * The canonical constructor deliberately performs no phase reconciliation. Every phase here
     * is ended by exactly one tick branch in {@link SpiritRuntime}, and a constructor that
     * silently rewrote a phase would race those branches: an elapsed wary reaction would leave
     * WARY before {@code tickWary} could arm its cooldown, and an elapsed attendance would leave
     * ATTEND before {@code tickAttend} could arm its own. Structural nonsense is still safe,
     * because {@code tickGuard} ends an unsupported guard through
     * {@code GuardEnd.INVALID_ATTACKER} and {@code tickAttend} ends an unsupported attendance
     * before either dereferences anything.
     */
    public SpiritState withPhase(final Phase updated) {
        return new SpiritState(schemaVersion, updated, anchor, wary, attendance, guard, cadence);
    }

    public SpiritState withAnchor(final Anchor updated) {
        return new SpiritState(schemaVersion, phase, updated, wary, attendance, guard, cadence);
    }

    public SpiritState withWary(final Wary updated) {
        return new SpiritState(schemaVersion, phase, anchor, updated, attendance, guard, cadence);
    }

    public SpiritState withAttendance(final Attendance updated) {
        return new SpiritState(schemaVersion, phase, anchor, wary, updated, guard, cadence);
    }

    public SpiritState withGuard(final Guard updated) {
        return new SpiritState(schemaVersion, phase, anchor, wary, attendance, updated, cadence);
    }

    public SpiritState withCadence(final Cadence updated) {
        return new SpiritState(schemaVersion, phase, anchor, wary, attendance, guard, updated);
    }

    public SpiritState endAttendance() {
        return withAnchor(Anchor.none())
            .withAttendance(Attendance.none())
            .withCadence(new Cadence(0, cadence.routeRetryTicks(), SpiritRules.ATTEND_COOLDOWN_TICKS))
            .withPhase(Phase.WANDER);
    }

    /**
     * Atomic binding transition. Avoidance, attendance and route accounting are dropped in the
     * same state write that enters bound following, so no wary reaction can survive a binding.
     */
    public SpiritState bind() {
        return new SpiritState(schemaVersion, Phase.BOUND, Anchor.none(), Wary.none(),
            Attendance.none(), Guard.none(), new Cadence(0, 0, 0));
    }

    public SpiritState unbind() {
        return new SpiritState(schemaVersion, Phase.WANDER, Anchor.none(), Wary.none(),
            Attendance.none(), Guard.none(), new Cadence(0, 0, SpiritRules.ATTEND_COOLDOWN_TICKS));
    }

    /** Ends the guard, keeping only the recovery window that forbids an immediate second defence. */
    public SpiritState endGuard() {
        return withGuard(Guard.recovering(SpiritRules.RECOVER_TICKS))
            .withCadence(new Cadence(0, cadence.routeRetryTicks(), cadence.attendCooldownTicks()))
            .withPhase(Phase.RECOVER);
    }

    /**
     * Compact fixed-cardinality encoding. Representative populated states must encode below
     * {@link SpiritRules#MAX_STATE_BYTES}.
     */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", schemaVersion);
        tag.putString("Phase", phase.name().toLowerCase(Locale.ROOT));
        anchor.position().ifPresent(position -> tag.putLong("AnchorPos", position.asLong()));
        anchor.dimension().ifPresent(dimension -> tag.putString("AnchorDim", dimension));
        tag.putInt("Wary", wary.remainingTicks());
        tag.putInt("WaryCool", wary.cooldownTicks());
        tag.putInt("Attend", attendance.remainingTicks());
        tag.putInt("AttendPulse", attendance.pulseRemainingTicks());
        tag.putInt("AttendCount", attendance.pulsesEmitted());
        guard.attackerId().ifPresent(id -> tag.putString("AtkId", id.toString()));
        guard.dimension().ifPresent(dimension -> tag.putString("AtkDim", dimension));
        tag.putInt("Warn", guard.warnRemainingTicks());
        tag.putInt("WarnPulse", guard.warnPulseRemainingTicks());
        tag.putInt("WarnCount", guard.warnPulsesEmitted());
        tag.putInt("Defend", guard.defendRemainingTicks());
        tag.putInt("Strikes", guard.strikes());
        tag.putInt("Recover", guard.recoverRemainingTicks());
        tag.putInt("RouteFail", cadence.routeFailures());
        tag.putInt("RouteRetry", cadence.routeRetryTicks());
        tag.putInt("AttendCool", cadence.attendCooldownTicks());
        return tag;
    }

    /**
     * Reads version 1. A missing or unknown schema resets to a safe wander. Every remaining
     * duration is clamped without consulting elapsed world time, the warn and attendance pulse
     * intervals are restored when they were persisted as zero so no feedback replays on load, and
     * a guard subject survives only while its own dimension coupling is intact. A persisted DEFEND
     * always reloads as WARN so no reload can begin already inside a strike window.
     */
    public static SpiritState read(final CompoundTag tag, final String currentDimension) {
        if (tag == null || tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return empty();
        }
        final Anchor anchor = new Anchor(
            readPosition(tag, "AnchorPos"),
            readDimension(tag, "AnchorDim").filter(dimension -> dimension.equals(currentDimension))
        );
        final Wary wary = new Wary(tag.getIntOr("Wary", 0), tag.getIntOr("WaryCool", 0));
        final Attendance attendance = new Attendance(
            tag.getIntOr("Attend", 0),
            SpiritRules.resetPulseIntervalOnLoad(
                tag.getIntOr("AttendPulse", 0), SpiritRules.ATTEND_PULSE_INTERVAL_TICKS
            ),
            tag.getIntOr("AttendCount", 0)
        );
        final Guard guard = new Guard(
            readUuid(tag, "AtkId"),
            readDimension(tag, "AtkDim").filter(dimension -> dimension.equals(currentDimension)),
            tag.getIntOr("Warn", 0),
            SpiritRules.resetPulseIntervalOnLoad(
                tag.getIntOr("WarnPulse", 0), SpiritRules.WARN_PULSE_INTERVAL_TICKS
            ),
            tag.getIntOr("WarnCount", 0),
            tag.getIntOr("Defend", 0),
            tag.getIntOr("Strikes", 0),
            tag.getIntOr("Recover", 0)
        );
        final Cadence cadence = new Cadence(
            tag.getIntOr("RouteFail", 0),
            tag.getIntOr("RouteRetry", 0),
            tag.getIntOr("AttendCool", 0)
        );
        final Phase stored = parsePhase(tag.getStringOr("Phase", ""));
        return new SpiritState(
            SCHEMA_VERSION,
            stored == Phase.DEFEND ? Phase.WARN : stored,
            anchor, wary, attendance, guard, cadence
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
