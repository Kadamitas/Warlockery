package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.behavior.Ticks;
import net.minecraft.nbt.CompoundTag;

/**
 * The whole durable F31 record: a schema version and three bounded integers, all counted in
 * remaining loaded ticks or in ladder steps, never in absolute world time.
 *
 * <p>Nothing else persists. No state name, no host, no candidate, no attacker, no owner copy, no
 * payload copy, no released-host grace, no residence remainder, no path, no destination, no route
 * ledger, no timestamp, no dimension key and no collection. The residence term is deliberately not
 * persisted, because persisting it would require persisting a host identity and a dimension guard
 * and would let a reload restore a tenancy against a host that is no longer there. The consequence
 * is stated plainly rather than hidden: a louse that unloads while attached wakes up free, and the
 * unload-reload loop that would otherwise restart its term forever is closed by
 * {@link ParasyticLouseTenancyRules#seekCooldownOnLoad}.</p>
 *
 * <h2>On the canonical constructor</h2>
 *
 * <p>Every field below is clamped <em>independently</em>. There is deliberately no cross-field
 * reconciliation of the timer shape here: no branch of the form "if this countdown reached zero,
 * zero its dependants too". Deciding that a countdown has ended is a tick branch's job, and a
 * constructor that made that decision would destroy the pair the branch tests for, so the branch
 * would never run and never arm the cooldown, backoff or counter its ending implies. The one
 * reconciliation this family does write is the identity shape, and it lives on the transient host
 * holder in {@link ParasyticLouseRuntime.Tenancy}, where a half-written host really is a
 * contradiction the type should refuse to represent.</p>
 *
 * @param schemaVersion the record version, always {@link #SCHEMA_VERSION} once read
 * @param nourishment the ladder position, inclusive 0 to 4
 * @param decayRemainingTicks loaded ticks until the next decay step, inclusive 0 to 399
 * @param seekCooldownRemainingTicks loaded ticks until a tenancy may begin, inclusive 0 to 600
 */
public record ParasyticLouseState(
    int schemaVersion,
    int nourishment,
    int decayRemainingTicks,
    int seekCooldownRemainingTicks
) {
    public static final int SCHEMA_VERSION = 1;
    /** The remainder is a 0-to-399 position inside the 400-tick decay window, so 399 is its top. */
    public static final int MAX_DECAY_REMAINDER = ParasyticLouseTenancyRules.NOURISHMENT_DECAY_TICKS - 1;

    public ParasyticLouseState {
        nourishment = Math.clamp(nourishment, 0, ParasyticLouseTenancyRules.MAX_NOURISHMENT);
        decayRemainingTicks = Ticks.clampRemaining(decayRemainingTicks, MAX_DECAY_REMAINDER);
        seekCooldownRemainingTicks = Ticks.clampRemaining(
            seekCooldownRemainingTicks, ParasyticLouseTenancyRules.SEEK_COOLDOWN_TICKS
        );
    }

    public static ParasyticLouseState empty() {
        return new ParasyticLouseState(SCHEMA_VERSION, 0, MAX_DECAY_REMAINDER, 0);
    }

    public ParasyticLouseState withNourishment(final int updated) {
        return new ParasyticLouseState(
            schemaVersion, updated, decayRemainingTicks, seekCooldownRemainingTicks
        );
    }

    public ParasyticLouseState withDecayRemaining(final int updated) {
        return new ParasyticLouseState(schemaVersion, nourishment, updated, seekCooldownRemainingTicks);
    }

    public ParasyticLouseState withSeekCooldown(final int updated) {
        return new ParasyticLouseState(schemaVersion, nourishment, decayRemainingTicks, updated);
    }

    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", schemaVersion);
        tag.putInt("Nourishment", nourishment);
        tag.putInt("DecayRemaining", decayRemainingTicks);
        tag.putInt("SeekCooldown", seekCooldownRemainingTicks);
        return tag;
    }

    /**
     * Reads version 1. A missing, malformed or unknown-schema record resets only the F31 semantic
     * values to a safe free default and never touches ordinary Mob state. Every field defaults and
     * clamps independently, so one corrupt value cannot discard a neighbouring good one. Per the
     * remaining-tick discipline a value at or below zero means due rather than "fired at world tick
     * zero", and per the bounded-sentinel discipline a value beyond its declared range is treated as
     * corrupt and pulled back rather than parked in a far future that entity-section scan order
     * would then decide.
     *
     * <p>The load cooldown floor is applied here rather than in the runtime so that every load path,
     * including a fixture that constructs the record directly from a saved tag, gets it.</p>
     */
    public static ParasyticLouseState read(final CompoundTag tag) {
        if (tag == null || tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return empty().withSeekCooldown(
                ParasyticLouseTenancyRules.seekCooldownOnLoad(0)
            );
        }
        return new ParasyticLouseState(
            SCHEMA_VERSION,
            tag.getIntOr("Nourishment", 0),
            tag.getIntOr("DecayRemaining", MAX_DECAY_REMAINDER),
            ParasyticLouseTenancyRules.seekCooldownOnLoad(tag.getIntOr("SeekCooldown", 0))
        );
    }
}
