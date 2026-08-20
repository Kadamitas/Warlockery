package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;

/**
 * The three bound animal familiars, as one closed identity set.
 *
 * <p>This enum is the only place that knows a Familiar Cat, an Owl and a Toad are the same
 * <em>kind of thing</em>. Everything they share -- ownership, binding, recall, companion
 * positioning, persistence, the priority ladder, the durable state shape -- lives once in
 * {@link AnimalFamiliarRules}, {@link AnimalFamiliarState} and {@link AnimalFamiliarRuntime} and is
 * selected by this discriminator. Everything that constitutes identity lives in the per-species
 * profile in {@code AnimalFamiliarRules} and in the three bodies.</p>
 *
 * <p>Deliberately not a superset: {@code CreatureKind.FAMILIAR} (the Spectral Familiar, F24) is not
 * a member. It is owner-bound and environmentally immune but it is a flying spirit with a sampled
 * block, no household, no prey and no classic damage sharing, and F24 owns it. F24 can reuse the
 * shared types by adding a fourth constant here only if it genuinely wants the household/prey
 * ladder; the more likely reuse is {@code AnimalFamiliarRules}' ownership, tether and target
 * legality section, which is written against plain values and needs no member of this enum.</p>
 *
 * <p>There is deliberately no {@code of(CreatureKind)} lookup and no {@code registryPath()}
 * accessor here. Both were written, both were genuinely useful looking, and neither had a single
 * production caller: exactly the shape that has shipped orphaned in this project before. F24 or a
 * later slice should add whichever it actually calls, at the moment it calls it.</p>
 */
public enum AnimalFamiliarSpecies {
    /** {@code warlockery:familiar_cat}. */
    CAT(CreatureKind.CAT),
    /** {@code warlockery:owl}. */
    OWL(CreatureKind.OWL),
    /** {@code warlockery:toad}. */
    TOAD(CreatureKind.TOAD);

    private final CreatureKind kind;

    AnimalFamiliarSpecies(final CreatureKind kind) {
        this.kind = kind;
    }

    public CreatureKind kind() {
        return kind;
    }

}
