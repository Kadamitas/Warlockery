package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.junit.jupiter.api.Test;

/** Aggregate audit for the independent per-species model and software-snapshot packages. */
final class CreatureModelVisualAuditTest {
    private static final Path MODEL_ROOT = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model"
    );
    private static final Path MODEL_TEST_ROOT = Path.of(
        "src/test/java/com/kadamitas/warlockery/client/model"
    );
    private static final Path TEXTURE_ROOT = Path.of(
        "src/main/resources/assets/warlockery/textures/entity"
    );
    private static final Map<String, String> MODELS = models();

    @Test
    void everyDedicatedSpeciesBakesItsOwnNonEmptyRig() throws Exception {
        assertEquals(46, MODELS.size());
        for (final Map.Entry<String, String> entry : MODELS.entrySet()) {
            final Class<?> modelClass = Class.forName(
                "com.kadamitas.warlockery.client.model." + entry.getValue()
            );
            final LayerDefinition layer = (LayerDefinition) modelClass
                .getMethod("createBodyLayer")
                .invoke(null);
            final ModelPart root = layer.bakeRoot();
            final long solids = root.getAllParts().stream().filter(part -> !part.isEmpty()).count();
            assertTrue(solids >= 6, entry.getKey() + " solid parts=" + solids);

            final String source = Files.readString(MODEL_ROOT.resolve(entry.getValue() + ".java"));
            final boolean ownsDirectRig = source.contains("extends EntityModel<");
            final boolean usesApprovedVanillaRig = (entry.getKey().equals("familiar_cat")
                && source.contains("extends AdultFelineModel<"))
                || (entry.getKey().equals("toad") && source.contains("extends FrogModel"));
            assertTrue(ownsDirectRig || usesApprovedVanillaRig, entry.getKey());
            assertFalse(source.contains("ArcaneCreatureModel"), entry.getKey());
            assertFalse(source.contains("CreatureModelProfile"), entry.getKey());
        }
    }

    @Test
    void productionAtlasesMatchEachDedicatedModelsDeclaredDimensions() throws Exception {
        final List<Path> atlases = new ArrayList<>();
        for (final Map.Entry<String, String> entry : MODELS.entrySet()) {
            final Class<?> modelClass = Class.forName(
                "com.kadamitas.warlockery.client.model." + entry.getValue()
            );
            final int width = modelClass.getField("TEXTURE_WIDTH").getInt(null);
            final int height = modelClass.getField("TEXTURE_HEIGHT").getInt(null);
            final List<Path> speciesAtlases = entry.getKey().equals("vampire")
                ? List.of(
                    TEXTURE_ROOT.resolve("vampire_masculine.png"),
                    TEXTURE_ROOT.resolve("vampire_feminine.png")
                )
                : List.of(TEXTURE_ROOT.resolve(entry.getKey() + ".png"));
            for (final Path atlas : speciesAtlases) {
                assertTrue(Files.isRegularFile(atlas), atlas.toString());
                final BufferedImage image = ImageIO.read(atlas.toFile());
                assertNotNull(image, atlas.toString());
                assertEquals(width, image.getWidth(), atlas + " width");
                assertEquals(height, image.getHeight(), atlas + " height");
                assertTrue(image.getColorModel().hasAlpha(), atlas + " alpha");
                assertBinaryAlpha(image, atlas);
                atlases.add(atlas);
            }
        }
        final Set<String> hashes = atlases.stream().map(CreatureModelVisualAuditTest::sha256)
            .collect(Collectors.toUnmodifiableSet());
        assertEquals(atlases.size(), hashes.size(), "dedicated atlases must be visually independent");
    }

    @Test
    void everyModelPackageCarriesSixViewSoftwareQa() throws Exception {
        int sheets = 0;
        for (final String modelClass : MODELS.values()) {
            final Path test = MODEL_TEST_ROOT.resolve(modelClass + "Test.java");
            assertTrue(Files.isRegularFile(test), test.toString());
            final String source = Files.readString(test);
            if (source.contains("software-contact-sheet")) {
                sheets++;
            } else {
                assertTrue(
                    modelClass.equals("MandrakeModel") || modelClass.equals("DreamrootModel"),
                    modelClass + " must emit its QA contact sheet"
                );
            }
        }
        assertEquals(44, sheets);
    }

    private static void assertBinaryAlpha(final BufferedImage image, final Path path) {
        boolean opaque = false;
        boolean transparent = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int alpha = image.getRGB(x, y) >>> 24;
                assertTrue(alpha == 0 || alpha == 255, path + " alpha at " + x + "," + y);
                opaque |= alpha == 255;
                transparent |= alpha == 0;
            }
        }
        assertTrue(opaque, path + " must contain paint");
        assertTrue(transparent, path + " must preserve unused transparency");
    }

    private static String sha256(final Path path) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash " + path, exception);
        }
    }

    private static Map<String, String> models() {
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
