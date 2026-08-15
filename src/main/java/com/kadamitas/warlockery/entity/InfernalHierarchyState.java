package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.InfernalHierarchyRules.AuthorityClass;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.Intent;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.OrderKind;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.PhaseState;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.Rank;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public record InfernalHierarchyState(
    int schemaVersion,
    Rank rank,
    Intent intent,
    AuthorityClass authorityClass,
    Optional<UUID> authorityId,
    Optional<UUID> leaderId,
    Optional<Rank> leaderRank,
    long membershipLeaseUntil,
    long orderEpoch,
    Optional<Order> order,
    int morale,
    long lastMoraleRecoveryAt,
    long lastAllyLossAt,
    long lastRallyAt,
    Optional<UUID> aggressorId,
    long aggressorExpiresAt,
    Optional<UUID> challengerId,
    long challengerExpiresAt,
    Optional<UUID> trucePlayerId,
    long truceExpiresAt,
    long truceRefreshedAt,
    long truceBreachUntil,
    Optional<Long> anchorPos,
    long anchorExpiresAt,
    List<Member> roster,
    boolean phaseCompleted,
    PhaseState phaseState,
    long phaseDeadline,
    List<UUID> summons,
    long summonExpiresAt,
    Optional<UUID> summonerId,
    Cadence cadence,
    int routeFailures,
    long actionBackoffUntil,
    long intentGeneration
) {
    public static final int SCHEMA_VERSION = 1;

    public InfernalHierarchyState {
        rank = Objects.requireNonNull(rank, "rank");
        intent = Objects.requireNonNull(intent, "intent");
        authorityClass = Objects.requireNonNull(authorityClass, "authorityClass");
        authorityId = Objects.requireNonNull(authorityId, "authorityId");
        leaderId = Objects.requireNonNull(leaderId, "leaderId");
        leaderRank = Objects.requireNonNull(leaderRank, "leaderRank");
        order = Objects.requireNonNull(order, "order");
        aggressorId = Objects.requireNonNull(aggressorId, "aggressorId");
        challengerId = Objects.requireNonNull(challengerId, "challengerId");
        trucePlayerId = Objects.requireNonNull(trucePlayerId, "trucePlayerId");
        anchorPos = Objects.requireNonNull(anchorPos, "anchorPos");
        summonerId = Objects.requireNonNull(summonerId, "summonerId");
        cadence = Objects.requireNonNull(cadence, "cadence");
        morale = InfernalHierarchyRules.clampMorale(morale);
        roster = List.copyOf(Objects.requireNonNull(roster, "roster")).stream()
            .limit(InfernalHierarchyRules.memberCap(rank)).toList();
        summons = List.copyOf(Objects.requireNonNull(summons, "summons")).stream()
            .limit(InfernalHierarchyRules.PHASE_SUMMON_CAP).toList();
        routeFailures = Math.clamp(routeFailures, 0, InfernalHierarchyRules.MAX_ROUTE_FAILURES);
        intentGeneration = Math.max(0L, intentGeneration);
        orderEpoch = Math.max(0L, orderEpoch);
    }

    public record Order(OrderKind kind, Optional<UUID> targetId, long expiresAt, long epoch, Rank issuerRank) {
        public Order {
            kind = Objects.requireNonNull(kind, "kind");
            targetId = Objects.requireNonNull(targetId, "targetId");
            issuerRank = Objects.requireNonNull(issuerRank, "issuerRank");
        }

        public boolean valid(final long now) {
            return expiresAt > now;
        }
    }

    public record Member(UUID id, Rank rank, long leaseUntil) {
        public Member {
            id = Objects.requireNonNull(id, "id");
            rank = Objects.requireNonNull(rank, "rank");
        }

        public boolean valid(final long now) {
            return leaseUntil > now;
        }
    }

    public record Cadence(
        long nextDecisionAt,
        long nextObservationAt,
        long nextGroupRefreshAt,
        long nextAnchorSearchAt,
        long nextNavigationAt,
        long nextFeedbackAt
    ) {
        public static Cadence due() {
            return new Cadence(0L, 0L, 0L, 0L, 0L, 0L);
        }
    }

    public static InfernalHierarchyState empty(final Rank rank, final UUID entityId, final long now) {
        final long offset = InfernalHierarchyRules.stableOffset(
            entityId, InfernalHierarchyRules.DECISION_INTERVAL_TICKS
        );
        return new InfernalHierarchyState(
            SCHEMA_VERSION, rank, Intent.IDLE, AuthorityClass.AUTONOMY,
            Optional.empty(), Optional.empty(), Optional.empty(), 0L, 0L, Optional.empty(),
            InfernalHierarchyRules.MORALE_BASELINE, Math.max(0L, now), 0L, 0L,
            Optional.empty(), 0L, Optional.empty(), 0L,
            Optional.empty(), 0L, 0L, 0L,
            Optional.empty(), 0L, List.of(), false, PhaseState.NONE, 0L,
            List.of(), 0L, Optional.empty(),
            new Cadence(Math.max(0L, now) + offset, 0L, 0L, 0L, 0L, 0L),
            0, 0L, 0L
        );
    }

    public InfernalHierarchyState withIntent(final Intent updated) {
        return new InfernalHierarchyState(schemaVersion, rank, updated, authorityClass, authorityId,
            leaderId, leaderRank, membershipLeaseUntil, orderEpoch, order, morale, lastMoraleRecoveryAt,
            lastAllyLossAt, lastRallyAt, aggressorId, aggressorExpiresAt, challengerId, challengerExpiresAt,
            trucePlayerId, truceExpiresAt, truceRefreshedAt, truceBreachUntil, anchorPos, anchorExpiresAt,
            roster, phaseCompleted, phaseState, phaseDeadline, summons, summonExpiresAt, summonerId, cadence,
            routeFailures, actionBackoffUntil,
            updated == intent ? intentGeneration : intentGeneration + 1L);
    }

    public InfernalHierarchyState withAuthority(final AuthorityClass updatedClass, final Optional<UUID> updatedId) {
        return new InfernalHierarchyState(schemaVersion, rank, intent, updatedClass, updatedId,
            leaderId, leaderRank, membershipLeaseUntil, orderEpoch, order, morale, lastMoraleRecoveryAt,
            lastAllyLossAt, lastRallyAt, aggressorId, aggressorExpiresAt, challengerId, challengerExpiresAt,
            trucePlayerId, truceExpiresAt, truceRefreshedAt, truceBreachUntil, anchorPos, anchorExpiresAt,
            roster, phaseCompleted, phaseState, phaseDeadline, summons, summonExpiresAt, summonerId, cadence,
            routeFailures, actionBackoffUntil, intentGeneration);
    }

    public InfernalHierarchyState withLeader(
        final Optional<UUID> updatedLeader,
        final Optional<Rank> updatedLeaderRank,
        final long leaseUntil
    ) {
        return new InfernalHierarchyState(schemaVersion, rank, intent, authorityClass, authorityId,
            updatedLeader, updatedLeaderRank, updatedLeader.isEmpty() ? 0L : leaseUntil, orderEpoch, order,
            morale, lastMoraleRecoveryAt, lastAllyLossAt, lastRallyAt, aggressorId, aggressorExpiresAt,
            challengerId, challengerExpiresAt, trucePlayerId, truceExpiresAt, truceRefreshedAt,
            truceBreachUntil, anchorPos, anchorExpiresAt, roster, phaseCompleted, phaseState, phaseDeadline,
            summons, summonExpiresAt, summonerId, cadence, routeFailures, actionBackoffUntil, intentGeneration);
    }

    public InfernalHierarchyState withOrder(final Optional<Order> updated) {
        return new InfernalHierarchyState(schemaVersion, rank, intent, authorityClass, authorityId,
            leaderId, leaderRank, membershipLeaseUntil, orderEpoch, updated, morale, lastMoraleRecoveryAt,
            lastAllyLossAt, lastRallyAt, aggressorId, aggressorExpiresAt, challengerId, challengerExpiresAt,
            trucePlayerId, truceExpiresAt, truceRefreshedAt, truceBreachUntil, anchorPos, anchorExpiresAt,
            roster, phaseCompleted, phaseState, phaseDeadline, summons, summonExpiresAt, summonerId, cadence,
            routeFailures, actionBackoffUntil, intentGeneration);
    }

    public InfernalHierarchyState withMorale(final int updatedMorale, final long recoveryAt) {
        return new InfernalHierarchyState(schemaVersion, rank, intent, authorityClass, authorityId,
            leaderId, leaderRank, membershipLeaseUntil, orderEpoch, order, updatedMorale, recoveryAt,
            lastAllyLossAt, lastRallyAt, aggressorId, aggressorExpiresAt, challengerId, challengerExpiresAt,
            trucePlayerId, truceExpiresAt, truceRefreshedAt, truceBreachUntil, anchorPos, anchorExpiresAt,
            roster, phaseCompleted, phaseState, phaseDeadline, summons, summonExpiresAt, summonerId, cadence,
            routeFailures, actionBackoffUntil, intentGeneration);
    }

    public InfernalHierarchyState withMoraleEvents(final long allyLossAt, final long rallyAt) {
        return new InfernalHierarchyState(schemaVersion, rank, intent, authorityClass, authorityId,
            leaderId, leaderRank, membershipLeaseUntil, orderEpoch, order, morale, lastMoraleRecoveryAt,
            allyLossAt, rallyAt, aggressorId, aggressorExpiresAt, challengerId, challengerExpiresAt,
            trucePlayerId, truceExpiresAt, truceRefreshedAt, truceBreachUntil, anchorPos, anchorExpiresAt,
            roster, phaseCompleted, phaseState, phaseDeadline, summons, summonExpiresAt, summonerId, cadence,
            routeFailures, actionBackoffUntil, intentGeneration);
    }

    public InfernalHierarchyState withAggressor(final Optional<UUID> updated, final long expiresAt) {
        return new InfernalHierarchyState(schemaVersion, rank, intent, authorityClass, authorityId,
            leaderId, leaderRank, membershipLeaseUntil, orderEpoch, order, morale, lastMoraleRecoveryAt,
            lastAllyLossAt, lastRallyAt, updated, updated.isEmpty() ? 0L : expiresAt,
            challengerId, challengerExpiresAt, trucePlayerId, truceExpiresAt, truceRefreshedAt,
            truceBreachUntil, anchorPos, anchorExpiresAt, roster, phaseCompleted, phaseState, phaseDeadline,
            summons, summonExpiresAt, summonerId, cadence, routeFailures, actionBackoffUntil, intentGeneration);
    }

    public InfernalHierarchyState withChallenger(final Optional<UUID> updated, final long expiresAt) {
        return new InfernalHierarchyState(schemaVersion, rank, intent, authorityClass, authorityId,
            leaderId, leaderRank, membershipLeaseUntil, orderEpoch, order, morale, lastMoraleRecoveryAt,
            lastAllyLossAt, lastRallyAt, aggressorId, aggressorExpiresAt,
            updated, updated.isEmpty() ? 0L : expiresAt,
            trucePlayerId, truceExpiresAt, truceRefreshedAt, truceBreachUntil, anchorPos, anchorExpiresAt,
            roster, phaseCompleted, phaseState, phaseDeadline, summons, summonExpiresAt, summonerId, cadence,
            routeFailures, actionBackoffUntil, intentGeneration);
    }

    public InfernalHierarchyState withTruce(
        final Optional<UUID> updated,
        final long expiresAt,
        final long refreshedAt,
        final long breachUntil
    ) {
        return new InfernalHierarchyState(schemaVersion, rank, intent, authorityClass, authorityId,
            leaderId, leaderRank, membershipLeaseUntil, orderEpoch, order, morale, lastMoraleRecoveryAt,
            lastAllyLossAt, lastRallyAt, aggressorId, aggressorExpiresAt, challengerId, challengerExpiresAt,
            updated, updated.isEmpty() ? 0L : expiresAt, refreshedAt, breachUntil,
            anchorPos, anchorExpiresAt, roster, phaseCompleted, phaseState, phaseDeadline, summons,
            summonExpiresAt, summonerId, cadence, routeFailures, actionBackoffUntil, intentGeneration);
    }

    public InfernalHierarchyState withAnchor(final Optional<Long> updated, final long expiresAt) {
        return new InfernalHierarchyState(schemaVersion, rank, intent, authorityClass, authorityId,
            leaderId, leaderRank, membershipLeaseUntil, orderEpoch, order, morale, lastMoraleRecoveryAt,
            lastAllyLossAt, lastRallyAt, aggressorId, aggressorExpiresAt, challengerId, challengerExpiresAt,
            trucePlayerId, truceExpiresAt, truceRefreshedAt, truceBreachUntil,
            updated, updated.isEmpty() ? 0L : expiresAt, roster, phaseCompleted, phaseState, phaseDeadline,
            summons, summonExpiresAt, summonerId, cadence, routeFailures, actionBackoffUntil, intentGeneration);
    }

    public InfernalHierarchyState withRoster(final List<Member> updated, final long epoch) {
        return new InfernalHierarchyState(schemaVersion, rank, intent, authorityClass, authorityId,
            leaderId, leaderRank, membershipLeaseUntil, epoch, order, morale, lastMoraleRecoveryAt,
            lastAllyLossAt, lastRallyAt, aggressorId, aggressorExpiresAt, challengerId, challengerExpiresAt,
            trucePlayerId, truceExpiresAt, truceRefreshedAt, truceBreachUntil, anchorPos, anchorExpiresAt,
            updated, phaseCompleted, phaseState, phaseDeadline, summons, summonExpiresAt, summonerId, cadence,
            routeFailures, actionBackoffUntil, intentGeneration);
    }

    public InfernalHierarchyState withPhase(final PhaseState updatedState, final boolean completed, final long deadline) {
        return new InfernalHierarchyState(schemaVersion, rank, intent, authorityClass, authorityId,
            leaderId, leaderRank, membershipLeaseUntil, orderEpoch, order, morale, lastMoraleRecoveryAt,
            lastAllyLossAt, lastRallyAt, aggressorId, aggressorExpiresAt, challengerId, challengerExpiresAt,
            trucePlayerId, truceExpiresAt, truceRefreshedAt, truceBreachUntil, anchorPos, anchorExpiresAt,
            roster, completed, updatedState, deadline, summons, summonExpiresAt, summonerId, cadence,
            routeFailures, actionBackoffUntil, intentGeneration);
    }

    /**
     * The summon deadline is shared by a summoner tracking its temporary bodies and by a temporary
     * body tracking its own life. Clearing the summon list must therefore never discard a summoned
     * entity's own expiry, or a reloaded reinforcement would leak permanently.
     */
    public InfernalHierarchyState withSummons(final List<UUID> updated, final long expiresAt) {
        final long resolved = updated.isEmpty()
            ? (summonerId.isPresent() ? summonExpiresAt : 0L)
            : expiresAt;
        return new InfernalHierarchyState(schemaVersion, rank, intent, authorityClass, authorityId,
            leaderId, leaderRank, membershipLeaseUntil, orderEpoch, order, morale, lastMoraleRecoveryAt,
            lastAllyLossAt, lastRallyAt, aggressorId, aggressorExpiresAt, challengerId, challengerExpiresAt,
            trucePlayerId, truceExpiresAt, truceRefreshedAt, truceBreachUntil, anchorPos, anchorExpiresAt,
            roster, phaseCompleted, phaseState, phaseDeadline, updated,
            resolved, summonerId, cadence,
            routeFailures, actionBackoffUntil, intentGeneration);
    }

    public InfernalHierarchyState withSummoner(final Optional<UUID> updated) {
        return withSummoner(updated, summonExpiresAt);
    }

    public InfernalHierarchyState withSummoner(final Optional<UUID> updated, final long expiresAt) {
        final long resolved = updated.isEmpty()
            ? (summons.isEmpty() ? 0L : summonExpiresAt)
            : expiresAt;
        return new InfernalHierarchyState(schemaVersion, rank, intent, authorityClass, authorityId,
            leaderId, leaderRank, membershipLeaseUntil, orderEpoch, order, morale, lastMoraleRecoveryAt,
            lastAllyLossAt, lastRallyAt, aggressorId, aggressorExpiresAt, challengerId, challengerExpiresAt,
            trucePlayerId, truceExpiresAt, truceRefreshedAt, truceBreachUntil, anchorPos, anchorExpiresAt,
            roster, phaseCompleted, phaseState, phaseDeadline, summons, resolved, updated, cadence,
            routeFailures, actionBackoffUntil, intentGeneration);
    }

    public InfernalHierarchyState withCadence(final Cadence updated) {
        return new InfernalHierarchyState(schemaVersion, rank, intent, authorityClass, authorityId,
            leaderId, leaderRank, membershipLeaseUntil, orderEpoch, order, morale, lastMoraleRecoveryAt,
            lastAllyLossAt, lastRallyAt, aggressorId, aggressorExpiresAt, challengerId, challengerExpiresAt,
            trucePlayerId, truceExpiresAt, truceRefreshedAt, truceBreachUntil, anchorPos, anchorExpiresAt,
            roster, phaseCompleted, phaseState, phaseDeadline, summons, summonExpiresAt, summonerId, updated,
            routeFailures, actionBackoffUntil, intentGeneration);
    }

    public InfernalHierarchyState withRouteFailures(final int updated, final long backoffUntil) {
        return new InfernalHierarchyState(schemaVersion, rank, intent, authorityClass, authorityId,
            leaderId, leaderRank, membershipLeaseUntil, orderEpoch, order, morale, lastMoraleRecoveryAt,
            lastAllyLossAt, lastRallyAt, aggressorId, aggressorExpiresAt, challengerId, challengerExpiresAt,
            trucePlayerId, truceExpiresAt, truceRefreshedAt, truceBreachUntil, anchorPos, anchorExpiresAt,
            roster, phaseCompleted, phaseState, phaseDeadline, summons, summonExpiresAt, summonerId, cadence,
            updated, backoffUntil, intentGeneration);
    }

    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", schemaVersion);
        tag.putString("Rank", rank.name().toLowerCase(Locale.ROOT));
        tag.putString("Intent", intent.name().toLowerCase(Locale.ROOT));
        tag.putString("AuthorityClass", authorityClass.name().toLowerCase(Locale.ROOT));
        authorityId.ifPresent(id -> tag.putString("AuthorityId", id.toString()));
        leaderId.ifPresent(id -> tag.putString("LeaderId", id.toString()));
        leaderRank.ifPresent(value -> tag.putString("LeaderRank", value.name().toLowerCase(Locale.ROOT)));
        tag.putLong("MembershipLeaseUntil", membershipLeaseUntil);
        tag.putLong("OrderEpoch", orderEpoch);
        order.ifPresent(value -> {
            final CompoundTag row = new CompoundTag();
            row.putString("Kind", value.kind().name().toLowerCase(Locale.ROOT));
            value.targetId().ifPresent(id -> row.putString("Target", id.toString()));
            row.putLong("ExpiresAt", value.expiresAt());
            row.putLong("Epoch", value.epoch());
            row.putString("IssuerRank", value.issuerRank().name().toLowerCase(Locale.ROOT));
            tag.put("Order", row);
        });
        tag.putInt("Morale", morale);
        tag.putLong("MoraleRecoveryAt", lastMoraleRecoveryAt);
        aggressorId.ifPresent(id -> tag.putString("AggressorId", id.toString()));
        tag.putLong("AggressorExpiresAt", aggressorExpiresAt);
        challengerId.ifPresent(id -> tag.putString("ChallengerId", id.toString()));
        tag.putLong("ChallengerExpiresAt", challengerExpiresAt);
        trucePlayerId.ifPresent(id -> tag.putString("TrucePlayerId", id.toString()));
        tag.putLong("TruceExpiresAt", truceExpiresAt);
        tag.putLong("TruceBreachUntil", truceBreachUntil);
        anchorPos.ifPresent(packed -> tag.putLong("AnchorPos", packed));
        tag.putLong("AnchorExpiresAt", anchorExpiresAt);
        tag.putInt("MemberCount", roster.size());
        for (int index = 0; index < roster.size(); index++) {
            final Member member = roster.get(index);
            final CompoundTag row = new CompoundTag();
            row.putString("Id", member.id().toString());
            row.putString("Rank", member.rank().name().toLowerCase(Locale.ROOT));
            row.putLong("LeaseUntil", member.leaseUntil());
            tag.put("Member" + index, row);
        }
        tag.putBoolean("PhaseCompleted", phaseCompleted);
        tag.putString("PhaseState", phaseState.name().toLowerCase(Locale.ROOT));
        tag.putLong("PhaseDeadline", phaseDeadline);
        tag.putInt("SummonCount", summons.size());
        for (int index = 0; index < summons.size(); index++) {
            tag.putString("Summon" + index, summons.get(index).toString());
        }
        tag.putLong("SummonExpiresAt", summonExpiresAt);
        summonerId.ifPresent(id -> tag.putString("SummonerId", id.toString()));
        tag.putInt("RouteFailures", routeFailures);
        tag.putLong("ActionBackoffUntil", actionBackoffUntil);
        tag.putLong("IntentGeneration", intentGeneration);
        return tag;
    }

    public static InfernalHierarchyState read(
        final CompoundTag tag,
        final Rank entityRank,
        final UUID entityId,
        final long now,
        final boolean legacyPhaseTriggered
    ) {
        final InfernalHierarchyState fallback = empty(entityRank, entityId, now)
            .withPhase(legacyPhaseTriggered ? PhaseState.DONE : PhaseState.NONE, legacyPhaseTriggered, 0L);
        if (tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return fallback;
        }
        final Optional<Rank> storedRank = parseEnum(Rank.values(), tag.getStringOr("Rank", ""));
        if (storedRank.isEmpty() || storedRank.orElseThrow() != entityRank) {
            return fallback;
        }
        final boolean storedCompleted = tag.getBooleanOr("PhaseCompleted", false) || legacyPhaseTriggered;
        // A save inside the telegraph, commit, or recovery window resumes that exact window instead of
        // collapsing to DONE, which would permanently cancel the once-per-Regent half-health phase. The
        // latch is forced true while an active window resumes so the phase can still never replay.
        final PhaseState storedPhase = parseEnum(PhaseState.values(), tag.getStringOr("PhaseState", ""))
            .orElse(storedCompleted ? PhaseState.DONE : PhaseState.NONE);
        final PhaseState resumedPhase;
        final long resumedPhaseDeadline;
        switch (storedPhase) {
            case TELEGRAPH -> {
                resumedPhase = PhaseState.TELEGRAPH;
                resumedPhaseDeadline = InfernalHierarchyRules.clampDeadline(
                    tag.getLongOr("PhaseDeadline", 0L), now, InfernalHierarchyRules.PHASE_TELEGRAPH_TICKS
                );
            }
            case COMMIT -> {
                resumedPhase = PhaseState.COMMIT;
                resumedPhaseDeadline = InfernalHierarchyRules.clampDeadline(
                    tag.getLongOr("PhaseDeadline", 0L), now, InfernalHierarchyRules.PHASE_EFFECT_TICKS
                );
            }
            case RECOVERY -> {
                resumedPhase = PhaseState.RECOVERY;
                resumedPhaseDeadline = InfernalHierarchyRules.clampDeadline(
                    tag.getLongOr("PhaseDeadline", 0L), now, InfernalHierarchyRules.PHASE_RECOVERY_TICKS
                );
            }
            case DONE -> {
                resumedPhase = PhaseState.DONE;
                resumedPhaseDeadline = 0L;
            }
            default -> {
                resumedPhase = storedCompleted ? PhaseState.DONE : PhaseState.NONE;
                resumedPhaseDeadline = 0L;
            }
        }
        final boolean completed = storedCompleted
            || resumedPhase == PhaseState.TELEGRAPH
            || resumedPhase == PhaseState.COMMIT
            || resumedPhase == PhaseState.RECOVERY;
        final Optional<Order> order = tag.getCompound("Order").flatMap(row -> {
            final Optional<OrderKind> kind = parseEnum(OrderKind.values(), row.getStringOr("Kind", ""));
            final Optional<Rank> issuer = parseEnum(Rank.values(), row.getStringOr("IssuerRank", ""));
            if (kind.isEmpty() || issuer.isEmpty()) return Optional.empty();
            final long expiresAt = InfernalHierarchyRules.clampDeadline(
                row.getLongOr("ExpiresAt", 0L), now,
                InfernalHierarchyRules.orderLifetimeTicks(issuer.orElseThrow())
            );
            if (expiresAt <= now) return Optional.empty();
            return Optional.of(new Order(
                kind.orElseThrow(),
                parseUuid(row.getStringOr("Target", "")),
                expiresAt,
                Math.max(0L, row.getLongOr("Epoch", 0L)),
                issuer.orElseThrow()
            ));
        });
        final int memberCount = Math.clamp(
            tag.getIntOr("MemberCount", 0), 0, InfernalHierarchyRules.memberCap(entityRank)
        );
        final java.util.ArrayList<Member> roster = new java.util.ArrayList<>();
        for (int index = 0; index < memberCount; index++) {
            final Optional<CompoundTag> stored = tag.getCompound("Member" + index);
            if (stored.isEmpty()) continue;
            final CompoundTag row = stored.orElseThrow();
            final Optional<UUID> id = parseUuid(row.getStringOr("Id", ""));
            final Optional<Rank> memberRank = parseEnum(Rank.values(), row.getStringOr("Rank", ""));
            if (id.isEmpty() || memberRank.isEmpty()) continue;
            final long leaseUntil = InfernalHierarchyRules.clampDeadline(
                row.getLongOr("LeaseUntil", 0L), now, InfernalHierarchyRules.MEMBERSHIP_LEASE_TICKS
            );
            if (leaseUntil <= now) continue;
            roster.add(new Member(id.orElseThrow(), memberRank.orElseThrow(), leaseUntil));
        }
        final Optional<UUID> leader = parseUuid(tag.getStringOr("LeaderId", ""));
        final long lease = InfernalHierarchyRules.clampDeadline(
            tag.getLongOr("MembershipLeaseUntil", 0L), now, InfernalHierarchyRules.MEMBERSHIP_LEASE_TICKS
        );
        final boolean leaseLive = leader.isPresent() && lease > now;
        final int summonCount = Math.clamp(
            tag.getIntOr("SummonCount", 0), 0, InfernalHierarchyRules.PHASE_SUMMON_CAP
        );
        final java.util.ArrayList<UUID> summons = new java.util.ArrayList<>();
        final long summonExpiresAt = InfernalHierarchyRules.clampDeadline(
            tag.getLongOr("SummonExpiresAt", 0L), now, InfernalHierarchyRules.SUMMON_LIFE_TICKS
        );
        if (summonExpiresAt > now) {
            for (int index = 0; index < summonCount; index++) {
                parseUuid(tag.getStringOr("Summon" + index, "")).ifPresent(summons::add);
            }
        }
        final Optional<UUID> summoner = parseUuid(tag.getStringOr("SummonerId", ""));
        // A temporary phase Demon stores its own life deadline with an empty summon list. Retaining the
        // deadline only for a non-empty list would let every reloaded reinforcement outlive its bound.
        final long liveSummonExpiresAt = summonExpiresAt > now
            && (!summons.isEmpty() || summoner.isPresent())
            ? summonExpiresAt
            : 0L;
        final long aggressorExpiresAt = clearExpired(
            tag.getLongOr("AggressorExpiresAt", 0L), now, InfernalHierarchyRules.AGGRESSOR_TICKS
        );
        final long challengerExpiresAt = clearExpired(
            tag.getLongOr("ChallengerExpiresAt", 0L), now, InfernalHierarchyRules.PROVOCATION_TICKS
        );
        final long truceExpiresAt = clearExpired(
            tag.getLongOr("TruceExpiresAt", 0L), now, InfernalHierarchyRules.TRUCE_TICKS
        );
        final long anchorExpiresAt = clearExpired(
            tag.getLongOr("AnchorExpiresAt", 0L), now, InfernalHierarchyRules.MEMBERSHIP_LEASE_TICKS
        );
        final long offset = InfernalHierarchyRules.stableOffset(
            entityId, InfernalHierarchyRules.DECISION_INTERVAL_TICKS
        );
        return new InfernalHierarchyState(
            SCHEMA_VERSION,
            entityRank,
            Intent.IDLE,
            AuthorityClass.AUTONOMY,
            parseUuid(tag.getStringOr("AuthorityId", "")),
            leaseLive ? leader : Optional.empty(),
            leaseLive ? parseEnum(Rank.values(), tag.getStringOr("LeaderRank", "")) : Optional.empty(),
            leaseLive ? lease : 0L,
            Math.max(0L, tag.getLongOr("OrderEpoch", 0L)),
            leaseLive ? order : Optional.empty(),
            InfernalHierarchyRules.recoveredMorale(
                tag.getIntOr("Morale", InfernalHierarchyRules.MORALE_BASELINE),
                tag.getLongOr("MoraleRecoveryAt", 0L),
                now
            ),
            Math.max(0L, now),
            0L,
            0L,
            aggressorExpiresAt > now ? parseUuid(tag.getStringOr("AggressorId", "")) : Optional.empty(),
            aggressorExpiresAt,
            challengerExpiresAt > now ? parseUuid(tag.getStringOr("ChallengerId", "")) : Optional.empty(),
            challengerExpiresAt,
            truceExpiresAt > now ? parseUuid(tag.getStringOr("TrucePlayerId", "")) : Optional.empty(),
            truceExpiresAt,
            0L,
            InfernalHierarchyRules.clampDeadline(
                tag.getLongOr("TruceBreachUntil", 0L), now, InfernalHierarchyRules.TRUCE_BREACH_TICKS
            ),
            anchorExpiresAt > now && tag.getLongOr("AnchorPos", Long.MIN_VALUE) != Long.MIN_VALUE
                ? Optional.of(tag.getLongOr("AnchorPos", 0L))
                : Optional.empty(),
            anchorExpiresAt,
            List.copyOf(roster),
            completed,
            resumedPhase,
            resumedPhaseDeadline,
            List.copyOf(summons),
            liveSummonExpiresAt,
            summoner,
            new Cadence(Math.max(0L, now) + offset, 0L, 0L, 0L, 0L, 0L),
            tag.getIntOr("RouteFailures", 0),
            InfernalHierarchyRules.clampDeadline(
                tag.getLongOr("ActionBackoffUntil", 0L), now, InfernalHierarchyRules.ROUTE_BACKOFF_TICKS
            ),
            Math.max(0L, tag.getLongOr("IntentGeneration", 0L))
        );
    }

    private static long clearExpired(final long deadline, final long now, final long maxHorizonTicks) {
        final long clamped = InfernalHierarchyRules.clampDeadline(deadline, now, maxHorizonTicks);
        return clamped <= now ? 0L : clamped;
    }

    private static Optional<UUID> parseUuid(final String value) {
        if (value.isEmpty()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(value));
        } catch (final IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static <T extends Enum<T>> Optional<T> parseEnum(final T[] values, final String value) {
        for (final T candidate : values) {
            if (candidate.name().equalsIgnoreCase(value)) return Optional.of(candidate);
        }
        return Optional.empty();
    }
}
