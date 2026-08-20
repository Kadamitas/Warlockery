package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.HellhoundLifeRules.Evidence;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.EvidenceKind;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.Intent;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.Mode;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.PackOrigin;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.PackRole;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.RouteFailure;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Versioned bounded semantic F09 state. It persists only semantic values, migrates 1.4 saves to
 * {@code LEGACY_SOLITARY}, truncates evidence deterministically, clears coupled fields as units,
 * clamps every loaded deadline, and recovers safely from unknown schemas and malformed data.
 */
public record HellhoundLifeState(
    int schemaVersion,
    Mode mode,
    Optional<UUID> ownerId,
    long ownerReconciledAt,
    UUID packId,
    PackOrigin packOrigin,
    Optional<UUID> historicalPackId,
    Optional<String> territoryDimension,
    Optional<BlockPos> territoryAnchor,
    Intent intent,
    Optional<UUID> challengerId,
    Optional<String> challengerDimension,
    long challengerExpiresAt,
    List<Evidence> evidence,
    Optional<UUID> warningPlayerId,
    long warningStartedAt,
    long warningCommitDeadline,
    long targetEvidenceEpoch,
    Optional<PackRole> packRole,
    long packRoleExpiresAt,
    Optional<BlockPos> destination,
    long destinationExpiresAt,
    Optional<BlockPos> heatPoint,
    long heatPointExpiresAt,
    long biteWindupStartedAt,
    long biteCommitDeadline,
    long biteRecoveryUntil,
    boolean retreatLatched,
    long regroupDeadline,
    Cadence cadence,
    int routeFailures,
    Optional<RouteFailure> lastRouteFailure,
    long routeRetryAfter,
    boolean legacyHearthReconciled
) {
    public static final int SCHEMA_VERSION = 1;
    private static final String KEY_VERSION = "Version";

    public HellhoundLifeState {
        mode = Objects.requireNonNull(mode, "mode");
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        packId = Objects.requireNonNull(packId, "packId");
        packOrigin = Objects.requireNonNull(packOrigin, "packOrigin");
        historicalPackId = Objects.requireNonNull(historicalPackId, "historicalPackId");
        territoryDimension = Objects.requireNonNull(territoryDimension, "territoryDimension");
        territoryAnchor = Objects.requireNonNull(territoryAnchor, "territoryAnchor").map(BlockPos::immutable);
        intent = Objects.requireNonNull(intent, "intent");
        challengerId = Objects.requireNonNull(challengerId, "challengerId");
        challengerDimension = Objects.requireNonNull(challengerDimension, "challengerDimension");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence")).stream()
            .limit(HellhoundLifeRules.MAX_EVIDENCE_RECORDS).toList();
        warningPlayerId = Objects.requireNonNull(warningPlayerId, "warningPlayerId");
        packRole = Objects.requireNonNull(packRole, "packRole");
        destination = Objects.requireNonNull(destination, "destination").map(BlockPos::immutable);
        heatPoint = Objects.requireNonNull(heatPoint, "heatPoint").map(BlockPos::immutable);
        cadence = Objects.requireNonNull(cadence, "cadence");
        routeFailures = Math.clamp(routeFailures, 0, HellhoundLifeRules.MAX_ROUTE_FAILURES);
        lastRouteFailure = Objects.requireNonNull(lastRouteFailure, "lastRouteFailure");
    }

    /** Bounded next-time sentinels; zero always reads as due. */
    public record Cadence(
        long nextDecisionAt,
        long nextOwnerRefreshAt,
        long nextEvidenceScanAt,
        long nextPackRefreshAt,
        long nextPackCallAt,
        long nextPatrolSearchAt,
        long nextHeatSearchAt,
        long nextEventFeedbackAt,
        long nextAmbientFeedbackAt,
        long nextNavigationAt
    ) {
        public static Cadence startingAt(final UUID hellhoundId, final long now) {
            final long offset = HellhoundLifeRules.stableOffset(
                hellhoundId, HellhoundLifeRules.DECISION_INTERVAL_TICKS
            );
            return new Cadence(Math.max(0L, now) + offset, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }
    }

    /** Fresh solitary state for a first loaded server tick or a safe corruption recovery. */
    public static HellhoundLifeState solitary(
        final UUID hellhoundId,
        final PackOrigin origin,
        final long now
    ) {
        return new HellhoundLifeState(
            SCHEMA_VERSION, Mode.WILD, Optional.empty(), 0L,
            UUID.nameUUIDFromBytes(("warlockery:hellhound_pack:" + hellhoundId).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            origin, Optional.empty(), Optional.empty(), Optional.empty(),
            Intent.IDLE, Optional.empty(), Optional.empty(), 0L,
            List.of(), Optional.empty(), 0L, 0L, 0L,
            Optional.empty(), 0L, Optional.empty(), 0L, Optional.empty(), 0L,
            0L, 0L, 0L, false, 0L,
            Cadence.startingAt(hellhoundId, now), 0, Optional.empty(), 0L, false
        );
    }

    /** Natural finalized group state sharing one exact pack identity and first group anchor. */
    public static HellhoundLifeState naturalGroup(
        final UUID hellhoundId,
        final UUID packId,
        final String dimension,
        final BlockPos anchor,
        final long now
    ) {
        return solitary(hellhoundId, PackOrigin.NATURAL_GROUP, now)
            .withPackIdentity(packId, PackOrigin.NATURAL_GROUP)
            .withTerritory(Optional.of(dimension), Optional.of(anchor));
    }

    public boolean bound() {
        return mode == Mode.ANIMUS_BOUND && ownerId.isPresent();
    }

    public HellhoundLifeState withMode(final Mode updatedMode, final Optional<UUID> updatedOwner) {
        final boolean binding = updatedMode == Mode.ANIMUS_BOUND && mode != Mode.ANIMUS_BOUND;
        return new HellhoundLifeState(schemaVersion, updatedMode, updatedOwner, ownerReconciledAt,
            packId, packOrigin,
            binding ? Optional.of(packId) : historicalPackId,
            territoryDimension, territoryAnchor, intent,
            binding ? Optional.empty() : challengerId,
            binding ? Optional.empty() : challengerDimension,
            binding ? 0L : challengerExpiresAt,
            evidence,
            binding ? Optional.empty() : warningPlayerId,
            binding ? 0L : warningStartedAt,
            binding ? 0L : warningCommitDeadline,
            targetEvidenceEpoch,
            binding ? Optional.empty() : packRole,
            binding ? 0L : packRoleExpiresAt,
            destination, destinationExpiresAt, heatPoint, heatPointExpiresAt,
            biteWindupStartedAt, biteCommitDeadline, biteRecoveryUntil, retreatLatched, regroupDeadline,
            cadence, routeFailures, lastRouteFailure, routeRetryAfter, legacyHearthReconciled);
    }

    public HellhoundLifeState withOwnerReconciledAt(final long at) {
        return new HellhoundLifeState(schemaVersion, mode, ownerId, at, packId, packOrigin,
            historicalPackId, territoryDimension, territoryAnchor, intent, challengerId,
            challengerDimension, challengerExpiresAt, evidence, warningPlayerId, warningStartedAt,
            warningCommitDeadline, targetEvidenceEpoch, packRole, packRoleExpiresAt, destination,
            destinationExpiresAt, heatPoint, heatPointExpiresAt, biteWindupStartedAt,
            biteCommitDeadline, biteRecoveryUntil, retreatLatched, regroupDeadline, cadence,
            routeFailures, lastRouteFailure, routeRetryAfter, legacyHearthReconciled);
    }

    public HellhoundLifeState withPackIdentity(final UUID updatedPackId, final PackOrigin origin) {
        return new HellhoundLifeState(schemaVersion, mode, ownerId, ownerReconciledAt, updatedPackId,
            origin, historicalPackId, territoryDimension, territoryAnchor, intent, challengerId,
            challengerDimension, challengerExpiresAt, evidence, warningPlayerId, warningStartedAt,
            warningCommitDeadline, targetEvidenceEpoch, packRole, packRoleExpiresAt, destination,
            destinationExpiresAt, heatPoint, heatPointExpiresAt, biteWindupStartedAt,
            biteCommitDeadline, biteRecoveryUntil, retreatLatched, regroupDeadline, cadence,
            routeFailures, lastRouteFailure, routeRetryAfter, legacyHearthReconciled);
    }

    public HellhoundLifeState withTerritory(
        final Optional<String> dimension,
        final Optional<BlockPos> anchor
    ) {
        return new HellhoundLifeState(schemaVersion, mode, ownerId, ownerReconciledAt, packId,
            packOrigin, historicalPackId, dimension, anchor, intent, challengerId,
            challengerDimension, challengerExpiresAt, evidence, warningPlayerId, warningStartedAt,
            warningCommitDeadline, targetEvidenceEpoch, packRole, packRoleExpiresAt, destination,
            destinationExpiresAt, heatPoint, heatPointExpiresAt, biteWindupStartedAt,
            biteCommitDeadline, biteRecoveryUntil, retreatLatched, regroupDeadline, cadence,
            routeFailures, lastRouteFailure, routeRetryAfter, legacyHearthReconciled);
    }

    public HellhoundLifeState withIntent(final Intent updated) {
        return new HellhoundLifeState(schemaVersion, mode, ownerId, ownerReconciledAt, packId,
            packOrigin, historicalPackId, territoryDimension, territoryAnchor, updated, challengerId,
            challengerDimension, challengerExpiresAt, evidence, warningPlayerId, warningStartedAt,
            warningCommitDeadline, targetEvidenceEpoch, packRole, packRoleExpiresAt, destination,
            destinationExpiresAt, heatPoint, heatPointExpiresAt, biteWindupStartedAt,
            biteCommitDeadline, biteRecoveryUntil, retreatLatched, regroupDeadline, cadence,
            routeFailures, lastRouteFailure, routeRetryAfter, legacyHearthReconciled);
    }

    /** Challenger fields form one coupled unit. */
    public HellhoundLifeState withChallenger(
        final Optional<UUID> challenger,
        final Optional<String> dimension,
        final long expiresAt
    ) {
        final boolean cleared = challenger.isEmpty();
        return new HellhoundLifeState(schemaVersion, mode, ownerId, ownerReconciledAt, packId,
            packOrigin, historicalPackId, territoryDimension, territoryAnchor, intent, challenger,
            cleared ? Optional.empty() : dimension, cleared ? 0L : expiresAt,
            evidence, warningPlayerId, warningStartedAt, warningCommitDeadline, targetEvidenceEpoch,
            packRole, packRoleExpiresAt, destination, destinationExpiresAt, heatPoint,
            heatPointExpiresAt, biteWindupStartedAt, biteCommitDeadline, biteRecoveryUntil,
            retreatLatched, regroupDeadline, cadence, routeFailures, lastRouteFailure,
            routeRetryAfter, legacyHearthReconciled);
    }

    public HellhoundLifeState withEvidence(final List<Evidence> ledger) {
        return new HellhoundLifeState(schemaVersion, mode, ownerId, ownerReconciledAt, packId,
            packOrigin, historicalPackId, territoryDimension, territoryAnchor, intent, challengerId,
            challengerDimension, challengerExpiresAt, ledger, warningPlayerId, warningStartedAt,
            warningCommitDeadline, targetEvidenceEpoch, packRole, packRoleExpiresAt, destination,
            destinationExpiresAt, heatPoint, heatPointExpiresAt, biteWindupStartedAt,
            biteCommitDeadline, biteRecoveryUntil, retreatLatched, regroupDeadline, cadence,
            routeFailures, lastRouteFailure, routeRetryAfter, legacyHearthReconciled);
    }

    /** Warning fields form one coupled unit. */
    public HellhoundLifeState withWarning(
        final Optional<UUID> player,
        final long startedAt,
        final long commitDeadline
    ) {
        final boolean cleared = player.isEmpty();
        return new HellhoundLifeState(schemaVersion, mode, ownerId, ownerReconciledAt, packId,
            packOrigin, historicalPackId, territoryDimension, territoryAnchor, intent, challengerId,
            challengerDimension, challengerExpiresAt, evidence, player,
            cleared ? 0L : startedAt, cleared ? 0L : commitDeadline,
            targetEvidenceEpoch, packRole, packRoleExpiresAt, destination, destinationExpiresAt,
            heatPoint, heatPointExpiresAt, biteWindupStartedAt, biteCommitDeadline,
            biteRecoveryUntil, retreatLatched, regroupDeadline, cadence, routeFailures,
            lastRouteFailure, routeRetryAfter, legacyHearthReconciled);
    }

    /** Role fields form one coupled unit and belong to one evidence epoch. */
    public HellhoundLifeState withPackRole(
        final Optional<PackRole> role,
        final long expiresAt,
        final long epoch
    ) {
        final boolean cleared = role.isEmpty();
        return new HellhoundLifeState(schemaVersion, mode, ownerId, ownerReconciledAt, packId,
            packOrigin, historicalPackId, territoryDimension, territoryAnchor, intent, challengerId,
            challengerDimension, challengerExpiresAt, evidence, warningPlayerId, warningStartedAt,
            warningCommitDeadline, epoch, role, cleared ? 0L : expiresAt, destination,
            destinationExpiresAt, heatPoint, heatPointExpiresAt, biteWindupStartedAt,
            biteCommitDeadline, biteRecoveryUntil, retreatLatched, regroupDeadline, cadence,
            routeFailures, lastRouteFailure, routeRetryAfter, legacyHearthReconciled);
    }

    /** Destination fields form one coupled unit. */
    public HellhoundLifeState withDestination(final Optional<BlockPos> updated, final long expiresAt) {
        final boolean cleared = updated.isEmpty();
        return new HellhoundLifeState(schemaVersion, mode, ownerId, ownerReconciledAt, packId,
            packOrigin, historicalPackId, territoryDimension, territoryAnchor, intent, challengerId,
            challengerDimension, challengerExpiresAt, evidence, warningPlayerId, warningStartedAt,
            warningCommitDeadline, targetEvidenceEpoch, packRole, packRoleExpiresAt, updated,
            cleared ? 0L : expiresAt, heatPoint, heatPointExpiresAt, biteWindupStartedAt,
            biteCommitDeadline, biteRecoveryUntil, retreatLatched, regroupDeadline, cadence,
            routeFailures, lastRouteFailure, routeRetryAfter, legacyHearthReconciled);
    }

    /** Heat fields form one coupled unit. */
    public HellhoundLifeState withHeatPoint(final Optional<BlockPos> updated, final long expiresAt) {
        final boolean cleared = updated.isEmpty();
        return new HellhoundLifeState(schemaVersion, mode, ownerId, ownerReconciledAt, packId,
            packOrigin, historicalPackId, territoryDimension, territoryAnchor, intent, challengerId,
            challengerDimension, challengerExpiresAt, evidence, warningPlayerId, warningStartedAt,
            warningCommitDeadline, targetEvidenceEpoch, packRole, packRoleExpiresAt, destination,
            destinationExpiresAt, updated, cleared ? 0L : expiresAt, biteWindupStartedAt,
            biteCommitDeadline, biteRecoveryUntil, retreatLatched, regroupDeadline, cadence,
            routeFailures, lastRouteFailure, routeRetryAfter, legacyHearthReconciled);
    }

    /** Bite windows form one coupled unit. */
    public HellhoundLifeState withBiteWindows(
        final long windupStartedAt,
        final long commitDeadline,
        final long recoveryUntil
    ) {
        return new HellhoundLifeState(schemaVersion, mode, ownerId, ownerReconciledAt, packId,
            packOrigin, historicalPackId, territoryDimension, territoryAnchor, intent, challengerId,
            challengerDimension, challengerExpiresAt, evidence, warningPlayerId, warningStartedAt,
            warningCommitDeadline, targetEvidenceEpoch, packRole, packRoleExpiresAt, destination,
            destinationExpiresAt, heatPoint, heatPointExpiresAt, windupStartedAt, commitDeadline,
            recoveryUntil, retreatLatched, regroupDeadline, cadence, routeFailures,
            lastRouteFailure, routeRetryAfter, legacyHearthReconciled);
    }

    public HellhoundLifeState withRetreat(final boolean latched, final long updatedRegroupDeadline) {
        return new HellhoundLifeState(schemaVersion, mode, ownerId, ownerReconciledAt, packId,
            packOrigin, historicalPackId, territoryDimension, territoryAnchor, intent, challengerId,
            challengerDimension, challengerExpiresAt, evidence, warningPlayerId, warningStartedAt,
            warningCommitDeadline, targetEvidenceEpoch, packRole, packRoleExpiresAt, destination,
            destinationExpiresAt, heatPoint, heatPointExpiresAt, biteWindupStartedAt,
            biteCommitDeadline, biteRecoveryUntil, latched, updatedRegroupDeadline, cadence,
            routeFailures, lastRouteFailure, routeRetryAfter, legacyHearthReconciled);
    }

    public HellhoundLifeState withCadence(final Cadence updated) {
        return new HellhoundLifeState(schemaVersion, mode, ownerId, ownerReconciledAt, packId,
            packOrigin, historicalPackId, territoryDimension, territoryAnchor, intent, challengerId,
            challengerDimension, challengerExpiresAt, evidence, warningPlayerId, warningStartedAt,
            warningCommitDeadline, targetEvidenceEpoch, packRole, packRoleExpiresAt, destination,
            destinationExpiresAt, heatPoint, heatPointExpiresAt, biteWindupStartedAt,
            biteCommitDeadline, biteRecoveryUntil, retreatLatched, regroupDeadline, updated,
            routeFailures, lastRouteFailure, routeRetryAfter, legacyHearthReconciled);
    }

    /** Route failure fields form one coupled unit. */
    public HellhoundLifeState withRouteFailures(
        final int failures,
        final Optional<RouteFailure> classified,
        final long retryAfter
    ) {
        return new HellhoundLifeState(schemaVersion, mode, ownerId, ownerReconciledAt, packId,
            packOrigin, historicalPackId, territoryDimension, territoryAnchor, intent, challengerId,
            challengerDimension, challengerExpiresAt, evidence, warningPlayerId, warningStartedAt,
            warningCommitDeadline, targetEvidenceEpoch, packRole, packRoleExpiresAt, destination,
            destinationExpiresAt, heatPoint, heatPointExpiresAt, biteWindupStartedAt,
            biteCommitDeadline, biteRecoveryUntil, retreatLatched, regroupDeadline, cadence,
            failures, failures == 0 ? Optional.empty() : classified,
            failures == 0 ? 0L : retryAfter, legacyHearthReconciled);
    }

    public HellhoundLifeState withLegacyHearthReconciled() {
        return new HellhoundLifeState(schemaVersion, mode, ownerId, ownerReconciledAt, packId,
            packOrigin, historicalPackId, territoryDimension, territoryAnchor, intent, challengerId,
            challengerDimension, challengerExpiresAt, evidence, warningPlayerId, warningStartedAt,
            warningCommitDeadline, targetEvidenceEpoch, packRole, packRoleExpiresAt, destination,
            destinationExpiresAt, heatPoint, heatPointExpiresAt, biteWindupStartedAt,
            biteCommitDeadline, biteRecoveryUntil, retreatLatched, regroupDeadline, cadence,
            routeFailures, lastRouteFailure, routeRetryAfter, true);
    }

    /** Clears every active claim while retaining identity, territory, and legacy facts. */
    public HellhoundLifeState released() {
        return withChallenger(Optional.empty(), Optional.empty(), 0L)
            .withEvidence(List.of())
            .withWarning(Optional.empty(), 0L, 0L)
            .withPackRole(Optional.empty(), 0L, targetEvidenceEpoch)
            .withDestination(Optional.empty(), 0L)
            .withHeatPoint(Optional.empty(), 0L)
            .withBiteWindows(0L, 0L, 0L)
            .withRetreat(false, 0L)
            .withRouteFailures(0, Optional.empty(), 0L)
            .withIntent(Intent.IDLE);
    }

    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_VERSION, schemaVersion);
        tag.putString("Mode", mode.name().toLowerCase(Locale.ROOT));
        ownerId.ifPresent(id -> tag.putString("Owner", id.toString()));
        tag.putLong("OwnerReconciledAt", ownerReconciledAt);
        tag.putString("PackId", packId.toString());
        tag.putString("PackOrigin", packOrigin.name().toLowerCase(Locale.ROOT));
        historicalPackId.ifPresent(id -> tag.putString("HistoricalPackId", id.toString()));
        territoryDimension.ifPresent(dimension -> tag.putString("TerritoryDimension", dimension));
        territoryAnchor.ifPresent(anchor -> tag.putLong("TerritoryAnchor", anchor.asLong()));
        tag.putString("Intent", intent.name().toLowerCase(Locale.ROOT));
        challengerId.ifPresent(id -> tag.putString("Challenger", id.toString()));
        challengerDimension.ifPresent(dimension -> tag.putString("ChallengerDimension", dimension));
        tag.putLong("ChallengerExpiresAt", challengerExpiresAt);
        tag.putInt("EvidenceCount", evidence.size());
        for (int index = 0; index < evidence.size(); index++) {
            final Evidence entry = evidence.get(index);
            final CompoundTag row = new CompoundTag();
            row.putString("Kind", entry.kind().name().toLowerCase(Locale.ROOT));
            entry.sourceId().ifPresent(id -> row.putString("Source", id.toString()));
            entry.dimension().ifPresent(dimension -> row.putString("Dimension", dimension));
            entry.packedPosition().ifPresent(packed -> row.putLong("Position", packed));
            row.putLong("ObservedAt", entry.observedAt());
            row.putLong("ExpiresAt", entry.expiresAt());
            row.putInt("Confidence", entry.confidence());
            tag.put("Evidence" + index, row);
        }
        warningPlayerId.ifPresent(id -> tag.putString("WarningPlayer", id.toString()));
        tag.putLong("WarningStartedAt", warningStartedAt);
        tag.putLong("WarningCommitDeadline", warningCommitDeadline);
        tag.putLong("TargetEvidenceEpoch", targetEvidenceEpoch);
        packRole.ifPresent(role -> tag.putString("PackRole", role.name().toLowerCase(Locale.ROOT)));
        tag.putLong("PackRoleExpiresAt", packRoleExpiresAt);
        // Destination claims are deliberately not persisted: load rebuilds them, so writing
        // them would only store dead data the reader discards.
        heatPoint.ifPresent(position -> tag.putLong("HeatPoint", position.asLong()));
        tag.putLong("HeatPointExpiresAt", heatPointExpiresAt);
        tag.putLong("BiteWindupStartedAt", biteWindupStartedAt);
        tag.putLong("BiteCommitDeadline", biteCommitDeadline);
        tag.putLong("BiteRecoveryUntil", biteRecoveryUntil);
        tag.putBoolean("RetreatLatched", retreatLatched);
        tag.putLong("RegroupDeadline", regroupDeadline);
        tag.putInt("RouteFailures", routeFailures);
        lastRouteFailure.ifPresent(failure ->
            tag.putString("LastRouteFailure", failure.name().toLowerCase(Locale.ROOT)));
        tag.putLong("RouteRetryAfter", routeRetryAfter);
        tag.putBoolean("LegacyHearthReconciled", legacyHearthReconciled);
        return tag;
    }

    /**
     * Loads and reconciles: unknown schemas keep only independently valid owner facts and
     * initialize safe solitary state; malformed units clear individually; every deadline clamps;
     * non-resumable intents restart as {@code IDLE}; offscreen time may only expire deadlines.
     */
    public static HellhoundLifeState read(final CompoundTag tag, final UUID hellhoundId, final long now) {
        if (tag.getIntOr(KEY_VERSION, 0) != SCHEMA_VERSION) {
            return solitary(hellhoundId, PackOrigin.SOLITARY, now);
        }
        final Optional<Mode> storedMode = parseEnum(Mode.values(), tag.getStringOr("Mode", ""));
        final Optional<UUID> owner = parseUuid(tag.getStringOr("Owner", ""));
        final Mode mode = storedMode.orElse(Mode.WILD) == Mode.ANIMUS_BOUND && owner.isPresent()
            ? Mode.ANIMUS_BOUND
            : Mode.WILD;
        final UUID packId = parseUuid(tag.getStringOr("PackId", ""))
            .orElseGet(() -> solitary(hellhoundId, PackOrigin.SOLITARY, now).packId());
        final PackOrigin packOrigin = parseEnum(PackOrigin.values(), tag.getStringOr("PackOrigin", ""))
            .orElse(PackOrigin.SOLITARY);
        final Optional<String> territoryDimension =
            nonEmpty(tag.getStringOr("TerritoryDimension", ""));
        final Optional<BlockPos> territoryAnchor = territoryDimension.isEmpty()
            ? Optional.empty()
            : readPos(tag, "TerritoryAnchor");
        final java.util.ArrayList<Evidence> evidence = new java.util.ArrayList<>();
        final int evidenceCount = Math.clamp(
            tag.getIntOr("EvidenceCount", 0), 0, HellhoundLifeRules.MAX_EVIDENCE_RECORDS
        );
        for (int index = 0; index < evidenceCount; index++) {
            final Optional<CompoundTag> stored = tag.getCompound("Evidence" + index);
            if (stored.isEmpty()) {
                continue;
            }
            final CompoundTag row = stored.orElseThrow();
            final Optional<EvidenceKind> kind = parseEnum(EvidenceKind.values(), row.getStringOr("Kind", ""));
            if (kind.isEmpty()) {
                continue;
            }
            final long expiresAt = HellhoundLifeRules.clampDeadline(
                row.getLongOr("ExpiresAt", 0L), now,
                HellhoundLifeRules.evidenceLifetimeTicks(kind.orElseThrow())
            );
            final Evidence entry = new Evidence(
                kind.orElseThrow(),
                parseUuid(row.getStringOr("Source", "")),
                nonEmpty(row.getStringOr("Dimension", "")),
                row.getLongOr("Position", Long.MIN_VALUE) == Long.MIN_VALUE
                    ? Optional.empty()
                    : Optional.of(row.getLongOr("Position", Long.MIN_VALUE)),
                row.getLongOr("ObservedAt", 0L),
                expiresAt,
                row.getIntOr("Confidence", 0)
            );
            if (entry.valid(now)) {
                evidence.add(entry);
            }
        }
        final List<Evidence> truncated = HellhoundLifeRules.truncate(evidence, now);
        final Optional<UUID> challenger = parseUuid(tag.getStringOr("Challenger", ""));
        final Optional<String> challengerDimension = nonEmpty(tag.getStringOr("ChallengerDimension", ""));
        final long challengerExpiresAt = HellhoundLifeRules.clampLoadedDeadline(
            tag.getLongOr("ChallengerExpiresAt", 0L), now, HellhoundLifeRules.SELF_DEFENSE_LEASH_TICKS
        );
        final boolean challengerValid = challenger.isPresent()
            && challengerDimension.isPresent()
            && challengerExpiresAt > now;
        final Optional<PackRole> role = parseEnum(PackRole.values(), tag.getStringOr("PackRole", ""));
        final long roleExpiresAt = HellhoundLifeRules.clampLoadedDeadline(
            tag.getLongOr("PackRoleExpiresAt", 0L), now, HellhoundLifeRules.SECTOR_SETUP_TICKS
        );
        final boolean roleValid = role.isPresent() && roleExpiresAt > now;
        final Optional<BlockPos> heatPoint = readPos(tag, "HeatPoint");
        final long heatExpiresAt = HellhoundLifeRules.clampLoadedDeadline(
            tag.getLongOr("HeatPointExpiresAt", 0L), now, HellhoundLifeRules.HEAT_POINT_TICKS
        );
        final boolean heatValid = heatPoint.isPresent() && heatExpiresAt > now;
        final Intent storedIntent = parseEnum(Intent.values(), tag.getStringOr("Intent", ""))
            .orElse(Intent.IDLE);
        final boolean retreatLatched = tag.getBooleanOr("RetreatLatched", false);
        final long regroupDeadline = clearExpired(
            tag.getLongOr("RegroupDeadline", 0L), now, HellhoundLifeRules.REGROUP_MAX_TICKS
        );
        final long biteRecoveryUntil = HellhoundLifeRules.clampLoadedDeadline(
            tag.getLongOr("BiteRecoveryUntil", 0L), now, HellhoundLifeRules.BITE_RECOVERY_TICKS
        );
        final int routeFailures = Math.clamp(tag.getIntOr("RouteFailures", 0), 0,
            HellhoundLifeRules.MAX_ROUTE_FAILURES);
        return new HellhoundLifeState(
            SCHEMA_VERSION,
            mode,
            mode == Mode.ANIMUS_BOUND ? owner : Optional.empty(),
            0L,
            packId,
            packOrigin,
            parseUuid(tag.getStringOr("HistoricalPackId", "")),
            territoryDimension,
            territoryAnchor,
            storedIntent.resumesFromDisk() ? storedIntent : Intent.IDLE,
            challengerValid ? challenger : Optional.empty(),
            challengerValid ? challengerDimension : Optional.empty(),
            challengerValid ? challengerExpiresAt : 0L,
            truncated,
            Optional.empty(),
            0L,
            0L,
            Math.max(0L, tag.getLongOr("TargetEvidenceEpoch", 0L)),
            roleValid ? role : Optional.empty(),
            roleValid ? roleExpiresAt : 0L,
            Optional.empty(),
            0L,
            heatValid ? heatPoint : Optional.empty(),
            heatValid ? heatExpiresAt : 0L,
            0L,
            0L,
            biteRecoveryUntil > now ? biteRecoveryUntil : 0L,
            retreatLatched,
            regroupDeadline,
            Cadence.startingAt(hellhoundId, now),
            routeFailures,
            routeFailures == 0
                ? Optional.empty()
                : parseEnum(RouteFailure.values(), tag.getStringOr("LastRouteFailure", "")),
            routeFailures == 0
                ? 0L
                : clearExpired(tag.getLongOr("RouteRetryAfter", 0L), now, HellhoundLifeRules.ROUTE_BACKOFF_TICKS),
            tag.getBooleanOr("LegacyHearthReconciled", false)
        );
    }

    private static long clearExpired(final long deadline, final long now, final long maxTicks) {
        final long clamped = HellhoundLifeRules.clampLoadedDeadline(deadline, now, maxTicks);
        return clamped <= now ? 0L : clamped;
    }

    private static Optional<BlockPos> readPos(final CompoundTag tag, final String key) {
        final long stored = tag.getLongOr(key, Long.MIN_VALUE);
        return stored == Long.MIN_VALUE ? Optional.empty() : Optional.of(BlockPos.of(stored));
    }

    private static Optional<String> nonEmpty(final String value) {
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private static Optional<UUID> parseUuid(final String value) {
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (final IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static <T extends Enum<T>> Optional<T> parseEnum(final T[] values, final String value) {
        for (final T candidate : values) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
