package com.kadamitas.warlockery.item;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record ManualProfile(String id, String titleKey, List<String> sections) {
    private static final List<ManualProfile> PROFILES = List.of(
        profile("bookbiomes2", "biomes_extended", "overview", "biome_notes", "shifting_rite"),
        profile("cauldronbook", "codex", "custom_brews", "delivery", "diagnostics"),
        profile("vampirebook", "immortal", "initiation", "blood", "weaknesses"),
        profile("ingredient_book_biomes", "biomes", "overview", "biome_notes"),
        profile("ingredient_book_burning", "conjuration", "summoning", "fetishes"),
        profile("ingredient_book_circle_magic", "circles", "chalk", "ritual_ui", "power"),
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
        return "manual.warlockery." + titleKey + "." + section;
    }

    public String translatedSectionTitleKey(final String section) {
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

    private static String normalized(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
