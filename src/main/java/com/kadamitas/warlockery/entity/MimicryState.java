package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.MimicryRules.Species;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/**
 * The one durable shape for all four mimics, guarded by species so a wrong-species payload can
 * never be adopted.
 *
 * <p>It is fixed cardinality and holds two bounded loaded-tick counters and nothing else. It holds
 * no phase, no bound subject, no observer, no attacker, no UUID, no dimension key, no position, no
 * station point, no recognition scalar, no accepted-hit count, no route failure count, no path, no
 * timestamp and no absolute game time. A copy that outlives its origin is the classic defect of
 * this genre, and it is closed here by writing nothing about an origin to disk at all: on load the
 * phase normalises to the species routine phase and every transient field clears, so a reload
 * replays no telegraph, no snare, no unmask and no withdrawal.</p>
 *
 * <h2>Reconcile classification</h2>
 *
 * <p>The compact constructor performs exactly two reconciles, and both are deliberate.</p>
 *
 * <ol>
 *   <li><b>Clamping</b> of both counters into their declared per-species ranges. This is a range
 *       repair, not a phase decision.</li>
 *   <li><b>The identity shape.</b> {@code species} is the identity of this record. There is no
 *       arm anywhere in this type of the form "if a timer reached zero, zero the fields that
 *       depended on it", because that is the defect where a constructor decides a phase ended and
 *       the tick branch that owned ending it never runs and never arms its cooldown. A zero
 *       counter here means <em>due</em> and nothing else; the tick branch alone ends phases.</li>
 * </ol>
 */
public record MimicryState(int schemaVersion, Species species, int primaryCooldown, int episodeCooldown) {

    /** Loaded ticks after a released, non-committal episode before another may be attempted. */
    public static final int EPISODE_COOLDOWN_TICKS = 200;

    private static final String VERSION_KEY = "SchemaVersion";
    private static final String SPECIES_KEY = "Species";
    private static final String PRIMARY_KEY = "PrimaryCooldownRemaining";
    private static final String EPISODE_KEY = "EpisodeCooldownRemaining";

    public MimicryState {
        species = Objects.requireNonNull(species, "species");
        schemaVersion = MimicryRules.STATE_SCHEMA_VERSION;
        primaryCooldown = MimicryRules.clampRemaining(primaryCooldown, species.primaryCooldownTicks());
        episodeCooldown = MimicryRules.clampRemaining(episodeCooldown, EPISODE_COOLDOWN_TICKS);
    }

    public static MimicryState empty(final Species species) {
        return new MimicryState(MimicryRules.STATE_SCHEMA_VERSION, species, 0, 0);
    }

    /** DC1. Zero reads as ready, never as "fired at world tick 0". */
    public boolean episodeAllowed() {
        return MimicryRules.due(primaryCooldown) && MimicryRules.due(episodeCooldown);
    }

    public MimicryState withPrimaryCooldown(final int remaining) {
        return new MimicryState(schemaVersion, species, remaining, episodeCooldown);
    }

    public MimicryState withEpisodeCooldown(final int remaining) {
        return new MimicryState(schemaVersion, species, primaryCooldown, remaining);
    }

    /** One loaded tick of decay. Unloaded time performs no decrement and no catch-up. */
    public MimicryState tickLoaded() {
        return new MimicryState(
            schemaVersion,
            species,
            MimicryRules.decrementLoaded(primaryCooldown),
            MimicryRules.decrementLoaded(episodeCooldown)
        );
    }

    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_KEY, schemaVersion);
        tag.putString(SPECIES_KEY, species.name());
        tag.putInt(PRIMARY_KEY, primaryCooldown);
        tag.putInt(EPISODE_KEY, episodeCooldown);
        return tag;
    }

    /**
     * Every field defaults independently, so a missing, malformed, negative, overflowing or
     * unknown-schema payload yields a safe bounded routine default rather than a partial adoption.
     * A payload written by a different species is discarded whole.
     */
    public static MimicryState read(final CompoundTag tag, final Species expected) {
        Objects.requireNonNull(expected, "expected");
        final MimicryState fallback = empty(expected);
        if (tag == null) {
            return fallback;
        }
        if (tag.getIntOr(VERSION_KEY, 0) != MimicryRules.STATE_SCHEMA_VERSION) {
            return fallback;
        }
        if (!expected.name().equals(tag.getStringOr(SPECIES_KEY, ""))) {
            return fallback;
        }
        return new MimicryState(
            MimicryRules.STATE_SCHEMA_VERSION,
            expected,
            tag.getIntOr(PRIMARY_KEY, 0),
            tag.getIntOr(EPISODE_KEY, 0)
        );
    }
}


