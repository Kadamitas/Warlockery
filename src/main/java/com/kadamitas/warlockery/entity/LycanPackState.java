package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.LycanPackRules.ActionKind;
import com.kadamitas.warlockery.entity.LycanPackRules.HuntPhase;
import com.kadamitas.warlockery.entity.LycanPackRules.HuntRole;
import com.kadamitas.warlockery.entity.LycanPackRules.PlayerRelation;
import com.kadamitas.warlockery.entity.LycanPackRules.Relation;
import com.kadamitas.warlockery.entity.LycanPackRules.TrailClass;
import com.kadamitas.warlockery.entity.LycanPackRules.Variant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public record LycanPackState(
    int schemaVersion,
    Variant variant,
    Needs needs,
    Refuge refuge,
    List<PlayerRelation> relationships,
    List<TrailFact> trails,
    Hunt hunt,
    Cohort cohort,
    ActionState action,
    Cadence cadence
) {
    public static final int SCHEMA_VERSION = 1;
    private static final long MAX_LOADED_FUTURE_TICKS = 24_000L;

    public record Needs(int hunger, int fear, long lastNeedUpdateAt, long lastSightFearAt, long forageCooldownUntil) {
        public Needs {
            hunger = Math.clamp(hunger, 0, LycanPackRules.MAX_HUNGER);
            fear = Math.clamp(fear, 0, LycanPackRules.MAX_HUNGER);
        }
    }

    public record Refuge(
        Optional<BlockPos> position,
        long expiresAt,
        long nextSearchAt,
        long defenseExpiresAt
    ) {
        public Refuge {
            position = Objects.requireNonNull(position, "position").map(BlockPos::immutable);
        }

        public static Refuge none() {
            return new Refuge(Optional.empty(), 0L, 0L, 0L);
        }
    }

    public record TrailFact(
        Optional<UUID> sourceId,
        TrailClass trailClass,
        BlockPos position,
        int confidence,
        long observedAt,
        long expiresAt
    ) {
        public TrailFact {
            sourceId = Objects.requireNonNull(sourceId, "sourceId");
            trailClass = Objects.requireNonNull(trailClass, "trailClass");
            position = Objects.requireNonNull(position, "position").immutable();
        }

        public UUID stableKey() {
            return sourceId.orElseGet(() -> new UUID(position.asLong(), trailClass.ordinal()));
        }
    }

    public record Hunt(
        Optional<UUID> episodeId,
        Optional<UUID> coordinatorId,
        List<UUID> memberIds,
        Optional<HuntRole> role,
        Optional<HuntPhase> phase,
        Optional<UUID> targetId,
        Optional<BlockPos> targetPosition,
        long episodeExpiresAt,
        long phaseExpiresAt,
        int targetChanges,
        Optional<BlockPos> returnIntent
    ) {
        public Hunt {
            episodeId = Objects.requireNonNull(episodeId, "episodeId");
            coordinatorId = Objects.requireNonNull(coordinatorId, "coordinatorId");
            memberIds = List.copyOf(new LinkedHashSet<>(Objects.requireNonNull(memberIds, "memberIds"))).stream()
                .limit(LycanPackRules.MAX_HUNT_MEMBERS).toList();
            role = Objects.requireNonNull(role, "role");
            phase = Objects.requireNonNull(phase, "phase");
            targetId = Objects.requireNonNull(targetId, "targetId");
            targetPosition = Objects.requireNonNull(targetPosition, "targetPosition").map(BlockPos::immutable);
            targetChanges = Math.clamp(targetChanges, 0, LycanPackRules.MAX_TARGET_CHANGES + 1);
            returnIntent = Objects.requireNonNull(returnIntent, "returnIntent").map(BlockPos::immutable);
        }

        public static Hunt none() {
            return new Hunt(
                Optional.empty(), Optional.empty(), List.of(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), 0L, 0L, 0, Optional.empty()
            );
        }
    }

    public record Familiarity(UUID otherId, int points, long lastObservedAt) {
        public Familiarity {
            points = Math.clamp(points, 0, LycanPackRules.FAMILIARITY_BOND_POINTS);
        }
    }

    public record Cohort(
        List<Familiarity> familiarity,
        Optional<UUID> cohortId,
        List<UUID> bondedIds,
        long cohortExpiresAt,
        long warningExpiresAt,
        long lastWarnAt
    ) {
        public Cohort {
            familiarity = List.copyOf(Objects.requireNonNull(familiarity, "familiarity")).stream()
                .limit(LycanPackRules.MAX_FAMILIARITY_ENTRIES).toList();
            cohortId = Objects.requireNonNull(cohortId, "cohortId");
            bondedIds = List.copyOf(new LinkedHashSet<>(Objects.requireNonNull(bondedIds, "bondedIds"))).stream()
                .limit(LycanPackRules.MAX_COHORT_MEMBERS).toList();
        }

        public static Cohort none() {
            return new Cohort(List.of(), Optional.empty(), List.of(), 0L, 0L, 0L);
        }
    }

    public record ActionState(
        ActionKind kind,
        long windupUntil,
        long executionUntil,
        long recoveryUntil,
        Optional<String> claimPurpose,
        Optional<String> claimKey,
        long claimExpiresAt,
        Optional<String> lastCancellation
    ) {
        public ActionState {
            kind = Objects.requireNonNull(kind, "kind");
            claimPurpose = Objects.requireNonNull(claimPurpose, "claimPurpose");
            claimKey = Objects.requireNonNull(claimKey, "claimKey");
            lastCancellation = Objects.requireNonNull(lastCancellation, "lastCancellation");
        }

        public static ActionState rest() {
            return new ActionState(
                ActionKind.NONE, 0L, 0L, 0L, Optional.empty(), Optional.empty(), 0L, Optional.empty()
            );
        }
    }

    public record Cadence(
        long nextDecisionAt,
        long nextPerceptionAt,
        long nextPlanAt,
        long nextFeedbackAt,
        long lastNavigationAt,
        long nextMoonSampleAt,
        int routeFailures,
        long retryAfter
    ) {
        public Cadence {
            routeFailures = Math.clamp(routeFailures, 0, LycanPackRules.MAX_ROUTE_FAILURES);
        }
    }

    public LycanPackState {
        variant = Objects.requireNonNull(variant, "variant");
        needs = Objects.requireNonNull(needs, "needs");
        refuge = Objects.requireNonNull(refuge, "refuge");
        relationships = List.copyOf(Objects.requireNonNull(relationships, "relationships")).stream()
            .limit(LycanPackRules.MAX_RELATIONSHIP_ENTRIES).toList();
        trails = List.copyOf(Objects.requireNonNull(trails, "trails")).stream()
            .limit(LycanPackRules.MAX_TRAIL_ENTRIES).toList();
        hunt = variant == Variant.WEREWOLF ? Objects.requireNonNull(hunt, "hunt") : Hunt.none();
        cohort = variant == Variant.FERAL_LYCAN ? Objects.requireNonNull(cohort, "cohort") : Cohort.none();
        action = Objects.requireNonNull(action, "action");
        cadence = Objects.requireNonNull(cadence, "cadence");
    }

    public static LycanPackState empty(final Variant variant, final long now) {
        return new LycanPackState(
            SCHEMA_VERSION, variant,
            new Needs(LycanPackRules.defaultHunger(variant), LycanPackRules.DEFAULT_FEAR, now, 0L, 0L),
            Refuge.none(), List.of(), List.of(), Hunt.none(), Cohort.none(), ActionState.rest(),
            new Cadence(now, now, now, now, 0L, now, 0, 0L)
        );
    }

    public LycanPackState withNeeds(final int hunger, final int fear, final long now) {
        return copy(new Needs(hunger, fear, now, needs.lastSightFearAt(), needs.forageCooldownUntil()),
            refuge, relationships, trails, hunt, cohort, action, cadence);
    }

    public LycanPackState withSightFearAt(final long at) {
        return copy(new Needs(needs.hunger(), needs.fear(), needs.lastNeedUpdateAt(), at, needs.forageCooldownUntil()),
            refuge, relationships, trails, hunt, cohort, action, cadence);
    }

    public LycanPackState withForageCooldownUntil(final long until) {
        return copy(new Needs(needs.hunger(), needs.fear(), needs.lastNeedUpdateAt(), needs.lastSightFearAt(), until),
            refuge, relationships, trails, hunt, cohort, action, cadence);
    }

    public LycanPackState withRefuge(final BlockPos position, final long expiresAt, final long nextSearchAt) {
        return copy(needs, new Refuge(Optional.ofNullable(position), expiresAt, nextSearchAt,
            refuge.defenseExpiresAt()), relationships, trails, hunt, cohort, action, cadence);
    }

    public LycanPackState withoutRefuge() {
        return copy(needs, new Refuge(Optional.empty(), 0L, refuge.nextSearchAt(), 0L),
            relationships, trails, hunt, cohort, action, cadence);
    }

    public LycanPackState withRefugeSearchAt(final long nextSearchAt) {
        return copy(needs, new Refuge(refuge.position(), refuge.expiresAt(), nextSearchAt,
            refuge.defenseExpiresAt()), relationships, trails, hunt, cohort, action, cadence);
    }

    public LycanPackState withRelationships(final List<PlayerRelation> value) {
        return copy(needs, refuge, value, trails, hunt, cohort, action, cadence);
    }

    public LycanPackState withTrails(final List<TrailFact> value) {
        return copy(needs, refuge, relationships, value, hunt, cohort, action, cadence);
    }

    public LycanPackState withHunt(final Hunt value) {
        return copy(needs, refuge, relationships, trails, value, cohort, action, cadence);
    }

    public LycanPackState withCohort(final Cohort value) {
        return copy(needs, refuge, relationships, trails, hunt, value, action, cadence);
    }

    public LycanPackState beginAction(
        final ActionKind kind,
        final long windupUntil,
        final long executionUntil,
        final long recoveryUntil
    ) {
        return copy(needs, refuge, relationships, trails, hunt, cohort, new ActionState(
            kind, windupUntil, executionUntil, recoveryUntil,
            action.claimPurpose(), action.claimKey(), action.claimExpiresAt(), Optional.empty()
        ), cadence);
    }

    public LycanPackState cancelAction(final String reason, final long recoveryUntil) {
        return copy(needs, refuge, relationships, trails, hunt, cohort, new ActionState(
            ActionKind.NONE, 0L, 0L, recoveryUntil,
            Optional.empty(), Optional.empty(), 0L, Optional.ofNullable(reason)
        ), cadence);
    }

    public LycanPackState withClaim(final String purpose, final String key, final long expiresAt) {
        return copy(needs, refuge, relationships, trails, hunt, cohort, new ActionState(
            action.kind(), action.windupUntil(), action.executionUntil(), action.recoveryUntil(),
            Optional.ofNullable(purpose), Optional.ofNullable(key), expiresAt, action.lastCancellation()
        ), cadence);
    }

    public LycanPackState withoutClaim() {
        return copy(needs, refuge, relationships, trails, hunt, cohort, new ActionState(
            action.kind(), action.windupUntil(), action.executionUntil(), action.recoveryUntil(),
            Optional.empty(), Optional.empty(), 0L, action.lastCancellation()
        ), cadence);
    }

    public LycanPackState withCadence(final Cadence value) {
        return copy(needs, refuge, relationships, trails, hunt, cohort, action, value);
    }

    public LycanPackState recordRouteResult(final boolean success, final long now) {
        if (success) {
            return withCadence(new Cadence(
                cadence.nextDecisionAt(), cadence.nextPerceptionAt(), cadence.nextPlanAt(),
                cadence.nextFeedbackAt(), cadence.lastNavigationAt(), cadence.nextMoonSampleAt(), 0, 0L
            ));
        }
        final int failures = LycanPackRules.routeFailures(cadence.routeFailures());
        return withCadence(new Cadence(
            cadence.nextDecisionAt(), cadence.nextPerceptionAt(), cadence.nextPlanAt(),
            cadence.nextFeedbackAt(), cadence.lastNavigationAt(), cadence.nextMoonSampleAt(),
            failures, LycanPackRules.routeBackoffUntil(failures, now)
        ));
    }

    public LycanPackState reconcile(final long now) {
        final Needs reconciledNeeds = new Needs(
            LycanPackRules.reconcileHunger(needs.hunger(), needs.lastNeedUpdateAt(), now),
            LycanPackRules.reconcileFear(needs.fear(), needs.lastNeedUpdateAt(), now),
            now,
            Math.min(needs.lastSightFearAt(), now),
            boundedFuture(needs.forageCooldownUntil(), now)
        );
        final Refuge reconciledRefuge = refuge.position().isPresent() && refuge.expiresAt() > now
            ? refuge
            : new Refuge(Optional.empty(), 0L, boundedLoaded(refuge.nextSearchAt(), now), 0L);
        final Hunt reconciledHunt = hunt.episodeId().isPresent() && hunt.episodeExpiresAt() > now
            ? hunt
            : Hunt.none();
        final Cohort reconciledCohort = new Cohort(
            cohort.familiarity(),
            cohort.cohortId().filter(id -> cohort.cohortExpiresAt() > now),
            cohort.cohortExpiresAt() > now ? cohort.bondedIds() : List.of(),
            cohort.cohortExpiresAt() > now ? cohort.cohortExpiresAt() : 0L,
            cohort.warningExpiresAt() > now ? cohort.warningExpiresAt() : 0L,
            Math.min(cohort.lastWarnAt(), now)
        );
        final boolean actionLive = action.kind() != ActionKind.NONE && action.recoveryUntil() > now;
        final boolean claimLive = action.claimPurpose().isPresent() && action.claimExpiresAt() > now;
        final ActionState reconciledAction = new ActionState(
            actionLive ? action.kind() : ActionKind.NONE,
            actionLive ? action.windupUntil() : 0L,
            actionLive ? action.executionUntil() : 0L,
            actionLive ? action.recoveryUntil() : Math.min(boundedFuture(action.recoveryUntil(), now),
                action.recoveryUntil()),
            claimLive ? action.claimPurpose() : Optional.empty(),
            claimLive ? action.claimKey() : Optional.empty(),
            claimLive ? action.claimExpiresAt() : 0L,
            action.lastCancellation()
        );
        final Cadence reconciledCadence = new Cadence(
            boundedLoaded(cadence.nextDecisionAt(), now),
            boundedLoaded(cadence.nextPerceptionAt(), now),
            boundedLoaded(cadence.nextPlanAt(), now),
            boundedLoaded(cadence.nextFeedbackAt(), now),
            Math.min(cadence.lastNavigationAt(), now),
            boundedLoaded(cadence.nextMoonSampleAt(), now),
            cadence.retryAfter() > maximumLoadedFuture(now) ? 0 : cadence.routeFailures(),
            cadence.retryAfter() > maximumLoadedFuture(now) ? 0L : cadence.retryAfter()
        );
        return copy(
            reconciledNeeds, reconciledRefuge,
            LycanPackRules.pruneRelations(relationships, now),
            trails.stream().filter(trail -> trail.expiresAt() > now).toList(),
            reconciledHunt, reconciledCohort, reconciledAction, reconciledCadence
        );
    }

    public LycanPackState afterDimensionChange(final long now) {
        return copy(
            needs, Refuge.none(), relationships, List.of(), Hunt.none(),
            new Cohort(cohort.familiarity(), Optional.empty(), List.of(), 0L, 0L,
                Math.min(cohort.lastWarnAt(), now)),
            ActionState.rest(),
            new Cadence(now, now, now, now, now, now, 0, 0L)
        );
    }

    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
        tag.putString("Variant", variant.name());
        tag.putInt("Hunger", needs.hunger());
        tag.putInt("Fear", needs.fear());
        tag.putLong("LastNeedUpdateAt", needs.lastNeedUpdateAt());
        tag.putLong("LastSightFearAt", needs.lastSightFearAt());
        tag.putLong("ForageCooldownUntil", needs.forageCooldownUntil());
        refuge.position().ifPresent(position -> tag.putLong("Refuge", position.asLong()));
        tag.putLong("RefugeExpiresAt", refuge.expiresAt());
        tag.putLong("NextRefugeSearchAt", refuge.nextSearchAt());
        tag.putLong("RefugeDefenseExpiresAt", refuge.defenseExpiresAt());
        tag.putInt("RelationCount", relationships.size());
        for (int index = 0; index < relationships.size(); index++) {
            final PlayerRelation relation = relationships.get(index);
            final CompoundTag row = new CompoundTag();
            row.putString("Player", relation.playerId().toString());
            row.putString("Standing", relation.relation().name());
            row.putInt("Confidence", relation.confidence());
            row.putLong("ObservedAt", relation.observedAt());
            row.putLong("ExpiresAt", relation.expiresAt());
            tag.put("Relation" + index, row);
        }
        tag.putInt("TrailCount", trails.size());
        for (int index = 0; index < trails.size(); index++) {
            final TrailFact trail = trails.get(index);
            final CompoundTag row = new CompoundTag();
            trail.sourceId().ifPresent(id -> row.putString("Source", id.toString()));
            row.putString("Class", trail.trailClass().name());
            row.putLong("Position", trail.position().asLong());
            row.putInt("Confidence", trail.confidence());
            row.putLong("ObservedAt", trail.observedAt());
            row.putLong("ExpiresAt", trail.expiresAt());
            tag.put("Trail" + index, row);
        }
        hunt.episodeId().ifPresent(id -> tag.putString("HuntEpisode", id.toString()));
        hunt.coordinatorId().ifPresent(id -> tag.putString("HuntCoordinator", id.toString()));
        tag.putInt("HuntMemberCount", hunt.memberIds().size());
        for (int index = 0; index < hunt.memberIds().size(); index++) {
            tag.putString("HuntMember" + index, hunt.memberIds().get(index).toString());
        }
        hunt.role().ifPresent(role -> tag.putString("HuntRole", role.name()));
        hunt.phase().ifPresent(phase -> tag.putString("HuntStage", phase.name()));
        hunt.targetId().ifPresent(id -> tag.putString("HuntTarget", id.toString()));
        hunt.targetPosition().ifPresent(position -> tag.putLong("HuntTargetPosition", position.asLong()));
        tag.putLong("HuntEpisodeExpiresAt", hunt.episodeExpiresAt());
        tag.putLong("HuntStageExpiresAt", hunt.phaseExpiresAt());
        tag.putInt("HuntTargetChanges", hunt.targetChanges());
        hunt.returnIntent().ifPresent(position -> tag.putLong("HuntReturnIntent", position.asLong()));
        tag.putInt("FamiliarityCount", cohort.familiarity().size());
        for (int index = 0; index < cohort.familiarity().size(); index++) {
            final Familiarity entry = cohort.familiarity().get(index);
            final CompoundTag row = new CompoundTag();
            row.putString("Other", entry.otherId().toString());
            row.putInt("Points", entry.points());
            row.putLong("LastObservedAt", entry.lastObservedAt());
            tag.put("Familiarity" + index, row);
        }
        cohort.cohortId().ifPresent(id -> tag.putString("CohortId", id.toString()));
        tag.putInt("BondedCount", cohort.bondedIds().size());
        for (int index = 0; index < cohort.bondedIds().size(); index++) {
            tag.putString("Bonded" + index, cohort.bondedIds().get(index).toString());
        }
        tag.putLong("CohortExpiresAt", cohort.cohortExpiresAt());
        tag.putLong("WarningExpiresAt", cohort.warningExpiresAt());
        tag.putLong("LastWarnAt", cohort.lastWarnAt());
        tag.putString("ActionKind", action.kind().name());
        tag.putLong("WindupUntil", action.windupUntil());
        tag.putLong("ExecutionUntil", action.executionUntil());
        tag.putLong("RecoveryUntil", action.recoveryUntil());
        action.claimPurpose().ifPresent(value -> tag.putString("ClaimPurpose", value));
        action.claimKey().ifPresent(value -> tag.putString("ClaimKey", value));
        tag.putLong("ClaimExpiresAt", action.claimExpiresAt());
        action.lastCancellation().ifPresent(value -> tag.putString("LastCancellation", value));
        tag.putLong("NextDecisionAt", cadence.nextDecisionAt());
        tag.putLong("NextPerceptionAt", cadence.nextPerceptionAt());
        tag.putLong("NextPlanAt", cadence.nextPlanAt());
        tag.putLong("NextFeedbackAt", cadence.nextFeedbackAt());
        tag.putLong("LastNavigationAt", cadence.lastNavigationAt());
        tag.putLong("NextMoonSampleAt", cadence.nextMoonSampleAt());
        tag.putInt("RouteFailures", cadence.routeFailures());
        tag.putLong("RetryAfter", cadence.retryAfter());
        return tag;
    }

    public static LycanPackState read(final CompoundTag tag, final Variant expectedVariant, final long now) {
        Objects.requireNonNull(tag, "tag");
        final LycanPackState fallback = empty(expectedVariant, now);
        if (tag.getIntOr("SchemaVersion", SCHEMA_VERSION) != SCHEMA_VERSION) return fallback;
        if (!tag.getStringOr("Variant", expectedVariant.name()).equals(expectedVariant.name())) return fallback;
        final List<PlayerRelation> relationships = new ArrayList<>();
        final int relationCount = Math.clamp(tag.getIntOr("RelationCount", 0), 0,
            LycanPackRules.MAX_RELATIONSHIP_ENTRIES);
        for (int index = 0; index < relationCount; index++) {
            tag.getCompound("Relation" + index).flatMap(LycanPackState::readRelation).ifPresent(relationships::add);
        }
        final List<TrailFact> trails = new ArrayList<>();
        final int trailCount = Math.clamp(tag.getIntOr("TrailCount", 0), 0, LycanPackRules.MAX_TRAIL_ENTRIES);
        for (int index = 0; index < trailCount; index++) {
            tag.getCompound("Trail" + index).flatMap(LycanPackState::readTrail).ifPresent(trails::add);
        }
        final List<UUID> members = new ArrayList<>();
        final int memberCount = Math.clamp(tag.getIntOr("HuntMemberCount", 0), 0, LycanPackRules.MAX_HUNT_MEMBERS);
        for (int index = 0; index < memberCount; index++) {
            optionalUuid(tag, "HuntMember" + index).ifPresent(members::add);
        }
        final Hunt hunt = new Hunt(
            optionalUuid(tag, "HuntEpisode"),
            optionalUuid(tag, "HuntCoordinator"),
            members,
            optionalEnum(tag, "HuntRole", HuntRole.class),
            optionalEnum(tag, "HuntStage", HuntPhase.class),
            optionalUuid(tag, "HuntTarget"),
            optionalPosition(tag, "HuntTargetPosition"),
            boundedStored(tag.getLongOr("HuntEpisodeExpiresAt", 0L), now),
            boundedStored(tag.getLongOr("HuntStageExpiresAt", 0L), now),
            tag.getIntOr("HuntTargetChanges", 0),
            optionalPosition(tag, "HuntReturnIntent")
        );
        final List<Familiarity> familiarity = new ArrayList<>();
        final int familiarityCount = Math.clamp(tag.getIntOr("FamiliarityCount", 0), 0,
            LycanPackRules.MAX_FAMILIARITY_ENTRIES);
        for (int index = 0; index < familiarityCount; index++) {
            tag.getCompound("Familiarity" + index).flatMap(LycanPackState::readFamiliarity)
                .ifPresent(familiarity::add);
        }
        final List<UUID> bonded = new ArrayList<>();
        final int bondedCount = Math.clamp(tag.getIntOr("BondedCount", 0), 0, LycanPackRules.MAX_COHORT_MEMBERS);
        for (int index = 0; index < bondedCount; index++) {
            optionalUuid(tag, "Bonded" + index).ifPresent(bonded::add);
        }
        final Cohort cohort = new Cohort(
            familiarity,
            optionalUuid(tag, "CohortId"),
            bonded,
            boundedStored(tag.getLongOr("CohortExpiresAt", 0L), now),
            boundedStored(tag.getLongOr("WarningExpiresAt", 0L), now),
            Math.max(0L, tag.getLongOr("LastWarnAt", 0L))
        );
        final ActionState action = new ActionState(
            optionalEnum(tag, "ActionKind", ActionKind.class).orElse(ActionKind.NONE),
            boundedStored(tag.getLongOr("WindupUntil", 0L), now),
            boundedStored(tag.getLongOr("ExecutionUntil", 0L), now),
            boundedStored(tag.getLongOr("RecoveryUntil", 0L), now),
            optionalString(tag, "ClaimPurpose"),
            optionalString(tag, "ClaimKey"),
            boundedStored(tag.getLongOr("ClaimExpiresAt", 0L), now),
            optionalString(tag, "LastCancellation")
        );
        final Cadence cadence = new Cadence(
            boundedStored(tag.getLongOr("NextDecisionAt", now), now),
            boundedStored(tag.getLongOr("NextPerceptionAt", now), now),
            boundedStored(tag.getLongOr("NextPlanAt", now), now),
            boundedStored(tag.getLongOr("NextFeedbackAt", now), now),
            Math.max(0L, Math.min(tag.getLongOr("LastNavigationAt", 0L), now)),
            boundedStored(tag.getLongOr("NextMoonSampleAt", now), now),
            tag.getIntOr("RouteFailures", 0),
            boundedStored(tag.getLongOr("RetryAfter", 0L), now)
        );
        final LycanPackState loaded = new LycanPackState(
            SCHEMA_VERSION, expectedVariant,
            new Needs(
                tag.getIntOr("Hunger", LycanPackRules.defaultHunger(expectedVariant)),
                tag.getIntOr("Fear", LycanPackRules.DEFAULT_FEAR),
                Math.max(0L, Math.min(tag.getLongOr("LastNeedUpdateAt", now), now)),
                Math.max(0L, Math.min(tag.getLongOr("LastSightFearAt", 0L), now)),
                boundedStored(tag.getLongOr("ForageCooldownUntil", 0L), now)
            ),
            new Refuge(
                optionalPosition(tag, "Refuge"),
                boundedStored(tag.getLongOr("RefugeExpiresAt", 0L), now),
                boundedStored(tag.getLongOr("NextRefugeSearchAt", 0L), now),
                boundedStored(tag.getLongOr("RefugeDefenseExpiresAt", 0L), now)
            ),
            relationships, trails, hunt, cohort, action, cadence
        );
        return loaded.reconcile(now);
    }

    private static Optional<PlayerRelation> readRelation(final CompoundTag tag) {
        try {
            return Optional.of(new PlayerRelation(
                UUID.fromString(tag.getStringOr("Player", "")),
                Relation.valueOf(tag.getStringOr("Standing", "").toUpperCase(Locale.ROOT)),
                tag.getIntOr("Confidence", 1),
                tag.getLongOr("ObservedAt", 0L),
                tag.getLongOr("ExpiresAt", 0L)
            ));
        } catch (final IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<TrailFact> readTrail(final CompoundTag tag) {
        try {
            return Optional.of(new TrailFact(
                optionalUuid(tag, "Source"),
                TrailClass.valueOf(tag.getStringOr("Class", "").toUpperCase(Locale.ROOT)),
                BlockPos.of(tag.getLongOr("Position", 0L)),
                tag.getIntOr("Confidence", 0),
                tag.getLongOr("ObservedAt", 0L),
                tag.getLongOr("ExpiresAt", 0L)
            ));
        } catch (final IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Familiarity> readFamiliarity(final CompoundTag tag) {
        try {
            return Optional.of(new Familiarity(
                UUID.fromString(tag.getStringOr("Other", "")),
                tag.getIntOr("Points", 0),
                tag.getLongOr("LastObservedAt", 0L)
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

    private static <T extends Enum<T>> Optional<T> optionalEnum(
        final CompoundTag tag,
        final String key,
        final Class<T> type
    ) {
        try {
            return optionalString(tag, key).map(value -> Enum.valueOf(type, value.toUpperCase(Locale.ROOT)));
        } catch (final IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<BlockPos> optionalPosition(final CompoundTag tag, final String key) {
        return tag.contains(key) ? Optional.of(BlockPos.of(tag.getLongOr(key, 0L))) : Optional.empty();
    }

    private static long maximumLoadedFuture(final long now) {
        return LycanPackRules.saturatingAdd(now, MAX_LOADED_FUTURE_TICKS);
    }

    private static long boundedStored(final long value, final long now) {
        if (value <= 0L) return 0L;
        return value > maximumLoadedFuture(now) ? now : value;
    }

    private static long boundedLoaded(final long value, final long now) {
        if (value < now || value > maximumLoadedFuture(now)) return now;
        return value;
    }

    private static long boundedFuture(final long value, final long now) {
        return value > maximumLoadedFuture(now) ? now : Math.max(0L, value);
    }

    private LycanPackState copy(
        final Needs newNeeds,
        final Refuge newRefuge,
        final List<PlayerRelation> newRelationships,
        final List<TrailFact> newTrails,
        final Hunt newHunt,
        final Cohort newCohort,
        final ActionState newAction,
        final Cadence newCadence
    ) {
        return new LycanPackState(
            SCHEMA_VERSION, variant, newNeeds, newRefuge, newRelationships, newTrails,
            newHunt, newCohort, newAction, newCadence
        );
    }
}
