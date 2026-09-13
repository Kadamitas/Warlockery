package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.brew.BrewKind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record ManualProfile(
    String id,
    String titleKey,
    List<String> sections,
    List<Chapter> chapters
) {
    private static final String RITUAL_PREFIX = "rite_";
    private static final String BREW_PREFIX = "brew_entry_";
    private static final String BIOME_PREFIX = "biome_entry_";
    private static final String MACHINE_RECIPE_PREFIX = "machine_recipe_";
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
        bind_trent
        bind_spectral
        bind_statue_player
        bind_waystone
        bind_witch_ladder
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
        cure_heat_metal
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
        hex_heat_metal
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
        marriage
        divorce
        natures_power
        necrostone
        part_earth
        prior_incarnation
        rain_of_toads
        raise_earth
        raise_earth_small
        raise_earth_large
        recharge_infusion
        sanctity
        spectral_stone
        storm
        storm_large
        storm_portable
        summon_banshee
        summon_cat_familiar
        summon_circle_mage
        blood_audience
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
    private static final List<String> BIOME_SECTIONS = entries(BIOME_PREFIX, """
        the_void plains sunflower_plains snowy_plains ice_spikes desert swamp mangrove_swamp forest
        flower_forest birch_forest dark_forest pale_garden old_growth_birch_forest old_growth_pine_taiga
        old_growth_spruce_taiga taiga snowy_taiga savanna savanna_plateau windswept_hills
        windswept_gravelly_hills windswept_forest windswept_savanna jungle sparse_jungle bamboo_jungle
        badlands eroded_badlands wooded_badlands meadow cherry_grove grove snowy_slopes frozen_peaks
        jagged_peaks stony_peaks river frozen_river beach snowy_beach stony_shore warm_ocean
        lukewarm_ocean deep_lukewarm_ocean ocean deep_ocean cold_ocean deep_cold_ocean frozen_ocean
        deep_frozen_ocean mushroom_fields dripstone_caves lush_caves deep_dark sulfur_caves nether_wastes
        warped_forest crimson_forest soul_sand_valley basalt_deltas the_end end_highlands end_midlands
        small_end_islands end_barrens
        """);
    private static final List<String> DISTILLING_RECIPE_SECTIONS = entries(MACHINE_RECIPE_PREFIX, """
        distill_magic distill_vitriol distill_diamond_vapour distill_ender_dew distill_refined_evil
        distill_infernal_blood distill_condensed_fear distill_infernal_blood_from_netherrack
        distill_glowstone
        """);
    private static final List<String> VAMPIRE_PROGRESSION_SECTIONS = entries("vampire_level_", """
        1 2 3 4 5 6 7 8 9 10
        """);
    private static final List<String> WEREWOLF_PROGRESSION_SECTIONS = entries("werewolf_level_", """
        1 2 3 4 5 6 7 8 9 10
        """);
    private static final List<ManualProfile> PROFILES = List.of(
        biomeProfile("bookbiomes2", "biomes_extended", true),
        brewProfile(),
        vampireProfile(),
        biomeProfile("ingredient_book_biomes", "biomes", false),
        conjurationProfile(),
        ritualProfile(),
        distillingProfile(),
        herbologyProfile(),
        infusionProfile(),
        fumesProfile(),
        symbologyProfile(),
        profile("ingredient_vbook_page", "torn_page", "immortal_fragment")
    );
    private static final Map<String, ManualProfile> BY_ID = PROFILES.stream()
        .collect(Collectors.toUnmodifiableMap(ManualProfile::id, Function.identity()));

    public ManualProfile {
        id = normalized(id, "id");
        titleKey = normalized(titleKey, "titleKey");
        sections = List.copyOf(sections);
        chapters = List.copyOf(chapters);
        if (sections.isEmpty() || sections.stream().anyMatch(String::isBlank)
            || Set.copyOf(sections).size() != sections.size()) {
            throw new IllegalArgumentException("A manual requires named sections");
        }
        if (chapters.isEmpty() || !chapters.stream().flatMap(chapter -> chapter.sections().stream()).toList()
            .equals(sections)) {
            throw new IllegalArgumentException("Manual chapters must contain every section in reading order");
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
        if (Set.of("bloodcrucible", "coffinblock", "glassgoblet").contains(section)) {
            return "manual.warlockery.immortal." + section;
        }
        if ("fetish_statue_broken_hexes".equals(section)) {
            return "manual.warlockery.conjuration.broken_hexes_ward";
        }
        if (section.startsWith("crafting_")) {
            return "manual.warlockery.crafting.intro";
        }
        if (isRitualSection(section)) {
            return "ritual.warlockery." + section.substring(RITUAL_PREFIX.length()) + ".description";
        }
        if (isBrewSection(section)) {
            return "item.warlockery." + "brew_" + section.substring(BREW_PREFIX.length());
        }
        if (isBiomeSection(section)) {
            return translatedBiomeEntryKey(section.substring(BIOME_PREFIX.length()));
        }
        if (isMachineRecipeSection(section)) {
            return "manual.warlockery.machine_recipe.entry";
        }
        return "manual.warlockery." + titleKey + "." + section;
    }

    public String translatedSectionTitleKey(final String section) {
        if (section.startsWith("crafting_")) {
            final String recipe = section.substring("crafting_".length());
            return switch (recipe) {
                case "chalkheart_from_gypsum" -> "item.warlockery.chalkheart";
                case "ingredient_clay_jar_from_smelting" -> "item.warlockery.ingredient_clay_jar";
                case "ingredient_soft_clay_jar" -> "item.warlockery.ingredient_clay_jar_soft";
                case "critter_snare" -> "item.warlockery.crittersnare";
                case "silver_bolts" -> "item.warlockery.ingredient_bolt_silver";
                case "voidbramble" -> "manual.warlockery.herbology.void_bramble_recipe.title";
                case "leech_chest" -> "item.warlockery.leechchest";
                case "shaded_glass" -> "item.warlockery.shadedglass";
                case "mirror_block" -> "item.warlockery.mirrorblock";
                case "warm_blood_from_blood_poppy" -> "item.warlockery.ingredient_warm_blood";
                case "altar", "alchemical_oven", "distilleryidle", "cauldron", "kettle", "silvervat", "brazier", "spinningwheel" -> "block.warlockery." + recipe;
                default -> "item.warlockery." + recipe;
            };
        }
        if ("preamble".equals(section)) {
            return "manual.warlockery.preamble.title";
        }
        if (isRitualSection(section)) {
            return "ritual.warlockery." + section.substring(RITUAL_PREFIX.length()) + ".title";
        }
        if (isBrewSection(section)) {
            return translatedSectionKey(section);
        }
        if (isBiomeSection(section)) {
            return translatedBiomeTitleKey(section.substring(BIOME_PREFIX.length()));
        }
        if (isMachineRecipeSection(section)) {
            return "manual.warlockery.recipe." + section.substring(MACHINE_RECIPE_PREFIX.length()) + ".title";
        }
        return translatedSectionKey(section) + ".title";
    }

    public static String translatedBiomeEntryKey(final String biomeId) {
        final String section = BIOME_PREFIX + biomeId;
        return BIOME_SECTIONS.contains(section)
            ? "manual.warlockery.biome.entry." + biomeChapter(section)
            : "manual.warlockery.biome.entry";
    }

    public static String translatedBiomeTitleKey(final String biomeId) {
        return "sulfur_caves".equals(biomeId)
            ? "biome.warlockery.sulfur_caves"
            : "biome.minecraft." + biomeId;
    }

    public Chapter chapterFor(final String section) {
        return chapters.stream()
            .filter(chapter -> chapter.sections().contains(section))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown manual section: " + section));
    }

    public List<String> sectionsInChapter(final String chapterId, final List<String> visibleSections) {
        return chapters.stream()
            .filter(chapter -> chapter.id().equals(chapterId))
            .findFirst()
            .map(chapter -> chapter.sections().stream().filter(visibleSections::contains).toList())
            .orElse(List.of());
    }

    public String adjacentSection(final String current, final int offset) {
        final int index = Math.max(0, sections.indexOf(current));
        return sections.get(Math.floorMod(index + offset, sections.size()));
    }

    private Stream<String> searchTerms(final Function<String, String> translationResolver) {
        final Stream<String> raw = Stream.of(
            Stream.of(id, titleKey),
            chapters.stream().map(Chapter::id),
            sections.stream()
        ).flatMap(Function.identity());
        final Stream<String> translated = Stream.concat(
            Stream.of(
                translationResolver.apply(translatedTitleKey())
            ),
            Stream.concat(
                chapters.stream().map(chapter -> translationResolver.apply(chapter.titleKey())),
                sections.stream().flatMap(section -> Stream.of(
                    translationResolver.apply(translatedSectionTitleKey(section)),
                    translationResolver.apply(translatedSectionKey(section))
                ))
            )
        );
        return Stream.concat(raw, translated);
    }

    private static ManualProfile profile(final String id, final String title, final String... sections) {
        final Chapter contents = chapter("contents", "manual.warlockery.chapter.contents", sections);
        return new ManualProfile(id, title, contents.sections(), List.of(contents));
    }

    private static ManualProfile ritualProfile() {
        final Map<String, List<String>> grouped = groupSections(RITUAL_SECTIONS, ManualProfile::ritualChapter);
        final List<Chapter> chapters = Stream.of(
            Stream.of(chapter(
                "chalk", "manual.warlockery.chapter.chalk",
                "chalk", "crafting_chalkritual", "crafting_chalkinfernal", "crafting_chalk_veil",
                "crafting_chalkheart_from_gypsum", "golden_chalk",
                "crafting_ingredient_clay_jar_soft", "crafting_ingredient_clay_jar_from_smelting",
                "crafting_alchemical_oven", "machine_recipe_oven_logs", "crafting_ingredient_quicklime",
                "crafting_altar", "machine_recipe_oven_fume_breath_of_the_goddess",
                "crafting_distilleryidle", "machine_recipe_distill_vitriol"
            )),
            Stream.of(chapter("introduction", "manual.warlockery.preamble.title", "preamble")),
            Stream.of(chapter(
                "foundations",
                "manual.warlockery.chapter.foundations",
                "arthana",
                "spirit_locator",
                "veil_waystones",
                "crafting_ingredient_waystone",
                "ingredient_waystone",
                "ingredient_waystone_bound",
                "ingredient_waystone_creature_bound",
                "circletalisman",
                "ritual_ui",
                "power"
            )),
            Stream.of(chapter(
                "crafting", "manual.warlockery.crafting.title",
                "crafting_ritual_knife", "crafting_arcane_focus"
            )),
            grouped.entrySet().stream().map(entry -> chapter(
                entry.getKey(),
                "manual.warlockery.chapter." + entry.getKey(),
                entry.getValue().toArray(String[]::new)
            )),
            Stream.of(chapter(
                "lycanthropy_trials",
                "manual.warlockery.chapter.lycanthropy_trials",
                Stream.concat(WEREWOLF_PROGRESSION_SECTIONS.stream(), Stream.of("mooncharm")).toArray(String[]::new)
            ))
        ).flatMap(Function.identity()).toList();
        return new ManualProfile(
            "ingredient_book_circle_magic", "circles",
            chapters.stream().flatMap(chapter -> chapter.sections().stream()).toList(), chapters
        );
    }

    private static ManualProfile brewProfile() {
        final Map<String, List<String>> grouped = groupSections(BREW_SECTIONS, ManualProfile::brewChapter);
        final List<Chapter> chapters = Stream.concat(
            Stream.of(chapter(
                "brewing_primer",
                "manual.warlockery.chapter.brewing_primer",
                "crafting_kettle",
                "crafting_ingredient_annointing_paste",
                "machines",
                "machine_recipe_cauldron_colored_brew_water",
                "machine_recipe_cauldron_drop_of_luck",
                "machine_recipe_cauldron_flowing_spirit",
                "machine_recipe_cauldron_playercompass",
                "machine_recipe_cauldron_verdant_catalyst_prime",
                "custom_brews",
                "antidotes",
                "circle_brewing",
                "delivery",
                "diagnostics"
            ),
            chapter(
                "fluid_vessels",
                "manual.warlockery.chapter.fluid_vessels",
                "bucketspirit", "buckethollowtears", "bucketerosionbrew", "bucketbrew"
            ),
            chapter("brew_storage", "manual.warlockery.chapter.brew_storage", "crafting_brewbag", "brewbag")),
            grouped.entrySet().stream().map(entry -> chapter(
                entry.getKey(),
                "manual.warlockery.chapter." + entry.getKey(),
                entry.getValue().toArray(String[]::new)
            ))
        ).toList();
        return groupedProfile("cauldronbook", "codex", chapters.toArray(Chapter[]::new));
    }

    private static ManualProfile biomeProfile(final String id, final String title, final boolean extended) {
        final List<Chapter> chapters = new java.util.ArrayList<>();
        chapters.add(chapter(
            "biome_practice",
            "manual.warlockery.chapter.biome_practice",
            extended ? new String[] {"overview", "biome_notes", "crafting_ingredient_book_biomes", "crafting_bookbiomes2", "crafting_biomenote", "biomenote", "shifting_rite"}
                : new String[] {"overview", "biome_notes", "crafting_ingredient_book_biomes", "crafting_bookbiomes2", "crafting_biomenote", "biomenote"}
        ));
        groupSections(BIOME_SECTIONS, ManualProfile::biomeChapter).forEach((chapterId, sections) -> chapters.add(chapter(
            chapterId,
            "manual.warlockery.chapter." + chapterId,
            sections.toArray(String[]::new)
        )));
        return groupedProfile(id, title, chapters.toArray(Chapter[]::new));
    }

    private static ManualProfile vampireProfile() {
        return groupedProfile(
            "vampirebook",
            "immortal",
            chapter(
                "vampire_awakening",
                "manual.warlockery.chapter.vampire_awakening",
                Stream.concat(
                    // Saved torn-page progress indexes this sequence; utility guides live in Conjuration.
                    Stream.of("nami", "blood_audience"),
                    VAMPIRE_PROGRESSION_SECTIONS.subList(0, 2).stream()
                ).toArray(String[]::new)
            ),
            chapter(
                "vampire_trials",
                "manual.warlockery.chapter.vampire_trials",
                VAMPIRE_PROGRESSION_SECTIONS.subList(2, 6).toArray(String[]::new)
            ),
            chapter(
                "vampire_ascendance",
                "manual.warlockery.chapter.vampire_ascendance",
                VAMPIRE_PROGRESSION_SECTIONS.subList(6, 10).toArray(String[]::new)
            )
        );
    }

    private static ManualProfile conjurationProfile() {
        return groupedProfile(
            "ingredient_book_burning",
            "conjuration",
            chapter("brazier_workshop", "manual.warlockery.chapter.brazier_workshop",
                "crafting_brazier", "brazier",
                "machine_recipe_brazier_summon_spectre", "machine_recipe_brazier_summon_banshee",
                "machine_recipe_brazier_summon_poltergeist", "machine_recipe_brazier_deathly_veil",
                "machine_recipe_brazier_graveyard_mist", "machine_recipe_brazier_fortification_of_the_corpse",
                "machine_recipe_brazier_anguish_of_the_dead", "machine_recipe_brazier_drain_growth"),
            chapter(
                "conjuration",
                "manual.warlockery.chapter.conjuration",
                "summoning",
                "rite_call_beasts",
                "rite_call_familiar"
            ),
            chapter(
                "summoned_allies",
                "manual.warlockery.chapter.summoned_allies",
                "rite_summon_cat_familiar",
                "rite_summon_circle_mage",
                "rite_summon_familiar",
                "rite_summon_imp",
                "rite_summon_storm_simian",
                "rite_summon_witch"
            ),
            chapter(
                "summoned_spirits",
                "manual.warlockery.chapter.summoned_spirits",
                "rite_summon_banshee",
                "rite_summon_lost_soul",
                "rite_summon_parasytic_louse", "louse",
                "rite_summon_poltergeist",
                "rite_summon_spectre"
            ),
            chapter(
                "summoned_powers",
                "manual.warlockery.chapter.summoned_powers",
                "rite_blood_audience",
                "rite_summon_demon",
                "device_demon_heart",
                "rite_hell_on_earth", "archfiends_urn",
                "rite_summon_forgewarden",
                "rite_summon_reflection",
                "rite_summon_stonebroker",
                "rite_summon_thorned_pursuer",
                "rite_summon_wither"
            ),
            chapter(
                "bound_fetishes",
                "manual.warlockery.chapter.bound_fetishes",
                "fetish_scarecrow",
                "fetish_trent_effigy",
                "fetish_dream_weaver_fasting",
                "fetish_dream_weaver_fleet_foot",
                "fetish_dream_weaver_intensity",
                "fetish_dream_weaver_iron_arm",
                "fetish_dream_weaver_nightmares",
                "crafting_alluringskull", "device_alluring_skull",
                "crafting_dreamcatcher", "crafting_dream_weaver_fasting",
                "crafting_dream_weaver_fleet_foot", "crafting_dream_weaver_intensity",
                "crafting_dream_weaver_iron_arm", "crafting_dream_weaver_nightmares", "device_dream_weaver",
                "fetish_alluring_skull",
                "fetish_statue_goddess",
                "fetish_statue_worship",
                "fetish_statue_broken_hexes",
                "fetish_statue_occluded_summons",
                "fetish_doll_shelf"
            ),
            chapter(
                "binding_tools",
                "manual.warlockery.chapter.binding_tools",
                "sympathetic_vials",
                "bloodcrucible", "coffinblock", "glassgoblet",
                "beast_speech",
                "crafting_replication_charge", "replication_charge",
                "crafting_ingredient_infernal_animus", "ingredient_infernal_animus",
                "crafting_sungrenade", "sungrenade"
            ),
            chapter(
                "imp_contracts",
                "manual.warlockery.chapter.imp_contracts",
                "ingredient_contract", "crafting_ingredient_contract",
                "crafting_ingredient_contract_fiery_touch", "ingredient_contract_fiery_touch",
                "crafting_ingredient_contract_evaporate", "ingredient_contract_evaporate",
                "crafting_ingredient_contract_resist_fire", "ingredient_contract_resist_fire",
                "crafting_ingredient_contract_smelting", "ingredient_contract_smelting",
                "crafting_ingredient_contract_blaze", "ingredient_contract_blaze",
                "crafting_ingredient_contract_torment", "ingredient_contract_torment"
            ),
            chapter(
                "workshop_devices",
                "manual.warlockery.chapter.workshop_devices",
                "crafting_leech_chest", "leechchest",
                "crafting_shaded_glass", "shadedglass", "shadedglass_active",
                "crafting_beartrap", "device_bear_trap",
                "crafting_wickerbundle", "device_wicker_bundle",
                "rowanwooddoor", "ingredient_door_key", "ingredient_door_keyring"
            ),
            chapter(
                "sympathetic_dolls",
                "manual.warlockery.chapter.sympathetic_dolls",
                "crafting_doll", "doll",
                "crafting_earth_guard_doll", "earth_guard_doll",
                "crafting_water_guard_doll", "water_guard_doll",
                "crafting_hunger_guard_doll", "hunger_guard_doll",
                "crafting_fire_guard_doll", "fire_guard_doll",
                "crafting_tool_mending_doll", "tool_mending_doll",
                "crafting_death_guard_doll", "death_guard_doll",
                "crafting_hex_guard_doll", "hex_guard_doll",
                "crafting_hexing_doll", "hexing_doll",
                "crafting_blood_link_doll", "blood_link_doll",
                "crafting_doll_guard", "doll_guard",
                "crafting_armor_mending_doll", "armor_mending_doll"
            ),
            chapter("enchanted_reagents", "manual.warlockery.chapter.enchanted_reagents",
                "crafting_ingredient_bone_needle", "ingredient_bone_needle",
                "crafting_ingredient_graveyard_dust", "ingredient_graveyard_dust",
                "ingredient_creeper_heart", "crafting_ingredient_rock", "ingredient_rock"),
            chapter(
                "capturing_and_calling",
                "manual.warlockery.chapter.capturing_and_calling",
                "crafting_ingredient_bat_ball",
                "ingredient_bat_ball",
                "spectralstone",
                "ingredient_subdued_spirit",
                "ingredient_subdued_spirit_village",
                "hornofthehunt",
                "ingredient_fool_skull",
                "ingredient_necro_stone",
                "ingredient_soul_of_torment"
            ),
            chapter(
                "spirit_world",
                "manual.warlockery.chapter.spirit_world",
                "spirit_world_entry",
                "spirit_world_laws",
                "spirit_world_harvest",
                "spirit_world_nightmares"
            )
        );
    }

    private static ManualProfile distillingProfile() {
        return groupedProfile(
            "ingredient_book_distilling",
            "distilling",
            chapter(
                "distilling_practice",
                "manual.warlockery.chapter.distilling_practice",
                "crafting_distilleryidle",
                "crafting_altar",
                "power",
                "inputs",
                "outputs",
                "automation"
            ),
            chapter(
                "distilling_recipes",
                "manual.warlockery.chapter.distilling_recipes",
                DISTILLING_RECIPE_SECTIONS.toArray(String[]::new)
            ),
            chapter("portable_altar_power", "manual.warlockery.chapter.portable_altar_power",
                "crafting_ingredient_attuned_stone", "ingredient_attuned_stone",
                "rite_charge_attuned_stone", "ingredient_attuned_stone_charged"),
            chapter("workshop_tools", "manual.warlockery.chapter.workshop_tools", "crafting_spinningwheel", "spinningwheel",
                "machine_recipe_spin_wool", "machine_recipe_spin_fanciful_thread", "machine_recipe_spin_golden_thread",
                "machine_recipe_spin_tormented_twine", "crafting_silvervat", "silvervat", "machine_recipe_silver_vat_silver_dust")
        );
    }

    private static ManualProfile herbologyProfile() {
        return groupedProfile(
            "ingredient_book_herbology",
            "herbology",
            chapter(
                "cultivated_herbs",
                "manual.warlockery.chapter.cultivated_herbs",
                "plant_artichoke",
                "ingredient_artichoke",
                "plant_belladonna",
                "plant_garlic",
                "plant_mandrake",
                "plant_dreamroot",
                "seedsdreamroot",
                "plant_snowbell",
                "plant_wolfsbane",
                "plant_wormwood"
            ),
            chapter(
                "wild_plants",
                "manual.warlockery.chapter.wild_plants",
                "plant_ember_moss",
                "plant_glint_weed",
                "plant_spanish_moss",
                "hex_sapling",
                "vine",
                "plant_somnian_cotton",
                "plant_leaping_lily",
                "plant_blood_rose",
                "plant_bramble",
                "plant_void_bramble", "crafting_voidbramble", "device_void_bramble",
                "crafting_plantmine", "device_plant_mine",
                "plant_grassper",
                "plant_pitgrass",
                "crafting_critter_snare",
                "plant_critter_snare"
            ),
            chapter(
                "mutations",
                "manual.warlockery.chapter.mutations",
                "crafting_mutator",
                "mutator",
                "mutations",
                "toad_mutation",
                "minedrake_mutation",
                "minedrake_bulbs",
                "safe_harvest"
            ),
            chapter(
                "field_tools",
                "manual.warlockery.chapter.field_tools",
                "crafting_canesword", "canesword",
                "crafting_boline",
                "boline",
                "ingredient_icy_needle",
                "ingredient_wolfsbane",
                "crafting_universal_antidote", "universal_antidote",
                "crafting_ingredient_purified_milk", "ingredient_purified_milk",
                "crafting_warm_blood_from_blood_poppy", "ingredient_warm_blood",
                "ingredient_verdant_catalyst", "ingredient_verdant_catalyst_prime", "ingredient_redstone_soup"
            )
        );
    }

    private static ManualProfile infusionProfile() {
        return groupedProfile(
            "ingredient_book_infusions",
            "infusions",
            chapter(
                "infusion_practice",
                "manual.warlockery.chapter.infusion_practice",
                "paths",
                "focus", "arcane_focus", "arcane_focus_targets",
                "infusion_passives", "imp_attunement",
                "infernal_sacrifices", "infernal_sacrifices_movement",
                "reserve"
            ),
            chapter(
                "personal_infusions",
                "manual.warlockery.chapter.personal_infusions",
                "rite_infusion_earth",
                "rite_infusion_ender",
                "rite_infusion_hell",
                "rite_infusion_light",
                "rite_infusion_sky",
                "rite_recharge_infusion"
            ),
            chapter(
                "object_infusions",
                "manual.warlockery.chapter.object_infusions",
                "rite_infuse_brew_grave", "ingredient_brew_grave",
                "rite_infuse_brew_soaring",
                "rite_infuse_broom",
                "rite_infuse_crystal_ball",
                "rite_infuse_mirror",
                "rite_infuse_mystic_branch",
                "rite_infuse_seer_stone",
                "crafting_replication_staff", "replication_staff"
            ),
            chapter(
                "enchanted_clothing",
                "manual.warlockery.chapter.enchanted_clothing",
                "earmuffs", "seepingshoes", "barkbelt", "twisting_band", "iceslippers",
                "bitingbelt", "emberstep_slippers"
            ),
            chapter(
                "patron_equipment",
                "manual.warlockery.chapter.patron_equipment",
                "forgewardens_girdle", "stonebrokers_quiver"
            ),
            chapter(
                "death_disguise",
                "manual.warlockery.chapter.death_disguise",
                "deathscowl", "deathsrobe", "deathsfeet", "deathshand"
            ),
            chapter(
                "brewing_garb",
                "manual.warlockery.chapter.brewing_garb",
                "witchhat", "witchrobe", "hedge_crones_hat", "necromancerrobe"
            ),
            chapter(
                "silver_armor",
                "manual.warlockery.chapter.silver_armor",
                "silverhelm", "silverchestplate", "silverleggings", "silverboots"
            ),
            chapter(
                "hunter_armor",
                "manual.warlockery.chapter.hunter_armor",
                "werewolf_hunter_hat", "werewolf_hunter_coat", "werewolf_hunter_leggings", "werewolf_hunter_boots",
                "werewolf_hunter_hat_silvered", "werewolf_hunter_coat_silvered",
                "werewolf_hunter_leggings_silvered", "werewolf_hunter_boots_silvered",
                "werewolf_hunter_hat_dawn", "werewolf_hunter_coat_dawn",
                "werewolf_hunter_leggings_dawn", "werewolf_hunter_boots_dawn"
            ),
            chapter(
                "hunter_ammunition",
                "manual.warlockery.chapter.hunter_ammunition",
                "crafting_ingredient_bolt_holy", "ingredient_bolt_holy",
                "crafting_ingredient_bolt_stake", "ingredient_bolt_stake",
                "crafting_ingredient_bolt_splitting", "ingredient_bolt_splitting",
                "crafting_ingredient_bolt_anti_magic", "ingredient_bolt_anti_magic",
                "crafting_silver_bolts", "ingredient_bolt_silver"
            )
        );
    }

    private static ManualProfile fumesProfile() {
        return groupedProfile(
            "ingredient_book_oven",
            "fumes",
            chapter(
                "fume_workshop",
                "manual.warlockery.chapter.fume_workshop",
                "crafting_alchemical_oven",
                "crafting_ingredient_soft_clay_jar",
                "crafting_ingredient_clay_jar_from_smelting",
                "oven",
                "jars",
                "funnels",
                "crafting_daylightcollector", "crafting_ingredient_quartz_sphere", "device_sun_collector"
            ),
            chapter(
                "common_fumes",
                "manual.warlockery.chapter.common_fumes",
                recipeSections("oven_logs", "oven_saplings", "oven_fume_breath_of_the_goddess", "oven_fume_hint_of_rebirth")
            ),
            chapter(
                "tree_essences",
                "manual.warlockery.chapter.tree_essences",
                recipeSections(
                    "oven_alder_sapling",
                    "oven_essence_alder",
                    "oven_hawthorn_sapling",
                    "oven_essence_hawthorn",
                    "oven_rowan_sapling",
                    "oven_essence_rowan"
                )
            ),
            chapter(
                "oven_transformations",
                "manual.warlockery.chapter.oven_transformations",
                recipeSections("oven_raw_pork")
            )
        );
    }

    private static ManualProfile symbologyProfile() {
        return groupedProfile(
            "ingredient_book_wands",
            "symbology",
            chapter(
                "foci_and_staves",
                "manual.warlockery.chapter.foci_and_staves",
                "focus", "mysticbranch", "mysticbranch_signs",
                "deflection"
            ),
            chapter(
                "gestures_and_marks",
                "manual.warlockery.chapter.gestures_and_marks",
                "gestures", "rite_infuse_crystal_ball", "device_crystal_ball"
            ),
            chapter("divination_tools", "manual.warlockery.chapter.divination_tools",
                "crafting_divinerwater", "divinerwater", "crafting_divinerlava", "divinerlava",
                "machine_recipe_cauldron_playercompass", "playercompass",
                "crafting_shelfcompass", "shelfcompass",
                "rite_infuse_seer_stone", "ingredient_seer_stone"),
            chapter("travel_tools", "manual.warlockery.chapter.travel_tools",
                "crafting_mirror_block", "mirrorblock", "mirrorblock2", "mirrorwall",
                "rite_infuse_mirror", "mirror", "crafting_ruby_slippers", "ruby_slippers")
        );
    }

    private static ManualProfile groupedProfile(
        final String id,
        final String title,
        final Chapter... chapters
    ) {
        final List<Chapter> supplied = List.of(chapters);
        final Chapter introduction = chapter(
            "introduction",
            "manual.warlockery.preamble.title",
            "preamble"
        );
        final List<Chapter> ordered = Stream.concat(Stream.of(introduction), supplied.stream()).toList();
        return new ManualProfile(
            id,
            title,
            ordered.stream().flatMap(chapter -> chapter.sections().stream()).toList(),
            ordered
        );
    }

    private static Chapter chapter(final String id, final String titleKey, final String... sections) {
        return new Chapter(id, titleKey, List.of(sections));
    }

    private static Map<String, List<String>> groupSections(
        final List<String> sections,
        final Function<String, String> classifier
    ) {
        final Map<String, List<String>> groups = new LinkedHashMap<>();
        sections.forEach(section -> groups.computeIfAbsent(classifier.apply(section), _ -> new java.util.ArrayList<>())
            .add(section));
        return groups;
    }

    private static List<String> entries(final String prefix, final String entries) {
        return Stream.of(entries.split("\\s+"))
            .map(String::strip)
            .filter(entry -> !entry.isEmpty())
            .map(prefix::concat)
            .toList();
    }

    private static String[] recipeSections(final String... recipeIds) {
        return Stream.of(recipeIds).map(MACHINE_RECIPE_PREFIX::concat).toArray(String[]::new);
    }

    private static String biomeChapter(final String section) {
        final String id = section.substring(BIOME_PREFIX.length());
        if (startsWithAny(id, "nether_", "warped_forest", "crimson_forest", "soul_sand_valley", "basalt_deltas")) {
            return "nether_biomes";
        }
        if (startsWithAny(id, "the_end", "end_", "small_end_islands")) {
            return "end_biomes";
        }
        if (id.equals("cherry_grove")) {
            return "green_lands";
        }
        if (containsAny(id, "snow", "ice", "frozen", "peak", "slope") || id.equals("grove")) {
            return "cold_lands_and_peaks";
        }
        if (containsAny(id, "ocean", "river", "beach", "shore", "swamp")) {
            return "waters_and_shores";
        }
        if (containsAny(id, "desert", "savanna", "badlands", "windswept")) {
            return "dry_and_windswept_lands";
        }
        if (containsAny(id, "cave", "deep_dark", "mushroom", "the_void")) {
            return "caves_and_strange_places";
        }
        return "green_lands";
    }

    private static String ritualChapter(final String section) {
        final String id = section.substring(RITUAL_PREFIX.length());
        if (startsWithAny(id, "bind_", "copy_waystone", "charge_attuned", "corrupt_doll")) {
            return "binding_rites";
        }
        if (startsWithAny(id, "summon_", "call_")) {
            return "summoning_rites";
        }
        if (startsWithAny(id, "hex_", "cure_", "blight", "blindness")) {
            return "hexes_and_cures";
        }
        if (startsWithAny(id, "infuse_", "infusion_", "recharge_infusion")) {
            return "infusion_rites";
        }
        if (startsWithAny(id, "fertility", "forestation", "natures_power", "drain_growth", "climate_change",
            "storm", "rain_", "volcano", "cook_food", "ice_shell", "graveyard_mist")) {
            return "land_and_weather";
        }
        if (startsWithAny(id, "teleport_", "transpose_", "part_earth", "raise_earth", "manifestation")) {
            return "passage_rites";
        }
        if (startsWithAny(id, "deathly_", "necro", "spectral_", "anguish_", "prior_incarnation",
            "imprisonment", "fortification_of_the_corpse")) {
            return "death_and_spirits";
        }
        return "greater_rites";
    }

    private static String brewChapter(final String section) {
        final String id = section.substring(BREW_PREFIX.length());
        if (containsAny(id, "heal", "health", "absorption", "regeneration", "fullness", "resistance",
            "remove_debuff", "keep_")) {
            return "restorative_brews";
        }
        if (containsAny(id, "movement", "jump", "float", "fall", "soaring", "air_hike", "swim", "transpose",
            "sinking", "frogs_leg")) {
            return "movement_brews";
        }
        if (containsAny(id, "grow", "fertiliz", "flower", "sapling", "harvest", "plant", "tree", "leaves",
            "land", "vines", "thorns", "web", "blight", "erosion")) {
            return "verdant_brews";
        }
        if (containsAny(id, "fire", "flame", "inferno", "combust", "freeze", "frost", "ice", "snow", "water",
            "lava", "blast", "volatility")) {
            return "elemental_brews";
        }
        if (containsAny(id, "bat", "soul", "spirit", "dead", "undead", "demon", "grave", "raising")) {
            return "spirit_brews";
        }
        if (containsAny(id, "poison", "harm", "wither", "weak", "blind", "paralysis", "infection", "disease",
            "fear", "wasting", "curse", "nightmare", "insanity")) {
            return "baneful_brews";
        }
        if (containsAny(id, "animal_", "werewolf", "vampire", "demonbane", "insect_bane", "toad", "flock",
            "bodega", "abyssal")) {
            return "creature_brews";
        }
        if (containsAny(id, "invisible", "night_vision", "remove_", "stout_belly", "absorb_magic",
            "attract_arrows", "gas_immunity", "ender_inhibition", "reflect_", "repel_attacker", "grotesque")) {
            return "protective_brews";
        }
        if (containsAny(id, "pulverize", "part_", "solidify_", "substitution", "resizing", "shifting_seasons",
            "tint_skin")) {
            return "transmutation_brews";
        }
        return "utility_brews";
    }

    private static boolean startsWithAny(final String value, final String... prefixes) {
        return Stream.of(prefixes).anyMatch(value::startsWith);
    }

    private static boolean containsAny(final String value, final String... fragments) {
        return Stream.of(fragments).anyMatch(value::contains);
    }

    private static boolean isRitualSection(final String section) {
        return section.startsWith(RITUAL_PREFIX);
    }

    private static boolean isBrewSection(final String section) {
        return section.startsWith(BREW_PREFIX);
    }

    private static boolean isBiomeSection(final String section) {
        return section.startsWith(BIOME_PREFIX);
    }

    private static boolean isMachineRecipeSection(final String section) {
        return section.startsWith(MACHINE_RECIPE_PREFIX);
    }

    private static String normalized(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }

    public record Chapter(String id, String titleKey, List<String> sections) {
        public Chapter {
            id = normalized(id, "chapter id");
            titleKey = normalized(titleKey, "chapter title key");
            sections = List.copyOf(sections);
            if (sections.isEmpty() || sections.stream().anyMatch(String::isBlank)
                || Set.copyOf(sections).size() != sections.size()) {
                throw new IllegalArgumentException("A chapter requires unique named subchapters");
            }
        }
    }
}
