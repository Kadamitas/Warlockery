package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.IronboundSentinelRules.Charge;
import com.kadamitas.warlockery.entity.behavior.Ticks;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * The whole durable meaning of an Ironbound Sentinel, version 1, stored beneath
 * {@code WarlockerySentinelState}.
 *
 * <p>Fixed cardinality by construction: one schema number, one charge, one transition counter, one
 * stationed flag, three station coordinates, one bearing and one strain scalar. There is no phase,
 * no subject, no attacker, no candidate set, no UUID, no dimension key, no path, no route counter,
 * no timestamp and no collection, because every one of those is either transient scratch the runtime
 * rebuilds after a load or a live reference that a save could resurrect into a delayed action.</p>
 *
 * <p>Both transitional charge arms carry their counter durably on purpose. A save taken during a
 * waking or a stand-down resumes that transition rather than losing it, and a counter that is
 * already zero on a transitional arm completes on the first loaded tick instead of stranding the
 * entity in a state with no exit.</p>
 */
public record IronboundSentinelState(
    int schemaVersion,
    Charge charge,
    int transitionRemaining,
    boolean stationed,
    int stationX,
    int stationY,
    int stationZ,
    int bearing,
    int strain
) {
    public static final int SCHEMA_VERSION = 1;
    static final String VERSION_KEY = "Version";
    static final String CHARGE_KEY = "Charge";
    static final String TRANSITION_KEY = "TransitionRemaining";
    static final String STATIONED_KEY = "Stationed";
    static final String STATION_X_KEY = "StationX";
    static final String STATION_Y_KEY = "StationY";
    static final String STATION_Z_KEY = "StationZ";
    static final String BEARING_KEY = "Bearing";
    static final String STRAIN_KEY = "Strain";

    /**
     * Independent range clamping plus exactly one coupled invariant, and it is deliberately the
     * <em>identity</em> shape rather than the timer shape.
     *
     * <p>The identity here is the pair {@code (charge, stationed)}. An unstationed record cannot
     * carry meaningful coordinates, because {@code (0, 0, 0)} is a legal block position and a zero
     * coordinate must never be readable as "no station"; and a settled charge cannot carry a
     * transition counter, because there is no transition for it to describe. Both are invariants the
     * type is the right place to enforce: no tick branch owns them and no behaviour is skipped by
     * enforcing them, so a caller cannot construct a record that says two contradictory things.</p>
     *
     * <p>What this constructor deliberately does <em>not</em> do is the timer shape,
     * {@code if (transitionRemaining <= 0) charge = chargeAfterTransition(charge)}. Deciding that a
     * waking or a stand-down has finished is a tick branch's job: that branch is what emits the one
     * bounded feedback event, resets strain on entering {@code INERT} and hands the phase back to
     * the ladder. A constructor that ended the transition the moment the counter hit zero would
     * destroy the {@code (transitional charge, zero counter)} pair the branch tests for, so the
     * branch would never run, the feedback would never fire and a stood-down Sentinel would keep its
     * accumulated strain forever.</p>
     */
    public IronboundSentinelState {
        Objects.requireNonNull(charge, "charge");
        schemaVersion = SCHEMA_VERSION;
        transitionRemaining = charge.transitional()
            ? Ticks.clampRemaining(transitionRemaining, IronboundSentinelRules.MAX_TRANSITION_TICKS)
            : 0;
        bearing = Math.floorMod(bearing, IronboundSentinelRules.BEARINGS);
        strain = IronboundSentinelRules.clampStrain(strain);
        if (!stationed) {
            stationX = 0;
            stationY = 0;
            stationZ = 0;
        }
    }

    /** A freshly made Sentinel: charged, unstationed until the runtime stations it, no strain. */
    public static IronboundSentinelState empty() {
        return new IronboundSentinelState(SCHEMA_VERSION, Charge.CHARGED, 0, false, 0, 0, 0, 0, 0);
    }

    public Optional<BlockPos> station() {
        return stationed ? Optional.of(new BlockPos(stationX, stationY, stationZ)) : Optional.empty();
    }

    public IronboundSentinelState stationedAt(final BlockPos position) {
        return new IronboundSentinelState(schemaVersion, charge, transitionRemaining, true,
            position.getX(), position.getY(), position.getZ(), bearing, strain);
    }

    /**
     * Enters a charge arm and loads the transition counter that arm declares. The settled arms load
     * zero, which the canonical constructor enforces anyway. Strain is cleared on the two arms the
     * design clears it on, entering {@code INERT} and seating a fresh charge through {@code WAKING},
     * and is carried unchanged everywhere else.
     */
    public IronboundSentinelState withCharge(final Charge updated) {
        return new IronboundSentinelState(schemaVersion, updated,
            IronboundSentinelRules.transitionTicksFor(updated), stationed,
            stationX, stationY, stationZ, bearing,
            updated == Charge.INERT || updated == Charge.WAKING ? 0 : strain);
    }

    /** Advances a transitional counter without ever deciding that the transition has finished. */
    public IronboundSentinelState withTransitionRemaining(final int updated) {
        return new IronboundSentinelState(schemaVersion, charge, updated, stationed,
            stationX, stationY, stationZ, bearing, strain);
    }

    public IronboundSentinelState withBearing(final int updated) {
        return new IronboundSentinelState(schemaVersion, charge, transitionRemaining, stationed,
            stationX, stationY, stationZ, updated, strain);
    }

    public IronboundSentinelState withStrain(final int updated) {
        return new IronboundSentinelState(schemaVersion, charge, transitionRemaining, stationed,
            stationX, stationY, stationZ, bearing, updated);
    }

    /** Compact fixed-cardinality encoding, well inside the declared representative byte target. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_KEY, schemaVersion);
        tag.putString(CHARGE_KEY, charge.name().toLowerCase(Locale.ROOT));
        tag.putInt(TRANSITION_KEY, transitionRemaining);
        tag.putBoolean(STATIONED_KEY, stationed);
        tag.putInt(STATION_X_KEY, stationX);
        tag.putInt(STATION_Y_KEY, stationY);
        tag.putInt(STATION_Z_KEY, stationZ);
        tag.putInt(BEARING_KEY, bearing);
        tag.putInt(STRAIN_KEY, strain);
        return tag;
    }

    /**
     * Reads version 1. An absent, malformed or future schema discards only F36 semantics and defaults
     * to a charged, unstationed, unstrained Sentinel; the caller's ordinary Mob state is untouched.
     * Every field defaults and clamps independently, so one corrupt value cannot poison another, and
     * nothing here consults elapsed world time, so a reload can neither replay nor skip a window.
     */
    public static IronboundSentinelState read(final CompoundTag tag) {
        if (tag == null || tag.getIntOr(VERSION_KEY, 0) != SCHEMA_VERSION) {
            return empty();
        }
        final Charge charge = parseCharge(tag.getStringOr(CHARGE_KEY, ""));
        return new IronboundSentinelState(
            SCHEMA_VERSION,
            charge,
            tag.getIntOr(TRANSITION_KEY, 0),
            tag.getBooleanOr(STATIONED_KEY, false),
            tag.getIntOr(STATION_X_KEY, 0),
            tag.getIntOr(STATION_Y_KEY, 0),
            tag.getIntOr(STATION_Z_KEY, 0),
            tag.getIntOr(BEARING_KEY, 0),
            tag.getIntOr(STRAIN_KEY, 0)
        );
    }

    private static Charge parseCharge(final String value) {
        for (final Charge candidate : Charge.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        return Charge.CHARGED;
    }
}
