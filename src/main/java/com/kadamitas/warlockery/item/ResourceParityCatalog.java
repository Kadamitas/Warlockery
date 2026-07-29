package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.block.CritterSnareBlock;
import com.kadamitas.warlockery.block.DisturbedCottonBlock;
import com.kadamitas.warlockery.entity.CreatureBehaviorRuntime;
import com.kadamitas.warlockery.mutation.AdvancedMutationResolver;
import com.kadamitas.warlockery.ritual.HuntsmanSummoningStructure;
import java.util.List;
import java.util.Objects;

public final class ResourceParityCatalog {
    private static final String REAGENT_TAG = "warlockery:resource_reagents";
    public static final List<Profile> PROFILES = List.of(
        profile("Attuned Stone", "ingredient_attuned_stone"),
        profile("Belladonna", "ingredient_belladonna"),
        profile("Belladonna Flower", "ingredient_belladonna"),
        profile("Blood Poppy", "bloodrose"),
        profile(
            "Bloodied Wicker Bundle",
            "wickerbundle",
            consumer(
                HuntsmanSummoningStructure.class,
                "consume",
                2,
                "Rite of the Bloodied Effigy consumes four bloodied cardinal bundles"
            )
        ),
        profile("Bone Needle", "ingredient_bone_needle"),
        profile("Breath of the Goddess", "ingredient_breath_of_the_goddess"),
        profile("Clay Jar", "ingredient_clay_jar"),
        profile(
            "Concentrated Bat Ball",
            "ingredient_bat_ball",
            consumer(BatBallItem.class, "use", 3, "Using a filled bat ball releases its stored bats")
        ),
        profile("Condensed Fear", "ingredient_condensed_fear"),
        profile("Creeper Heart", "ingredient_creeper_heart"),
        profile(
            "Critter Snare",
            "crittersnare",
            consumer(CritterSnareBlock.class, "entityInside", 6, "A placed snare captures a tagged small creature")
        ),
        profile("Demon Heart", "demonheart"),
        profile("Demonic Blood", "ingredient_infernal_blood"),
        profile(
            "Demonic Contract",
            "ingredient_contract",
            consumer(
                CreatureBehaviorRuntime.class,
                "bindCompanion",
                4,
                "Binding an imp consumes an item from the infernal contracts tag"
            )
        ),
        profile("Dense Web", "ingredient_web"),
        profile("Diamond Vapor", "ingredient_diamond_vapour"),
        profile(
            "Disturbed Cotton",
            "ingredient_disturbed_cotton",
            acquisition(
                DisturbedCottonBlock.class,
                "playerDestroy",
                6,
                "Breaking Somnian Cotton in a qualifying nightmare state drops Disturbed Cotton"
            )
        ),
        profile("Ender Dew", "ingredient_ender_dew"),
        profile("Ent Twig", "ingredient_heartwood_splinter"),
        profile("Exhale of the Horned One", "ingredient_exhale_of_the_horned_one"),
        profile("Fanciful Thread", "ingredient_fanciful_thread"),
        profile("Focused Will", "ingredient_focused_will"),
        profile("Foul Fume", "ingredient_foul_fume"),
        profile("Frozen Heart", "ingredient_frozen_heart"),
        profile("Golden Thread", "ingredient_golden_thread"),
        profile(
            "Grassper",
            "grassper",
            consumer(
                AdvancedMutationResolver.class,
                "attempt",
                3,
                "Advanced mutations use four tagged Grasspers as ingredient holders"
            )
        ),
        profile("Graveyard Dust", "ingredient_graveyard_dust"),
        profile("Gypsum", "ingredient_gypsum"),
        profile("Hint of Rebirth", "ingredient_hint_of_rebirth"),
        profile("Icy Needle", "ingredient_icy_needle"),
        profile("Impregnated Leather", "ingredient_impregnated_leather"),
        profile("Mandrake Plant", "ingredient_mandrake_root"),
        profile("Mandrake Root", "ingredient_mandrake_root"),
        profile("Mellifluous Hunger", "ingredient_mellifluous_hunger"),
        profile("Minedrake Plant", "seedsdreamroot"),
        profile("Mutandis", "ingredient_verdant_catalyst"),
        profile("Mutandis Extremis", "ingredient_verdant_catalyst_prime"),
        profile("Null Catalyst", "ingredient_nullcatalyst"),
        profile("Nullified Leather", "ingredient_nullifiedleather"),
        profile("Odour of Purity", "ingredient_odour_of_purity"),
        profile("Oil of Vitriol", "ingredient_oil_of_vitriol"),
        profile("Owlet's Wing", "ingredient_owlets_wing"),
        profile("Poisoned Apple", "ingredient_sleeping_apple"),
        profile("Purified Milk", "ingredient_purified_milk"),
        profile("Quartz Sphere", "ingredient_quartz_sphere"),
        profile("Quicklime", "ingredient_quicklime"),
        profile("Reek of Misfortune", "ingredient_reek_of_misfortune"),
        profile("Refined Evil", "ingredient_refined_evil"),
        profile("Rowan Berries", "ingredient_berries_rowan"),
        profile("Silver Deposits", "ingredient_silverdust"),
        profile("Snowbell", "seedssnowbell"),
        profile("Soft Clay Jar", "ingredient_clay_jar_soft"),
        profile("Spanish Moss", "spanishmoss"),
        profile("Spectral Dust", "ingredient_spectral_dust"),
        profile("Subdued Spirit", "ingredient_subdued_spirit"),
        profile("Tear of the Goddess", "ingredient_tear_of_the_goddess"),
        profile("Toe of Frog", "ingredient_toe_of_frog"),
        profile("Tongue of Dog", "ingredient_dog_tongue"),
        profile("Tormented Twine", "ingredient_tormented_twine"),
        profile("Treefyd Seed", "ingredient_bramble_colossus_seed"),
        profile("Water Artichoke", "ingredient_artichoke"),
        profile("Water Artichoke Globe", "ingredient_artichoke"),
        profile("Whiff of Magic", "ingredient_whiff_of_magic"),
        profile(
            "Wicker Bundle",
            "wickerbundle",
            consumer(
                HuntsmanSummoningStructure.class,
                "consume",
                2,
                "A bloodied Wicker Bundle is consumed by the Rite of the Bloodied Effigy"
            )
        ),
        profile("Wispy Cotton", "somniancotton"),
        profile("Wolfsbane", "ingredient_wolfsbane"),
        profile("Wolfsbane Plant", "ingredient_wolfsbane"),
        profile("Wood Ash", "ingredient_ash_wood"),
        profile("Wool of Bat", "ingredient_bat_wool"),
        profile("Wormwood", "ingredient_wormwood"),
        profile("Wormy Apple", "ingredient_apple_wormy")
    );

    private ResourceParityCatalog() {
    }

    public static Diagnostic diagnose(
        final boolean acquired,
        final boolean consumed,
        final boolean compatible
    ) {
        if (!acquired) {
            return Diagnostic.MISSING_ACQUISITION;
        }
        if (!consumed) {
            return Diagnostic.MISSING_CONSUMER;
        }
        return compatible ? Diagnostic.READY : Diagnostic.MISSING_COMPATIBILITY;
    }

    private static Profile profile(
        final String wikiPage,
        final String registryPath,
        final RuntimeEvidence... runtimeEvidence
    ) {
        return new Profile(wikiPage, "warlockery:" + registryPath, REAGENT_TAG, List.of(runtimeEvidence));
    }

    private static RuntimeEvidence acquisition(
        final Class<?> owner,
        final String member,
        final int parameterCount,
        final String interaction
    ) {
        return new RuntimeEvidence(EvidenceKind.ACQUISITION, owner, member, parameterCount, interaction);
    }

    private static RuntimeEvidence consumer(
        final Class<?> owner,
        final String member,
        final int parameterCount,
        final String interaction
    ) {
        return new RuntimeEvidence(EvidenceKind.CONSUMER, owner, member, parameterCount, interaction);
    }

    public enum Diagnostic {
        MISSING_ACQUISITION,
        MISSING_CONSUMER,
        MISSING_COMPATIBILITY,
        READY
    }

    public enum EvidenceKind {
        ACQUISITION,
        CONSUMER
    }

    public record RuntimeEvidence(
        EvidenceKind kind,
        Class<?> owner,
        String member,
        int parameterCount,
        String interaction
    ) {
        public RuntimeEvidence {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(member, "member");
            Objects.requireNonNull(interaction, "interaction");
            if (member.isBlank() || interaction.isBlank() || parameterCount < 0) {
                throw new IllegalArgumentException("Runtime evidence requires an exact member and interaction");
            }
        }
    }

    public record Profile(
        String wikiPage,
        String registryId,
        String compatibilityTag,
        List<RuntimeEvidence> runtimeEvidence
    ) {
        public Profile {
            Objects.requireNonNull(wikiPage, "wikiPage");
            Objects.requireNonNull(registryId, "registryId");
            Objects.requireNonNull(compatibilityTag, "compatibilityTag");
            runtimeEvidence = List.copyOf(runtimeEvidence);
        }

        public List<RuntimeEvidence> runtimeEvidence(final EvidenceKind kind) {
            return runtimeEvidence.stream().filter(evidence -> evidence.kind() == kind).toList();
        }
    }
}
