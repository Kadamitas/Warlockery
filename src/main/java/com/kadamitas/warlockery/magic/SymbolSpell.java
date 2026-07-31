package com.kadamitas.warlockery.magic;

import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public enum SymbolSpell implements StringIdentified {
    SENTINEL_WARD("sentinel_ward", targets(Target.BLOCK)),
    SOULFIRE_LANCE("soulfire_lance", targets(Target.ENTITY), "ingredient_brew_soul_anguish"),
    RAVENOUS_COMMUNION("ravenous_communion", targets(Target.SELF), "ingredient_brew_soul_hunger"),
    DREAD_SIGIL("dread_sigil", targets(Target.BLOCK, Target.ENTITY), "ingredient_brew_soul_fear"),
    ABYSSAL_BANISHMENT("abyssal_banishment", targets(Target.ENTITY), "ingredient_brew_soul_torment", true),
    GRASP_OF_AIR("grasp_of_air", targets(Target.SELF)),
    WELLSPRING("wellspring", targets(Target.BLOCK)),
    UNSEAL("unseal", targets(Target.BLOCK)),
    SEAL("seal", targets(Target.BLOCK)),
    BEWILDER("bewilder", targets(Target.ENTITY)),
    AGONY("agony", targets(Target.ENTITY), true),
    DELVE("delve", targets(Target.BLOCK)),
    AWAKEN("awaken", targets(Target.SELF, Target.ENTITY)),
    MEND_FLESH("mend_flesh", targets(Target.SELF, Target.ENTITY)),
    DISARM("disarm", targets(Target.ENTITY)),
    REPULSE("repulse", targets(Target.ENTITY)),
    HOBBLE("hobble", targets(Target.ENTITY)),
    DOMINATE("dominate", targets(Target.ENTITY), true),
    KINDLE("kindle", targets(Target.BLOCK)),
    WITCHLIGHT("witchlight", targets(Target.BLOCK)),
    CALM_SKIES("calm_skies", targets(Target.SELF)),
    SNUFF_LIGHT("snuff_light", targets(Target.SELF, Target.BLOCK)),
    STUN("stun", targets(Target.ENTITY));

    public static final java.util.List<SymbolSpell> VALUES = java.util.List.of(values());
    private static final EnumLookup<SymbolSpell> LOOKUP = EnumLookup.create("symbol spell", values());

    private final String id;
    private final Set<Target> targets;
    private final Optional<String> soulIngredient;
    private final boolean infernal;

    SymbolSpell(final String id, final Set<Target> targets) {
        this(id, targets, null, false);
    }

    SymbolSpell(final String id, final Set<Target> targets, final boolean infernal) {
        this(id, targets, null, infernal);
    }

    SymbolSpell(final String id, final Set<Target> targets, final String soulIngredient) {
        this(id, targets, soulIngredient, false);
    }

    SymbolSpell(final String id, final Set<Target> targets, final String soulIngredient, final boolean infernal) {
        this.id = id;
        this.targets = Set.copyOf(targets);
        this.soulIngredient = Optional.ofNullable(soulIngredient);
        this.infernal = infernal;
    }

    public String id() {
        return id;
    }

    public boolean supports(final Target target) {
        return targets.contains(target);
    }

    public Optional<String> soulIngredient() {
        return soulIngredient;
    }

    public boolean infernal() {
        return infernal;
    }

    public String translationKey() {
        return "message.warlockery.symbol.spell." + id;
    }

    public SymbolSpell next() {
        return VALUES.get((ordinal() + 1) % VALUES.size());
    }

    public static Optional<SymbolSpell> find(final String id) {
        return LOOKUP.find(id);
    }

    private static Set<Target> targets(final Target first, final Target... rest) {
        final EnumSet<Target> targets = EnumSet.of(first);
        targets.addAll(java.util.List.of(rest));
        return targets;
    }

    public enum Target {
        SELF,
        BLOCK,
        ENTITY
    }
}
