package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class DedicatedCreatureRegistrationsTest {
    private static final Path CLIENT = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/WarlockeryClient.java"
    );
    private static final Path REGISTRATIONS = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/DedicatedCreatureRenderers.java"
    );
    private static final Map<String, String> DEDICATED = expectedRegistrations();

    @Test
    void allFortySixCreatureBodiesRouteExplicitlyToTheirOwnModel() throws Exception {
        assertEquals(46, DEDICATED.size());
        final String source = Files.readString(REGISTRATIONS);
        for (final Map.Entry<String, String> entry : DEDICATED.entrySet()) {
            assertEquals(1, occurrences(source, "register(event, \"" + entry.getKey() + "\""),
                entry.getKey());
            assertTrue(source.contains("new " + entry.getValue() + "("), entry.getValue());
        }
        assertEquals(46, occurrences(source, "register(event, \""));
        for (final String forbidden : List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "CubeListBuilder", "ModelPart",
            "MeshDefinition", "LayerDefinition", "registerArcane", "registerNaamah",
            "TexturedCreatureRenderers.Naamah", "mixin"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void clientKeepsOnlyNamiAndGlassOnTheirIntentionalLegacyPaths() throws Exception {
        final String source = Files.readString(CLIENT);
        assertTrue(source.contains("DedicatedCreatureRenderers.registerAll(event)"));
        assertTrue(source.contains("TexturedCreatureRenderers.registerNami"));
        assertTrue(source.contains("\"glass_doppelganger\""));
        assertTrue(source.contains("TexturedCreatureRenderers.registerArcane"));
        assertFalse(source.contains("ModEntities.ALL.forEach"));
        assertFalse(source.contains("TexturedCreatureRenderers.registerNaamah"));
    }

    @Test
    void specializedRenderLayersPreserveVariantAndVillagerIdentity() throws Exception {
        final String source = Files.readString(REGISTRATIONS);
        assertTrue(source.contains("VampireModel.textureFor(state.variant)"));
        assertEquals(2, occurrences(source, "new NativeVillagerClothingLayer<>("));
        assertTrue(source.contains("LycanVillagerModel.createBodyLayerNoHat()"));
        assertTrue(source.contains("new WerewolfVillagerClothingModel("));
        assertTrue(source.contains("WerewolfVillagerClothingModel.createBodyLayer(false)"));
        assertTrue(source.contains("WerewolfVillagerClothingModel.createBodyLayer(true)"));
    }

    private static int occurrences(final String source, final String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static Map<String, String> expectedRegistrations() {
        final Map<String, String> models = new LinkedHashMap<>();
        models.put("abyssal_regent", "AbyssalRegentModel");
        models.put("banshee", "BansheeModel");
        models.put("blood_thrall", "BloodThrallModel");
        models.put("bramble_colossus", "BrambleColossusModel");
        models.put("circle_mage", "CircleMageModel");
        models.put("corpse", "CorpseModel");
        models.put("death", "DeathModel");
        models.put("demon", "DemonModel");
        models.put("dreamroot", "DreamrootModel");
        models.put("echo_shade", "EchoShadeModel");
        models.put("eldritch_watcher", "EldritchWatcherModel");
        models.put("emberhorn_archfiend", "EmberhornArchfiendModel");
        models.put("ent", "EntModel");
        models.put("familiar_cat", "FamiliarCatModel");
        models.put("feral_lycan", "FeralLycanModel");
        models.put("forgewarden", "ForgewardenModel");
        models.put("goblin", "GoblinModel");
        models.put("hedge_crone", "HedgeCroneModel");
        models.put("hellhound", "HellhoundModel");
        models.put("hex_bat", "HexBatModel");
        models.put("hobgoblin", "HobgoblinModel");
        models.put("illusion_creeper", "IllusionCreeperModel");
        models.put("illusion_spider", "IllusionSpiderModel");
        models.put("illusion_zombie", "IllusionZombieModel");
        models.put("imp", "ImpModel");
        models.put("ironbound_sentinel", "IronboundSentinelModel");
        models.put("lost_soul", "LostSoulModel");
        models.put("lycan_villager", "LycanVillagerModel");
        models.put("mandrake", "MandrakeModel");
        models.put("naamah", "NaamahModel");
        models.put("nightmare", "NightmareModel");
        models.put("owl", "OwlModel");
        models.put("pale_steed", "PaleSteedModel");
        models.put("parasytic_louse", "ParasyticLouseModel");
        models.put("poltergeist", "PoltergeistModel");
        models.put("spectral_familiar", "SpectralFamiliarModel");
        models.put("spectre", "SpectreModel");
        models.put("spirit", "SpiritModel");
        models.put("stonebroker", "StonebrokerModel");
        models.put("storm_simian", "StormSimianModel");
        models.put("thorned_pursuer", "ThornedPursuerModel");
        models.put("toad", "ToadModel");
        models.put("umbral_sigil", "UmbralSigilModel");
        models.put("vampire", "VampireModel");
        models.put("werewolf", "WerewolfModel");
        models.put("werewolf_hunter", "WerewolfHunterModel");
        return Map.copyOf(models);
    }
}
