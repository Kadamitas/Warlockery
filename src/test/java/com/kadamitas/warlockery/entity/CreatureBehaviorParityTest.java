package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CreatureBehaviorProfile.Feature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class CreatureBehaviorParityTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");
    private static final Path LANGUAGE = Path.of(
        "src", "main", "resources", "assets", "warlockery", "lang", "en_us.json"
    );
    private static final UUID PLAYER = UUID.fromString("f219c6f9-2434-4bf1-a1cc-96b6e39552cd");
    private static final UUID OTHER = UUID.fromString("86cb633e-2a18-49e8-a2df-44355864c449");
    private static final List<MobCase> CASES = List.of(
        mob("baba_yaga", CreatureKind.HEDGE_CRONE, Feature.POTION_VOLLEY),
        mob("banshee", CreatureKind.BANSHEE, Feature.DUST_EMPOWERMENT),
        mob("pale_steed", CreatureKind.PALE_STEED, Feature.RIDEABLE_BOND),
        mob("circle_mage", CreatureKind.CIRCLE_MAGE, Feature.COVEN_RECRUITMENT),
        mob("death", CreatureKind.DEATH, Feature.DEATH_DISGUISE),
        mob("demon", CreatureKind.DEMON, Feature.INFERNAL_BARTER),
        mob("ent", CreatureKind.ENT, Feature.BIOME_VARIANTS),
        mob("familiar_cat", CreatureKind.CAT, Feature.FAMILIAR_BOND),
        mob("imp", CreatureKind.IMP, Feature.FIRE_MELEE),
        mob("storm_simian", CreatureKind.STORM_SIMIAN, Feature.WAYSTONE_TRAVEL),
        mob("forgewarden", CreatureKind.FORGEWARDEN, Feature.FORGE_AURA),
        mob("hellhound", CreatureKind.HELLHOUND, Feature.INFERNAL_CURE),
        mob("goblin", CreatureKind.GOBLIN, Feature.ORE_MINING),
        mob("hobgoblin", CreatureKind.HOBGOBLIN, Feature.ORE_MINING),
        mob("thorned_pursuer", CreatureKind.THORNED_PURSUER, Feature.WOLF_SUMMONING),
        mob("naamah", CreatureKind.NAAMAH, Feature.VAMPIRE_INITIATION),
        mob("abyssal_regent", CreatureKind.ABYSSAL_REGENT, Feature.TORMENT_BANISHMENT),
        mob("lost_soul", CreatureKind.LOST_SOUL, Feature.SPIRIT_BINDING),
        mob("mandrake", CreatureKind.MANDRAKE, Feature.SCREECH),
        mob("dreamroot", CreatureKind.DREAMROOT, Feature.ROOTED_DRAIN),
        mob("reflection", CreatureKind.GLASS_DOPPELGANGER, Feature.MIRROR_COPY),
        mob("stonebroker", CreatureKind.STONEBROKER, Feature.PATRON_OFFERING),
        mob("nightmare", CreatureKind.NIGHTMARE, Feature.RIDEABLE_BOND),
        mob("owl", CreatureKind.OWL, Feature.BROOM_AURA),
        mob("parasytic_louse", CreatureKind.LOUSE, Feature.EFFECT_REDIRECTION),
        mob("poltergeist", CreatureKind.POLTERGEIST, Feature.TELEKINESIS),
        mob("emberhorn_archfiend", CreatureKind.EMBERHORN_ARCHFIEND, Feature.CAULDRON_AURA),
        mob("spectral_familiar", CreatureKind.FAMILIAR, Feature.ORE_GUIDANCE),
        mob("spectre", CreatureKind.SPECTRE, Feature.FEAR_AURA),
        mob("spirit", CreatureKind.SPIRIT, Feature.SPIRIT_BINDING),
        mob("toad", CreatureKind.TOAD, Feature.AMPHIBIOUS_AURA),
        mob("bramble_colossus", CreatureKind.BRAMBLE_COLOSSUS, Feature.HEART_EMPOWERMENT),
        mob("vampire", CreatureKind.VAMPIRE, Feature.BLOOD_DRAIN),
        mob("blood_thrall", CreatureKind.BLOOD_THRALL, Feature.COURT_SUBORDINATE),
        mob("werewolf_hunter", CreatureKind.WEREWOLF_HUNTER, Feature.SILVER_HUNTING),
        mob("werewolf", CreatureKind.WEREWOLF, Feature.WEREWOLF_INTEGRATION)
    );

    @Test
    void everyAuditedMobHasAProfile() {
        assertEquals(36, CASES.size());
        assertEquals(36, CreatureBehaviorProfile.audited().size());
        assertEquals(
            CASES.stream().map(MobCase::kind).collect(java.util.stream.Collectors.toUnmodifiableSet()),
            CreatureBehaviorProfile.audited().stream()
                .map(CreatureBehaviorProfile::kind)
                .collect(java.util.stream.Collectors.toUnmodifiableSet())
        );
    }

    @TestFactory
    Stream<DynamicContainer> oneFailureStateAndSuccessContainerPerMob() {
        return CASES.stream().map(testCase -> DynamicContainer.dynamicContainer(testCase.auditId(), List.of(
            DynamicTest.dynamicTest("failure is bounded", () -> failure(testCase)),
            DynamicTest.dynamicTest("diagnostic or state is exposed", () -> state(testCase)),
            DynamicTest.dynamicTest("success restores a distinctive behavior", () -> success(testCase))
        )));
    }

    private static void failure(final MobCase testCase) {
        final CreatureBehaviorProfile profile = profile(testCase);
        assertFalse(CreatureBehaviorRules.shouldPulse(profile.pulseIntervalTicks() - 1, 0, profile.pulseIntervalTicks()));
        profile.offering().ifPresent(_ -> assertFalse(CreatureBehaviorRules.canBind(
            Optional.empty(),
            PLAYER,
            false
        )));
        switch (testCase.requiredFeature()) {
            case RIDEABLE_BOND -> assertFalse(CreatureBehaviorRules.canMount(Optional.of(OTHER), PLAYER));
            case COVEN_RECRUITMENT -> assertFalse(CreatureBehaviorRules.canRecruit(
                Optional.empty(), PLAYER, true, false
            ));
            case WOLF_SUMMONING -> assertFalse(CreatureBehaviorRules.shouldSummonWolves(20.0F, 20.0F, 0, 400));
            case EFFECT_REDIRECTION -> assertFalse(CreatureBehaviorRules.canRedirectEffect(true, false, true, true));
            case CAULDRON_AURA -> assertEquals(0, CreatureBehaviorRules.cauldronRangeBonus(0));
            case BLOOD_DRAIN -> assertFalse(CreatureBehaviorRules.shouldBurnInSun(false, true, false, false));
            default -> assertFalse(profile.features().isEmpty());
        }
    }

    private static void state(final MobCase testCase) {
        final CreatureBehaviorProfile profile = profile(testCase);
        assertEquals(testCase.auditId(), profile.auditId());
        assertNotNull(profile.auditStatus());
        assertEquals(profile, CreatureBehaviorFactory.create(testCase.kind()).profile());
        profile.offering().ifPresent(tag -> {
            final Path resource = DATA.resolve("tags").resolve("item")
                .resolve(tag.location().getPath() + ".json");
            final JsonObject json = json(resource);
            assertFalse(json.getAsJsonArray("values").isEmpty(), resource.toString());
        });
        final JsonObject language = json(LANGUAGE);
        Set.of(
            "message.warlockery.creature.bound",
            "message.warlockery.creature.empowered",
            "message.warlockery.creature.owner_required"
        ).forEach(key -> assertTrue(language.has(key), key));
    }

    private static void success(final MobCase testCase) {
        final CreatureBehaviorProfile profile = profile(testCase);
        assertTrue(profile.has(testCase.requiredFeature()), testCase.requiredFeature().name());
        if (testCase.kind() == CreatureKind.BANSHEE) {
            assertEquals(Set.of(Feature.DUST_EMPOWERMENT), profile.features(),
                "the Banshee profile is compatibility metadata only: its mob-specific behavior "
                    + "lives in the dedicated BansheeRuntime, and no generic SCREECH or PHASED "
                    + "claim may return");
        } else if (testCase.kind() == CreatureKind.ENT) {
            assertEquals(Set.of(Feature.BIOME_VARIANTS), profile.features(),
                "the Ent profile keeps variant compatibility metadata only; its behavior lives "
                    + "in the dedicated EntRuntime and generic proximity aggression may not return");
        } else {
            assertTrue(profile.features().size() >= 2);
        }
        if (testCase.kind() == CreatureKind.BLOOD_THRALL) {
            assertTrue(profile.has(Feature.SUNLIGHT_WEAKNESS));
            assertFalse(profile.has(Feature.BLOOD_DRAIN));
        }
        switch (testCase.requiredFeature()) {
            case DUST_EMPOWERMENT, HEART_EMPOWERMENT ->
                assertEquals(1, CreatureBehaviorRules.empoweredLevel(0, 1));
            case RIDEABLE_BOND -> assertTrue(CreatureBehaviorRules.canMount(Optional.of(PLAYER), PLAYER));
            case COVEN_RECRUITMENT -> assertTrue(CreatureBehaviorRules.canRecruit(
                Optional.empty(), PLAYER, true, true
            ));
            case WOLF_SUMMONING -> assertTrue(CreatureBehaviorRules.shouldSummonWolves(10.0F, 20.0F, 0, 400));
            case EFFECT_REDIRECTION -> assertTrue(CreatureBehaviorRules.canRedirectEffect(true, true, true, true));
            case CAULDRON_AURA -> assertEquals(16, CreatureBehaviorRules.cauldronRangeBonus(4));
            case BLOOD_DRAIN -> assertTrue(CreatureBehaviorRules.shouldBurnInSun(true, true, false, false));
            default -> assertTrue(CreatureBehaviorRules.shouldPulse(0, 0, profile.pulseIntervalTicks()));
        }
        assertSupportingTags(profile);
    }

    private static void assertSupportingTags(final CreatureBehaviorProfile profile) {
        if (profile.has(Feature.CAULDRON_AURA)) {
            assertTag("block", "creature_habitats/magical_cauldrons");
        }
        if (profile.has(Feature.ORE_GUIDANCE)) {
            assertTag("block", "creature_habitats/spectral_ores");
            assertTag("item", "creature_interactions/spectral_ore_samples");
        }
        if (profile.has(Feature.FAMILIAR_BOND) || profile.has(Feature.COVEN_RECRUITMENT)) {
            assertTag("entity_type", "creature_families/familiars");
        }
        if (profile.has(Feature.GOBLIN_AURA) || profile.has(Feature.FORGE_AURA)) {
            assertTag("entity_type", "creature_families/goblins");
        }
        if (profile.has(Feature.ROOTED_DRAIN)) {
            assertTag("block", "creature_habitats/living_ground");
        }
    }

    @Test
    void bothF13PractitionersDispatchThroughTheirDedicatedRuntimesOnly() {
        // Neither dedicated entity calls CreatureBehaviorRuntime.tick at all, so the generic hex
        // pulse and the generic bound-companion follow/aura execute zero times for them; the
        // profile facts that other suites assert are untouched.
        final String crone = source("HedgeCroneEntity.java");
        final String mage = source("CircleMageEntity.java");
        assertFalse(crone.contains("CreatureBehaviorRuntime.tick"));
        assertFalse(mage.contains("CreatureBehaviorRuntime.tick"));
        assertFalse(crone.contains("CreatureBehaviorFactory.create"),
            "the Hedge Crone has no interaction, binding, offering, or trade surface at all");
        assertTrue(crone.contains("HedgeCroneRuntime.tick(this, level)"));
        assertTrue(mage.contains("CircleMageRuntime.tick(this, level)"));
        assertTrue(mage.contains("covenBehavior.interact(this, player, hand)"),
            "recruitment keeps the exact existing shared interaction surface");

        // Generic tactical, ambient, and hazard runtimes are never reached from either entity.
        Stream.of(crone, mage).forEach(entity -> {
            assertFalse(entity.contains("TacticalCombatRuntime"));
            assertFalse(entity.contains("AmbientActivityRuntime"));
            assertFalse(entity.contains("HazardEscapeRuntime"));
        });
    }

    @Test
    void everyNonF13KindKeepsItsGenericBehaviorDispatch() {
        final Set<CreatureKind> dedicated = Set.of(CreatureKind.HEDGE_CRONE, CreatureKind.CIRCLE_MAGE);
        CASES.stream()
            .map(MobCase::kind)
            .filter(kind -> !dedicated.contains(kind))
            .forEach(kind -> assertNotNull(CreatureBehaviorProfile.find(kind).orElse(null),
                kind + " keeps its audited generic profile"));
        assertNotNull(CreatureBehaviorProfile.find(CreatureKind.HEDGE_CRONE).orElse(null),
            "the F13 profile facts remain registered for parity and integrity suites");
        assertNotNull(CreatureBehaviorProfile.find(CreatureKind.CIRCLE_MAGE).orElse(null));
    }

    private static String source(final String fileName) {
        try {
            return Files.readString(Path.of(
                "src", "main", "java", "com", "kadamitas", "warlockery", "entity", fileName));
        } catch (IOException exception) {
            throw new UncheckedIOException(fileName, exception);
        }
    }

    private static void assertTag(final String registry, final String path) {
        final JsonObject json = json(DATA.resolve("tags").resolve(registry).resolve(path + ".json"));
        assertFalse(json.getAsJsonArray("values").isEmpty(), registry + "/" + path);
    }

    private static CreatureBehaviorProfile profile(final MobCase testCase) {
        return CreatureBehaviorProfile.find(testCase.kind()).orElseThrow();
    }

    private static JsonObject json(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private static MobCase mob(final String auditId, final CreatureKind kind, final Feature feature) {
        return new MobCase(auditId, kind, feature);
    }

    private record MobCase(String auditId, CreatureKind kind, Feature requiredFeature) {
    }

    @Test
    void waterAndRainPutOutTheSunForSunlightWeakCreatures() {
        // Bare daylight under open sky burns, exactly as before.
        assertTrue(CreatureBehaviorRules.shouldBurnInSun(true, true, false, false));
        // Being in water or standing in rain puts it out, which is how vanilla's own undead
        // behave and what lets Naamah's line live in a drowned monument at all.
        assertFalse(CreatureBehaviorRules.shouldBurnInSun(true, true, false, true));
        // Wetness is not a substitute for the other guards, nor they for it.
        assertFalse(CreatureBehaviorRules.shouldBurnInSun(false, true, false, false));
        assertFalse(CreatureBehaviorRules.shouldBurnInSun(true, false, false, false));
        assertFalse(CreatureBehaviorRules.shouldBurnInSun(true, true, true, false));
    }
}
