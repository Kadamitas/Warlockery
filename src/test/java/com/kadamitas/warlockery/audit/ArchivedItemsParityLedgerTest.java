package com.kadamitas.warlockery.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.registry.ContentCatalog;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class ArchivedItemsParityLedgerTest {
    private static final Set<String> VANILLA_IMPLEMENTATIONS = Set.of(
        "minecraft:crossbow",
        "minecraft:mutton",
        "minecraft:cooked_mutton"
    );

    private static final List<PageCoverage> PAGES = List.of(
        page("Circle Talisman", "circle-talisman", "circletalisman"),
        page("Arthana", "arthana", "ritual_knife"),
        page("Boline", "boline", "boline"),
        page("Spear of the Huntsman", "spear-of-the-huntsman", "thorn_spear"),
        page("Baba Yaga's Hat", "baba-yagas-hat", "hedge_crones_hat"),
        page("Water Diviner", "water-diviner", "divinerwater"),
        page("Lava Diviner", "lava-diviner", "divinerlava"),
        page("Witch Hand", "witches-hand", "arcane_focus"),
        page("Ritual Chalk", "ritual-chalk", "chalkritual", "circleglyphritual"),
        page("Golden Chalk", "golden-chalk", "chalkheart", "circleglyphgolden"),
        page("Otherwhere Chalk", "otherwhere-chalk", "chalk_veil", "circleglyph_veil"),
        page("Infernal Chalk", "infernal-chalk", "chalkinfernal", "circleglyphinfernal"),
        page("Enchanted Broom", "enchanted-broom", "ingredient_broom_enchanted"),
        page("Mutandis", "mutandis", "ingredient_verdant_catalyst"),
        page("Mutandis Extremis", "mutandis-extremis", "ingredient_verdant_catalyst_prime"),
        page("Necromantic Stone", "necromantic-stone", "ingredient_necro_stone"),
        page("Spectral Stone", "spectral-stone", "spectralstone"),
        page("Trapped Plants", "trapped-plants", "plantmine"),
        page("Leaping Lily", "leaping-lily", "leapinglily"),
        page("Witches Hat", "witches-hat", "witchhat"),
        page("Witches Robes", "witches-robes", "witchrobe"),
        page("Necromancers Robes", "necromancers-robes", "necromancerrobe"),
        page("Icy Slippers", "icy-slippers", "iceslippers"),
        page("Ruby Slippers", "ruby-slippers", "ruby_slippers"),
        page("Seeping Shoes", "seeping-shoes", "seepingshoes"),
        page("Biting Belt", "biting-belt", "bitingbelt"),
        page("Bark Belt", "bark-belt", "barkbelt"),
        page("Brew Bag", "brew-bag", "brewbag"),
        page("Polynesia Charm", "polynesia-charm", "beast_speech_charm"),
        page("Devils Tongue Charm", "devils-tongue-charm", "silver_tongue_charm"),
        page("Charm of Fanciful Thoughts", "charm-of-fanciful-thoughts", "ingredient_charm_disrupted_dreams"),
        page("Mystic Branch", "mystic-branch", "mysticbranch"),
        page("Mutating Sprig", "mutating-sprig", "mutator"),
        page("Seer Stone", "seer-stone", "ingredient_seer_stone"),
        page("Apple of Sleeping", "apple-of-sleeping", "ingredient_sleeping_apple"),
        page("Witch Hunter Armo", "witch-hunter-armor", "werewolf_hunter_hat", "werewolf_hunter_coat",
            "werewolf_hunter_leggings", "werewolf_hunter_boots"),
        page("Silvered Hunter Armor", "silvered-hunter-armor", "werewolf_hunter_hat_silvered",
            "werewolf_hunter_coat_silvered", "werewolf_hunter_leggings_silvered", "werewolf_hunter_boots_silvered"),
        page("Hunter Dawn Armor", "hunter-dawn-armor", "werewolf_hunter_hat_dawn", "werewolf_hunter_coat_dawn",
            "werewolf_hunter_leggings_dawn", "werewolf_hunter_boots_dawn"),
        page("Sunlight Grenade", "sunlight-grenade", "sungrenade"),
        page("Silver Sword", "silver-sword", "silversword"),
        page("Witch Hunter Crossbow Pistol", "witch-hunter-crossbow-pistol", "minecraft:crossbow"),
        page("Wooden Bolt", "wooden-bolt", "ingredient_bolt_stake"),
        page("Bone Bolt", "bone-bolt", "ingredient_bolt_holy"),
        page("Splitting Bolt", "splitting-bolt", "ingredient_bolt_splitting"),
        page("Nullifying Bolt", "nullifying-bolt", "ingredient_bolt_anti_magic"),
        page("Poppet Shelf Compass", "poppet-shelf-compass", "shelfcompass"),
        page("Universal Antidote", "universal-antidote", "universal_antidote"),
        page("Hand of Death", "hand-of-death", "deathshand"),
        page("Death's Hood", "deaths-hood", "deathscowl"),
        page("Death's Robe", "deaths-robes", "deathsrobe"),
        page("Death's Footwear", "deaths-footwear", "deathsfeet"),
        page("Binky's Skull", "binkys-skull", "ingredient_fool_skull"),
        page("Demonic Contract", "demonic-contract", "ingredient_contract"),
        page("Mog's Quiver", "mogs-quiver", "stonebrokers_quiver"),
        page("Gulg's Gurdle", "gulgs-gurdle", "forgewardens_girdle"),
        page("Koboldite Pickaxe", "koboldite-pickaxe", "delvealloypickaxe"),
        page("Leonard's Urn", "leonards-urn", "archfiends_urn"),
        page("Anointing Paste", "anointing-paste", "ingredient_annointing_paste"),
        page("Moon Charm", "moon-charm", "mooncharm"),
        page("Horn of the Hunt", "horn-of-the-hunt", "hornofthehunt"),
        page("Lambchop", "raw-lambchop", "minecraft:mutton", "minecraft:cooked_mutton"),
        page("Vampire Clothing", "vampire-clothing", "vampirehat", "vampirecoat", "vampirechaincoat",
            "vampirelegs", "vampireboots"),
        page("Cane Sword", "cane-sword", "canesword"),
        page("Meaty Stew", "meaty-stew", "stewraw", "stew"),
        page("Glass Goblet", "glass-goblet", "glassgoblet"),
        page("Wooden Stake", "wooden-stake", "ingredient_stake"),
        page("Bottle of Warm Blood", "bottle-of-warm-blood", "ingredient_warm_blood"),
        page("Bottle of Lilith's Blood", "bottle-of-liliths-blood", "ingredient_matriarchs_blood"),
        page("Duplication Grenade", "duplication-grenade", "replication_charge"),
        page("Taglock Kit", "taglock-kit", "sympathetic_vial")
    );

    @Test
    void everyArchivedItemsLinkHasConcreteModernCoverage() {
        final Set<String> registered = Stream.of(
                ContentCatalog.BLOCKS.stream().map(ContentCatalog::modernize),
                ContentCatalog.ITEMS.stream().map(ContentCatalog::modernize),
                ContentCatalog.INGREDIENTS.stream().map(ContentCatalog::ingredientId)
            )
            .flatMap(stream -> stream)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertEquals(70, PAGES.size());
        assertEquals(PAGES.size(), PAGES.stream().map(PageCoverage::title).distinct().count());
        assertEquals(PAGES.size(), PAGES.stream().map(PageCoverage::archivedPath).distinct().count());
        PAGES.stream()
            .flatMap(page -> page.implementationIds().stream())
            .forEach(id -> assertTrue(registered.contains(id) || VANILLA_IMPLEMENTATIONS.contains(id),
                () -> "Missing implementation for archived Items coverage anchor: " + id));
    }

    private static PageCoverage page(
        final String title,
        final String archivedPath,
        final String... implementationIds
    ) {
        return new PageCoverage(title, archivedPath, List.of(implementationIds));
    }

    private record PageCoverage(String title, String archivedPath, List<String> implementationIds) {
    }
}
