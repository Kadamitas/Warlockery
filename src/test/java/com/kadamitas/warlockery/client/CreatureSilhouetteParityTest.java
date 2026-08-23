package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class CreatureSilhouetteParityTest {
    private static final Path MODELS = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model"
    );
    private static final Path REGISTRATIONS = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/DedicatedCreatureRenderers.java"
    );

    @Test
    void allFortySixSpeciesOwnIndependentModelClasses() throws Exception {
        final String registrations = Files.readString(REGISTRATIONS);
        final Matcher matcher = Pattern.compile("new ([A-Za-z0-9]+Model)\\(").matcher(registrations);
        final Set<String> classes = new LinkedHashSet<>();
        while (matcher.find()) {
            classes.add(matcher.group(1));
        }
        assertEquals(47, classes.size(), "46 species plus transformed-villager clothing mesh");
        assertTrue(classes.contains("NaamahModel"));
        assertTrue(classes.contains("WerewolfVillagerClothingModel"));

        for (final String modelClass : classes) {
            final String source = Files.readString(MODELS.resolve(modelClass + ".java"));
            final boolean ownsDirectRig = source.contains("extends EntityModel<");
            final boolean usesApprovedVanillaRig = (modelClass.equals("FamiliarCatModel")
                && source.contains("extends AdultFelineModel<"))
                || (modelClass.equals("ToadModel") && source.contains("extends FrogModel"));
            assertTrue(ownsDirectRig || usesApprovedVanillaRig, modelClass);
            assertTrue(source.contains("createBodyLayer"), modelClass);
            assertFalse(source.contains("ArcaneCreatureModel"), modelClass);
            assertFalse(source.contains("CreatureModelProfile"), modelClass);
            assertFalse(source.contains("GeometryHelper"), modelClass);
            assertFalse(source.contains("ModelHelper"), modelClass);
            assertFalse(source.contains("WarlockeryModel"), modelClass);
            if (!usesApprovedVanillaRig) {
                final Matcher inheritedRig = Pattern.compile(
                    "class\\s+" + modelClass + "\\s+extends\\s+(?!EntityModel\\b)([A-Za-z0-9]+Model)"
                ).matcher(source);
                assertFalse(inheritedRig.find(), modelClass + " must not inherit another creature rig");
            }
        }
    }

    @Test
    void demonAndAbyssalRegentRemainWeaponlessBodyAttackers() throws Exception {
        for (final String modelClass : List.of("DemonModel", "AbyssalRegentModel")) {
            final String source = Files.readString(MODELS.resolve(modelClass + ".java"));
            assertFalse(source.contains("ArmedModel"), modelClass);
            assertFalse(source.contains("translateToHand"), modelClass);
            assertFalse(source.contains("ItemInHand"), modelClass);
            assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains("weapon"), modelClass);
        }
    }

    @Test
    void goblinClanKeepsItsExplicitPenguinAnatomy() throws Exception {
        for (final String modelClass : List.of(
            "GoblinModel", "HobgoblinModel", "StonebrokerModel", "ForgewardenModel"
        )) {
            final String source = Files.readString(MODELS.resolve(modelClass + ".java"))
                .toLowerCase(java.util.Locale.ROOT);
            assertTrue(source.contains("beak"), modelClass);
            assertTrue(source.contains("flipper"), modelClass);
            assertTrue(source.contains("webbed"), modelClass);
        }
    }

    @Test
    void naamahOwnsAGoddessRigWhileNamiStaysOutOfTheModelCatalog() throws Exception {
        final String naamah = Files.readString(MODELS.resolve("NaamahModel.java"));
        assertTrue(naamah.contains("three_crest_crown"));
        assertTrue(naamah.contains("rear_tidal_mantle"));
        assertTrue(naamah.contains("drowningSurgeProgress"));
        assertFalse(naamah.contains("PlayerModel"));
        assertFalse(Files.exists(MODELS.resolve("NamiModel.java")));
    }
}
