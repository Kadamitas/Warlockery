package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.CreatureVisualProfile;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class CreatureConceptImplementationTest {
    private static final Path ENTITY_TEXTURES = Path.of(
        "src/main/resources/assets/warlockery/textures/entity"
    );
    private static final Path CONCEPTS = Path.of("docs/art-source/creature-concepts");
    private static final Path ENTITY_LOOT = Path.of("src/main/resources/data/warlockery/loot_table/entities");
    private static final Set<String> CONCEPT_SHEETS = Set.of(
        "familiars-and-vermin.png",
        "illusions-and-anomalies.png",
        "infernal-and-bosses.png",
        "kobold-clans.png",
        "lycans-and-mounts.png",
        "occult-humanoids.png",
        "spectral-entities.png",
        "verdant-creatures.png"
    );

    @Test
    void everyRegisteredCreatureHasAnExactModelProfile() throws IOException {
        final Set<String> profileIds = Arrays.stream(CreatureModelProfile.Variant.values())
            .map(CreatureModelProfile.Variant::id)
            .collect(Collectors.toUnmodifiableSet());
        final Set<String> registeredIds = registeredCreatureIds();
        assertEquals(registeredIds, profileIds);
        registeredIds.forEach(id -> {
            final CreatureVisualProfile visual = new CreatureVisualProfile(
                0.8F,
                1.8F,
                CreatureVisualProfile.Archetype.HUMANOID
            );
            final CreatureModelProfile profile = CreatureModelProfile.forEntity(id, visual);
            final ModelPart root = ArcaneCreatureModel.createLayer(profile).bakeRoot();
            assertFalse(root.getChild("head").isEmpty(), id + " head");
            assertFalse(root.getChild("body").isEmpty(), id + " body");
            assertTrue(root.getAllParts().stream().filter(part -> !part.isEmpty()).count() >= 7, id + " geometry");
        });
    }

    @Test
    void everyCreatureTextureIsPixelSizedAndOriginal() throws Exception {
        final Set<String> registeredIds = registeredCreatureIds();
        final Set<String> hashes = registeredIds.stream()
            .map(id -> ENTITY_TEXTURES.resolve(id + ".png"))
            .peek(path -> assertTrue(Files.isRegularFile(path), path.toString()))
            .map(CreatureConceptImplementationTest::inspectTexture)
            .collect(Collectors.toUnmodifiableSet());
        assertEquals(registeredIds.size(), hashes.size());
    }

    @Test
    void everyGeneratedConceptSheetIsSavedWithProductionResolution() throws IOException {
        try (Stream<Path> files = Files.list(CONCEPTS)) {
            assertEquals(CONCEPT_SHEETS, files
                .filter(path -> path.getFileName().toString().endsWith(".png"))
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toUnmodifiableSet()));
        }
        for (final String fileName : CONCEPT_SHEETS) {
            final BufferedImage image = ImageIO.read(CONCEPTS.resolve(fileName).toFile());
            assertTrue(image.getWidth() >= 1024, fileName + " width");
            assertTrue(image.getHeight() >= 768, fileName + " height");
        }
    }

    @Test
    void customCreatureRenderingDoesNotReuseVanillaMobModels() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/client/TexturedCreatureRenderers.java"
        ));
        assertFalse(source.contains("VexRenderer"));
        assertFalse(source.contains("VillagerRenderer"));
        assertFalse(source.contains("PillagerRenderer"));
        assertFalse(source.contains("IronGolemRenderer"));
    }

    @Test
    void impAndStormSimianHaveArticulatedSilhouettes() {
        final ModelPart imp = modelFor("imp");
        assertFalse(imp.getChild("right_wing").getChild("right_wing_finger").isEmpty());
        assertFalse(imp.getChild("left_wing").getChild("left_wing_finger").isEmpty());
        final ModelPart simian = modelFor("storm_simian");
        assertFalse(simian.getChild("right_arm").getChild("right_hand").isEmpty());
        assertFalse(simian.getChild("left_arm").getChild("left_hand").isEmpty());
        assertFalse(simian.getChild("right_wing").getChild("right_primary_feathers").isEmpty());
        assertFalse(simian.getChild("left_wing").getChild("left_primary_feathers").isEmpty());
    }

    private static String inspectTexture(final Path path) {
        try {
            final BufferedImage image = ImageIO.read(path.toFile());
            assertEquals(64, image.getWidth(), path + " width");
            assertEquals(64, image.getHeight(), path + " height");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (final IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to inspect " + path, exception);
        }
    }

    private static Set<String> registeredCreatureIds() throws IOException {
        try (Stream<Path> files = Files.list(ENTITY_LOOT)) {
            return files
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                .collect(Collectors.toUnmodifiableSet());
        }
    }

    private static ModelPart modelFor(final String id) {
        final CreatureVisualProfile visual = new CreatureVisualProfile(
            0.8F,
            1.8F,
            CreatureVisualProfile.Archetype.HUMANOID
        );
        return ArcaneCreatureModel.createLayer(CreatureModelProfile.forEntity(id, visual)).bakeRoot();
    }
}
