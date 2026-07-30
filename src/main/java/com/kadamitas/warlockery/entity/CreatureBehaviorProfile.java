package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public record CreatureBehaviorProfile(
    String auditId,
    CreatureKind kind,
    AuditStatus auditStatus,
    Set<Feature> features,
    Optional<TagKey<Item>> offering,
    int pulseIntervalTicks
) {
    private static final List<CreatureBehaviorProfile> AUDITED = List.of(
        profile("baba_yaga", CreatureKind.HEDGE_CRONE, AuditStatus.MODERN_EQUIVALENT, null, 80,
            Feature.POTION_VOLLEY, Feature.THORN_RETALIATION),
        profile("banshee", CreatureKind.BANSHEE, AuditStatus.COMPLETE,
            CreatureBehaviorTags.Items.BANSHEE_EMPOWERMENT, 120,
            Feature.DUST_EMPOWERMENT, Feature.SCREECH, Feature.PHASED),
        profile("binky", CreatureKind.PALE_STEED, AuditStatus.MODERN_EQUIVALENT,
            CreatureBehaviorTags.Items.PALE_STEED_BONDING, 20,
            Feature.RIDEABLE_BOND, Feature.OWNER_AURA),
        profile("coven_witch", CreatureKind.CIRCLE_MAGE, AuditStatus.MODERN_EQUIVALENT,
            CreatureBehaviorTags.Items.COVEN_OFFERINGS, 20,
            Feature.COVEN_RECRUITMENT, Feature.OWNER_AURA, Feature.PROTECT_OWNER),
        profile("death", CreatureKind.DEATH, AuditStatus.COMPLETE, null, 20,
            Feature.DEATH_DISGUISE, Feature.SOUL_REAP, Feature.PHASED),
        profile("demon", CreatureKind.DEMON, AuditStatus.MODERN_EQUIVALENT,
            CreatureBehaviorTags.Items.DEMON_BARTER, 20,
            Feature.INFERNAL_BARTER, Feature.OWNER_AURA, Feature.PROTECT_OWNER, Feature.FIRE_MELEE),
        profile("ent", CreatureKind.ENT, AuditStatus.COMPLETE, null, 20,
            Feature.PROXIMITY_AGGRESSION, Feature.BIOME_VARIANTS),
        profile("familiar", CreatureKind.CAT, AuditStatus.MODERN_EQUIVALENT,
            CreatureBehaviorTags.Items.COMPANION_BINDERS, 20,
            Feature.FAMILIAR_BOND, Feature.OWNER_AURA, Feature.PROTECT_OWNER),
        profile("flame_imp", CreatureKind.IMP, AuditStatus.MODERN_EQUIVALENT,
            CreatureBehaviorTags.Items.INFERNAL_CONTRACTS, 20,
            Feature.FAMILIAR_BOND, Feature.OWNER_AURA, Feature.PROTECT_OWNER, Feature.FIRE_MELEE),
        profile("flying_monkey", CreatureKind.STORM_SIMIAN, AuditStatus.COMPLETE,
            CreatureBehaviorTags.Items.COMPANION_BINDERS, 20,
            Feature.FAMILIAR_BOND, Feature.WAYSTONE_TRAVEL, Feature.OWNER_AURA, Feature.PROTECT_OWNER),
        profile("gulg", CreatureKind.FORGEWARDEN, AuditStatus.MODERN_EQUIVALENT,
            CreatureBehaviorTags.Items.HEART_OFFERINGS, 40,
            Feature.HEART_EMPOWERMENT, Feature.FORGE_AURA, Feature.FIRE_MELEE),
        profile("hobgoblin", CreatureKind.HOBGOBLIN, AuditStatus.COMPLETE,
            CreatureBehaviorTags.Items.HOBGOBLIN_CONTRACTS, 20,
            Feature.KOBOLD_WORKER, Feature.PROFESSION_TRADING, Feature.ORE_MINING),
        profile("horned_huntsman", CreatureKind.THORNED_PURSUER, AuditStatus.COMPLETE, null, 100,
            Feature.TELEPORTING_HUNTER, Feature.WOLF_SUMMONING, Feature.THORN_RETALIATION, Feature.RANGED_THORN_VOLLEY),
        profile("lilith", CreatureKind.CRIMSON_MATRIARCH, AuditStatus.COMPLETE,
            CreatureBehaviorTags.Items.VAMPIRE_INITIATION, 20,
            Feature.VAMPIRE_INITIATION, Feature.BLOOD_DRAIN, Feature.SUNLIGHT_WEAKNESS),
        profile("lord_of_torment", CreatureKind.ABYSSAL_REGENT, AuditStatus.MODERN_EQUIVALENT, null, 80,
            Feature.TORMENT_BANISHMENT, Feature.FEAR_AURA),
        profile("lost_soul", CreatureKind.LOST_SOUL, AuditStatus.MODERN_EQUIVALENT,
            CreatureBehaviorTags.Items.SPIRIT_BINDERS, 40,
            Feature.SPIRIT_BINDING, Feature.OWNER_AURA, Feature.PHASED),
        profile("mandrake", CreatureKind.MANDRAKE, AuditStatus.COMPLETE, null, 80,
            Feature.SCREECH, Feature.CROP_RELEASED),
        profile("minedrake", CreatureKind.DREAMROOT, AuditStatus.COMPLETE,
            CreatureBehaviorTags.Items.HEART_OFFERINGS, 40,
            Feature.ROOTED_DRAIN, Feature.THORN_RETALIATION, Feature.HEART_EMPOWERMENT,
            Feature.MUTATION_CREATED, Feature.SAFE_BLAST),
        profile("mog", CreatureKind.STONEBROKER, AuditStatus.MODERN_EQUIVALENT,
            CreatureBehaviorTags.Items.HEART_OFFERINGS, 40,
            Feature.PATRON_OFFERING, Feature.KOBOLD_AURA, Feature.HEART_EMPOWERMENT),
        profile("nightmare", CreatureKind.NIGHTMARE, AuditStatus.MODERN_EQUIVALENT,
            CreatureBehaviorTags.Items.NIGHTMARE_BONDING, 20,
            Feature.RIDEABLE_BOND, Feature.OWNER_AURA, Feature.FIRE_MELEE),
        profile("owl", CreatureKind.OWL, AuditStatus.MODERN_EQUIVALENT,
            CreatureBehaviorTags.Items.COMPANION_BINDERS, 20,
            Feature.FAMILIAR_BOND, Feature.BROOM_AURA, Feature.PROTECT_OWNER),
        profile("parasytic_louse", CreatureKind.LOUSE, AuditStatus.MODERN_EQUIVALENT, null, 40,
            Feature.EFFECT_CAPTURE, Feature.EFFECT_REDIRECTION),
        profile("poltergeist", CreatureKind.POLTERGEIST, AuditStatus.MODERN_EQUIVALENT, null, 80,
            Feature.TELEKINESIS, Feature.PHASED),
        profile("shade_of_leonard", CreatureKind.EMBERHORN_ARCHFIEND, AuditStatus.COMPLETE, null, 100,
            Feature.CAULDRON_AURA, Feature.RISK_AURA, Feature.PASSIVE_UNTIL_HURT),
        profile("spectral_familiar", CreatureKind.FAMILIAR, AuditStatus.COMPLETE,
            CreatureBehaviorTags.Items.COMPANION_BINDERS, 40,
            Feature.FAMILIAR_BOND, Feature.ORE_GUIDANCE, Feature.PROTECT_OWNER),
        profile("spectre", CreatureKind.SPECTRE, AuditStatus.MODERN_EQUIVALENT, null, 80,
            Feature.FEAR_AURA, Feature.PHASED),
        profile("spirit", CreatureKind.SPIRIT, AuditStatus.MODERN_EQUIVALENT,
            CreatureBehaviorTags.Items.SPIRIT_BINDERS, 40,
            Feature.SPIRIT_BINDING, Feature.OWNER_AURA, Feature.PROTECT_OWNER, Feature.PHASED),
        profile("toad", CreatureKind.TOAD, AuditStatus.COMPLETE,
            CreatureBehaviorTags.Items.COMPANION_BINDERS, 20,
            Feature.FAMILIAR_BOND, Feature.AMPHIBIOUS_AURA, Feature.PROTECT_OWNER, Feature.MUTATION_CREATED),
        profile("treefyd", CreatureKind.BRAMBLE_COLOSSUS, AuditStatus.COMPLETE,
            CreatureBehaviorTags.Items.HEART_OFFERINGS, 40,
            Feature.HEART_EMPOWERMENT, Feature.ROOTED_DRAIN, Feature.THORN_RETALIATION),
        profile("vampire", CreatureKind.VAMPIRE, AuditStatus.MODERN_EQUIVALENT, null, 20,
            Feature.BLOOD_DRAIN, Feature.SUNLIGHT_WEAKNESS),
        profile("witch_hunter", CreatureKind.WEREWOLF_HUNTER, AuditStatus.MODERN_EQUIVALENT, null, 20,
            Feature.SILVER_HUNTING, Feature.PILLAGER_INTEGRATION),
        profile("wolfman", CreatureKind.WEREWOLF, AuditStatus.MODERN_EQUIVALENT, null, 20,
            Feature.WEREWOLF_INTEGRATION, Feature.SILVER_WEAKNESS, Feature.PILLAGER_RIVALRY)
    );
    private static final Map<CreatureKind, CreatureBehaviorProfile> BY_KIND = AUDITED.stream()
        .collect(Collectors.toUnmodifiableMap(CreatureBehaviorProfile::kind, Function.identity()));

    public CreatureBehaviorProfile {
        features = Set.copyOf(features);
        offering = offering == null ? Optional.empty() : offering;
        if (auditId.isBlank() || features.isEmpty() || pulseIntervalTicks < 1) {
            throw new IllegalArgumentException("Creature behavior profiles require an identity, features, and a pulse interval");
        }
    }

    public boolean has(final Feature feature) {
        return features.contains(feature);
    }

    public static Optional<CreatureBehaviorProfile> find(final CreatureKind kind) {
        return Optional.ofNullable(BY_KIND.get(kind));
    }

    public static List<CreatureBehaviorProfile> audited() {
        return AUDITED;
    }

    private static CreatureBehaviorProfile profile(
        final String auditId,
        final CreatureKind kind,
        final AuditStatus status,
        final TagKey<Item> offering,
        final int interval,
        final Feature first,
        final Feature... rest
    ) {
        final EnumSet<Feature> features = EnumSet.of(first, rest);
        return new CreatureBehaviorProfile(auditId, kind, status, features, Optional.ofNullable(offering), interval);
    }

    public enum AuditStatus {
        COMPLETE,
        MODERN_EQUIVALENT,
        PARTIAL
    }

    public enum Feature {
        POTION_VOLLEY,
        THORN_RETALIATION,
        DUST_EMPOWERMENT,
        SCREECH,
        PHASED,
        RIDEABLE_BOND,
        OWNER_AURA,
        COVEN_RECRUITMENT,
        PROTECT_OWNER,
        DEATH_DISGUISE,
        SOUL_REAP,
        INFERNAL_BARTER,
        FIRE_MELEE,
        PROXIMITY_AGGRESSION,
        BIOME_VARIANTS,
        FAMILIAR_BOND,
        WAYSTONE_TRAVEL,
        HEART_EMPOWERMENT,
        FORGE_AURA,
        KOBOLD_WORKER,
        PROFESSION_TRADING,
        ORE_MINING,
        TELEPORTING_HUNTER,
        WOLF_SUMMONING,
        RANGED_THORN_VOLLEY,
        VAMPIRE_INITIATION,
        BLOOD_DRAIN,
        SUNLIGHT_WEAKNESS,
        TORMENT_BANISHMENT,
        FEAR_AURA,
        SPIRIT_BINDING,
        CROP_RELEASED,
        ROOTED_DRAIN,
        PATRON_OFFERING,
        KOBOLD_AURA,
        BROOM_AURA,
        EFFECT_CAPTURE,
        EFFECT_REDIRECTION,
        TELEKINESIS,
        CAULDRON_AURA,
        RISK_AURA,
        PASSIVE_UNTIL_HURT,
        ORE_GUIDANCE,
        AMPHIBIOUS_AURA,
        MUTATION_CREATED,
        SAFE_BLAST,
        SILVER_HUNTING,
        PILLAGER_INTEGRATION,
        WEREWOLF_INTEGRATION,
        SILVER_WEAKNESS,
        PILLAGER_RIVALRY
    }
}
