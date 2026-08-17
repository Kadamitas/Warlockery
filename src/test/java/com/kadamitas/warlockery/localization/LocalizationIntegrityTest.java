package com.kadamitas.warlockery.localization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.block.DreamWeaverMode;
import com.kadamitas.warlockery.block.FetishMode;
import com.kadamitas.warlockery.block.PlantMinePayload;
import com.kadamitas.warlockery.brew.custom.CustomBrewDelivery;
import com.kadamitas.warlockery.brew.custom.CustomBrewFailure;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.entity.EntEntity;
import com.kadamitas.warlockery.item.DollHexAction;
import com.kadamitas.warlockery.magic.MagicPath;
import com.kadamitas.warlockery.magic.SymbolSpell;
import com.kadamitas.warlockery.registry.ContentCatalog;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalPower;
import com.kadamitas.warlockery.transformation.VampireProgressionRules;
import com.kadamitas.warlockery.transformation.WerewolfProgressionRules;
import com.kadamitas.warlockery.transformation.WerewolfShape;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class LocalizationIntegrityTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path ASSETS = RESOURCES.resolve("assets/warlockery");
    private static final Path DATA = RESOURCES.resolve("data/warlockery");
    private static final Path JAVA = Path.of("src", "main", "java");
    private static final List<String> LOCALES = List.of(
        "en_us", "fr_fr", "es_es", "pt_br", "de_de", "pl_pl", "ja_jp", "ko_kr", "ru_ru", "tr_tr",
        "zh_cn", "zh_tw"
    );
    private static final Map<String, Pattern> ORDINARY_DRINK_TERMS = Map.ofEntries(
        Map.entry("de_de", words("bier", "kaffee", "tee")),
        Map.entry("es_es", words("cerveza", "cervecero", "café", "té")),
        Map.entry("fr_fr", words("bière", "café", "thé")),
        Map.entry("ja_jp", Pattern.compile("ビール|コーヒー|お茶|紅茶")),
        Map.entry("ko_kr", Pattern.compile("맥주|커피|홍차|녹차")),
        Map.entry("pl_pl", words("piwo", "piwny", "kawa", "herbata")),
        Map.entry("pt_br", words("cerveja", "café", "chá")),
        Map.entry("ru_ru", words("пиво", "пивной", "кофе", "чай")),
        Map.entry("tr_tr", words("bira", "kahve", "çay")),
        Map.entry("zh_cn", Pattern.compile("啤酒|咖啡|茶")),
        Map.entry("zh_tw", Pattern.compile("啤酒|咖啡|茶"))
    );
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[a-zA-Z%]");
    private static final Pattern TRANSLATION_SHAPED_STRING = Pattern.compile(
        "\"((?:block|container|custom_brew_delivery|doll_action|dream_weaver_mode|entity|fetish_mode|fluid_type|"
            + "item|itemGroup|jei|magic_path|manual|message|overlay|plant_mine_payload|ritual|ritual.requirement|"
            + "screen|subtitle|supernatural_form|tooltip)\\.warlockery\\.[a-zA-Z0-9_.-]+)\""
    );
    private static final Pattern ENGLISH_LITERAL = Pattern.compile("Component\\.literal\\(\"([^\"]*[A-Za-z][^\"]*)\"\\)");
    private static final Map<String, String> ENGLISH = locale("en_us");

    @Test
    void supportedLocalesHaveExactKeyAndPlaceholderParity() {
        for (String locale : LOCALES) {
            final Map<String, String> translations = locale(locale);
            assertEquals(ENGLISH.keySet(), translations.keySet(), locale + " must match the English key set");
            ENGLISH.forEach((key, english) -> {
                final String translated = translations.get(key);
                assertFalse(translated.isBlank(), locale + " has a blank value for " + key);
                assertFalse(translated.contains("\uFFFD"), locale + " has a replacement character for " + key);
                assertFalse(translated.contains("—"), locale + " has an em dash in " + key);
                assertEquals(
                    placeholders(english),
                    placeholders(translated),
                    locale + " changes the format placeholders for " + key
                );
            });
        }
    }

    @Test
    void onlyTheIntendedLocaleFilesArePackaged() throws IOException {
        try (Stream<Path> paths = Files.list(ASSETS.resolve("lang"))) {
            final Set<String> packaged = paths
                .filter(path -> path.toString().endsWith(".json"))
                .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                .collect(Collectors.toUnmodifiableSet());
            assertEquals(Set.copyOf(LOCALES), packaged);
            assertFalse(packaged.contains("uk_ua"));
        }
    }

    @Test
    void magicalBrewsAreNeverTranslatedAsOrdinaryDrinks() {
        ORDINARY_DRINK_TERMS.forEach((locale, forbidden) -> locale(locale).entrySet().stream()
            .filter(entry -> entry.getKey().toLowerCase(java.util.Locale.ROOT).contains("brew"))
            .forEach(entry -> assertFalse(
                forbidden.matcher(entry.getValue()).find(),
                locale + " describes " + entry.getKey() + " as an ordinary drink: " + entry.getValue()
            )));
    }

    @Test
    void everyBuiltInRitualUsesLocalizedTitleAndDescriptionKeys() throws IOException {
        try (Stream<Path> paths = Files.list(DATA.resolve("ritual"))) {
            paths.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
                final String id = path.getFileName().toString().replaceFirst("\\.json$", "");
                final JsonObject ritual = json(path);
                final String title = "ritual.warlockery." + id + ".title";
                final String description = "ritual.warlockery." + id + ".description";
                assertEquals(title, ritual.get("title").getAsString(), id + " title must be a stable key");
                assertEquals(description, ritual.get("description").getAsString(), id + " description must be a stable key");
                assertTrue(ENGLISH.containsKey(title), id + " is missing its English title");
                assertTrue(ENGLISH.containsKey(description), id + " is missing its English description");
                assertFalse(ENGLISH.get(title).equals(title), id + " title must resolve to prose");
                assertFalse(ENGLISH.get(description).equals(description), id + " description must resolve to prose");
            });
        }
    }

    @Test
    void registeredAndResourceBackedContentHasEnglishNames() throws IOException {
        ContentCatalog.BLOCKS.stream()
            .map(ContentCatalog::modernize)
            .forEach(id -> assertKey("block.warlockery." + id));
        Stream.concat(
            Stream.concat(
                ContentCatalog.ITEMS.stream().map(ContentCatalog::modernize),
                ContentCatalog.INGREDIENTS.stream().map(ContentCatalog::ingredientId)
            ),
            ContentCatalog.BREWS.stream()
        ).forEach(id -> assertKey("item.warlockery." + id));
        assertDynamicAssetKeys(ASSETS.resolve("blockstates"), "block.warlockery.");
        assertItemAssetKeys();
        registeredEntityIds().forEach(id -> {
            assertKey("entity.warlockery." + id);
            assertKey("item.warlockery." + id + "_spawn_egg");
        });
        Set.of("spirit", "hollow_tears", "colored_brew_water", "erosion_brew")
            .forEach(id -> assertKey("fluid_type.warlockery." + id));
        json(ASSETS.resolve("sounds.json")).entrySet().stream()
            .map(Map.Entry::getValue)
            .map(value -> value.getAsJsonObject().get("subtitle").getAsString())
            .forEach(LocalizationIntegrityTest::assertKey);
    }

    @Test
    void itemAndBlockNamesNeverExposeTranslationKeys() {
        ENGLISH.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith("item.warlockery.")
                || entry.getKey().startsWith("block.warlockery."))
            .forEach(entry -> {
                assertFalse(entry.getValue().equals(entry.getKey()), entry.getKey());
                assertFalse(entry.getValue().startsWith("item.warlockery."), entry.getKey());
                assertFalse(entry.getValue().startsWith("block.warlockery."), entry.getKey());
            });
    }

    @Test
    void JavaTranslationKeysAndDynamicEnumFamiliesAreComplete() throws IOException {
        try (Stream<Path> paths = Files.walk(JAVA)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .map(LocalizationIntegrityTest::read)
                .flatMap(source -> TRANSLATION_SHAPED_STRING.matcher(source).results())
                .map(match -> match.group(1))
                .filter(key -> !key.endsWith("."))
                .forEach(LocalizationIntegrityTest::assertKey);
        }
        Stream.of(SupernaturalForm.values())
            .map(value -> "supernatural_form.warlockery." + value.name().toLowerCase(java.util.Locale.ROOT))
            .forEach(LocalizationIntegrityTest::assertKey);
        Stream.of(SupernaturalPower.values())
            .map(SupernaturalPower::translationKey)
            .forEach(LocalizationIntegrityTest::assertKey);
        Stream.of(WerewolfShape.values())
            .map(value -> "shape.warlockery." + value.name().toLowerCase(java.util.Locale.ROOT))
            .forEach(LocalizationIntegrityTest::assertKey);
        VampireProgressionRules.quests().stream()
            .map(quest -> "quest.warlockery.vampire." + quest.id())
            .forEach(LocalizationIntegrityTest::assertKey);
        WerewolfProgressionRules.quests().stream()
            .map(quest -> "quest.warlockery.werewolf." + quest.id())
            .forEach(LocalizationIntegrityTest::assertKey);
        Stream.of(VampireProgressionRules.Diagnostic.values())
            .map(VampireProgressionRules.Diagnostic::messageKey)
            .forEach(LocalizationIntegrityTest::assertKey);
        Stream.of(WerewolfProgressionRules.Diagnostic.values())
            .map(WerewolfProgressionRules.Diagnostic::messageKey)
            .forEach(LocalizationIntegrityTest::assertKey);
        Set.of(
            "key.category.warlockery.supernatural",
            "key.warlockery.cycle_power",
            "key.warlockery.activate_power",
            "overlay.warlockery.supernatural.level",
            "overlay.warlockery.supernatural.passive",
            "overlay.warlockery.supernatural.power",
            "overlay.warlockery.supernatural.complete",
            "path.warlockery.vampire",
            "path.warlockery.werewolf",
            "message.warlockery.moon_charm.locked",
            "message.warlockery.moon_charm.wolfman_locked",
            "message.warlockery.moon_charm.shifted",
            "message.warlockery.progression.level_up",
            "message.warlockery.progression.updated"
        ).forEach(LocalizationIntegrityTest::assertKey);
        Stream.of(MagicPath.values())
            .map(value -> "magic_path.warlockery." + value.id())
            .forEach(LocalizationIntegrityTest::assertKey);
        SymbolSpell.VALUES.stream()
            .map(SymbolSpell::translationKey)
            .forEach(LocalizationIntegrityTest::assertKey);
        Stream.of(CustomBrewDelivery.values())
            .map(value -> "custom_brew_delivery.warlockery." + value.id())
            .forEach(LocalizationIntegrityTest::assertKey);
        Stream.of(PlantMinePayload.values())
            .map(value -> "plant_mine_payload.warlockery." + value.getSerializedName())
            .forEach(LocalizationIntegrityTest::assertKey);
        DreamWeaverMode.VALUES.stream()
            .map(value -> "dream_weaver_mode.warlockery." + value.getSerializedName())
            .forEach(LocalizationIntegrityTest::assertKey);
        FetishMode.VALUES.stream()
            .map(value -> "fetish_mode.warlockery." + value.getSerializedName())
            .forEach(LocalizationIntegrityTest::assertKey);
        Stream.of(DollHexAction.values())
            .map(value -> "doll_action.warlockery." + value.id())
            .forEach(LocalizationIntegrityTest::assertKey);
        Stream.of(EntEntity.EntVariant.values())
            .map(value -> "entity.warlockery.ent.variant." + value.serializedName())
            .forEach(LocalizationIntegrityTest::assertKey);
        goblinProfessionIds().stream()
            .map(value -> "entity.warlockery.hobgoblin.profession." + value)
            .forEach(LocalizationIntegrityTest::assertKey);
        Stream.of(CustomBrewFailure.values())
            .map(value -> "overlay.warlockery.custom_brew.failure." + value.id())
            .forEach(LocalizationIntegrityTest::assertKey);
        MachineProfiles.blockIds().stream()
            .map(MachineProfiles::forBlock)
            .map(profile -> "container.warlockery." + profile.recipeType())
            .forEach(LocalizationIntegrityTest::assertKey);
        Set.of(
            "selected_hex_present", "bound_hex_target", "owned_familiar", "nearby_spectral",
            "bound_sympathetic_sample", "nearby_familiar", "nearby_volcanic_fluid", "recorded_biome_book",
            "climate_seer_stone", "climate_participants", "climate_nether_stars",
            "recoverable_death_drops", "bloodied_wicker_structure", "ritual_inhibitors", "bound_sleeping_target",
            "sleeping_target", "unmanifested_target", "manifestation_ready", "night", "day", "full_moon",
            "rain", "thunder"
        ).stream()
            .map(value -> "screen.warlockery.ritual.requirement." + value)
            .forEach(LocalizationIntegrityTest::assertKey);
    }

    @Test
    void playerFacingJavaDoesNotEmbedEnglishComponentLiterals() throws IOException {
        final Map<Path, List<String>> violations;
        try (Stream<Path> paths = Files.walk(JAVA)) {
            violations = paths.filter(path -> path.toString().endsWith(".java"))
                .map(path -> Map.entry(path, ENGLISH_LITERAL.matcher(read(path)).results()
                    .map(MatchResult::group)
                    .toList()))
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        assertTrue(violations.isEmpty(), () -> "English Component literals remain: " + violations);
    }

    private static void assertDynamicAssetKeys(final Path directory, final String prefix) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            paths.filter(path -> path.toString().endsWith(".json"))
                .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                .map(prefix::concat)
                .forEach(LocalizationIntegrityTest::assertKey);
        }
    }

    private static void assertItemAssetKeys() throws IOException {
        try (Stream<Path> paths = Files.list(ASSETS.resolve("items"))) {
            paths.filter(path -> path.toString().endsWith(".json"))
                .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                .forEach(id -> assertTrue(
                    ENGLISH.containsKey("item.warlockery." + id) || ENGLISH.containsKey("block.warlockery." + id),
                    id + " has no item or block translation"
                ));
        }
    }

    private static Set<String> registeredEntityIds() {
        final String source = read(JAVA.resolve("com/kadamitas/warlockery/registry/ModEntities.java"));
        final Pattern pattern = Pattern.compile("(?:Map\\.entry|register|hobgoblin)\\(\"([a-z0-9_]+)\"");
        return pattern.matcher(source).results().map(result -> result.group(1)).collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> goblinProfessionIds() {
        final String source = read(JAVA.resolve("com/kadamitas/warlockery/entity/GoblinProfession.java"));
        final Pattern pattern = Pattern.compile("[A-Z_]+\\(\"([a-z_]+)\", Blocks\\.");
        final Set<String> ids = pattern.matcher(source).results()
            .map(result -> result.group(1))
            .collect(Collectors.toUnmodifiableSet());
        assertFalse(ids.isEmpty(), "Goblin professions must remain discoverable by the localization audit");
        return ids;
    }

    private static List<String> placeholders(final String value) {
        return PLACEHOLDER.matcher(value).results().map(MatchResult::group).toList();
    }

    private static Pattern words(final String... terms) {
        return Pattern.compile(
            "(?iu)(?<!\\p{L})(?:" + Stream.of(terms).map(Pattern::quote).collect(Collectors.joining("|"))
                + ")(?!\\p{L})"
        );
    }

    private static void assertKey(final String key) {
        assertTrue(ENGLISH.containsKey(key), "Missing English translation: " + key);
    }

    private static Map<String, String> locale(final String locale) {
        final JsonObject object = json(ASSETS.resolve("lang").resolve(locale + ".json"));
        return object.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey,
            entry -> entry.getValue().getAsString(),
            (_, replacement) -> replacement,
            LinkedHashMap::new
        ));
    }

    private static JsonObject json(final Path path) {
        return JsonParser.parseString(read(path)).getAsJsonObject();
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
