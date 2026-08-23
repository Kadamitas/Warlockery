package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.client.model.ImpModel;
import com.kadamitas.warlockery.client.model.NaamahModel;
import com.kadamitas.warlockery.client.model.StormSimianModel;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
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
    private static final String NAMI_SHA256 =
        "9ffc406b45205ccfb2e9c1faddb9702a0cb430aa14d37bcbad00407a912ca2d0";

    @Test
    void everyEntityTextureUsesAnAuthoredPixelAtlas() throws Exception {
        final List<Path> textures;
        try (Stream<Path> files = Files.list(ENTITY_TEXTURES)) {
            textures = files.filter(path -> path.getFileName().toString().endsWith(".png")).toList();
        }
        assertTrue(textures.size() >= 48);
        final Set<String> hashes = textures.stream()
            .map(CreatureConceptImplementationTest::inspectTexture)
            .collect(Collectors.toUnmodifiableSet());
        assertEquals(textures.size(), hashes.size(), "entity atlases must not be palette-swap duplicates");
    }

    @Test
    void everySavedCreatureConceptHasReviewableProductionResolution() throws IOException {
        final List<Path> sheets;
        try (Stream<Path> files = Files.walk(CONCEPTS)) {
            sheets = files.filter(path -> path.getFileName().toString().endsWith(".png")).toList();
        }
        assertTrue(sheets.size() >= 15);
        for (final Path sheet : sheets) {
            final BufferedImage image = ImageIO.read(sheet.toFile());
            assertNotNull(image, sheet.toString());
            assertTrue(image.getWidth() >= 1024, sheet + " width");
            assertTrue(image.getHeight() >= 768, sheet + " height");
        }
    }

    @Test
    void customRenderingKeepsOnlyNamiAndGlassOnIntentionalLegacyPaths() throws IOException {
        final String legacy = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/client/TexturedCreatureRenderers.java"
        ));
        final String dedicated = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/client/DedicatedCreatureRenderers.java"
        ));
        assertTrue(legacy.contains("Nami extends SkinnedHumanoid<NamiEntity>"));
        assertTrue(legacy.contains("super(context, \"nami\""));
        assertFalse(legacy.contains("Naamah extends SkinnedHumanoid"));
        assertFalse(dedicated.contains("ArcaneCreatureModel"));
        assertTrue(dedicated.contains("new NaamahModel("));
        assertFalse(dedicated.contains("ModelLayers.PLAYER"));
    }

    @Test
    void namiStaysBytePinnedWhileNaamahOwnsHerGoddessRigAndGenerator() throws Exception {
        assertEquals(NAMI_SHA256, sha256(ENTITY_TEXTURES.resolve("nami.png")));
        final BufferedImage nami = ImageIO.read(ENTITY_TEXTURES.resolve("nami.png").toFile());
        assertEquals(64, nami.getWidth());
        assertEquals(64, nami.getHeight());

        final BufferedImage naamah = ImageIO.read(ENTITY_TEXTURES.resolve("naamah.png").toFile());
        assertEquals(NaamahModel.TEXTURE_WIDTH, naamah.getWidth());
        assertEquals(NaamahModel.TEXTURE_HEIGHT, naamah.getHeight());
        assertEquals(192, naamah.getWidth());
        assertEquals(128, naamah.getHeight());

        final String legacyGenerator = Files.readString(Path.of("tools/GenerateOriginalAssets.java"));
        assertFalse(legacyGenerator.contains("ImageIO.write(namiSkin()"));
        assertFalse(legacyGenerator.contains("ImageIO.write(naamahSkin()"));
        assertTrue(legacyGenerator.contains("Nami is intentionally pinned and unchanged"));
        final String naamahGenerator = Files.readString(Path.of(
            "tools/creature_models/generate_naamah.ps1"
        )).toLowerCase(java.util.Locale.ROOT);
        assertTrue(naamahGenerator.contains("naamah.png"));
        assertFalse(naamahGenerator.contains("nami.png"));
    }

    @Test
    void generalAssetGeneratorsCannotWriteAuthoredEntityAtlases() throws IOException {
        final String javaGenerator = Files.readString(Path.of("tools/GenerateOriginalAssets.java"));
        assertTrue(
            javaGenerator.contains(".filter(path -> !path.normalize().startsWith(entityRoot.normalize()))"),
            "the normal generator must recursively exclude the complete entity atlas tree"
        );
        assertTrue(
            javaGenerator.contains("if (path.normalize().startsWith(entityRoot.normalize()))"),
            "the generic ImageIO write loop must fail closed for every path below entityRoot"
        );
        assertFalse(javaGenerator.contains("entityRoot.resolve("),
            "no explicit generator destination may resolve below entityRoot");
        assertFalse(javaGenerator.contains("textureRoot.resolve(\"entity/"),
            "no indirect generator destination may resolve below the entity texture tree");
        assertFalse(javaGenerator.contains("case \"entity\" ->"),
            "the generic texture switch must not have an entity-atlas generation branch");
        assertFalse(javaGenerator.contains("ImageIO.write(entityTexture("),
            "loot-table IDs must never drive writes to authored entity atlases");
        assertFalse(javaGenerator.contains("ImageIO.write(penguinGoblinTexture("),
            "the retired goblin CLI must never rewrite Goblin or Hobgoblin atlases");
        assertFalse(javaGenerator.contains("writeHunterArmorTextures("),
            "the general generator must not own nested entity equipment atlases");
        assertTrue(javaGenerator.contains("--goblins is retired"),
            "--goblins remains accepted as an explicit no-op for CLI compatibility");

        final String plantGenerator = Files.readString(Path.of(
            "tools/generate_1_5_1_visual_assets.ps1"
        ));
        assertFalse(plantGenerator.contains("$entityRoot"),
            "the general plant generator must not resolve an entity texture root");
        assertFalse(plantGenerator.contains("New-EntityAtlas"),
            "Mandrake and Dreamroot atlases belong to dedicated creature generators");
    }

    @Test
    void impAndStormSimianUseTheirIndependentArticulatedRigs() {
        final ModelPart imp = ImpModel.createBodyLayer().bakeRoot();
        assertFalse(imp.getChild("right_wing").isEmpty());
        assertFalse(imp.getChild("left_wing").isEmpty());

        final ModelPart simian = StormSimianModel.createBodyLayer().bakeRoot();
        assertFalse(simian.getChild("right_arm").isEmpty());
        assertFalse(simian.getChild("left_arm").isEmpty());
        assertFalse(simian.getChild("head").getChild("muzzle").isEmpty());
        assertFalse(simian.getChild("right_leg").isEmpty());
        assertFalse(simian.getChild("left_leg").isEmpty());
        assertFalse(simian.getChild("tail_base").isEmpty());
    }

    private static String inspectTexture(final Path path) {
        try {
            final BufferedImage image = ImageIO.read(path.toFile());
            assertNotNull(image, path.toString());
            assertTrue(image.getWidth() >= 32 && image.getWidth() <= 256, path + " width");
            assertTrue(image.getHeight() >= 32 && image.getHeight() <= 256, path + " height");
            assertEquals(0, image.getWidth() % 16, path + " pixel-grid width");
            assertEquals(0, image.getHeight() % 16, path + " pixel-grid height");
            assertTrue(image.getColorModel().hasAlpha(), path + " alpha channel");
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    final int alpha = image.getRGB(x, y) >>> 24;
                    assertTrue(alpha == 0 || alpha == 255, path + " binary alpha at " + x + "," + y);
                }
            }
            return sha256(path);
        } catch (final IOException exception) {
            throw new IllegalStateException("Unable to inspect " + path, exception);
        }
    }

    private static String sha256(final Path path) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
            );
        } catch (final IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash " + path, exception);
        }
    }
}
