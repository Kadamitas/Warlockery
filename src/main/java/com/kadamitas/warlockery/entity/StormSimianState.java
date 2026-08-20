package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.behavior.Cadence;
import com.kadamitas.warlockery.entity.behavior.RouteRequest;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Versioned Storm Simian semantics: the visible storm charge, the one semantic grip, the three
 * cooldowns, the shared route ledger and the completed observation epoch.
 *
 * <p>Every duration here is a remaining loaded tick count, never an absolute world deadline, so an
 * unloaded simian pauses meaning instead of expiring it and no reload can hand it a catch up. No
 * path, live entity reference, candidate list, alarm recipient or open action window is stored: all
 * of those are transient by design and live in {@link StormSimianRuntime.TransientState}, which is
 * cleared on load.</p>
 */
public record StormSimianState(
    int schemaVersion,
    int charge,
    Optional<BlockPos> grip,
    int gripHoldTicks,
    Cooldowns cooldowns,
    Route route,
    long observationEpoch
) {
    public static final int SCHEMA_VERSION = 1;

    /**
     * Range clamping, plus exactly one coupled invariant.
     *
     * <p>The invariant is the <em>identity</em> shape: a hold countdown is time spent on a grip, so
     * with no grip there is nothing to be holding and the two fields cannot be allowed to disagree.
     * That is a property of the type, not a decision that something ended.</p>
     *
     * <p>It is deliberately not the <em>timer</em> shape. There is no
     * {@code if (gripHoldTicks == 0) grip = empty} here, and there must not be: a hold reaching zero
     * means the canopy branch may look for a better position, and that branch is what re releases
     * the grip, re seeds the scan cursor and arms the route cadence. A constructor that dropped the
     * grip the moment the hold ran out would destroy the pair the branch tests for, so the branch
     * would never run, the cadence would never be armed, and the sweep would repeat every tick.</p>
     */
    public StormSimianState {
        charge = Math.clamp(charge, 0, StormSimianRules.MAX_CHARGE);
        grip = grip == null ? Optional.empty() : grip;
        gripHoldTicks = grip.isEmpty()
            ? 0
            : StormSimianRules.clampRemaining(gripHoldTicks, StormSimianRules.GRIP_HOLD_TICKS);
        cooldowns = cooldowns == null ? Cooldowns.none() : cooldowns;
        route = route == null ? Route.fresh() : route;
        observationEpoch = Math.max(0L, observationEpoch);
    }

    /** The three cooldowns armed after work runs, whatever that work found. */
    public record Cooldowns(int alarmTicks, int curiosityTicks, int observationTicks) {
        public Cooldowns {
            alarmTicks = StormSimianRules.clampRemaining(
                alarmTicks, StormSimianRules.ALARM_COOLDOWN_TICKS);
            curiosityTicks = StormSimianRules.clampRemaining(
                curiosityTicks, StormSimianRules.CURIOSITY_COOLDOWN_TICKS);
            observationTicks = StormSimianRules.clampRemaining(
                observationTicks, StormSimianRules.OBSERVATION_COOLDOWN_TICKS);
        }

        public static Cooldowns none() {
            return new Cooldowns(0, 0, 0);
        }

        public Cooldowns step() {
            return new Cooldowns(
                StormSimianRules.decrementLoaded(alarmTicks),
                StormSimianRules.decrementLoaded(curiosityTicks),
                StormSimianRules.decrementLoaded(observationTicks)
            );
        }
    }

    /**
     * The one navigation ledger, shared by both movement writers so the twenty tick cadence and the
     * three failure backoff bind across the canopy reposition and the curiosity approach together
     * rather than once each.
     */
    public record Route(int sinceLastRequest, int consecutiveFailures, int backoffTicks) {
        public Route {
            sinceLastRequest = Math.clamp(sinceLastRequest, 0, StormSimianRules.ROUTE_PERIOD_TICKS);
            consecutiveFailures = Math.clamp(
                consecutiveFailures, 0, StormSimianRules.ROUTE_FAILURES_BEFORE_BACKOFF);
            backoffTicks = StormSimianRules.clampRemaining(
                backoffTicks, StormSimianRules.ROUTE_BACKOFF_MAX_TICKS);
        }

        /** A ledger that may request at once, as a freshly spawned simian should. */
        public static Route fresh() {
            return new Route(StormSimianRules.ROUTE_PERIOD_TICKS, 0, 0);
        }

        public RouteRequest request() {
            return new RouteRequest(
                new Cadence(StormSimianRules.ROUTE_PERIOD_TICKS, sinceLastRequest),
                consecutiveFailures,
                backoffTicks
            );
        }

        public static Route of(final RouteRequest request) {
            return new Route(
                request.cadence().sinceLast(),
                request.consecutiveFailures(),
                request.backoffRemaining()
            );
        }
    }

    public static StormSimianState empty() {
        return new StormSimianState(
            SCHEMA_VERSION, 0, Optional.empty(), 0, Cooldowns.none(), Route.fresh(), 0L);
    }

    public StormSimianState withCharge(final int updated) {
        return new StormSimianState(
            schemaVersion, updated, grip, gripHoldTicks, cooldowns, route, observationEpoch);
    }

    /** Takes a grip and starts its hold. The two always move together, by construction. */
    public StormSimianState withGrip(final BlockPos position) {
        return new StormSimianState(schemaVersion, charge, Optional.of(position),
            StormSimianRules.GRIP_HOLD_TICKS, cooldowns, route, observationEpoch);
    }

    /** Releases the grip. The coupled hold is zeroed by the canonical constructor. */
    public StormSimianState withoutGrip() {
        return new StormSimianState(
            schemaVersion, charge, Optional.empty(), 0, cooldowns, route, observationEpoch);
    }

    public StormSimianState withGripHold(final int updated) {
        return new StormSimianState(
            schemaVersion, charge, grip, updated, cooldowns, route, observationEpoch);
    }

    public StormSimianState withCooldowns(final Cooldowns updated) {
        return new StormSimianState(
            schemaVersion, charge, grip, gripHoldTicks, updated, route, observationEpoch);
    }

    public StormSimianState withRoute(final Route updated) {
        return new StormSimianState(
            schemaVersion, charge, grip, gripHoldTicks, cooldowns, updated, observationEpoch);
    }

    public StormSimianState withRouteRequest(final RouteRequest updated) {
        return withRoute(Route.of(updated));
    }

    /** Records one completed observation epoch. Only a completed window may call this. */
    public StormSimianState withCompletedObservation(final int updatedCharge) {
        return new StormSimianState(schemaVersion, updatedCharge, grip, gripHoldTicks,
            new Cooldowns(cooldowns.alarmTicks(), cooldowns.curiosityTicks(),
                StormSimianRules.OBSERVATION_COOLDOWN_TICKS),
            route, observationEpoch + 1L);
    }

    /** One loaded tick of every countdown this record owns. */
    public StormSimianState step() {
        final RouteRequest stepped = route.request().step();
        return new StormSimianState(schemaVersion, charge, grip,
            StormSimianRules.decrementLoaded(gripHoldTicks), cooldowns.step(),
            Route.of(stepped), observationEpoch);
    }

    /**
     * Starts a fresh routine stretch after the arbiter was away on something more urgent.
     *
     * <p>The route failure run is cleared, because those failures were accrued by the previous
     * stretch and a ledger inherited across the boundary makes the next stretch give up before it
     * has tried anything. The open backoff window is deliberately preserved: it describes the
     * neighbourhood, not the stretch, and clearing it would let an unusable spot be hammered again
     * the moment anything interrupted the simian.</p>
     */
    public StormSimianState startRoutineStretch() {
        if (route.consecutiveFailures() == 0) {
            return this;
        }
        return withRoute(new Route(route.sinceLastRequest(), 0, route.backoffTicks()));
    }

    /** Compact fixed cardinality encoding, well inside {@link StormSimianRules#MAX_STATE_BYTES}. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", schemaVersion);
        tag.putInt("Charge", charge);
        tag.putBoolean("HasGrip", grip.isPresent());
        tag.putLong("Grip", grip.map(BlockPos::asLong).orElse(0L));
        tag.putInt("GripHold", gripHoldTicks);
        tag.putInt("AlarmCooldown", cooldowns.alarmTicks());
        tag.putInt("CuriosityCooldown", cooldowns.curiosityTicks());
        tag.putInt("ObservationCooldown", cooldowns.observationTicks());
        tag.putInt("RouteSince", route.sinceLastRequest());
        tag.putInt("RouteFail", route.consecutiveFailures());
        tag.putInt("RouteBackoff", route.backoffTicks());
        tag.putLong("Epoch", observationEpoch);
        return tag;
    }

    /**
     * Reads version one. A missing or unknown schema resets to a safe empty state. Every countdown
     * is clamped without consulting elapsed world time, so a long unload can neither expire a
     * cooldown nor grant a charge. The completed epoch count survives precisely so a reload cannot
     * present itself as a fresh observation.
     */
    public static StormSimianState read(final CompoundTag tag) {
        if (tag == null || tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return empty();
        }
        final Optional<BlockPos> grip = tag.getBooleanOr("HasGrip", false)
            ? Optional.of(BlockPos.of(tag.getLongOr("Grip", 0L)))
            : Optional.empty();
        return new StormSimianState(
            SCHEMA_VERSION,
            tag.getIntOr("Charge", 0),
            grip,
            tag.getIntOr("GripHold", 0),
            new Cooldowns(
                tag.getIntOr("AlarmCooldown", 0),
                tag.getIntOr("CuriosityCooldown", 0),
                tag.getIntOr("ObservationCooldown", 0)
            ),
            new Route(
                tag.getIntOr("RouteSince", StormSimianRules.ROUTE_PERIOD_TICKS),
                tag.getIntOr("RouteFail", 0),
                tag.getIntOr("RouteBackoff", 0)
            ),
            tag.getLongOr("Epoch", 0L)
        );
    }
}
