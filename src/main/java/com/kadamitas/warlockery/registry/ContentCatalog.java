package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.brew.BrewFactory;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ContentCatalog {
    private static final Pattern CAMEL_CASE = Pattern.compile("([a-z0-9])([A-Z])");

    public static final List<String> BLOCKS = words("""
        alderwooddoor alluringskull altar artichoke barrier beartrap belladonna bloodcrucible bloodedwool bloodrose
        bramble brazier brew brewgas brewliquid erosionbrew candelabra cauldron chalice circle
        circleglyphinfernal circleglyph_veil circleglyphritual coffinblock crittersnare crystalball
        daylightcollector broken_hexes_statue
        occluded_summons_statue demonheart disease distilleryburning distilleryidle dreamcatcher embermoss filteredfumefunnel
        force fumefunnel garlicgarland garlicplant glintweed glowglobe grassper hollowtears icedoor icedoubleslab
        icefence icefencegate icepressureplate iceslab icestairs icestockade paradox_egg kettle leapinglily leechchest pentacle
        light mandrake dreamroot mirrorblock mirrorblock2 mirrorwall perpetualice pitdirt pitgrass placeditem plantmine
        doll_shelf refillingchest rowanwooddoor scarecrow shadedglass shadedglass_active silvervat snowbell
        snowdoubleslab snowpressureplate snowslab snowstairs somniancotton spanishmoss spinningwheel spiritflowing
        spiritportal stairswoodalder stairswoodhawthorn stairswoodrowan statuegoddess statueofworship stockade
        abyssal_portal abyssal_stone trent vine voidbramble wallgen web wickerbundle alchemical_oven_lit alchemical_oven
        hex_leaves hex_log hex_sapling hex_ladder hexwood hexwooddoubleslab hexwoodslab wolfaltar wolfhead
        wolfsbane wolftrap wormwood silverOre deepslateSilverOre rawSilverBlock silverBlock delvealloyOre
        deepslateDelvealloyOre rawDelvealloyBlock delvealloyBlock
        alder_log alder_planks alder_leaves alder_sapling hawthorn_log hawthorn_planks hawthorn_leaves hawthorn_sapling
        rowan_log rowan_planks rowan_leaves rowan_sapling
        """);

    public static final List<String> ITEMS = words("""
        ritual_knife hedge_crones_hat barkbelt biomenote bitingbelt boline bookbiomes2 brew.fuel brew.water brewbag
        bucketbrew bucketerosionbrew buckethollowtears bucketspirit canesword cauldronbook chalkheart chalkinfernal chalk_veil
        chalkritual circletalisman coffin deathscowl deathsfeet deathshand deathsrobe silver_tongue_charm divinerlava
        divinerwater replication_charge replication_staff earmuffs garlic glassgoblet forgewardens_girdle hornofthehunt werewolf_hunter_boots
        werewolf_hunter_boots_dawn werewolf_hunter_boots_silvered werewolf_hunter_coat werewolf_hunter_coat_dawn werewolf_hunter_coat_silvered werewolf_hunter_hat
        werewolf_hunter_hat_dawn werewolf_hunter_hat_silvered werewolf_hunter_leggings werewolf_hunter_leggings_dawn werewolf_hunter_leggings_silvered thorn_spear
        icedoubleslab iceslab iceslippers delvealloysword delvealloyaxe delvealloypickaxe delvealloyshovel
        delvealloyhoe delvealloyhelm delvealloychestplate delvealloyleggings delvealloyboots archfiends_urn louse mirror mooncharm
        mutator mysticbranch necromancerrobe playercompass beast_speech_charm doll earth_guard_doll water_guard_doll
        hunger_guard_doll fire_guard_doll tool_mending_doll death_guard_doll hex_guard_doll hexing_doll
        blood_link_doll doll_guard armor_mending_doll stonebrokers_quiver emberstep_slippers
        seedsartichoke seedsbelladonna seedsmandrake seedsdreamroot seedssnowbell seedswolfsbane seedswormwood
        seepingshoes shelfcompass silversword snowdoubleslab snowslab spectralstone stew stewraw sungrenade sympathetic_vial
        vampirebook vampireboots vampirechaincoat vampirechaincoat_female vampirecoat vampirecoat_female vampirehat
        vampirehelmet vampirelegs vampirelegs_kilt arcane_focus witchhat witchrobe hexwooddoubleslab hexwoodslab wolftoken
        rawSilver silverIngot rawDelvealloy ruby_slippers hellhound_head twisting_band
        """);

    public static final List<String> BREWS = BrewFactory.ids();

    public static final List<String> INGREDIENTS = words("""
        annointingPaste appleWormy artichoke ashWood attunedStone attunedStoneCharged batBall batWool belladonna
        berriesRowan foolSkull boltAntiMagic boltHoly boltSilver boltSplitting boltStake boneNeedle bookBiomes
        bookBurning bookCircleMagic bookDistilling bookHerbology bookInfusions bookOven bookWands breathOfTheGoddess
        brewBats brewCongealedSpirit brewHexedLeaping brewDepths brewErosion brewFrogsTongue brewGrave brewGrotesque
        brewHitchcock brewIce brewInfection brewInk brewLove brewRaising brewRevealing brewSleep brewSoaring
        brewSolidDirt brewSolidErosion brewSolidSand brewSolidSandstone brewSolidStone brewSoulAnguish brewSoulFear
        brewSoulHunger brewSoulTorment brewSprouting brewSubstitution brewThorns brewVines brewWasting brewWeb broom
        broomEnchanted candelabra chalice chaliceFull charmDisruptedDreams clayJar clayJarSoft condensedFear contract
        contractBlaze contractEvaporate contractFieryTouch contractResistFire contractSmelting contractTorment
        creeperHeart darkCloth diamondVapour disturbedCotton dogTongue doorAlder doorIce doorKey doorKeyring doorRowan
        dropOfLuck enderDew exhaleOfTheHornedOne fancifulThread flyingOintment focusedWill foulFume
        frozenHeart fumeFilter ghostOfTheLight goldenThread graveyardDust gypsum happenstanceOil heartofgold hintOfRebirth infernalAnimus
        heartwoodSplinter icyNeedle impregnatedLeather infernalBlood infusionBase delvealloydust delvealloyingot delvealloynugget matriarchsBlood
        mandrakeRoot mellifluousHunger verdantCatalyst verdantCatalystPrime mysticunguent necroStone
        nullcatalyst nullifiedleather oddPorkchopCooked oddPorkchopRaw odourOfPurity oilOfVitriol owletsWing pentacle
        purifiedMilk quartzSphere quicklime redstoneSoup reekOfMisfortune refinedEvil rock brambleColossusSeed seerStone
        silverdust sleepingApple soulOfTheWorld soulOfTorment spectralDust spiritOfTheVeil stake subduedSpirit subduedSpiritVillage
        tearOfTheGoddess toeOfFrog tormentedTwine vbookPage warmBlood waystone waystoneBound waystoneCreatureBound web
        whiffOfMagic wolfsbane wormwood wovenCruor
        """);

    public static final Set<String> CROPS = Set.of(
        "artichoke", "belladonna", "garlicplant", "mandrake", "dreamroot", "snowbell", "wolfsbane", "wormwood"
    );

    public static final Set<String> NON_SOLID = Set.of(
        "barrier", "bloodrose", "bramble", "brewgas", "brewliquid", "erosionbrew", "circle", "circleglyphinfernal",
        "circleglyph_veil", "circleglyphritual", "crittersnare", "disease", "embermoss", "force",
        "glintweed", "grassper", "leapinglily", "light", "placeditem", "plantmine", "somniancotton",
        "spanishmoss", "spiritflowing", "spiritportal", "abyssal_portal", "vine", "wallgen"
    );

    private ContentCatalog() {
    }

    public static String modernize(final String catalogName) {
        return CAMEL_CASE.matcher(catalogName).replaceAll("$1_$2").toLowerCase(Locale.ROOT);
    }

    public static String ingredientId(final String catalogName) {
        return "ingredient_" + modernize(catalogName);
    }

    private static List<String> words(final String values) {
        return Pattern.compile("\\s+").splitAsStream(values.strip())
            .filter(value -> !value.isBlank())
            .toList();
    }
}
