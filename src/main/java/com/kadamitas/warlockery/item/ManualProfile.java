package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.brew.BrewKind;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record ManualProfile(String id, String titleKey, List<String> sections) {
    private static final String RITUAL_PREFIX = "rite_";
    private static final String BREW_PREFIX = "brew_entry_";
    private static final List<String> RITUAL_SECTIONS = """
        anguish_of_the_dead
        banish_demon
        banish_demon_portable
        barrier
        barrier_large
        barrier_portable
        bind_circle
        bind_circle_portable
        bind_death
        bind_familiar
        bind_fetish
        bind_spectral
        bind_statue_player
        bind_waystone
        bind_waystone_player
        bind_waystone_portable
        blight
        blindness
        call_beasts
        call_familiar
        charge_attuned_stone
        climate_change
        cook_food
        copy_waystone
        copy_waystone_portable
        corrupt_doll
        cure_insanity
        cure_misfortune
        cure_nightmare
        cure_overheating
        cure_sinking
        cure_vampire
        cure_wolf
        deathly_veil
        drain_growth
        eclipse
        eclipse_portable
        fertility
        fertility_portable
        forestation
        fortification_of_the_corpse
        glyph_to_infernal
        glyph_to_ritual
        glyph_to_the_veil
        graveyard_mist
        hell_on_earth
        hex_insanity
        hex_misfortune
        hex_nightmare
        hex_overheating
        hex_sinking
        hex_wolf
        ice_shell
        imprisonment
        infuse_brew_grave
        infuse_brew_soaring
        infuse_broom
        infuse_crystal_ball
        infuse_mirror
        infuse_mystic_branch
        infuse_seer_stone
        infusion_earth
        infusion_ender
        infusion_hell
        infusion_light
        infusion_sky
        manifestation
        natures_power
        necrostone
        part_earth
        prior_incarnation
        rain_of_toads
        raise_earth
        recharge_infusion
        sanctity
        spectral_stone
        storm
        storm_large
        storm_portable
        summon_banshee
        summon_cat_familiar
        summon_circle_mage
        summon_crimson_matriarch
        summon_demon
        summon_familiar
        summon_forgewarden
        summon_imp
        summon_lost_soul
        summon_parasytic_louse
        summon_poltergeist
        summon_reflection
        summon_spectre
        summon_stonebroker
        summon_storm_simian
        summon_thorned_pursuer
        summon_witch
        summon_wither
        teleport_entity
        teleport_waystone
        transpose_ore
        volcano
        """.lines()
        .map(String::strip)
        .filter(section -> !section.isEmpty())
        .map(RITUAL_PREFIX::concat)
        .toList();
    private static final List<String> BREW_SECTIONS = BrewKind.builtIns().stream()
        .map(BrewKind::id)
        .map(BREW_PREFIX::concat)
        .toList();
    private static final List<ManualProfile> PROFILES = List.of(
        profile("bookbiomes2", "biomes_extended", "overview", "biome_notes", "shifting_rite"),
        brewProfile("cauldronbook", "codex", "custom_brews", "delivery", "diagnostics"),
        profile("vampirebook", "immortal", "initiation", "blood", "weaknesses"),
        profile("ingredient_book_biomes", "biomes", "overview", "biome_notes"),
        profile("ingredient_book_burning", "conjuration", "summoning", "fetishes"),
        ritualProfile("ingredient_book_circle_magic", "circles", "chalk", "ritual_ui", "power"),
        profile("ingredient_book_distilling", "distilling", "inputs", "outputs", "automation"),
        profile("ingredient_book_herbology", "herbology", "crops", "mutations", "toad_mutation",
            "minedrake_mutation", "minedrake_bulbs", "safe_harvest"),
        profile("ingredient_book_infusions", "infusions", "paths", "focus", "reserve"),
        profile("ingredient_book_oven", "fumes", "oven", "jars", "funnels"),
        profile("ingredient_book_wands", "symbology", "focus", "gestures", "deflection"),
        profile("ingredient_vbook_page", "torn_page", "immortal_fragment")
    );
    private static final Map<String, ManualProfile> BY_ID = PROFILES.stream()
        .collect(Collectors.toUnmodifiableMap(ManualProfile::id, Function.identity()));

    public ManualProfile {
        id = normalized(id, "id");
        titleKey = normalized(titleKey, "titleKey");
        sections = List.copyOf(sections);
        if (sections.isEmpty() || sections.stream().anyMatch(String::isBlank)
            || Set.copyOf(sections).size() != sections.size()) {
            throw new IllegalArgumentException("A manual requires named sections");
        }
    }

    public static List<ManualProfile> profiles() {
        return PROFILES;
    }

    public static Optional<ManualProfile> find(final String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static Set<String> ids() {
        return BY_ID.keySet();
    }

    public static List<ManualProfile> search(
        final String query,
        final Function<String, String> translationResolver
    ) {
        final String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return PROFILES;
        }
        return PROFILES.stream()
            .filter(profile -> profile.searchTerms(translationResolver)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(needle)))
            .toList();
    }

    public UtilityDecision diagnose() {
        return sections.isEmpty()
            ? UtilityDecision.failure("missing_sections")
            : UtilityDecision.success("ready");
    }

    public String translatedTitleKey() {
        return "manual.warlockery." + titleKey + ".title";
    }

    public String translatedSectionKey(final String section) {
        if (isRitualSection(section)) {
            return "ritual.warlockery." + section.substring(RITUAL_PREFIX.length()) + ".description";
        }
        if (isBrewSection(section)) {
            return "item.warlockery." + "brew_" + section.substring(BREW_PREFIX.length());
        }
        return "manual.warlockery." + titleKey + "." + section;
    }

    public String translatedSectionTitleKey(final String section) {
        if (isRitualSection(section)) {
            return "ritual.warlockery." + section.substring(RITUAL_PREFIX.length()) + ".title";
        }
        if (isBrewSection(section)) {
            return translatedSectionKey(section);
        }
        return translatedSectionKey(section) + ".title";
    }

    public String adjacentSection(final String current, final int offset) {
        final int index = Math.max(0, sections.indexOf(current));
        return sections.get(Math.floorMod(index + offset, sections.size()));
    }

    private Stream<String> searchTerms(final Function<String, String> translationResolver) {
        final Stream<String> raw = Stream.concat(Stream.of(id, titleKey), sections.stream());
        final Stream<String> translated = Stream.concat(
            Stream.of(translationResolver.apply(translatedTitleKey())),
            sections.stream().flatMap(section -> Stream.of(
                translationResolver.apply(translatedSectionTitleKey(section)),
                translationResolver.apply(translatedSectionKey(section))
            ))
        );
        return Stream.concat(raw, translated);
    }

    private static ManualProfile profile(final String id, final String title, final String... sections) {
        return new ManualProfile(id, title, List.of(sections));
    }

    private static ManualProfile ritualProfile(final String id, final String title, final String... sections) {
        return new ManualProfile(id, title, Stream.concat(Stream.of(sections), RITUAL_SECTIONS.stream()).toList());
    }

    private static ManualProfile brewProfile(final String id, final String title, final String... sections) {
        return new ManualProfile(id, title, Stream.concat(Stream.of(sections), BREW_SECTIONS.stream()).toList());
    }

    private static boolean isRitualSection(final String section) {
        return section.startsWith(RITUAL_PREFIX);
    }

    private static boolean isBrewSection(final String section) {
        return section.startsWith(BREW_PREFIX);
    }

    private static String normalized(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
