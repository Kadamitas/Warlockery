package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.CircleMageRules.Action;
import com.kadamitas.warlockery.entity.CircleMageRules.Mode;
import com.kadamitas.warlockery.entity.CircleMageRules.TargetSource;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Versioned, fixed-cardinality Circle Mage semantics. The canonical constructors validate
 * structure (a fact without its coupled identity collapses) but deliberately never end a phase on
 * a timer: tick dispatch is the single place a threat, action, or session ends, so every timeout
 * transition is observable exactly once and can be counted.
 *
 * <p>Versioned, fixed-cardinality Circle Mage semantics. The existing
 * {@link CreatureBehaviorState} owner UUID stays authoritative and is deliberately not duplicated
 * here. Nothing in this record is a roster, member collection, vote, rank, permanent role,
 * knowledge list, spell list, path, live reference, inventory, or world object.
 */
public record CircleMageState(
    int schemaVersion,
    Mode mode,
    Anchor anchor,
    Threat threat,
    ActionState action,
    Study study,
    Session session,
    Cadence cadence
) {
    public static final int SCHEMA_VERSION = 1;

    public CircleMageState {
        mode = Objects.requireNonNull(mode, "mode");
        anchor = Objects.requireNonNull(anchor, "anchor");
        threat = Objects.requireNonNull(threat, "threat");
        action = Objects.requireNonNull(action, "action");
        study = Objects.requireNonNull(study, "study");
        session = Objects.requireNonNull(session, "session");
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

        public boolean present() {
            return position.isPresent() && dimension.isPresent();
        }
    }

    /** One target or one unexpired same-owner peer report. Never both and never a list. */
    public record Threat(
        Optional<UUID> id,
        Optional<String> dimension,
        TargetSource source,
        int remainingTicks
    ) {
        public Threat {
            id = Objects.requireNonNull(id, "id");
            dimension = Objects.requireNonNull(dimension, "dimension").filter(value -> !value.isBlank());
            source = Objects.requireNonNull(source, "source");
            if (id.isEmpty() || dimension.isEmpty() || source == TargetSource.NONE) {
                id = Optional.empty();
                dimension = Optional.empty();
                source = TargetSource.NONE;
                remainingTicks = 0;
            }
            // The countdown is clamped but never acted on here. A threat whose window reached
            // zero stays observable so that CircleMageRuntime.revalidateThreat is the single place
            // the phase ends: it releases, counts the release, and clears the one-hop report
            // marker. Ending the phase in this constructor made the transition invisible.
            remainingTicks =
                CircleMageRules.clampRemaining(remainingTicks, CircleMageRules.REPORT_EXPIRY_TICKS);
        }

        public static Threat none() {
            return new Threat(Optional.empty(), Optional.empty(), TargetSource.NONE, 0);
        }

        public static Threat of(final UUID id, final String dimension, final TargetSource source) {
            return new Threat(Optional.of(id), Optional.of(dimension), source,
                CircleMageRules.REPORT_EXPIRY_TICKS);
        }

        public boolean present() {
            return id.isPresent() && dimension.isPresent() && source != TargetSource.NONE;
        }
    }

    /** One immutable in-flight action with a frozen target identity and reserved focus flag. */
    public record ActionState(
        Action action,
        Optional<UUID> targetId,
        Optional<String> dimension,
        boolean focusReserved,
        int windupRemainingTicks
    ) {
        public ActionState {
            action = Objects.requireNonNull(action, "action");
            targetId = Objects.requireNonNull(targetId, "targetId");
            dimension = Objects.requireNonNull(dimension, "dimension").filter(value -> !value.isBlank());
            final boolean boltCoupled =
                action == Action.BOLT && targetId.isPresent() && dimension.isPresent();
            final boolean studyCoupled =
                action == Action.STUDY && targetId.isEmpty() && !focusReserved;
            if (!boltCoupled && !studyCoupled) {
                action = Action.NONE;
                targetId = Optional.empty();
                dimension = Optional.empty();
                focusReserved = false;
                windupRemainingTicks = 0;
            }
            windupRemainingTicks = CircleMageRules.clampRemaining(
                windupRemainingTicks,
                action == Action.STUDY
                    ? CircleMageRules.REHEARSAL_TICKS
                    : CircleMageRules.BOLT_WINDUP_TICKS
            );
        }

        public static ActionState none() {
            return new ActionState(Action.NONE, Optional.empty(), Optional.empty(), false, 0);
        }

        public static ActionState bolt(
            final UUID target,
            final String dimension,
            final boolean focusReserved
        ) {
            return new ActionState(Action.BOLT, Optional.of(target), Optional.of(dimension),
                focusReserved, CircleMageRules.BOLT_WINDUP_TICKS);
        }

        public static ActionState study(final String dimension) {
            return new ActionState(Action.STUDY, Optional.empty(), Optional.of(dimension), false,
                CircleMageRules.REHEARSAL_TICKS);
        }

        public boolean pending() {
            return action != Action.NONE;
        }
    }

    /** One retained workstation destination plus the single focus boolean. */
    public record Study(
        boolean focusPrepared,
        Optional<BlockPos> workstation,
        Optional<String> dimension,
        int studyCooldownTicks,
        int searchCooldownTicks
    ) {
        public Study {
            workstation = Objects.requireNonNull(workstation, "workstation").map(BlockPos::immutable);
            dimension = Objects.requireNonNull(dimension, "dimension").filter(value -> !value.isBlank());
            if (workstation.isEmpty() || dimension.isEmpty()) {
                workstation = Optional.empty();
                dimension = Optional.empty();
            }
            studyCooldownTicks =
                CircleMageRules.clampRemaining(studyCooldownTicks, CircleMageRules.STUDY_COOLDOWN_TICKS);
            searchCooldownTicks = CircleMageRules.clampRemaining(
                searchCooldownTicks, CircleMageRules.STUDY_SEARCH_INTERVAL_TICKS
            );
        }

        public static Study none() {
            return new Study(false, Optional.empty(), Optional.empty(), 0, 0);
        }

        public boolean hasWorkstation() {
            return workstation.isPresent() && dimension.isPresent();
        }
    }

    /**
     * A temporary conclave membership. Each participant stores only the coordinator, the game-time
     * epoch, and its own slot; nobody ever stores a participant list, quorum, or vote.
     */
    public record Session(
        Optional<UUID> coordinator,
        Optional<String> dimension,
        long epoch,
        int slot,
        int remainingTicks
    ) {
        public Session {
            coordinator = Objects.requireNonNull(coordinator, "coordinator");
            dimension = Objects.requireNonNull(dimension, "dimension").filter(value -> !value.isBlank());
            if (coordinator.isEmpty() || dimension.isEmpty()) {
                coordinator = Optional.empty();
                dimension = Optional.empty();
                epoch = 0L;
                slot = 0;
                remainingTicks = 0;
            }
            slot = Math.clamp(slot, 0, CircleMageRules.MAX_SESSION_SIZE - 1);
            // Same rule as Threat: a session whose window reached zero stays observable so the
            // timeout is released and counted exactly once, by advanceSession.
            remainingTicks =
                CircleMageRules.clampRemaining(remainingTicks, CircleMageRules.SESSION_TIMEOUT_TICKS);
        }

        public static Session none() {
            return new Session(Optional.empty(), Optional.empty(), 0L, 0, 0);
        }

        public static Session joined(
            final UUID coordinator,
            final String dimension,
            final long epoch,
            final int slot
        ) {
            return new Session(Optional.of(coordinator), Optional.of(dimension), epoch, slot,
                CircleMageRules.SESSION_TIMEOUT_TICKS);
        }

        public boolean present() {
            return coordinator.isPresent() && dimension.isPresent();
        }
    }

    public record Cadence(
        int castRecoveryTicks,
        int withdrawalTicks,
        int auraTicks,
        int ownerCheckTicks,
        int peerScanTicks,
        int reportCooldownTicks,
        int safeStepTicks,
        int routeFailures,
        int routeRetryTicks
    ) {
        public Cadence {
            castRecoveryTicks =
                CircleMageRules.clampRemaining(castRecoveryTicks, CircleMageRules.BOLT_RECOVERY_TICKS);
            withdrawalTicks =
                CircleMageRules.clampRemaining(withdrawalTicks, CircleMageRules.WITHDRAW_TICKS);
            auraTicks = CircleMageRules.clampRemaining(auraTicks, CircleMageRules.AURA_INTERVAL_TICKS);
            ownerCheckTicks =
                CircleMageRules.clampRemaining(ownerCheckTicks, CircleMageRules.OWNER_CHECK_INTERVAL_TICKS);
            peerScanTicks =
                CircleMageRules.clampRemaining(peerScanTicks, CircleMageRules.PEER_SCAN_INTERVAL_TICKS);
            reportCooldownTicks =
                CircleMageRules.clampRemaining(reportCooldownTicks, CircleMageRules.PEER_SCAN_INTERVAL_TICKS);
            safeStepTicks =
                CircleMageRules.clampRemaining(safeStepTicks, CircleMageRules.SAFE_STEP_INTERVAL_TICKS);
            routeFailures = Math.clamp(routeFailures, 0, CircleMageRules.MAX_ROUTE_FAILURES);
            routeRetryTicks =
                CircleMageRules.clampRemaining(routeRetryTicks, CircleMageRules.ROUTE_BACKOFF_TICKS);
        }

        public static Cadence none() {
            return new Cadence(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    public static CircleMageState empty() {
        return new CircleMageState(SCHEMA_VERSION, Mode.IDLE, Anchor.none(), Threat.none(),
            ActionState.none(), Study.none(), Session.none(), Cadence.none());
    }

    public CircleMageState withMode(final Mode updated) {
        return new CircleMageState(schemaVersion, updated, anchor, threat, action, study, session, cadence);
    }

    public CircleMageState withAnchor(final Anchor updated) {
        return new CircleMageState(schemaVersion, mode, updated, threat, action, study, session, cadence);
    }

    public CircleMageState withThreat(final Threat updated) {
        return new CircleMageState(schemaVersion, mode, anchor, updated, action, study, session, cadence);
    }

    public CircleMageState withAction(final ActionState updated) {
        return new CircleMageState(schemaVersion, mode, anchor, threat, updated, study, session, cadence);
    }

    public CircleMageState withStudy(final Study updated) {
        return new CircleMageState(schemaVersion, mode, anchor, threat, action, updated, session, cadence);
    }

    public CircleMageState withSession(final Session updated) {
        return new CircleMageState(schemaVersion, mode, anchor, threat, action, study, updated, cadence);
    }

    public CircleMageState withCadence(final Cadence updated) {
        return new CircleMageState(schemaVersion, mode, anchor, threat, action, study, session, updated);
    }

    /**
     * Cancels every live action, report, session, and destination. The focus boolean and the study
     * cooldown survive because they are independently valid facts, and no missed session is ever
     * replayed. Used by both the ordinary cancel path and {@code CircleMageRuntime.onSeerRecall}.
     */
    public CircleMageState cancelLiveWork() {
        return withAction(ActionState.none())
            .withThreat(Threat.none())
            .withSession(Session.none())
            .withStudy(new Study(study.focusPrepared(), Optional.empty(), Optional.empty(),
                study.studyCooldownTicks(), study.searchCooldownTicks()))
            .withMode(Mode.IDLE);
    }

    /**
     * Combat urgency releases the temporary conclave and its destination but keeps the retained
     * threat: a defending Mage leaves the session rather than dragging the session into a fight.
     */
    public CircleMageState cancelSessionOnly() {
        return withSession(Session.none())
            .withStudy(new Study(study.focusPrepared(), Optional.empty(), Optional.empty(),
                study.studyCooldownTicks(), study.searchCooldownTicks()));
    }

    /**
     * Compact fixed-cardinality encoding. Representative populated states must encode below
     * {@link CircleMageRules#MAX_STATE_BYTES}.
     */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", schemaVersion);
        tag.putString("Mode", mode.name().toLowerCase(Locale.ROOT));
        anchor.position().ifPresent(position -> tag.putLong("AnchorPos", position.asLong()));
        anchor.dimension().ifPresent(dimension -> tag.putString("AnchorDim", dimension));
        threat.id().ifPresent(id -> tag.putString("ThreatId", id.toString()));
        threat.dimension().ifPresent(dimension -> tag.putString("ThreatDim", dimension));
        tag.putString("ThreatSource", threat.source().name().toLowerCase(Locale.ROOT));
        tag.putInt("ThreatTicks", threat.remainingTicks());
        tag.putString("Action", action.action().name().toLowerCase(Locale.ROOT));
        action.targetId().ifPresent(id -> tag.putString("ActionTarget", id.toString()));
        action.dimension().ifPresent(dimension -> tag.putString("ActionDim", dimension));
        tag.putBoolean("ActionFocus", action.focusReserved());
        tag.putInt("Windup", action.windupRemainingTicks());
        tag.putBoolean("Focus", study.focusPrepared());
        study.workstation().ifPresent(position -> tag.putLong("StudyPos", position.asLong()));
        study.dimension().ifPresent(dimension -> tag.putString("StudyDim", dimension));
        tag.putInt("StudyCooldown", study.studyCooldownTicks());
        tag.putInt("StudySearch", study.searchCooldownTicks());
        session.coordinator().ifPresent(id -> tag.putString("Coordinator", id.toString()));
        session.dimension().ifPresent(dimension -> tag.putString("SessionDim", dimension));
        tag.putLong("Epoch", session.epoch());
        tag.putInt("Slot", session.slot());
        tag.putInt("SessionTicks", session.remainingTicks());
        tag.putInt("CastRecovery", cadence.castRecoveryTicks());
        tag.putInt("Withdraw", cadence.withdrawalTicks());
        tag.putInt("RouteFail", cadence.routeFailures());
        tag.putInt("RouteRetry", cadence.routeRetryTicks());
        return tag;
    }

    /**
     * Reads version 1 only. A missing, older, or unknown future schema falls back to safe idle
     * defaults. Loading cancels every action, target, report, session, and destination before
     * physical AI resumes; only the focus boolean, its bounded cooldown, the same-dimension soft
     * anchor, and the cast recovery survive. It performs no entity query, path, or block access.
     */
    public static CircleMageState read(final CompoundTag tag, final String currentDimension) {
        if (tag == null || tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return empty();
        }
        final Anchor anchor = new Anchor(
            readPosition(tag, "AnchorPos"),
            readDimension(tag, "AnchorDim").filter(dimension -> dimension.equals(currentDimension))
        );
        final Study study = new Study(
            tag.getBooleanOr("Focus", false),
            Optional.empty(),
            Optional.empty(),
            CircleMageRules.clampRemaining(
                tag.getIntOr("StudyCooldown", 0), CircleMageRules.STUDY_COOLDOWN_TICKS
            ),
            CircleMageRules.clampRemaining(
                tag.getIntOr("StudySearch", 0), CircleMageRules.STUDY_SEARCH_INTERVAL_TICKS
            )
        );
        final Cadence cadence = new Cadence(
            tag.getIntOr("CastRecovery", 0), 0, 0, 0, 0, 0, 0, 0, 0
        );
        return new CircleMageState(
            SCHEMA_VERSION, Mode.IDLE, anchor, Threat.none(), ActionState.none(),
            study, Session.none(), cadence
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
