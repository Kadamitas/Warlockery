package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.NaamahCourtRules.Action;
import com.kadamitas.warlockery.entity.NaamahCourtRules.Phase;
import com.kadamitas.warlockery.entity.HazardEscapeRules.Hazard;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public record NaamahCourtState(
    int schemaVersion,
    Optional<String> anchorDimension,
    Optional<BlockPos> anchor,
    Phase phase,
    Phase highestPhase,
    Action action,
    long actionStartedAt,
    long actionExecuteAt,
    long recoverUntil,
    Optional<UUID> challenger,
    long challengerExpiresAt,
    Optional<UUID> recentAttacker,
    long attackerExpiresAt,
    Optional<BlockPos> destination,
    long destinationExpiresAt,
    long nextDecisionAt,
    long nextCandidateScanAt,
    long nextShadeScanAt,
    long nextAmbientFeedbackAt,
    long lastNavigationAt,
    int routeFailures,
    long retryAfter,
    Optional<Hazard> localHazard,
    boolean audienceConcluded,
    Optional<UUID> concludedOwner,
    Optional<UUID> actionTarget,
    Optional<String> actionDimension
) {
    public static final int SCHEMA_VERSION = 1;
    private static final long MAX_LOADED_FUTURE_TICKS = 600L;

    public NaamahCourtState {
        anchorDimension = Objects.requireNonNull(anchorDimension, "anchorDimension");
        anchor = Objects.requireNonNull(anchor, "anchor").map(BlockPos::immutable);
        phase = Objects.requireNonNull(phase, "phase");
        highestPhase = Objects.requireNonNull(highestPhase, "highestPhase");
        action = Objects.requireNonNull(action, "action");
        challenger = Objects.requireNonNull(challenger, "challenger");
        recentAttacker = Objects.requireNonNull(recentAttacker, "recentAttacker");
        destination = Objects.requireNonNull(destination, "destination").map(BlockPos::immutable);
        localHazard = Objects.requireNonNull(localHazard, "localHazard");
        concludedOwner = Objects.requireNonNull(concludedOwner, "concludedOwner");
        actionTarget = Objects.requireNonNull(actionTarget, "actionTarget");
        actionDimension = Objects.requireNonNull(actionDimension, "actionDimension");
        routeFailures = Math.clamp(routeFailures, 0, NaamahCourtRules.MAX_ROUTE_FAILURES);
        if (action == Action.NONE) {
            actionTarget = Optional.empty();
            actionDimension = Optional.empty();
        }
        if (audienceConcluded) {
            phase = Phase.AUDIENCE_CONCLUDED;
        } else if (highestPhase.ordinal() > phase.ordinal()) {
            phase = highestPhase;
        }
    }

    public static NaamahCourtState empty() {
        return new NaamahCourtState(
            SCHEMA_VERSION,
            Optional.empty(), Optional.empty(),
            Phase.ENTHRONED, Phase.ENTHRONED,
            Action.NONE, 0L, 0L, 0L,
            Optional.empty(), 0L, Optional.empty(), 0L,
            Optional.empty(), 0L,
            0L, 0L, 0L, 0L, 0L,
            0, 0L, Optional.empty(), false, Optional.empty(), Optional.empty(), Optional.empty()
        );
    }

    public NaamahCourtState withAnchor(final String dimension, final BlockPos position) {
        if (dimension == null || dimension.isBlank() || position == null) {
            return this;
        }
        return copy(Optional.of(dimension), Optional.of(position), phase, highestPhase, action,
            actionStartedAt, actionExecuteAt, recoverUntil, challenger, challengerExpiresAt,
            recentAttacker, attackerExpiresAt, destination, destinationExpiresAt,
            nextDecisionAt, nextCandidateScanAt, nextShadeScanAt, nextAmbientFeedbackAt,
            lastNavigationAt, routeFailures, retryAfter, audienceConcluded, concludedOwner);
    }

    public NaamahCourtState latchPhase(final float health, final float maximumHealth) {
        if (audienceConcluded) {
            return this;
        }
        final Phase latched = NaamahCourtRules.latchPhase(highestPhase, health, maximumHealth);
        return copy(anchorDimension, anchor, latched, latched, action, actionStartedAt,
            actionExecuteAt, recoverUntil, challenger, challengerExpiresAt, recentAttacker,
            attackerExpiresAt, destination, destinationExpiresAt, nextDecisionAt,
            nextCandidateScanAt, nextShadeScanAt, nextAmbientFeedbackAt, lastNavigationAt,
            routeFailures, retryAfter, false, Optional.empty());
    }

    public NaamahCourtState beginAction(final Action nextAction, final long now) {
        return beginAction(
            nextAction, now, challenger.orElse(null), anchorDimension.orElse(null)
        );
    }

    public NaamahCourtState beginAction(
        final Action nextAction,
        final long now,
        final UUID target,
        final String dimension
    ) {
        final NaamahCourtRules.ActionWindow window = NaamahCourtRules.begin(nextAction, now, 0, 0);
        return copy(anchorDimension, anchor, phase, highestPhase, window.action(), window.startedAt(),
            window.executeAt(), window.recoverUntil(), challenger, challengerExpiresAt,
            recentAttacker, attackerExpiresAt, destination, destinationExpiresAt,
            nextDecisionAt, nextCandidateScanAt, nextShadeScanAt, nextAmbientFeedbackAt,
            lastNavigationAt, routeFailures, retryAfter, audienceConcluded, concludedOwner)
            .withActionIdentity(Optional.ofNullable(target), Optional.ofNullable(dimension)
                .filter(value -> !value.isBlank()));
    }

    public NaamahCourtState finishAction() {
        return copy(anchorDimension, anchor, phase, highestPhase, Action.NONE, 0L, 0L,
            recoverUntil, challenger, challengerExpiresAt, recentAttacker, attackerExpiresAt,
            Optional.empty(), 0L, nextDecisionAt, nextCandidateScanAt, nextShadeScanAt,
            nextAmbientFeedbackAt, lastNavigationAt, routeFailures, retryAfter,
            audienceConcluded, concludedOwner);
    }

    public NaamahCourtState cancelAction(final long now) {
        return copy(anchorDimension, anchor, phase, highestPhase, Action.NONE, 0L, 0L,
            Math.max(recoverUntil, now + NaamahCourtRules.MIN_RECOVERY_TICKS), challenger,
            challengerExpiresAt, recentAttacker, attackerExpiresAt, Optional.empty(), 0L,
            nextDecisionAt, nextCandidateScanAt, nextShadeScanAt, nextAmbientFeedbackAt,
            lastNavigationAt, routeFailures, retryAfter, audienceConcluded, concludedOwner);
    }

    public NaamahCourtState withChallenger(final UUID id, final long expiresAt) {
        return copy(anchorDimension, anchor, phase, highestPhase, action, actionStartedAt,
            actionExecuteAt, recoverUntil, Optional.ofNullable(id), Math.max(0L, expiresAt),
            recentAttacker, attackerExpiresAt, destination, destinationExpiresAt,
            nextDecisionAt, nextCandidateScanAt, nextShadeScanAt, nextAmbientFeedbackAt,
            lastNavigationAt, routeFailures, retryAfter, audienceConcluded, concludedOwner);
    }

    public NaamahCourtState releaseChallenger(final long now) {
        return copy(anchorDimension, anchor, phase, highestPhase, Action.NONE, 0L, 0L,
            Math.max(recoverUntil, now), Optional.empty(), 0L, recentAttacker,
            attackerExpiresAt, Optional.empty(), 0L, nextDecisionAt, nextCandidateScanAt,
            nextShadeScanAt, nextAmbientFeedbackAt, lastNavigationAt, routeFailures,
            retryAfter, audienceConcluded, concludedOwner);
    }

    public NaamahCourtState rememberAttacker(final UUID id, final long expiresAt) {
        return copy(anchorDimension, anchor, phase, highestPhase, action, actionStartedAt,
            actionExecuteAt, recoverUntil, challenger, challengerExpiresAt,
            Optional.ofNullable(id), Math.max(0L, expiresAt), destination, destinationExpiresAt,
            nextDecisionAt, nextCandidateScanAt, nextShadeScanAt, nextAmbientFeedbackAt,
            lastNavigationAt, routeFailures, retryAfter, audienceConcluded, concludedOwner);
    }

    public NaamahCourtState withDestination(final BlockPos position, final long expiresAt) {
        return copy(anchorDimension, anchor, phase, highestPhase, action, actionStartedAt,
            actionExecuteAt, recoverUntil, challenger, challengerExpiresAt, recentAttacker,
            attackerExpiresAt, Optional.ofNullable(position), Math.max(0L, expiresAt),
            nextDecisionAt, nextCandidateScanAt, nextShadeScanAt, nextAmbientFeedbackAt,
            lastNavigationAt, routeFailures, retryAfter, audienceConcluded, concludedOwner);
    }

    public NaamahCourtState withLocalHazard(final Optional<Hazard> observed) {
        return new NaamahCourtState(
            SCHEMA_VERSION, anchorDimension, anchor, phase, highestPhase,
            action, actionStartedAt, actionExecuteAt, recoverUntil,
            challenger, challengerExpiresAt, recentAttacker, attackerExpiresAt,
            destination, destinationExpiresAt, nextDecisionAt, nextCandidateScanAt,
            nextShadeScanAt, nextAmbientFeedbackAt, lastNavigationAt, routeFailures,
            retryAfter, Objects.requireNonNull(observed, "observed"), audienceConcluded,
            concludedOwner, actionTarget, actionDimension
        );
    }

    public NaamahCourtState withSchedule(
        final long decisionAt,
        final long candidateScanAt,
        final long shadeScanAt,
        final long ambientFeedbackAt,
        final long navigationAt
    ) {
        return copy(anchorDimension, anchor, phase, highestPhase, action, actionStartedAt,
            actionExecuteAt, recoverUntil, challenger, challengerExpiresAt, recentAttacker,
            attackerExpiresAt, destination, destinationExpiresAt, decisionAt, candidateScanAt,
            shadeScanAt, ambientFeedbackAt, navigationAt, routeFailures, retryAfter,
            audienceConcluded, concludedOwner);
    }

    public NaamahCourtState withRouteRetry(final int failures, final long newRetryAfter) {
        return copy(anchorDimension, anchor, phase, highestPhase, action, actionStartedAt,
            actionExecuteAt, recoverUntil, challenger, challengerExpiresAt, recentAttacker,
            attackerExpiresAt, destination, destinationExpiresAt, nextDecisionAt,
            nextCandidateScanAt, nextShadeScanAt, nextAmbientFeedbackAt, lastNavigationAt,
            failures, newRetryAfter, audienceConcluded, concludedOwner);
    }

    public NaamahCourtState recordRouteResult(final boolean accepted, final long now) {
        final NaamahCourtRules.RouteRetry retry = accepted
            ? NaamahCourtRules.routeSuccess()
            : NaamahCourtRules.routeFailure(routeFailures, now);
        final boolean exhausted = !accepted && retry.failures() >= NaamahCourtRules.MAX_ROUTE_FAILURES;
        return copy(anchorDimension, anchor, phase, highestPhase, action, actionStartedAt,
            actionExecuteAt, recoverUntil, challenger, challengerExpiresAt, recentAttacker,
            attackerExpiresAt, exhausted ? Optional.empty() : destination,
            exhausted ? 0L : destinationExpiresAt, nextDecisionAt, nextCandidateScanAt,
            nextShadeScanAt, nextAmbientFeedbackAt, lastNavigationAt, retry.failures(),
            retry.retryAfter(), audienceConcluded, concludedOwner);
    }

    public NaamahCourtState conclude(final UUID owner) {
        return copy(anchorDimension, anchor, Phase.AUDIENCE_CONCLUDED, highestPhase,
            Action.NONE, 0L, 0L, 0L, Optional.empty(), 0L, Optional.empty(), 0L,
            Optional.empty(), 0L, nextDecisionAt, nextCandidateScanAt, nextShadeScanAt,
            nextAmbientFeedbackAt, lastNavigationAt, 0, 0L, true,
            Optional.ofNullable(owner));
    }

    public NaamahCourtState reconcile(final long now) {
        final boolean challengerExpired = challengerExpiresAt > 0L && challengerExpiresAt <= now;
        final boolean attackerExpired = attackerExpiresAt > 0L && attackerExpiresAt <= now;
        final boolean destinationExpired = destinationExpiresAt > 0L && destinationExpiresAt <= now;
        return copy(anchorDimension, anchor, phase, highestPhase, action, actionStartedAt,
            actionExecuteAt, recoverUntil,
            challengerExpired ? Optional.empty() : challenger,
            challengerExpired ? 0L : challengerExpiresAt,
            attackerExpired ? Optional.empty() : recentAttacker,
            attackerExpired ? 0L : attackerExpiresAt,
            destinationExpired ? Optional.empty() : destination,
            destinationExpired ? 0L : destinationExpiresAt,
            nextDecisionAt, nextCandidateScanAt, nextShadeScanAt, nextAmbientFeedbackAt,
            lastNavigationAt, routeFailures, retryAfter, audienceConcluded, concludedOwner);
    }

    public NaamahCourtState reconcileAfterLoad(final long now) {
        NaamahCourtState reconciled = reconcile(now);
        if (reconciled.action != Action.NONE && reconciled.actionExecuteAt <= now) {
            reconciled = reconciled.copy(reconciled.anchorDimension, reconciled.anchor,
                reconciled.phase, reconciled.highestPhase, Action.NONE, 0L, 0L, 0L,
                reconciled.challenger, reconciled.challengerExpiresAt, reconciled.recentAttacker,
                reconciled.attackerExpiresAt, Optional.empty(), 0L, reconciled.nextDecisionAt,
                reconciled.nextCandidateScanAt, reconciled.nextShadeScanAt,
                reconciled.nextAmbientFeedbackAt, reconciled.lastNavigationAt,
                reconciled.routeFailures, reconciled.retryAfter, reconciled.audienceConcluded,
                reconciled.concludedOwner);
        }
        return reconciled.withSchedule(
            Math.max(now, reconciled.nextDecisionAt),
            Math.max(now, reconciled.nextCandidateScanAt),
            Math.max(now, reconciled.nextShadeScanAt),
            Math.max(now, reconciled.nextAmbientFeedbackAt),
            Math.max(now, reconciled.lastNavigationAt)
        );
    }

    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
        anchorDimension.ifPresent(value -> tag.putString("AnchorDimension", value));
        anchor.ifPresent(value -> tag.putLong("Anchor", value.asLong()));
        tag.putString("Phase", phase.name());
        tag.putString("HighestPhase", highestPhase.name());
        tag.putString("Action", action.name());
        actionTarget.ifPresent(value -> tag.putString("ActionTarget", value.toString()));
        actionDimension.ifPresent(value -> tag.putString("ActionDimension", value));
        tag.putLong("ActionStartedAt", actionStartedAt);
        tag.putLong("ActionExecuteAt", actionExecuteAt);
        tag.putLong("RecoverUntil", recoverUntil);
        challenger.ifPresent(value -> tag.putString("Challenger", value.toString()));
        tag.putLong("ChallengerExpiresAt", challengerExpiresAt);
        recentAttacker.ifPresent(value -> tag.putString("RecentAttacker", value.toString()));
        tag.putLong("AttackerExpiresAt", attackerExpiresAt);
        destination.ifPresent(value -> tag.putLong("Destination", value.asLong()));
        tag.putLong("DestinationExpiresAt", destinationExpiresAt);
        localHazard.ifPresent(value -> tag.putString("LocalHazard", value.name()));
        tag.putLong("NextDecisionAt", nextDecisionAt);
        tag.putLong("NextCandidateScanAt", nextCandidateScanAt);
        tag.putLong("NextShadeScanAt", nextShadeScanAt);
        tag.putLong("NextAmbientFeedbackAt", nextAmbientFeedbackAt);
        tag.putLong("LastNavigationAt", lastNavigationAt);
        tag.putInt("RouteFailures", routeFailures);
        tag.putLong("RetryAfter", retryAfter);
        tag.putBoolean("AudienceConcluded", audienceConcluded);
        concludedOwner.ifPresent(value -> tag.putString("ConcludedOwner", value.toString()));
        return tag;
    }

    public static NaamahCourtState read(
        final CompoundTag tag,
        final long now,
        final float health,
        final float maximumHealth
    ) {
        Objects.requireNonNull(tag, "tag");
        final Optional<String> dimension = optionalString(tag, "AnchorDimension")
            .filter(value -> value.contains(":"));
        final Optional<BlockPos> anchor = optionalBlock(tag, "Anchor");
        NaamahCourtState safe = empty().latchPhase(health, maximumHealth);
        if (dimension.isPresent() && anchor.isPresent()) {
            safe = safe.withAnchor(dimension.orElseThrow(), anchor.orElseThrow());
        }
        if (tag.getIntOr("SchemaVersion", SCHEMA_VERSION) != SCHEMA_VERSION) {
            return safe.reconcileAfterLoad(now);
        }

        final Phase implied = NaamahCourtRules.latchPhase(Phase.ENTHRONED, health, maximumHealth);
        final Phase rawHighest = phase(tag.getStringOr("HighestPhase", implied.name()), implied);
        final Phase rawPhase = phase(tag.getStringOr("Phase", rawHighest.name()), rawHighest);
        final boolean concluded = tag.getBooleanOr("AudienceConcluded", false);
        final Optional<UUID> concludedOwner = optionalUuid(tag, "ConcludedOwner");
        final boolean validConclusion = concluded && concludedOwner.isPresent()
            && rawPhase == Phase.AUDIENCE_CONCLUDED && rawHighest != Phase.AUDIENCE_CONCLUDED;
        final boolean hasAnyConclusionField = concluded || concludedOwner.isPresent()
            || rawPhase == Phase.AUDIENCE_CONCLUDED || rawHighest == Phase.AUDIENCE_CONCLUDED;
        if (hasAnyConclusionField && !validConclusion) {
            return safe.reconcileAfterLoad(now);
        }
        final Phase storedHighest = rawHighest == Phase.AUDIENCE_CONCLUDED ? implied : rawHighest;
        final Phase highest = storedHighest.ordinal() >= implied.ordinal() ? storedHighest : implied;
        Phase storedPhase = validConclusion ? Phase.AUDIENCE_CONCLUDED : rawPhase;
        if (storedPhase != Phase.AUDIENCE_CONCLUDED && highest.ordinal() > storedPhase.ordinal()) {
            storedPhase = highest;
        }

        final long maximumFuture = maximumLoadedFuture(now);
        Action storedAction = action(tag.getStringOr("Action", Action.NONE.name()));
        Optional<UUID> actionTarget = optionalUuid(tag, "ActionTarget");
        Optional<String> actionDimension = optionalString(tag, "ActionDimension")
            .filter(value -> value.contains(":"));
        long actionStartedAt = tag.getLongOr("ActionStartedAt", 0L);
        long actionExecuteAt = tag.getLongOr("ActionExecuteAt", 0L);
        long recoverUntil = boundedStoredTime(tag.getLongOr("RecoverUntil", 0L), now, maximumFuture);
        final boolean validWindow = validActionWindow(
            storedAction, actionStartedAt, actionExecuteAt, recoverUntil, now, maximumFuture
        ) && (storedAction == Action.NONE
            ? actionTarget.isEmpty() && actionDimension.isEmpty()
            : actionTarget.isPresent() && actionDimension.isPresent());
        if (!validWindow || validConclusion) {
            storedAction = Action.NONE;
            actionTarget = Optional.empty();
            actionDimension = Optional.empty();
            actionStartedAt = 0L;
            actionExecuteAt = 0L;
            recoverUntil = 0L;
        }

        Optional<UUID> challenger = validConclusion ? Optional.empty() : optionalUuid(tag, "Challenger");
        long challengerExpiresAt = boundedStoredTime(
            tag.getLongOr("ChallengerExpiresAt", 0L), now, maximumFuture
        );
        if (challenger.isEmpty() || challengerExpiresAt <= now) {
            challenger = Optional.empty();
            challengerExpiresAt = 0L;
        }
        Optional<UUID> attacker = validConclusion ? Optional.empty() : optionalUuid(tag, "RecentAttacker");
        long attackerExpiresAt = boundedStoredTime(
            tag.getLongOr("AttackerExpiresAt", 0L), now, maximumFuture
        );
        if (attacker.isEmpty() || attackerExpiresAt <= now) {
            attacker = Optional.empty();
            attackerExpiresAt = 0L;
        }
        Optional<BlockPos> destination = validConclusion ? Optional.empty() : optionalBlock(tag, "Destination");
        long destinationExpiresAt = boundedStoredTime(
            tag.getLongOr("DestinationExpiresAt", 0L), now, maximumFuture
        );
        if (destination.isEmpty() || destinationExpiresAt <= now) {
            destination = Optional.empty();
            destinationExpiresAt = 0L;
        }

        final long rawRetryAfter = tag.getLongOr("RetryAfter", 0L);
        final boolean invalidRetry = rawRetryAfter > maximumFuture;
        final int routeFailures = validConclusion || invalidRetry ? 0 : tag.getIntOr("RouteFailures", 0);
        final long retryAfter = routeFailures == 0 ? 0L
            : boundedStoredTime(rawRetryAfter, now, maximumFuture);
        final Optional<Hazard> localHazard = optionalHazard(tag, "LocalHazard");
        final NaamahCourtState loaded = new NaamahCourtState(
            SCHEMA_VERSION, safe.anchorDimension, safe.anchor,
            storedPhase,
            highest,
            storedAction, actionStartedAt, actionExecuteAt, recoverUntil,
            challenger, challengerExpiresAt, attacker, attackerExpiresAt,
            destination, destinationExpiresAt,
            boundedStoredTime(tag.getLongOr("NextDecisionAt", 0L), now, maximumFuture),
            boundedStoredTime(tag.getLongOr("NextCandidateScanAt", 0L), now, maximumFuture),
            boundedStoredTime(tag.getLongOr("NextShadeScanAt", 0L), now, maximumFuture),
            boundedStoredTime(tag.getLongOr("NextAmbientFeedbackAt", 0L), now, maximumFuture),
            boundedStoredTime(tag.getLongOr("LastNavigationAt", 0L), now, maximumFuture),
            routeFailures, retryAfter, localHazard, validConclusion,
            validConclusion ? concludedOwner : Optional.empty(), actionTarget, actionDimension
        );
        return loaded.reconcileAfterLoad(now);
    }

    private static long maximumLoadedFuture(final long now) {
        return now > Long.MAX_VALUE - MAX_LOADED_FUTURE_TICKS
            ? Long.MAX_VALUE : now + MAX_LOADED_FUTURE_TICKS;
    }

    private static long boundedStoredTime(final long value, final long now, final long maximumFuture) {
        if (value <= 0L) return 0L;
        return value > maximumFuture ? now : value;
    }

    private static boolean validActionWindow(
        final Action action,
        final long startedAt,
        final long executeAt,
        final long recoverUntil,
        final long now,
        final long maximumFuture
    ) {
        if (action == Action.NONE) {
            return startedAt == 0L && executeAt == 0L;
        }
        return startedAt >= 0L && startedAt <= now
            && executeAt > startedAt && executeAt <= maximumFuture
            && executeAt - startedAt >= NaamahCourtRules.MIN_WINDUP_TICKS
            && recoverUntil >= executeAt && recoverUntil <= maximumFuture
            && recoverUntil - executeAt >= NaamahCourtRules.MIN_RECOVERY_TICKS;
    }

    private static Phase phase(final String value, final Phase fallback) {
        try {
            return Phase.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static Action action(final String value) {
        try {
            return Action.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return Action.NONE;
        }
    }

    private static Optional<String> optionalString(final CompoundTag tag, final String key) {
        final String value = tag.getStringOr(key, "");
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static Optional<UUID> optionalUuid(final CompoundTag tag, final String key) {
        try {
            return optionalString(tag, key).map(UUID::fromString);
        } catch (final IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<BlockPos> optionalBlock(final CompoundTag tag, final String key) {
        return tag.contains(key) ? Optional.of(BlockPos.of(tag.getLongOr(key, 0L))) : Optional.empty();
    }

    private static Optional<Hazard> optionalHazard(final CompoundTag tag, final String key) {
        try {
            return optionalString(tag, key).map(value -> Hazard.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (final IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private NaamahCourtState copy(
        final Optional<String> newAnchorDimension,
        final Optional<BlockPos> newAnchor,
        final Phase newPhase,
        final Phase newHighestPhase,
        final Action newAction,
        final long newActionStartedAt,
        final long newActionExecuteAt,
        final long newRecoverUntil,
        final Optional<UUID> newChallenger,
        final long newChallengerExpiresAt,
        final Optional<UUID> newRecentAttacker,
        final long newAttackerExpiresAt,
        final Optional<BlockPos> newDestination,
        final long newDestinationExpiresAt,
        final long newNextDecisionAt,
        final long newNextCandidateScanAt,
        final long newNextShadeScanAt,
        final long newNextAmbientFeedbackAt,
        final long newLastNavigationAt,
        final int newRouteFailures,
        final long newRetryAfter,
        final boolean newAudienceConcluded,
        final Optional<UUID> newConcludedOwner
    ) {
        return new NaamahCourtState(
            SCHEMA_VERSION, newAnchorDimension, newAnchor, newPhase, newHighestPhase,
            newAction, newActionStartedAt, newActionExecuteAt, newRecoverUntil,
            newChallenger, newChallengerExpiresAt, newRecentAttacker, newAttackerExpiresAt,
            newDestination, newDestinationExpiresAt, newNextDecisionAt, newNextCandidateScanAt,
            newNextShadeScanAt, newNextAmbientFeedbackAt, newLastNavigationAt,
            newRouteFailures, newRetryAfter, localHazard, newAudienceConcluded, newConcludedOwner,
            actionTarget, actionDimension
        );
    }

    private NaamahCourtState withActionIdentity(
        final Optional<UUID> target,
        final Optional<String> dimension
    ) {
        return new NaamahCourtState(
            schemaVersion, anchorDimension, anchor, phase, highestPhase,
            action, actionStartedAt, actionExecuteAt, recoverUntil,
            challenger, challengerExpiresAt, recentAttacker, attackerExpiresAt,
            destination, destinationExpiresAt, nextDecisionAt, nextCandidateScanAt,
            nextShadeScanAt, nextAmbientFeedbackAt, lastNavigationAt,
            routeFailures, retryAfter, localHazard, audienceConcluded, concludedOwner,
            Objects.requireNonNull(target, "target"), Objects.requireNonNull(dimension, "dimension")
        );
    }
}
