package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.HedgeCroneRules.Action;
import com.kadamitas.warlockery.entity.HedgeCroneRules.Hex;
import com.kadamitas.warlockery.entity.HedgeCroneRules.Mode;
import com.kadamitas.warlockery.entity.HedgeCroneRules.ThreatClass;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Versioned, fixed-cardinality Hedge Crone semantics. The canonical constructors validate
 * structure (a fact without its coupled identity collapses) but deliberately never end a phase on
 * a timer: tick dispatch is the single place a threat or action ends, so every timeout transition
 * is observable exactly once and can be counted.
 *
 * <p>Versioned, fixed-cardinality Hedge Crone semantics. Exactly one anchor, one threat, one action,
 * one workstation, and one ward boolean may exist. It stores no player list, reputation map, coven,
 * member collection, item stack, inventory, path, {@code Entity}, {@code Level}, block entity, live
 * damage source, or task queue, and every duration is a bounded remaining tick count.
 */
public record HedgeCroneState(
    int schemaVersion,
    Mode mode,
    Anchor anchor,
    Threat threat,
    ActionState action,
    Work work,
    Cadence cadence
) {
    public static final int SCHEMA_VERSION = 1;

    public HedgeCroneState {
        mode = Objects.requireNonNull(mode, "mode");
        anchor = Objects.requireNonNull(anchor, "anchor");
        threat = Objects.requireNonNull(threat, "threat");
        action = Objects.requireNonNull(action, "action");
        work = Objects.requireNonNull(work, "work");
        cadence = Objects.requireNonNull(cadence, "cadence");
    }

    /** A soft anchor. It claims no block, block entity, structure, land, or chunk. */
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

    /** One warned or escalated boundary candidate, or one accepted direct attacker. */
    public record Threat(
        Optional<UUID> id,
        Optional<String> dimension,
        ThreatClass threatClass,
        int remainingTicks,
        int warningRemainingTicks,
        int ticksWithoutSight
    ) {
        public Threat {
            id = Objects.requireNonNull(id, "id");
            dimension = Objects.requireNonNull(dimension, "dimension").filter(value -> !value.isBlank());
            threatClass = Objects.requireNonNull(threatClass, "threatClass");
            if (id.isEmpty() || dimension.isEmpty() || threatClass == ThreatClass.NONE) {
                id = Optional.empty();
                dimension = Optional.empty();
                threatClass = ThreatClass.NONE;
                remainingTicks = 0;
                warningRemainingTicks = 0;
                ticksWithoutSight = 0;
            }
            remainingTicks = HedgeCroneRules.clampRemaining(remainingTicks, HedgeCroneRules.THREAT_TICKS);
            warningRemainingTicks =
                HedgeCroneRules.clampRemaining(warningRemainingTicks, HedgeCroneRules.WARNING_TICKS);
            ticksWithoutSight = HedgeCroneRules.clampRemaining(
                ticksWithoutSight, HedgeCroneRules.LOST_SIGHT_RELEASE_TICKS
            );
            // The countdown is clamped but never acted on here. A threat whose window reached
            // zero stays observable so HedgeCroneRuntime.revalidateThreat is the single place the
            // phase ends, through HedgeCroneRules.threatReleases, which also counts the release.
        }

        public static Threat none() {
            return new Threat(Optional.empty(), Optional.empty(), ThreatClass.NONE, 0, 0, 0);
        }

        public static Threat warned(final UUID id, final String dimension) {
            return new Threat(Optional.of(id), Optional.of(dimension), ThreatClass.BOUNDARY_WARNED,
                HedgeCroneRules.THREAT_TICKS, HedgeCroneRules.WARNING_TICKS, 0);
        }

        public static Threat escalated(final UUID id, final String dimension) {
            return new Threat(Optional.of(id), Optional.of(dimension), ThreatClass.BOUNDARY_ESCALATED,
                HedgeCroneRules.THREAT_TICKS, 0, 0);
        }

        public static Threat direct(final UUID id, final String dimension) {
            return new Threat(Optional.of(id), Optional.of(dimension), ThreatClass.DIRECT,
                HedgeCroneRules.THREAT_TICKS, 0, 0);
        }

        public boolean present() {
            return id.isPresent() && dimension.isPresent() && threatClass != ThreatClass.NONE;
        }
    }

    /** One immutable in-flight action with a frozen target identity and selected hex. */
    public record ActionState(
        Action action,
        Optional<UUID> targetId,
        Optional<String> dimension,
        Optional<Hex> hex,
        int windupRemainingTicks
    ) {
        public ActionState {
            action = Objects.requireNonNull(action, "action");
            targetId = Objects.requireNonNull(targetId, "targetId");
            dimension = Objects.requireNonNull(dimension, "dimension").filter(value -> !value.isBlank());
            hex = Objects.requireNonNull(hex, "hex");
            final boolean hexCoupled = action == Action.HEX
                && targetId.isPresent() && dimension.isPresent() && hex.isPresent();
            final boolean workCoupled = action == Action.WARD_PREPARATION
                && targetId.isEmpty() && hex.isEmpty();
            if (!hexCoupled && !workCoupled) {
                action = Action.NONE;
                targetId = Optional.empty();
                dimension = Optional.empty();
                hex = Optional.empty();
                windupRemainingTicks = 0;
            }
            windupRemainingTicks = HedgeCroneRules.clampRemaining(
                windupRemainingTicks,
                action == Action.WARD_PREPARATION
                    ? HedgeCroneRules.PREPARATION_TICKS
                    : HedgeCroneRules.HEX_WINDUP_TICKS
            );
        }

        public static ActionState none() {
            return new ActionState(Action.NONE, Optional.empty(), Optional.empty(), Optional.empty(), 0);
        }

        public static ActionState hex(final UUID target, final String dimension, final Hex selected) {
            return new ActionState(Action.HEX, Optional.of(target), Optional.of(dimension),
                Optional.of(selected), HedgeCroneRules.HEX_WINDUP_TICKS);
        }

        public static ActionState preparation(final String dimension) {
            return new ActionState(Action.WARD_PREPARATION, Optional.empty(), Optional.of(dimension),
                Optional.empty(), HedgeCroneRules.PREPARATION_TICKS);
        }

        public boolean pending() {
            return action != Action.NONE;
        }
    }

    /** One retained workstation destination plus the single ward boolean. */
    public record Work(
        boolean wardPrepared,
        Optional<BlockPos> workstation,
        Optional<String> dimension,
        int wardCooldownTicks,
        int workstationSearchTicks
    ) {
        public Work {
            workstation = Objects.requireNonNull(workstation, "workstation").map(BlockPos::immutable);
            dimension = Objects.requireNonNull(dimension, "dimension").filter(value -> !value.isBlank());
            if (workstation.isEmpty() || dimension.isEmpty()) {
                workstation = Optional.empty();
                dimension = Optional.empty();
            }
            wardCooldownTicks =
                HedgeCroneRules.clampRemaining(wardCooldownTicks, HedgeCroneRules.WARD_COOLDOWN_TICKS);
            workstationSearchTicks = HedgeCroneRules.clampRemaining(
                workstationSearchTicks, HedgeCroneRules.WORKSTATION_INTERVAL_TICKS
            );
        }

        public static Work none() {
            return new Work(false, Optional.empty(), Optional.empty(), 0, 0);
        }

        public boolean hasWorkstation() {
            return workstation.isPresent() && dimension.isPresent();
        }
    }

    public record Cadence(
        int castRecoveryTicks,
        int withdrawalTicks,
        int routeFailures,
        int routeRetryTicks,
        int anchorUnavailableTicks
    ) {
        public Cadence {
            castRecoveryTicks =
                HedgeCroneRules.clampRemaining(castRecoveryTicks, HedgeCroneRules.CAST_RECOVERY_TICKS);
            withdrawalTicks =
                HedgeCroneRules.clampRemaining(withdrawalTicks, HedgeCroneRules.WITHDRAW_TICKS);
            routeFailures = Math.clamp(routeFailures, 0, HedgeCroneRules.MAX_ROUTE_FAILURES);
            routeRetryTicks =
                HedgeCroneRules.clampRemaining(routeRetryTicks, HedgeCroneRules.ROUTE_BACKOFF_TICKS);
            anchorUnavailableTicks =
                HedgeCroneRules.clampRemaining(anchorUnavailableTicks, HedgeCroneRules.ANCHOR_REPLACE_TICKS);
        }

        public static Cadence none() {
            return new Cadence(0, 0, 0, 0, 0);
        }
    }

    public static HedgeCroneState empty() {
        return new HedgeCroneState(SCHEMA_VERSION, Mode.IDLE, Anchor.none(), Threat.none(),
            ActionState.none(), Work.none(), Cadence.none());
    }

    public HedgeCroneState withMode(final Mode updated) {
        return new HedgeCroneState(schemaVersion, updated, anchor, threat, action, work, cadence);
    }

    public HedgeCroneState withAnchor(final Anchor updated) {
        return new HedgeCroneState(schemaVersion, mode, updated, threat, action, work, cadence);
    }

    public HedgeCroneState withThreat(final Threat updated) {
        return new HedgeCroneState(schemaVersion, mode, anchor, updated, action, work, cadence);
    }

    public HedgeCroneState withAction(final ActionState updated) {
        return new HedgeCroneState(schemaVersion, mode, anchor, threat, updated, work, cadence);
    }

    public HedgeCroneState withWork(final Work updated) {
        return new HedgeCroneState(schemaVersion, mode, anchor, threat, action, updated, cadence);
    }

    public HedgeCroneState withCadence(final Cadence updated) {
        return new HedgeCroneState(schemaVersion, mode, anchor, threat, action, work, updated);
    }

    /**
     * Cancels the immutable action and its destination without ever applying its effect. The ward
     * boolean and its cooldown survive because they are independently valid facts.
     */
    public HedgeCroneState cancelAction() {
        return withAction(ActionState.none())
            .withWork(new Work(work.wardPrepared(), Optional.empty(), Optional.empty(),
                work.wardCooldownTicks(), work.workstationSearchTicks()))
            .withMode(Mode.IDLE);
    }

    public HedgeCroneState releaseThreat() {
        return withThreat(Threat.none()).withMode(Mode.IDLE);
    }

    /**
     * Compact fixed-cardinality encoding. Nothing here is a path, live reference, or collection.
     * Representative populated states must encode below {@link HedgeCroneRules#MAX_STATE_BYTES}.
     */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", schemaVersion);
        tag.putString("Mode", mode.name().toLowerCase(Locale.ROOT));
        anchor.position().ifPresent(position -> tag.putLong("AnchorPos", position.asLong()));
        anchor.dimension().ifPresent(dimension -> tag.putString("AnchorDim", dimension));
        threat.id().ifPresent(id -> tag.putString("ThreatId", id.toString()));
        threat.dimension().ifPresent(dimension -> tag.putString("ThreatDim", dimension));
        tag.putString("ThreatClass", threat.threatClass().name().toLowerCase(Locale.ROOT));
        tag.putInt("ThreatTicks", threat.remainingTicks());
        tag.putInt("WarnTicks", threat.warningRemainingTicks());
        tag.putString("Action", action.action().name().toLowerCase(Locale.ROOT));
        action.targetId().ifPresent(id -> tag.putString("ActionTarget", id.toString()));
        action.dimension().ifPresent(dimension -> tag.putString("ActionDim", dimension));
        action.hex().ifPresent(hex -> tag.putString("Hex", hex.name().toLowerCase(Locale.ROOT)));
        tag.putInt("Windup", action.windupRemainingTicks());
        tag.putBoolean("Ward", work.wardPrepared());
        work.workstation().ifPresent(position -> tag.putLong("WorkPos", position.asLong()));
        work.dimension().ifPresent(dimension -> tag.putString("WorkDim", dimension));
        tag.putInt("WardCooldown", work.wardCooldownTicks());
        tag.putInt("WorkSearch", work.workstationSearchTicks());
        tag.putInt("CastRecovery", cadence.castRecoveryTicks());
        tag.putInt("Withdraw", cadence.withdrawalTicks());
        tag.putInt("RouteFail", cadence.routeFailures());
        tag.putInt("RouteRetry", cadence.routeRetryTicks());
        tag.putInt("AnchorMissing", cadence.anchorUnavailableTicks());
        return tag;
    }

    /**
     * Reads version 1 only. A missing, older, or unknown future schema falls back to safe idle
     * defaults. Every loaded action, target, warning, and destination is canceled to recovery
     * before physical AI resumes, cross-dimension facts are dropped, and the independently valid
     * ward boolean plus its bounded cooldown are the only work facts that survive. Loading reads
     * serialized data only: no entity query, path, sound, particle, effect, or block access.
     */
    public static HedgeCroneState read(final CompoundTag tag, final String currentDimension) {
        if (tag == null || tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return empty();
        }
        final Anchor anchor = new Anchor(
            readPosition(tag, "AnchorPos"),
            readDimension(tag, "AnchorDim").filter(dimension -> dimension.equals(currentDimension))
        );
        final Work work = new Work(
            tag.getBooleanOr("Ward", false),
            Optional.empty(),
            Optional.empty(),
            HedgeCroneRules.clampRemaining(
                tag.getIntOr("WardCooldown", 0), HedgeCroneRules.WARD_COOLDOWN_TICKS
            ),
            HedgeCroneRules.clampRemaining(
                tag.getIntOr("WorkSearch", 0), HedgeCroneRules.WORKSTATION_INTERVAL_TICKS
            )
        );
        final Cadence cadence = new Cadence(
            tag.getIntOr("CastRecovery", 0),
            0,
            0,
            0,
            tag.getIntOr("AnchorMissing", 0)
        );
        // Every live action, target, warning, and path is deliberately dropped on load: an attack
        // is never replayed and a warning can never silently rebind to a replacement entity.
        return new HedgeCroneState(
            SCHEMA_VERSION, Mode.IDLE, anchor, Threat.none(), ActionState.none(), work, cadence
        );
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
