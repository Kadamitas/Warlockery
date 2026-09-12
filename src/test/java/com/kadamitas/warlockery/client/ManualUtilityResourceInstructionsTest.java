package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.item.ManualProfile;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ManualUtilityResourceInstructionsTest {
    private static final Map<String, String> ITEM_BOOKS = Map.ofEntries(
        Map.entry("divinerwater", "ingredient_book_wands"),
        Map.entry("divinerlava", "ingredient_book_wands"),
        Map.entry("playercompass", "ingredient_book_wands"),
        Map.entry("shelfcompass", "ingredient_book_wands"),
        Map.entry("brewbag", "cauldronbook"),
        Map.entry("mirror", "ingredient_book_wands"),
        Map.entry("ingredient_seer_stone", "ingredient_book_wands"),
        Map.entry("ruby_slippers", "ingredient_book_wands"),
        Map.entry("ingredient_attuned_stone", "ingredient_book_distilling"),
        Map.entry("ingredient_attuned_stone_charged", "ingredient_book_distilling"),
        Map.entry("ingredient_bone_needle", "ingredient_book_burning"),
        Map.entry("ingredient_creeper_heart", "ingredient_book_burning"),
        Map.entry("ingredient_graveyard_dust", "ingredient_book_burning"),
        Map.entry("ingredient_artichoke", "ingredient_book_herbology"),
        Map.entry("ingredient_subdued_spirit", "ingredient_book_burning"),
        Map.entry("ingredient_subdued_spirit_village", "ingredient_book_burning"),
        Map.entry("mutator", "ingredient_book_herbology"),
        Map.entry("seedsdreamroot", "ingredient_book_herbology"),
        Map.entry("ingredient_rock", "ingredient_book_burning"),
        Map.entry("biomenote", "ingredient_book_biomes"),
        Map.entry("replication_staff", "ingredient_book_infusions"),
        Map.entry("replication_charge", "ingredient_book_burning"),
        Map.entry("universal_antidote", "ingredient_book_herbology"),
        Map.entry("ingredient_purified_milk", "ingredient_book_herbology"),
        Map.entry("ingredient_warm_blood", "ingredient_book_herbology"),
        Map.entry("ingredient_infernal_animus", "ingredient_book_burning"),
        Map.entry("sungrenade", "ingredient_book_burning"),
        Map.entry("ingredient_soul_of_torment", "ingredient_book_burning")
    );

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void toolsHaveReachableItemNamedUsageWithTheActualItemPicture() throws Exception {
        final JsonObject english = english();
        assertEquals(28, ITEM_BOOKS.size());
        ITEM_BOOKS.forEach((item, book) -> {
            final var profile = ManualProfile.find(book).orElseThrow();
            assertEquals(1, profile.sections().stream().filter(item::equals).count(), item);
            assertEquals(english.get("item.warlockery." + item).getAsString() + ": Use",
                english.get(profile.translatedSectionTitleKey(item)).getAsString(), item);
            final String body = english.get(profile.translatedSectionKey(item)).getAsString();
            assertTrue(body.contains("\n\n"), item + " needs readable use and conditions");
            assertFalse(body.contains("\\n"), item + " must contain real paragraph breaks");
            assertEquals("warlockery:" + item,
                ManualArticleCatalog.article(profile, item).pictograms().getFirst().itemId(), item);
        });
    }

    @Test
    void utilityCraftingAndInfusionPagesAreIndexedAlongsideNamedUsage() {
        final var tools = ManualProfile.find("ingredient_book_wands").orElseThrow();
        for (String item : List.of("divinerwater", "divinerlava", "shelfcompass", "ruby_slippers")) {
            final String section = "crafting_" + item;
            assertTrue(tools.sections().contains(section), section);
            assertEquals("warlockery:" + item, ManualArticleCatalog.article(tools, section).pictograms().getFirst().itemId());
        }
        assertTrue(tools.sections().containsAll(List.of(
            "crafting_mirror_block", "rite_infuse_mirror", "rite_infuse_seer_stone", "machine_recipe_cauldron_playercompass")));
        assertEquals("item.warlockery.mirrorblock", tools.translatedSectionTitleKey("crafting_mirror_block"));
        assertEquals("warlockery:mirrorblock",
            ManualArticleCatalog.article(tools, "crafting_mirror_block").pictograms().getFirst().itemId());
        final var brews = ManualProfile.find("cauldronbook").orElseThrow();
        assertTrue(brews.sections().contains("crafting_brewbag"));
        assertEquals("warlockery:brewbag",
            ManualArticleCatalog.article(brews, "crafting_brewbag").pictograms().getFirst().itemId());
    }

    @Test
    void divinersAndCompassesExplainBindingTargetsAndSearchLimits() throws Exception {
        for (String item : List.of("divinerwater", "divinerlava"))
            require(item, "use it on a ground block", "exact vertical column", "one durability",
                "unsuccessful", "128 uses", "does not search sideways");
        require("playercompass", "directly on another player", "same dimension", "stale", "replaces", "Cauldron");
        require("shelfcompass", "crouch", "exact shelf position", "does not automatically choose the nearest shelf",
            "moved", "same dimension");
        final String gestures = english().get("manual.warlockery.symbology.gestures").getAsString();
        assertFalse(gestures.contains("seeks the nearest compatible shelf"));
    }

    @Test
    void storageDivinationAndTravelNameTheirControlsAndCosts() throws Exception {
        require("brewbag", "normal click", "right-click an empty", "one selected brew", "reusable bag",
            "drink-only", "cannot be launched");
        require("mirror", "use it on the block", "use it in the air", "one durability", "128 uses",
            "unbound", "another full Mirror block", "clear landing space");
        require("ingredient_seer_stone", "in the air", "other hand", "one oil", "Golden Chalk center",
            "loaded", "needs no altar charge");
        require("ruby_slippers", "in your hand", "Merely wearing", "five-minute cooldown", "not consumed",
            "missing or obstructed", "world spawn");
    }

    @Test
    void reagentsExplainTheirSeparateUsesAndConsumption() throws Exception {
        require("ingredient_attuned_stone", "250", "2,000", "reserved", "starts empty", "does not change its item type");
        require("ingredient_attuned_stone_charged", "starts with 2,000", "250", "any stored power", "consume the offered stone");
        for (String item : List.of("ingredient_attuned_stone", "ingredient_attuned_stone_charged", "ingredient_subdued_spirit"))
            require(item, "eight Ritual Chalk", "without a Golden Chalk center", "three seconds",
                "32 blocks", "Overworld", "Nether", "pick", "again");
        require("ingredient_subdued_spirit_village", "distance", "another dimension", "consumes nothing", "does not", "teleport");
        require("ingredient_bone_needle", "Hexing Doll", "offhand", "main hand", "consumes one", "protections", "unavailable");
        require("ingredient_creeper_heart", "Bramble Colossus", "100", "fully heals", "even when full", "explosion", "blocks intact");
        require("ingredient_graveyard_dust", "bound to you", "50", "two", "Unowned", "does not establish ownership");
        require("ingredient_artichoke", "Hunger III", "three seconds per", "level-ten", "completely full blood",
            "all stored blood", "five charges", "Observations of an Immortal");
        require("ingredient_rock", "throw one", "no direct health damage", "Blaze", "three damage");
    }

    @Test
    void mutationAndDreamrootInstructionsMatchTheActualPreparation() throws Exception {
        require("mutator", "Mine", "water immediately above", "one durability", "128",
            "at least four slime-filled", "at least four bat-filled", "four mature Mandrakes",
            "four blocks horizontally", "stone is consumed");
        require("seedsdreamroot", "farmland", "consuming one bulb", "three seconds", "at most four", "does not tame");
        final var english = english();
        assertTrue(english.get("manual.warlockery.herbology.toad_mutation").getAsString().contains("at least four"));
        for (String key : List.of("plant_dreamroot", "minedrake_bulbs", "crops"))
            assertFalse(english.get("manual.warlockery.herbology." + key).getAsString().contains("hunt"), key);
    }

    @Test
    void separateBrewWorkingsHaveAVisibleParagraphBreak() {
        final var profile = ManualProfile.find("cauldronbook").orElseThrow();
        final String text = ManualArticleCatalog.article(profile, "brew_entry_raising").body().getString();
        final String summoning = net.minecraft.network.chat.Component.translatableWithFallback(
            "manual.warlockery.brew.behavior.raise_dead", "Raise dead").getString();
        final String strengthening = net.minecraft.network.chat.Component.translatableWithFallback(
            "manual.warlockery.brew.behavior.buff_undead", "Buff undead").getString();
        assertTrue(text.contains(summoning + "\n\n" + strengthening));
    }

    @Test
    void remainingToolsDescribeRealTargetsChargeLimitsAndSafeAcquisitionLinks() throws Exception {
        require("replication_staff", "512 blocks", "third use", "Crouch-use", "no materials", "replace blocks");
        assertFalse(english().get("manual.warlockery.symbology.deflection").getAsString().contains("spends Replication Charges"));
        require("replication_charge", "three blocks horizontally", "Glass Doppelganger", "does not copy",
            "even if there is no nearby target");
        require("universal_antidote", "Poison and Wither", "does not remove other", "Glass Bottle");
        require("ingredient_purified_milk", "only one active effect", "Harmful effects", "beneficial", "Glass Bottle");
        require("ingredient_warm_blood", "20 blood reserve", "Hunger II", "30 seconds", "does not turn", "Glass Bottle");
        require("ingredient_infernal_animus", "another player", "Naamah are immune", "32 blocks", "consumes one");
        require("sungrenade", "5.2", "Undead and vampires", "double", "including you", "Sun Collector");
        require("ingredient_soul_of_torment", "Abyssal Regent", "not a player", "three-second cooldown",
            "64 durability", "not a kill", "Brew of Summon Abyssal Regent");
        require("biomenote", "paper, an ink sac and a compass", "Book of Biomes", "crouch", "ordinary use opens");
        for (String book : List.of("ingredient_book_biomes", "bookbiomes2")) {
            final var profile = ManualProfile.find(book).orElseThrow();
            assertTrue(profile.sections().contains("biomenote"));
            assertTrue(profile.sections().contains("crafting_biomenote"));
            assertEquals("warlockery:biomenote",
                ManualArticleCatalog.article(profile, "crafting_biomenote").pictograms().getFirst().itemId());
            assertTrue(english().has(profile.translatedSectionKey("biomenote")));
        }
    }

    private static void require(final String item, final String... phrases) throws Exception {
        final var profile = ManualProfile.find(ITEM_BOOKS.get(item)).orElseThrow();
        final String body = english().get(profile.translatedSectionKey(item)).getAsString().toLowerCase(java.util.Locale.ROOT);
        for (String phrase : phrases) assertTrue(body.contains(phrase.toLowerCase(java.util.Locale.ROOT)), item + ": " + phrase);
    }

    private static JsonObject english() throws Exception {
        try (var stream = ManualUtilityResourceInstructionsTest.class.getResourceAsStream("/assets/warlockery/lang/en_us.json")) {
            assertNotNull(stream);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
