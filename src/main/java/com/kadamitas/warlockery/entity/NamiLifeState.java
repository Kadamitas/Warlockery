package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.NamiLifeRules.Activity;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public record NamiLifeState(
    int schemaVersion,
    Optional<String> homeDimension,
    Optional<BlockPos> home,
    Activity activity,
    long activityExpiresAt,
    Optional<BlockPos> activityBlock,
    Optional<UUID> activityEntity,
    Optional<UUID> welcomedVisitor,
    long visitorExpiresAt,
    long greetingReadyAt,
    Optional<UUID> recentAggressor,
    long aggressorExpiresAt,
    Optional<UUID> wardTarget,
    long wardChargeReadyAt,
    long wardCooldownUntil,
    int routeFailures,
    long retryAfter,
    long nextDecisionAt,
    long nextDiscoveryAt,
    long lastNavigationAt
) {
    public static final int SCHEMA_VERSION = 1;

    public NamiLifeState {
        homeDimension = Objects.requireNonNull(homeDimension, "homeDimension");
        home = Objects.requireNonNull(home, "home");
        activity = Objects.requireNonNull(activity, "activity");
        activityBlock = Objects.requireNonNull(activityBlock, "activityBlock");
        activityEntity = Objects.requireNonNull(activityEntity, "activityEntity");
        welcomedVisitor = Objects.requireNonNull(welcomedVisitor, "welcomedVisitor");
        recentAggressor = Objects.requireNonNull(recentAggressor, "recentAggressor");
        wardTarget = Objects.requireNonNull(wardTarget, "wardTarget");
        routeFailures = Math.clamp(routeFailures, 0, NamiLifeRules.MAX_ROUTE_FAILURES);
    }

    public static NamiLifeState empty() {
        return new NamiLifeState(
            SCHEMA_VERSION,
            Optional.empty(), Optional.empty(),
            Activity.IDLE, 0L, Optional.empty(), Optional.empty(),
            Optional.empty(), 0L, 0L,
            Optional.empty(), 0L,
            Optional.empty(), 0L, 0L,
            0, 0L, 0L, 0L, 0L
        );
    }

    public NamiLifeState withHome(final String dimension, final BlockPos position) {
        if (dimension == null || dimension.isBlank() || position == null) {
            return this;
        }
        return copy(Optional.of(dimension), Optional.of(position.immutable()), activity, activityExpiresAt,
            activityBlock, activityEntity, welcomedVisitor, visitorExpiresAt, greetingReadyAt,
            recentAggressor, aggressorExpiresAt, wardTarget, wardChargeReadyAt, wardCooldownUntil,
            routeFailures, retryAfter, nextDecisionAt, nextDiscoveryAt, lastNavigationAt);
    }

    public NamiLifeState begin(
        final Activity next,
        final long expiresAt,
        final Optional<BlockPos> block,
        final Optional<UUID> entity
    ) {
        return copy(homeDimension, home, next, expiresAt, block.map(BlockPos::immutable), entity,
            welcomedVisitor, visitorExpiresAt, greetingReadyAt, recentAggressor, aggressorExpiresAt,
            wardTarget, wardChargeReadyAt, wardCooldownUntil, routeFailures, retryAfter,
            nextDecisionAt, nextDiscoveryAt, lastNavigationAt);
    }

    public NamiLifeState rememberVisitor(final UUID visitor, final long expiresAt, final long readyAt) {
        return copy(homeDimension, home, activity, activityExpiresAt, activityBlock, activityEntity,
            Optional.of(visitor), expiresAt, readyAt, recentAggressor, aggressorExpiresAt,
            wardTarget, wardChargeReadyAt, wardCooldownUntil, routeFailures, retryAfter,
            nextDecisionAt, nextDiscoveryAt, lastNavigationAt);
    }

    public NamiLifeState rememberAggressor(final UUID aggressor, final long expiresAt) {
        return copy(homeDimension, home, activity, activityExpiresAt, activityBlock, activityEntity,
            welcomedVisitor, visitorExpiresAt, greetingReadyAt, Optional.of(aggressor), expiresAt,
            wardTarget, wardChargeReadyAt, wardCooldownUntil, routeFailures, retryAfter,
            nextDecisionAt, nextDiscoveryAt, lastNavigationAt);
    }

    public NamiLifeState chargeWard(final UUID target, final long readyAt, final long cooldownUntil) {
        return copy(homeDimension, home, activity, activityExpiresAt, activityBlock, activityEntity,
            welcomedVisitor, visitorExpiresAt, greetingReadyAt, recentAggressor, aggressorExpiresAt,
            Optional.of(target), readyAt, cooldownUntil, routeFailures, retryAfter,
            nextDecisionAt, nextDiscoveryAt, lastNavigationAt);
    }

    public NamiLifeState releaseWard() {
        return copy(homeDimension, home, activity, activityExpiresAt, activityBlock, activityEntity,
            welcomedVisitor, visitorExpiresAt, greetingReadyAt, recentAggressor, aggressorExpiresAt,
            Optional.empty(), 0L, wardCooldownUntil, routeFailures, retryAfter,
            nextDecisionAt, nextDiscoveryAt, lastNavigationAt);
    }

    public NamiLifeState withRouteFailure(final int failures, final long newRetryAfter) {
        return copy(homeDimension, home, activity, activityExpiresAt, activityBlock, activityEntity,
            welcomedVisitor, visitorExpiresAt, greetingReadyAt, recentAggressor, aggressorExpiresAt,
            wardTarget, wardChargeReadyAt, wardCooldownUntil, failures, newRetryAfter,
            nextDecisionAt, nextDiscoveryAt, lastNavigationAt);
    }

    public NamiLifeState withSchedule(
        final long decisionAt,
        final long discoveryAt,
        final long navigationAt
    ) {
        return copy(homeDimension, home, activity, activityExpiresAt, activityBlock, activityEntity,
            welcomedVisitor, visitorExpiresAt, greetingReadyAt, recentAggressor, aggressorExpiresAt,
            wardTarget, wardChargeReadyAt, wardCooldownUntil, routeFailures, retryAfter,
            decisionAt, discoveryAt, navigationAt);
    }

    public NamiLifeState reconcile(final long now) {
        final boolean activityExpired = activityExpiresAt > 0L && activityExpiresAt <= now;
        final boolean visitorExpired = visitorExpiresAt > 0L && visitorExpiresAt <= now;
        final boolean aggressorExpired = aggressorExpiresAt > 0L && aggressorExpiresAt <= now;
        final boolean wardExpired = wardCooldownUntil > 0L && wardCooldownUntil <= now;
        return copy(
            homeDimension,
            home,
            activityExpired ? Activity.IDLE : activity,
            activityExpired ? 0L : activityExpiresAt,
            activityExpired ? Optional.empty() : activityBlock,
            activityExpired ? Optional.empty() : activityEntity,
            visitorExpired ? Optional.empty() : welcomedVisitor,
            visitorExpired ? 0L : visitorExpiresAt,
            greetingReadyAt,
            aggressorExpired ? Optional.empty() : recentAggressor,
            aggressorExpired ? 0L : aggressorExpiresAt,
            wardExpired ? Optional.empty() : wardTarget,
            wardExpired ? 0L : wardChargeReadyAt,
            wardCooldownUntil,
            routeFailures,
            retryAfter,
            nextDecisionAt,
            nextDiscoveryAt,
            lastNavigationAt
        );
    }

    public NamiLifeState reconcileAfterLoad(final long now) {
        final NamiLifeState reconciled = reconcile(now);
        return reconciled.withSchedule(
            Math.max(now, reconciled.nextDecisionAt),
            Math.max(now, reconciled.nextDiscoveryAt),
            Math.max(now, reconciled.lastNavigationAt)
        );
    }

    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
        homeDimension.ifPresent(value -> tag.putString("HomeDimension", value));
        home.ifPresent(value -> tag.putLong("Home", value.asLong()));
        tag.putString("Activity", activity.name());
        tag.putLong("ActivityExpiresAt", activityExpiresAt);
        activityBlock.ifPresent(value -> tag.putLong("ActivityBlock", value.asLong()));
        activityEntity.ifPresent(value -> tag.putString("ActivityEntity", value.toString()));
        welcomedVisitor.ifPresent(value -> tag.putString("WelcomedVisitor", value.toString()));
        tag.putLong("VisitorExpiresAt", visitorExpiresAt);
        tag.putLong("GreetingReadyAt", greetingReadyAt);
        recentAggressor.ifPresent(value -> tag.putString("RecentAggressor", value.toString()));
        tag.putLong("AggressorExpiresAt", aggressorExpiresAt);
        wardTarget.ifPresent(value -> tag.putString("WardTarget", value.toString()));
        tag.putLong("WardChargeReadyAt", wardChargeReadyAt);
        tag.putLong("WardCooldownUntil", wardCooldownUntil);
        tag.putInt("RouteFailures", routeFailures);
        tag.putLong("RetryAfter", retryAfter);
        tag.putLong("NextDecisionAt", nextDecisionAt);
        tag.putLong("NextDiscoveryAt", nextDiscoveryAt);
        tag.putLong("LastNavigationAt", lastNavigationAt);
        return tag;
    }

    public static NamiLifeState read(final CompoundTag tag, final long now) {
        Objects.requireNonNull(tag, "tag");
        final Optional<String> dimension = optionalString(tag, "HomeDimension")
            .filter(value -> value.contains(":"));
        final Optional<BlockPos> home = tag.contains("Home")
            ? Optional.of(BlockPos.of(tag.getLongOr("Home", 0L)))
            : Optional.empty();
        NamiLifeState state = empty();
        if (dimension.isPresent() && home.isPresent()) {
            state = state.withHome(dimension.orElseThrow(), home.orElseThrow());
        }
        if (tag.getIntOr("SchemaVersion", SCHEMA_VERSION) != SCHEMA_VERSION) {
            return state.reconcileAfterLoad(now);
        }
        final Activity activity = activity(tag.getStringOr("Activity", "idle"));
        state = new NamiLifeState(
            SCHEMA_VERSION, state.homeDimension, state.home,
            activity, tag.getLongOr("ActivityExpiresAt", 0L),
            optionalBlock(tag, "ActivityBlock"), optionalUuid(tag, "ActivityEntity"),
            optionalUuid(tag, "WelcomedVisitor"), tag.getLongOr("VisitorExpiresAt", 0L),
            tag.getLongOr("GreetingReadyAt", 0L),
            optionalUuid(tag, "RecentAggressor"), tag.getLongOr("AggressorExpiresAt", 0L),
            optionalUuid(tag, "WardTarget"), tag.getLongOr("WardChargeReadyAt", 0L),
            tag.getLongOr("WardCooldownUntil", 0L), tag.getIntOr("RouteFailures", 0),
            tag.getLongOr("RetryAfter", 0L), tag.getLongOr("NextDecisionAt", 0L),
            tag.getLongOr("NextDiscoveryAt", 0L), tag.getLongOr("LastNavigationAt", 0L)
        );
        return state.reconcileAfterLoad(now);
    }

    private static Activity activity(final String value) {
        try {
            return Activity.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return Activity.IDLE;
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

    private NamiLifeState copy(
        final Optional<String> newHomeDimension,
        final Optional<BlockPos> newHome,
        final Activity newActivity,
        final long newActivityExpiresAt,
        final Optional<BlockPos> newActivityBlock,
        final Optional<UUID> newActivityEntity,
        final Optional<UUID> newWelcomedVisitor,
        final long newVisitorExpiresAt,
        final long newGreetingReadyAt,
        final Optional<UUID> newRecentAggressor,
        final long newAggressorExpiresAt,
        final Optional<UUID> newWardTarget,
        final long newWardChargeReadyAt,
        final long newWardCooldownUntil,
        final int newRouteFailures,
        final long newRetryAfter,
        final long newNextDecisionAt,
        final long newNextDiscoveryAt,
        final long newLastNavigationAt
    ) {
        return new NamiLifeState(
            SCHEMA_VERSION, newHomeDimension, newHome, newActivity, newActivityExpiresAt,
            newActivityBlock, newActivityEntity, newWelcomedVisitor, newVisitorExpiresAt,
            newGreetingReadyAt, newRecentAggressor, newAggressorExpiresAt, newWardTarget,
            newWardChargeReadyAt, newWardCooldownUntil, newRouteFailures, newRetryAfter,
            newNextDecisionAt, newNextDiscoveryAt, newLastNavigationAt
        );
    }
}
