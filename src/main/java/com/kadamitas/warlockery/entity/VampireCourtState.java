package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.VampireCourtRules.AssaultRole;
import com.kadamitas.warlockery.entity.VampireCourtRules.Intent;
import com.kadamitas.warlockery.entity.VampireCourtRules.ReportOutcome;
import com.kadamitas.warlockery.entity.VampireCourtRules.RouteRetry;
import com.kadamitas.warlockery.entity.VampireCourtRules.VictimReport;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public record VampireCourtState(
    int schemaVersion,
    CreatureKind kind,
    Intent intent,
    long intentExpiresAt,
    int pressure,
    long lastPressureAt,
    Optional<String> shelterDimension,
    Optional<BlockPos> shelter,
    long shelterExpiresAt,
    Optional<UUID> targetId,
    long targetExpiresAt,
    Optional<UUID> masterId,
    AssaultRole assaultRole,
    long waveringUntil,
    Optional<UUID> recentAttacker,
    long attackerExpiresAt,
    List<VictimReport> reports,
    long nextDecisionAt,
    long nextEntityScanAt,
    long nextShelterScanAt,
    long nextFeedbackAt,
    long lastNavigationAt,
    int routeFailures,
    long retryAfter
) {
    public static final int SCHEMA_VERSION = 1;
    private static final long MAX_LOADED_FUTURE_TICKS = 24_000L;

    public VampireCourtState {
        if (kind != CreatureKind.VAMPIRE && kind != CreatureKind.BLOOD_THRALL) {
            throw new IllegalArgumentException("Court state belongs only to Vampires and Blood Thralls");
        }
        intent = Objects.requireNonNull(intent, "intent");
        shelterDimension = Objects.requireNonNull(shelterDimension, "shelterDimension");
        shelter = Objects.requireNonNull(shelter, "shelter").map(BlockPos::immutable);
        targetId = Objects.requireNonNull(targetId, "targetId");
        masterId = Objects.requireNonNull(masterId, "masterId");
        assaultRole = Objects.requireNonNull(assaultRole, "assaultRole");
        recentAttacker = Objects.requireNonNull(recentAttacker, "recentAttacker");
        reports = List.copyOf(Objects.requireNonNull(reports, "reports")).stream()
            .limit(VampireCourtRules.MAX_REPORTS).toList();
        routeFailures = Math.clamp(routeFailures, 0, VampireCourtRules.MAX_ROUTE_FAILURES);
        pressure = kind == CreatureKind.VAMPIRE ? Math.clamp(pressure, 0, VampireCourtRules.MAX_PRESSURE) : 0;
        if (kind == CreatureKind.BLOOD_THRALL) {
            reports = List.of();
            if (intent == Intent.ROOST || intent == Intent.WATCH || intent == Intent.STALK
                || intent == Intent.FEED || intent == Intent.ASSAULT_LEAD) {
                intent = Intent.UNBOUND;
            }
        }
        if (kind == CreatureKind.VAMPIRE) {
            masterId = Optional.empty();
            waveringUntil = 0L;
            if (assaultRole == AssaultRole.BOUND_GUARD) assaultRole = AssaultRole.UNBOUND;
        }
    }

    public static VampireCourtState empty(final CreatureKind kind, final long now) {
        final boolean vampire = kind == CreatureKind.VAMPIRE;
        return new VampireCourtState(
            SCHEMA_VERSION, kind, vampire ? Intent.ROOST : Intent.UNBOUND, 0L,
            vampire ? VampireCourtRules.DEFAULT_PRESSURE : 0, now,
            Optional.empty(), Optional.empty(), 0L,
            Optional.empty(), 0L, Optional.empty(), AssaultRole.UNBOUND, 0L,
            Optional.empty(), 0L, List.of(), now, now, now, now, now, 0, 0L
        );
    }

    public VampireCourtState withPressure(final int value, final long updatedAt) {
        return copy(intent, intentExpiresAt, value, updatedAt, shelterDimension, shelter,
            shelterExpiresAt, targetId, targetExpiresAt, masterId, assaultRole, waveringUntil,
            recentAttacker, attackerExpiresAt, reports, nextDecisionAt, nextEntityScanAt,
            nextShelterScanAt, nextFeedbackAt, lastNavigationAt, routeFailures, retryAfter);
    }

    public VampireCourtState reconcilePressure(final long now) {
        if (kind != CreatureKind.VAMPIRE || now <= lastPressureAt) return this;
        final long quanta = (now - lastPressureAt) / VampireCourtRules.PRESSURE_INTERVAL_TICKS;
        final long reconciledAt = quanta == 0L ? lastPressureAt
            : VampireCourtRules.saturatingAdd(
                lastPressureAt,
                Math.min(Long.MAX_VALUE / VampireCourtRules.PRESSURE_INTERVAL_TICKS, quanta)
                    * VampireCourtRules.PRESSURE_INTERVAL_TICKS
            );
        return withPressure(
            VampireCourtRules.reconcilePressure(kind, pressure, lastPressureAt, now),
            Math.min(now, reconciledAt)
        );
    }

    public VampireCourtState afterOrdinaryFeed(final long now) {
        return kind == CreatureKind.VAMPIRE
            ? withPressure(VampireCourtRules.afterOrdinaryFeed(pressure), now) : this;
    }

    public VampireCourtState afterAssaultFeed(final long now) {
        return kind == CreatureKind.VAMPIRE
            ? withPressure(VampireCourtRules.afterAssaultFeed(pressure), now) : this;
    }

    public VampireCourtState withIntent(final Intent value, final long expiresAt) {
        return copy(value, Math.max(0L, expiresAt), pressure, lastPressureAt, shelterDimension,
            shelter, shelterExpiresAt, targetId, targetExpiresAt, masterId, assaultRole,
            waveringUntil, recentAttacker, attackerExpiresAt, reports, nextDecisionAt,
            nextEntityScanAt, nextShelterScanAt, nextFeedbackAt, lastNavigationAt,
            routeFailures, retryAfter);
    }

    public VampireCourtState withShelter(final String dimension, final BlockPos position, final long expiresAt) {
        if (dimension == null || dimension.isBlank() || position == null) return this;
        return copy(intent, intentExpiresAt, pressure, lastPressureAt, Optional.of(dimension),
            Optional.of(position), Math.max(0L, expiresAt), targetId, targetExpiresAt, masterId,
            assaultRole, waveringUntil, recentAttacker, attackerExpiresAt, reports,
            nextDecisionAt, nextEntityScanAt, nextShelterScanAt, nextFeedbackAt,
            lastNavigationAt, routeFailures, retryAfter);
    }

    public VampireCourtState withoutShelter() {
        return copy(intent, intentExpiresAt, pressure, lastPressureAt, Optional.empty(),
            Optional.empty(), 0L, targetId, targetExpiresAt, masterId, assaultRole,
            waveringUntil, recentAttacker, attackerExpiresAt, reports, nextDecisionAt,
            nextEntityScanAt, nextShelterScanAt, nextFeedbackAt, lastNavigationAt,
            routeFailures, retryAfter);
    }

    public VampireCourtState withTarget(final UUID id, final long expiresAt) {
        return copy(intent, intentExpiresAt, pressure, lastPressureAt, shelterDimension, shelter,
            shelterExpiresAt, Optional.ofNullable(id), Math.max(0L, expiresAt), masterId,
            assaultRole, waveringUntil, recentAttacker, attackerExpiresAt, reports,
            nextDecisionAt, nextEntityScanAt, nextShelterScanAt, nextFeedbackAt,
            lastNavigationAt, routeFailures, retryAfter);
    }

    public VampireCourtState withMaster(final UUID id, final AssaultRole role) {
        if (kind != CreatureKind.BLOOD_THRALL) return this;
        return copy(intent, intentExpiresAt, pressure, lastPressureAt, shelterDimension, shelter,
            shelterExpiresAt, targetId, targetExpiresAt, Optional.ofNullable(id), role, 0L,
            recentAttacker, attackerExpiresAt, reports, nextDecisionAt, nextEntityScanAt,
            nextShelterScanAt, nextFeedbackAt, lastNavigationAt, routeFailures, retryAfter);
    }

    public VampireCourtState withAssaultRole(final AssaultRole role) {
        return copy(intent, intentExpiresAt, pressure, lastPressureAt, shelterDimension, shelter,
            shelterExpiresAt, targetId, targetExpiresAt, masterId, role, waveringUntil,
            recentAttacker, attackerExpiresAt, reports, nextDecisionAt, nextEntityScanAt,
            nextShelterScanAt, nextFeedbackAt, lastNavigationAt, routeFailures, retryAfter);
    }

    public VampireCourtState loseMaster(final long now) {
        if (kind != CreatureKind.BLOOD_THRALL) return this;
        final long deadline = waveringUntil > 0L ? waveringUntil
            : VampireCourtRules.saturatingAdd(now, VampireCourtRules.WAVERING_TICKS);
        return copy(Intent.WAVERING, deadline,
            pressure, lastPressureAt, shelterDimension, shelter, shelterExpiresAt,
            Optional.empty(), 0L, Optional.empty(), AssaultRole.UNBOUND,
            deadline,
            recentAttacker, attackerExpiresAt, reports, nextDecisionAt, nextEntityScanAt,
            nextShelterScanAt, nextFeedbackAt, lastNavigationAt, routeFailures, retryAfter);
    }

    public VampireCourtState resolveMasterLoss(final Intent resolvedIntent, final long expiresAt) {
        if (kind != CreatureKind.BLOOD_THRALL) return this;
        return copy(resolvedIntent, Math.max(0L, expiresAt), pressure, lastPressureAt,
            shelterDimension, shelter, shelterExpiresAt, Optional.empty(), 0L, Optional.empty(),
            AssaultRole.UNBOUND, 0L, recentAttacker, attackerExpiresAt, reports,
            nextDecisionAt, nextEntityScanAt, nextShelterScanAt, nextFeedbackAt,
            lastNavigationAt, routeFailures, retryAfter);
    }

    public VampireCourtState rememberAttacker(final UUID id, final long expiresAt) {
        return copy(intent, intentExpiresAt, pressure, lastPressureAt, shelterDimension, shelter,
            shelterExpiresAt, targetId, targetExpiresAt, masterId, assaultRole, waveringUntil,
            Optional.ofNullable(id), Math.max(0L, expiresAt), reports, nextDecisionAt,
            nextEntityScanAt, nextShelterScanAt, nextFeedbackAt, lastNavigationAt,
            routeFailures, retryAfter);
    }

    public VampireCourtState rememberVictim(final VictimReport report, final long now) {
        if (kind != CreatureKind.VAMPIRE) return this;
        return copy(intent, intentExpiresAt, pressure, lastPressureAt, shelterDimension, shelter,
            shelterExpiresAt, targetId, targetExpiresAt, masterId, assaultRole, waveringUntil,
            recentAttacker, attackerExpiresAt, VampireCourtRules.rememberVictim(reports, report, now),
            nextDecisionAt, nextEntityScanAt, nextShelterScanAt, nextFeedbackAt,
            lastNavigationAt, routeFailures, retryAfter);
    }

    public VampireCourtState withCadence(
        final long decisionAt,
        final long entityScanAt,
        final long shelterScanAt,
        final long feedbackAt,
        final long navigationAt
    ) {
        return copy(intent, intentExpiresAt, pressure, lastPressureAt, shelterDimension, shelter,
            shelterExpiresAt, targetId, targetExpiresAt, masterId, assaultRole, waveringUntil,
            recentAttacker, attackerExpiresAt, reports, decisionAt, entityScanAt, shelterScanAt,
            feedbackAt, navigationAt, routeFailures, retryAfter);
    }

    public VampireCourtState withRouteRetry(final int failures, final long nextRetryAt) {
        return copy(intent, intentExpiresAt, pressure, lastPressureAt, shelterDimension, shelter,
            shelterExpiresAt, targetId, targetExpiresAt, masterId, assaultRole, waveringUntil,
            recentAttacker, attackerExpiresAt, reports, nextDecisionAt, nextEntityScanAt,
            nextShelterScanAt, nextFeedbackAt, lastNavigationAt, failures, nextRetryAt);
    }

    public VampireCourtState recordRouteResult(final boolean success, final long now) {
        final RouteRetry retry = success ? VampireCourtRules.routeSuccess()
            : VampireCourtRules.routeFailure(routeFailures, now);
        final boolean exhausted = !success && retry.failures() == VampireCourtRules.MAX_ROUTE_FAILURES;
        return copy(intent, intentExpiresAt, pressure, lastPressureAt,
            exhausted ? Optional.empty() : shelterDimension,
            exhausted ? Optional.empty() : shelter,
            exhausted ? 0L : shelterExpiresAt,
            exhausted ? Optional.empty() : targetId,
            exhausted ? 0L : targetExpiresAt,
            masterId, assaultRole, waveringUntil, recentAttacker, attackerExpiresAt, reports,
            nextDecisionAt, nextEntityScanAt, nextShelterScanAt, nextFeedbackAt,
            lastNavigationAt, retry.failures(), retry.retryAfter());
    }

    public VampireCourtState reconcileAfterLoad(final long now) {
        VampireCourtState state = reconcilePressure(now);
        final boolean unresolvedMasterLoss = state.kind == CreatureKind.BLOOD_THRALL
            && state.waveringUntil > 0L;
        final boolean expiredIntent = state.intentExpiresAt > 0L && state.intentExpiresAt <= now
            && !unresolvedMasterLoss;
        final boolean expiredShelter = state.shelterExpiresAt > 0L && state.shelterExpiresAt <= now;
        final boolean expiredTarget = state.targetExpiresAt > 0L && state.targetExpiresAt <= now;
        final boolean expiredAttacker = state.attackerExpiresAt > 0L && state.attackerExpiresAt <= now;
        state = state.copy(
            expiredIntent ? (state.kind == CreatureKind.VAMPIRE ? Intent.RECOVER : Intent.UNBOUND) : state.intent,
            expiredIntent ? 0L : state.intentExpiresAt,
            state.pressure, state.lastPressureAt,
            expiredShelter ? Optional.empty() : state.shelterDimension,
            expiredShelter ? Optional.empty() : state.shelter,
            expiredShelter ? 0L : state.shelterExpiresAt,
            expiredTarget ? Optional.empty() : state.targetId,
            expiredTarget ? 0L : state.targetExpiresAt,
            state.masterId, state.assaultRole, state.waveringUntil,
            expiredAttacker ? Optional.empty() : state.recentAttacker,
            expiredAttacker ? 0L : state.attackerExpiresAt,
            state.kind == CreatureKind.VAMPIRE ? VampireCourtRules.pruneReports(state.reports, now) : List.of(),
            boundedLoadedTime(state.nextDecisionAt, now), boundedLoadedTime(state.nextEntityScanAt, now),
            boundedLoadedTime(state.nextShelterScanAt, now), boundedLoadedTime(state.nextFeedbackAt, now),
            boundedPastTime(state.lastNavigationAt, now),
            state.retryAfter > maximumLoadedFuture(now) ? 0 : state.routeFailures,
            state.retryAfter > maximumLoadedFuture(now) ? 0L : state.retryAfter
        );
        return state;
    }

    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
        tag.putString("Kind", kind.name());
        tag.putString("Intent", intent.name());
        tag.putLong("IntentExpiresAt", intentExpiresAt);
        tag.putInt("Pressure", pressure);
        tag.putLong("LastPressureAt", lastPressureAt);
        shelterDimension.ifPresent(value -> tag.putString("ShelterDimension", value));
        shelter.ifPresent(value -> tag.putLong("Shelter", value.asLong()));
        tag.putLong("ShelterExpiresAt", shelterExpiresAt);
        targetId.ifPresent(value -> tag.putString("Target", value.toString()));
        tag.putLong("TargetExpiresAt", targetExpiresAt);
        masterId.ifPresent(value -> tag.putString("Master", value.toString()));
        tag.putString("AssaultRole", assaultRole.name());
        tag.putLong("WaveringUntil", waveringUntil);
        recentAttacker.ifPresent(value -> tag.putString("RecentAttacker", value.toString()));
        tag.putLong("AttackerExpiresAt", attackerExpiresAt);
        tag.putInt("ReportCount", reports.size());
        for (int index = 0; index < reports.size(); index++) {
            final VictimReport report = reports.get(index);
            final CompoundTag row = new CompoundTag();
            row.putString("Victim", report.victimId().toString());
            row.putInt("X", report.x());
            row.putInt("Y", report.y());
            row.putInt("Z", report.z());
            row.putLong("EncounteredAt", report.encounteredAt());
            row.putString("Outcome", report.outcome().name());
            row.putInt("Importance", report.importance());
            tag.put("Report" + index, row);
        }
        tag.putLong("NextDecisionAt", nextDecisionAt);
        tag.putLong("NextEntityScanAt", nextEntityScanAt);
        tag.putLong("NextShelterScanAt", nextShelterScanAt);
        tag.putLong("NextFeedbackAt", nextFeedbackAt);
        tag.putLong("LastNavigationAt", lastNavigationAt);
        tag.putInt("RouteFailures", routeFailures);
        tag.putLong("RetryAfter", retryAfter);
        return tag;
    }

    public static VampireCourtState read(final CompoundTag tag, final CreatureKind expectedKind, final long now) {
        Objects.requireNonNull(tag, "tag");
        final VampireCourtState fallback = empty(expectedKind, now);
        if (tag.getIntOr("SchemaVersion", SCHEMA_VERSION) != SCHEMA_VERSION) return fallback;
        if (!tag.getStringOr("Kind", expectedKind.name()).equals(expectedKind.name())) return fallback;
        final Intent intent = enumValue(Intent.class, tag.getStringOr("Intent", fallback.intent.name()), fallback.intent);
        final AssaultRole role = enumValue(
            AssaultRole.class, tag.getStringOr("AssaultRole", AssaultRole.UNBOUND.name()), AssaultRole.UNBOUND
        );
        final int rawPressure = tag.getIntOr("Pressure", fallback.pressure);
        if (rawPressure < 0 || rawPressure > VampireCourtRules.MAX_PRESSURE) return fallback;
        final Optional<String> shelterDimension = optionalString(tag, "ShelterDimension")
            .filter(value -> value.contains(":"));
        final Optional<BlockPos> shelter = tag.contains("Shelter")
            ? Optional.of(BlockPos.of(tag.getLongOr("Shelter", 0L))) : Optional.empty();
        final ArrayList<VictimReport> reports = new ArrayList<>();
        final int reportCount = Math.clamp(tag.getIntOr("ReportCount", 0), 0, VampireCourtRules.MAX_REPORTS);
        for (int index = 0; index < reportCount; index++) {
            final Optional<CompoundTag> row = tag.getCompound("Report" + index);
            row.flatMap(VampireCourtState::readReport).ifPresent(reports::add);
        }
        final VampireCourtState loaded = new VampireCourtState(
            SCHEMA_VERSION, expectedKind, intent,
            boundedStoredTime(tag.getLongOr("IntentExpiresAt", 0L), now), rawPressure,
            Math.max(0L, tag.getLongOr("LastPressureAt", now)), shelterDimension, shelter,
            boundedStoredTime(tag.getLongOr("ShelterExpiresAt", 0L), now),
            optionalUuid(tag, "Target"), boundedStoredTime(tag.getLongOr("TargetExpiresAt", 0L), now),
            optionalUuid(tag, "Master"), role,
            boundedStoredTime(tag.getLongOr("WaveringUntil", 0L), now),
            optionalUuid(tag, "RecentAttacker"),
            boundedStoredTime(tag.getLongOr("AttackerExpiresAt", 0L), now), reports,
            boundedStoredTime(tag.getLongOr("NextDecisionAt", now), now),
            boundedStoredTime(tag.getLongOr("NextEntityScanAt", now), now),
            boundedStoredTime(tag.getLongOr("NextShelterScanAt", now), now),
            boundedStoredTime(tag.getLongOr("NextFeedbackAt", now), now),
            boundedStoredTime(tag.getLongOr("LastNavigationAt", now), now),
            tag.getIntOr("RouteFailures", 0), boundedStoredTime(tag.getLongOr("RetryAfter", 0L), now)
        );
        return loaded.reconcileAfterLoad(now);
    }

    private static Optional<VictimReport> readReport(final CompoundTag tag) {
        try {
            final UUID victim = UUID.fromString(tag.getStringOr("Victim", ""));
            final ReportOutcome outcome = ReportOutcome.valueOf(tag.getStringOr("Outcome", "").toUpperCase(Locale.ROOT));
            return Optional.of(new VictimReport(
                victim, tag.getIntOr("X", 0), tag.getIntOr("Y", 0), tag.getIntOr("Z", 0),
                tag.getLongOr("EncounteredAt", 0L), outcome, tag.getIntOr("Importance", 0)
            ));
        } catch (final IllegalArgumentException ignored) {
            return Optional.empty();
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

    private static <T extends Enum<T>> T enumValue(final Class<T> type, final String value, final T fallback) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static long maximumLoadedFuture(final long now) {
        return VampireCourtRules.saturatingAdd(now, MAX_LOADED_FUTURE_TICKS);
    }

    private static long boundedStoredTime(final long value, final long now) {
        if (value <= 0L) return 0L;
        return value > maximumLoadedFuture(now) ? now : value;
    }

    private static long boundedLoadedTime(final long value, final long now) {
        if (value < now || value > maximumLoadedFuture(now)) return now;
        return value;
    }

    private static long boundedPastTime(final long value, final long now) {
        if (value < 0L || value > maximumLoadedFuture(now)) return now;
        return value;
    }

    private VampireCourtState copy(
        final Intent newIntent,
        final long newIntentExpiresAt,
        final int newPressure,
        final long newLastPressureAt,
        final Optional<String> newShelterDimension,
        final Optional<BlockPos> newShelter,
        final long newShelterExpiresAt,
        final Optional<UUID> newTargetId,
        final long newTargetExpiresAt,
        final Optional<UUID> newMasterId,
        final AssaultRole newAssaultRole,
        final long newWaveringUntil,
        final Optional<UUID> newRecentAttacker,
        final long newAttackerExpiresAt,
        final List<VictimReport> newReports,
        final long newNextDecisionAt,
        final long newNextEntityScanAt,
        final long newNextShelterScanAt,
        final long newNextFeedbackAt,
        final long newLastNavigationAt,
        final int newRouteFailures,
        final long newRetryAfter
    ) {
        return new VampireCourtState(
            SCHEMA_VERSION, kind, newIntent, newIntentExpiresAt, newPressure, newLastPressureAt,
            newShelterDimension, newShelter, newShelterExpiresAt, newTargetId, newTargetExpiresAt,
            newMasterId, newAssaultRole, newWaveringUntil, newRecentAttacker, newAttackerExpiresAt,
            newReports, newNextDecisionAt, newNextEntityScanAt, newNextShelterScanAt,
            newNextFeedbackAt, newLastNavigationAt, newRouteFailures, newRetryAfter
        );
    }
}
