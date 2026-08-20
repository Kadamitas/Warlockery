package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.BansheeRules.Mode;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Versioned, fixed-cardinality Banshee semantics. Exactly one anchor, one subject, one observed
 * death, and one attacker may exist. Every duration is a remaining loaded-active tick count, never
 * an absolute world deadline, so unloading pauses meaning instead of expiring it. No path, live
 * entity reference, collection, line-of-sight cache, or client fact is ever stored.
 */
public record BansheeState(
    int schemaVersion,
    Mode mode,
    Anchor anchor,
    Subject subject,
    Death death,
    Attacker attacker,
    Cadence cadence
) {
    public static final int SCHEMA_VERSION = 1;

    public BansheeState {
        mode = Objects.requireNonNull(mode, "mode");
        anchor = Objects.requireNonNull(anchor, "anchor");
        subject = Objects.requireNonNull(subject, "subject");
        death = Objects.requireNonNull(death, "death");
        attacker = Objects.requireNonNull(attacker, "attacker");
        cadence = Objects.requireNonNull(cadence, "cadence");
        mode = reconcile(mode, subject, death, attacker);
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

        public boolean present() {
            return position.isPresent() && dimension.isPresent();
        }
    }

    public record Subject(
        Optional<UUID> id,
        Optional<String> dimension,
        Optional<BlockPos> lastSeen,
        int missingTicks,
        int lostSightTicks,
        int recoveryTicks,
        int episodeRemainingTicks,
        int pulseRemainingTicks,
        int pulsesEmitted
    ) {
        public Subject {
            id = Objects.requireNonNull(id, "id");
            dimension = Objects.requireNonNull(dimension, "dimension").filter(value -> !value.isBlank());
            lastSeen = Objects.requireNonNull(lastSeen, "lastSeen").map(BlockPos::immutable);
            if (id.isEmpty() || dimension.isEmpty()) {
                id = Optional.empty();
                dimension = Optional.empty();
                lastSeen = Optional.empty();
                missingTicks = 0;
                lostSightTicks = 0;
                recoveryTicks = 0;
                episodeRemainingTicks = 0;
                pulseRemainingTicks = 0;
                pulsesEmitted = 0;
            }
            missingTicks = BansheeRules.clampRemaining(missingTicks, BansheeRules.MISSING_GRACE_TICKS);
            lostSightTicks = BansheeRules.clampRemaining(lostSightTicks, BansheeRules.LOST_SIGHT_TICKS);
            recoveryTicks = BansheeRules.clampRemaining(recoveryTicks, BansheeRules.RECOVERY_TICKS);
            episodeRemainingTicks =
                BansheeRules.clampRemaining(episodeRemainingTicks, BansheeRules.EPISODE_TICKS);
            pulseRemainingTicks =
                BansheeRules.clampRemaining(pulseRemainingTicks, BansheeRules.WARNING_PULSE_INTERVAL_TICKS);
            pulsesEmitted = Math.clamp(pulsesEmitted, 0, BansheeRules.MAX_WARNING_PULSES);
        }

        public static Subject none() {
            return new Subject(Optional.empty(), Optional.empty(), Optional.empty(),
                0, 0, 0, 0, 0, 0);
        }

        public static Subject acquired(final UUID id, final String dimension) {
            return new Subject(Optional.of(id), Optional.of(dimension), Optional.empty(),
                0, 0, 0, BansheeRules.EPISODE_TICKS, BansheeRules.WARNING_PULSE_INTERVAL_TICKS, 0);
        }

        public boolean present() {
            return id.isPresent() && dimension.isPresent();
        }
    }

    public record Death(
        Optional<BlockPos> position,
        Optional<String> dimension,
        int remainingTicks,
        int pulseRemainingTicks,
        int pulsesEmitted
    ) {
        public Death {
            position = Objects.requireNonNull(position, "position").map(BlockPos::immutable);
            dimension = Objects.requireNonNull(dimension, "dimension").filter(value -> !value.isBlank());
            if (position.isEmpty() || dimension.isEmpty()) {
                position = Optional.empty();
                dimension = Optional.empty();
                remainingTicks = 0;
                pulseRemainingTicks = 0;
                pulsesEmitted = 0;
            }
            remainingTicks = BansheeRules.clampRemaining(remainingTicks, BansheeRules.LAMENT_TICKS);
            pulseRemainingTicks =
                BansheeRules.clampRemaining(pulseRemainingTicks, BansheeRules.LAMENT_PULSE_INTERVAL_TICKS);
            pulsesEmitted = Math.clamp(pulsesEmitted, 0, BansheeRules.MAX_LAMENT_PULSES);
            if (remainingTicks <= 0) {
                position = Optional.empty();
                dimension = Optional.empty();
                pulseRemainingTicks = 0;
                pulsesEmitted = 0;
            }
        }

        public static Death none() {
            return new Death(Optional.empty(), Optional.empty(), 0, 0, 0);
        }

        public static Death observed(final BlockPos position, final String dimension) {
            return new Death(Optional.of(position), Optional.of(dimension), BansheeRules.LAMENT_TICKS,
                BansheeRules.LAMENT_FIRST_PULSE_DELAY_TICKS, 0);
        }

        public boolean present() {
            return position.isPresent() && dimension.isPresent();
        }
    }

    public record Attacker(
        Optional<UUID> id,
        Optional<String> dimension,
        int remainingTicks,
        boolean teleportAttempted
    ) {
        public Attacker {
            id = Objects.requireNonNull(id, "id");
            dimension = Objects.requireNonNull(dimension, "dimension").filter(value -> !value.isBlank());
            if (id.isEmpty() || dimension.isEmpty()) {
                id = Optional.empty();
                dimension = Optional.empty();
                remainingTicks = 0;
                teleportAttempted = false;
            }
            remainingTicks = BansheeRules.clampRemaining(remainingTicks, BansheeRules.RECOIL_TICKS);
            if (remainingTicks <= 0) {
                id = Optional.empty();
                dimension = Optional.empty();
                teleportAttempted = false;
            }
        }

        public static Attacker none() {
            return new Attacker(Optional.empty(), Optional.empty(), 0, false);
        }

        public static Attacker recoiling(final UUID id, final String dimension) {
            return new Attacker(Optional.of(id), Optional.of(dimension), BansheeRules.RECOIL_TICKS, false);
        }

        public boolean present() {
            return id.isPresent() && dimension.isPresent();
        }
    }

    public record Cadence(
        int tabooCooldownTicks,
        int routeFailures,
        int routeRetryTicks,
        int reacquireTicks,
        int anchorUnavailableTicks
    ) {
        public Cadence {
            tabooCooldownTicks =
                BansheeRules.clampRemaining(tabooCooldownTicks, BansheeRules.TABOO_COOLDOWN_TICKS);
            routeFailures = Math.clamp(routeFailures, 0, BansheeRules.MAX_ROUTE_FAILURES);
            routeRetryTicks = BansheeRules.clampRemaining(routeRetryTicks, BansheeRules.ROUTE_BACKOFF_TICKS);
            reacquireTicks =
                BansheeRules.clampRemaining(reacquireTicks, BansheeRules.REACQUIRE_COOLDOWN_TICKS);
            anchorUnavailableTicks =
                BansheeRules.clampRemaining(anchorUnavailableTicks, BansheeRules.ANCHOR_UNAVAILABLE_TICKS);
        }

        public static Cadence none() {
            return new Cadence(0, 0, 0, 0, 0);
        }
    }

    public static BansheeState empty() {
        return new BansheeState(SCHEMA_VERSION, Mode.VIGIL, Anchor.none(), Subject.none(),
            Death.none(), Attacker.none(), Cadence.none());
    }

    private static Mode reconcile(
        final Mode mode,
        final Subject subject,
        final Death death,
        final Attacker attacker
    ) {
        return switch (mode) {
            case APPROACH, WARNING -> subject.present() ? mode : Mode.VIGIL;
            case LAMENT -> death.present() ? mode : Mode.VIGIL;
            case RECOIL -> attacker.present() ? mode : Mode.VIGIL;
            case RECOVERY, VIGIL -> mode;
        };
    }

    public BansheeState withMode(final Mode updated) {
        return new BansheeState(schemaVersion, updated, anchor, subject, death, attacker, cadence);
    }

    public BansheeState withAnchor(final Anchor updated) {
        return new BansheeState(schemaVersion, mode, updated, subject, death, attacker, cadence);
    }

    public BansheeState withSubject(final Subject updated) {
        return new BansheeState(schemaVersion, mode, anchor, updated, death, attacker, cadence);
    }

    public BansheeState withDeath(final Death updated) {
        return new BansheeState(schemaVersion, mode, anchor, subject, updated, attacker, cadence);
    }

    public BansheeState withAttacker(final Attacker updated) {
        return new BansheeState(schemaVersion, mode, anchor, subject, death, updated, cadence);
    }

    public BansheeState withCadence(final Cadence updated) {
        return new BansheeState(schemaVersion, mode, anchor, subject, death, attacker, updated);
    }

    /** Clears the subject and every warning fact without touching a real observed death report. */
    public BansheeState releaseSubject() {
        return withSubject(Subject.none()).withCadence(new Cadence(
            cadence.tabooCooldownTicks(), 0, cadence.routeRetryTicks(),
            BansheeRules.REACQUIRE_COOLDOWN_TICKS, cadence.anchorUnavailableTicks()
        )).withMode(death.present() ? Mode.LAMENT : Mode.RECOVERY);
    }

    /**
     * Compact fixed-cardinality encoding. Transient missing/lost-sight graces are deliberately not
     * written: reload restarts them so unloading can never expire a subject. Representative
     * populated states must encode below {@link BansheeRules#MAX_STATE_BYTES}.
     */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", schemaVersion);
        tag.putString("Mode", mode.name().toLowerCase(Locale.ROOT));
        anchor.position().ifPresent(position -> tag.putLong("AnchorPos", position.asLong()));
        anchor.dimension().ifPresent(dimension -> tag.putString("AnchorDim", dimension));
        subject.id().ifPresent(id -> tag.putString("SubjId", id.toString()));
        subject.dimension().ifPresent(dimension -> tag.putString("SubjDim", dimension));
        subject.lastSeen().ifPresent(position -> tag.putLong("SubjSeen", position.asLong()));
        tag.putInt("SubjRecovery", subject.recoveryTicks());
        tag.putInt("Episode", subject.episodeRemainingTicks());
        tag.putInt("WarnPulse", subject.pulseRemainingTicks());
        tag.putInt("WarnCount", subject.pulsesEmitted());
        death.position().ifPresent(position -> tag.putLong("DeathPos", position.asLong()));
        death.dimension().ifPresent(dimension -> tag.putString("DeathDim", dimension));
        tag.putInt("Lament", death.remainingTicks());
        tag.putInt("LamentPulse", death.pulseRemainingTicks());
        tag.putInt("LamentCount", death.pulsesEmitted());
        attacker.id().ifPresent(id -> tag.putString("AtkId", id.toString()));
        attacker.dimension().ifPresent(dimension -> tag.putString("AtkDim", dimension));
        tag.putInt("Recoil", attacker.remainingTicks());
        tag.putBoolean("RecoilTp", attacker.teleportAttempted());
        tag.putInt("Taboo", cadence.tabooCooldownTicks());
        tag.putInt("RouteFail", cadence.routeFailures());
        tag.putInt("RouteRetry", cadence.routeRetryTicks());
        tag.putInt("Reacquire", cadence.reacquireTicks());
        return tag;
    }

    /**
     * Reads version 1. Missing or unknown schema resets to a safe vigil. Every remaining duration is
     * clamped without consulting elapsed world time, warning and lament pulse intervals are restored
     * when they were persisted as zero so no feedback replays, and recoil survives only when its own
     * coupling is intact. Reload never resumes a pulse, effect, teleport, or path.
     */
    public static BansheeState read(final CompoundTag tag, final String currentDimension) {
        if (tag == null || tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return empty();
        }
        final Anchor anchor = new Anchor(
            readPosition(tag, "AnchorPos"),
            readDimension(tag, "AnchorDim")
        );
        final Optional<String> subjectDimension = readDimension(tag, "SubjDim");
        final Subject subject = new Subject(
            readUuid(tag, "SubjId"),
            subjectDimension,
            readPosition(tag, "SubjSeen"),
            0,
            0,
            tag.getIntOr("SubjRecovery", 0),
            tag.getIntOr("Episode", 0),
            BansheeRules.resetPulseIntervalOnLoad(
                tag.getIntOr("WarnPulse", 0), BansheeRules.WARNING_PULSE_INTERVAL_TICKS
            ),
            tag.getIntOr("WarnCount", 0)
        );
        final Optional<String> deathDimension = readDimension(tag, "DeathDim");
        final Death death = new Death(
            readPosition(tag, "DeathPos"),
            deathDimension.filter(dimension -> dimension.equals(currentDimension)),
            tag.getIntOr("Lament", 0),
            BansheeRules.resetPulseIntervalOnLoad(
                tag.getIntOr("LamentPulse", 0), BansheeRules.LAMENT_PULSE_INTERVAL_TICKS
            ),
            tag.getIntOr("LamentCount", 0)
        );
        final Attacker attacker = new Attacker(
            readUuid(tag, "AtkId"),
            readDimension(tag, "AtkDim").filter(dimension -> dimension.equals(currentDimension)),
            tag.getIntOr("Recoil", 0),
            tag.getBooleanOr("RecoilTp", false)
        );
        final Cadence cadence = new Cadence(
            tag.getIntOr("Taboo", 0),
            tag.getIntOr("RouteFail", 0),
            tag.getIntOr("RouteRetry", 0),
            tag.getIntOr("Reacquire", 0),
            0
        );
        final Mode stored = parseMode(tag.getStringOr("Mode", ""));
        final BansheeState loaded = new BansheeState(
            SCHEMA_VERSION,
            stored == Mode.APPROACH || stored == Mode.WARNING ? Mode.APPROACH : stored,
            anchor, subject, death, attacker, cadence
        );
        return loaded.anchor().dimension()
            .filter(dimension -> !dimension.equals(currentDimension))
            .map(_ -> loaded.withAnchor(Anchor.none()))
            .orElse(loaded);
    }

    private static Mode parseMode(final String value) {
        for (final Mode candidate : Mode.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        return Mode.VIGIL;
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
