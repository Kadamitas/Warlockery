package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.imageSnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.softwareSnapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class WerewolfModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/WerewolfModel.java"
    );
    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/warlockery/textures/entity/werewolf.png"
    );

    @Test
    void bakesDisciplinedToweringLycanHierarchyAgainstItsOwnAtlas() throws Exception {
        assertEquals(192, WerewolfModel.TEXTURE_WIDTH);
        assertEquals(192, WerewolfModel.TEXTURE_HEIGHT);
        final ModelPart root = WerewolfModel.createBodyLayer().bakeRoot();
        assertFalse(requiredChild(root, "pelvis").isEmpty());
        final ModelPart chest = requiredChild(root, "chest");
        final ModelPart head = requiredChild(requiredChild(chest, "neck"), "head");
        for (final String feature : List.of("muzzle", "left_ear", "right_ear", "mane_crown")) {
            assertFalse(requiredChild(head, feature).isEmpty(), feature);
        }
        assertThreeStage(root, "left_arm", "left_forearm", "left_hand");
        assertThreeStage(root, "right_arm", "right_forearm", "right_hand");
        assertThreeStage(root, "left_leg", "left_shin", "left_foot");
        assertThreeStage(root, "right_leg", "right_shin", "right_foot");
        final ModelPart tail = requiredChild(root, "tail");
        assertFalse(requiredChild(requiredChild(tail, "tail_mid"), "tail_tip").isEmpty());
        assertTrue(CreatureModelTestSupport.solidPartCount(root) >= 23);
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        assertGrayTexture(texture);
        CreatureModelTestSupport.assertUvsWithin(root, 192, 192);
        CreatureModelTestSupport.assertOpaqueUvs(root, texture, cube -> true);
        assertEquals(0, texture.getRGB(191, 191) >>> 24);
        assertEquals("a9249754e3eb2f00d1fe97bef703546fa802ef1f4e3c105f92561fc8dbd8727e",
            textureHash());
    }

    @Test
    void conceptTopologyCarriesFacetedShouldersLayeredManeClawsAndToes() {
        final ModelPart root = WerewolfModel.createBodyLayer().bakeRoot();
        final ModelPart chest = requiredChild(root, "chest");
        for (final String facet : List.of(
            "sternum_keel", "left_scapular_facet", "right_scapular_facet"
        )) {
            assertFalse(requiredChild(chest, facet).isEmpty(), facet);
        }
        final ModelPart head = requiredChild(requiredChild(chest, "neck"), "head");
        assertFalse(requiredChild(head, "brow_wedge").isEmpty());
        assertFalse(requiredChild(head, "lower_jaw").isEmpty());
        final ModelPart mane = requiredChild(head, "mane_crown");
        for (final String plate : List.of(
            "mane_plate_left", "mane_plate_center", "mane_plate_right"
        )) {
            assertFalse(requiredChild(mane, plate).isEmpty(), plate);
        }
        assertThreeDigits(
            requiredChild(requiredChild(root, "left_arm"), "left_forearm"),
            "left_hand",
            "left_claw"
        );
        assertThreeDigits(
            requiredChild(requiredChild(root, "right_arm"), "right_forearm"),
            "right_hand",
            "right_claw"
        );
        assertThreeDigits(
            requiredChild(requiredChild(root, "left_leg"), "left_shin"),
            "left_foot",
            "left_toe"
        );
        assertThreeDigits(
            requiredChild(requiredChild(root, "right_leg"), "right_shin"),
            "right_foot",
            "right_toe"
        );
    }

    @Test
    void uprightWolfmanRigHasReadableConnectedBulkyUpperBodyMass() {
        final ModelPart root = WerewolfModel.createBodyLayer().bakeRoot();
        final ModelPart chest = requiredChild(root, "chest");
        for (final String mass : List.of(
            "trapezius", "left_lat", "right_lat",
            "left_scapular_facet", "right_scapular_facet"
        )) {
            assertFalse(requiredChild(chest, mass).isEmpty(), mass);
        }
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        final float width = bounds.maxX() - bounds.minX();
        final float height = bounds.maxY() - bounds.minY();
        assertTrue(width / height >= 0.68F && width / height <= 0.75F,
            "wolfman must keep a bulky but doorway-readable player-height build, got width/height "
                + width / height);
    }

    @Test
    void neutralSilhouetteTracksTheDisciplinedWerewolfConceptEnvelope() {
        final ModelPart root = WerewolfModel.createBodyLayer().bakeRoot();
        final double frontAspect = silhouetteAspect(
            root, CreatureModelTestSupport.Projection.FRONT
        );
        final double sideAspect = silhouetteAspect(
            root, CreatureModelTestSupport.Projection.SIDE
        );
        assertTrue(frontAspect >= 0.68 && frontAspect <= 0.77,
            "concept front aspect 0.721 requires 0.68..0.77, got " + frontAspect);
        assertTrue(sideAspect >= 0.48 && sideAspect <= 0.56,
            "concept left aspect 0.517 requires 0.48..0.56, got " + sideAspect);
    }

    @Test
    void transformedBoundsMeetGroundAndNeutralSilhouettesStayApproved() {
        final ModelPart root = WerewolfModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        assertEquals(24.0F, bounds.maxY(), 0.001F);
        assertTrue(bounds.maxX() - bounds.minX() >= 15.0F);
        assertTrue(bounds.maxY() - bounds.minY() >= 27.0F);
        final String front = imageSnapshot(softwareSnapshot(
            root, CreatureModelTestSupport.Projection.FRONT, 192, 8
        ));
        final String side = imageSnapshot(softwareSnapshot(
            root, CreatureModelTestSupport.Projection.SIDE, 192, 8
        ));
        final ModelPart threeQuarter = WerewolfModel.createBodyLayer().bakeRoot();
        threeQuarter.yRot = 0.7853982F;
        final String oblique = imageSnapshot(softwareSnapshot(
            threeQuarter, CreatureModelTestSupport.Projection.FRONT, 192, 8
        ));
        assertNotEquals(front, side);
        assertNotEquals(front, oblique);
        assertEquals("84ee8763c3e7a97ef7ea26e5f2a98e73b42f0f3140c5a78153ccd2804652a510\n"
            + "Bounds[minX=-11.252054, minY=-8.638891, minZ=-7.5, maxX=11.252054, maxY=24.0, maxZ=9.534491]\n"
            + "223748be129cc4577def973cf2bf8067c27db624bfd0969c0ba9ebec8be011b2\n"
            + "cb156487449ddd222fd71cd9e9ad7dcfdf631c4a9ab1deff90d4a7fbeedb3e57\n"
            + "24755e4f89fa1668f1ded0f27388376f109d511d63d2f5a7616f755a16add380", String.join("\n",
            geometrySnapshot(root), bounds.toString(), front, side, oblique));
    }

    @Test
    void disciplinedWerewolfDoesNotShareTheFeralsHunchedRearSilhouette() {
        final ModelPart werewolf = WerewolfModel.createBodyLayer().bakeRoot();
        final ModelPart feral = FeralLycanModel.createBodyLayer().bakeRoot();
        werewolf.yRot = 3.1415927F;
        feral.yRot = 3.1415927F;
        final BufferedImage disciplinedRear = softwareSnapshot(
            werewolf, CreatureModelTestSupport.Projection.FRONT, 192, 8
        );
        final BufferedImage feralRear = softwareSnapshot(
            feral, CreatureModelTestSupport.Projection.FRONT, 192, 8
        );
        final float overlap = silhouetteDice(disciplinedRear, feralRear);
        assertTrue(overlap < 0.68F,
            "disciplined broad and feral hunched rear silhouettes overlap too much: " + overlap);
    }

    @Test
    void neutralStrideAndDisciplinedPounceAreDistinctAndApproved() {
        final WerewolfModel neutral = new WerewolfModel(WerewolfModel.createBodyLayer().bakeRoot());
        neutral.setupAnim(new WerewolfModel.State());
        final WerewolfModel moving = new WerewolfModel(WerewolfModel.createBodyLayer().bakeRoot());
        moving.setupAnim(motionState());
        final WerewolfModel pouncing = new WerewolfModel(WerewolfModel.createBodyLayer().bakeRoot());
        final WerewolfModel.State pounceState = motionState();
        pounceState.pouncing = true;
        pounceState.aggressive = true;
        pouncing.setupAnim(pounceState);
        final String neutralHash = geometrySnapshot(neutral.root());
        final String movingHash = geometrySnapshot(moving.root());
        final String pounceHash = geometrySnapshot(pouncing.root());
        assertNotEquals(neutralHash, movingHash);
        assertNotEquals(movingHash, pounceHash);
        assertEquals("84ee8763c3e7a97ef7ea26e5f2a98e73b42f0f3140c5a78153ccd2804652a510\n"
            + "fc0df4ebeb17d36965b5fed7f13b3000bb34f0970b6b9519a9d492878fea661e\n"
            + "c34ffa953380fbb29fb2923e483c3bfc6114b2b11bab79642a678d860d4ef615",
            String.join("\n", neutralHash, movingHash, pounceHash));
    }

    @Test
    void sourceOwnsRigStateExtractionAndConcreteWerewolfBinding() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<WerewolfModel.State>"));
        assertTrue(source.contains("extractRenderState("));
        assertTrue(source.contains("final WerewolfEntity entity"));
        assertTrue(source.contains("entity.presentationHunger()"));
        assertTrue(source.contains("entity.presentationFear()"));
        assertTrue(source.contains("entity.presentationAction()"));
        assertFalse(source.contains("entity.packState()"));
        assertFalse(source.contains("left_deltoid"));
        assertFalse(source.contains("right_deltoid"));
        assertTrue(source.contains("implements VillagerDataHolderRenderState"));
        assertTrue(source.contains("entity.transformedVillagerData()"));
        for (final String forbidden : List.of(
            "ArcaneCreatureModel", "CreatureModelProfile", "WarlockeryModel", "AnimationHelper",
            "GeometryHelper", "ModelHelper", "FeralLycanModel", "HellhoundModel",
            "PaleSteedModel", "NightmareModel"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        assertFalse(source.contains("static void add"));
    }

    @Test
    void writesSoftwareContactSheet() throws Exception {
        final ModelPart neutral = WerewolfModel.createBodyLayer().bakeRoot();
        final ModelPart oblique = WerewolfModel.createBodyLayer().bakeRoot();
        oblique.yRot = 0.7853982F;
        final WerewolfModel moving = new WerewolfModel(WerewolfModel.createBodyLayer().bakeRoot());
        moving.setupAnim(motionState());
        final WerewolfModel action = new WerewolfModel(WerewolfModel.createBodyLayer().bakeRoot());
        final WerewolfModel.State actionState = motionState();
        actionState.pouncing = true;
        actionState.aggressive = true;
        action.setupAnim(actionState);
        writeContactSheet(List.of(
            softwareSnapshot(neutral, CreatureModelTestSupport.Projection.FRONT, 192, 10),
            softwareSnapshot(neutral, CreatureModelTestSupport.Projection.SIDE, 192, 10),
            softwareSnapshot(oblique, CreatureModelTestSupport.Projection.FRONT, 192, 10),
            softwareSnapshot(moving.root(), CreatureModelTestSupport.Projection.FRONT, 192, 10),
            softwareSnapshot(action.root(), CreatureModelTestSupport.Projection.FRONT, 192, 10),
            softwareSnapshot(action.root(), CreatureModelTestSupport.Projection.SIDE, 192, 10)
        ));
    }

    private static WerewolfModel.State motionState() {
        final WerewolfModel.State state = new WerewolfModel.State();
        state.yRot = -24.0F;
        state.xRot = 8.0F;
        state.walkAnimationPos = 3.2F;
        state.walkAnimationSpeed = 0.88F;
        state.ageInTicks = 43.0F;
        state.hunger = 760;
        state.fear = 180;
        return state;
    }

    private static void assertThreeStage(
        final ModelPart root,
        final String proximal,
        final String middle,
        final String distal
    ) {
        final ModelPart first = requiredChild(root, proximal);
        final ModelPart second = requiredChild(first, middle);
        assertFalse(first.isEmpty());
        assertFalse(second.isEmpty());
        assertFalse(requiredChild(second, distal).isEmpty());
    }

    private static String textureHash() throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(TEXTURE)));
    }

    private static void assertGrayTexture(final BufferedImage texture) {
        assertEquals(192, texture.getWidth());
        assertEquals(192, texture.getHeight());
        final Set<Integer> palette = new HashSet<>();
        int opaquePixels = 0;
        int partialAlphaPixels = 0;
        int maximumChannelSpread = 0;
        for (int y = 0; y < texture.getHeight(); y++) {
            for (int x = 0; x < texture.getWidth(); x++) {
                final Color color = new Color(texture.getRGB(x, y), true);
                if (color.getAlpha() == 255) {
                    opaquePixels++;
                    palette.add(color.getRGB() & 0x00FFFFFF);
                    maximumChannelSpread = Math.max(maximumChannelSpread,
                        Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()))
                            - Math.min(color.getRed(), Math.min(color.getGreen(), color.getBlue())));
                } else if (color.getAlpha() != 0) {
                    partialAlphaPixels++;
                }
            }
        }
        assertEquals(2784, opaquePixels);
        assertEquals(0, partialAlphaPixels);
        assertEquals(6, palette.size());
        assertTrue(maximumChannelSpread <= 11,
            "werewolf palette must remain neutral gray, got RGB spread " + maximumChannelSpread);
    }

    private static float silhouetteDice(final BufferedImage first, final BufferedImage second) {
        int firstPixels = 0;
        int secondPixels = 0;
        int intersection = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                final boolean inFirst = (first.getRGB(x, y) >>> 24) != 0;
                final boolean inSecond = (second.getRGB(x, y) >>> 24) != 0;
                if (inFirst) firstPixels++;
                if (inSecond) secondPixels++;
                if (inFirst && inSecond) intersection++;
            }
        }
        return 2.0F * intersection / (firstPixels + secondPixels);
    }

    private static double silhouetteAspect(
        final ModelPart root,
        final CreatureModelTestSupport.Projection projection
    ) {
        final BufferedImage image = softwareSnapshot(root, projection, 256, 8);
        int minimumX = image.getWidth();
        int minimumY = image.getHeight();
        int maximumX = -1;
        int maximumY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    minimumX = Math.min(minimumX, x);
                    minimumY = Math.min(minimumY, y);
                    maximumX = Math.max(maximumX, x);
                    maximumY = Math.max(maximumY, y);
                }
            }
        }
        assertTrue(maximumX >= minimumX && maximumY >= minimumY, "empty silhouette");
        return (maximumX - minimumX + 1.0) / (maximumY - minimumY + 1.0);
    }

    private static void assertThreeDigits(
        final ModelPart parent,
        final String handOrFoot,
        final String prefix
    ) {
        final ModelPart distal = requiredChild(parent, handOrFoot);
        for (final String suffix : List.of("inner", "middle", "outer")) {
            assertFalse(requiredChild(distal, prefix + "_" + suffix).isEmpty(),
                prefix + "_" + suffix);
        }
    }

    private static void writeContactSheet(final List<BufferedImage> views) throws Exception {
        final Path output = Path.of(
            "build/reports/visual-audit/creatures/werewolf-software-contact-sheet.png"
        );
        final BufferedImage sheet = new BufferedImage(576, 384, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        try {
            graphics.setColor(new Color(38, 44, 50));
            graphics.fillRect(0, 0, 576, 384);
            for (int index = 0; index < views.size(); index++) {
                graphics.drawImage(views.get(index), index % 3 * 192, index / 3 * 192, null);
            }
        } finally {
            graphics.dispose();
        }
        Files.createDirectories(output.getParent());
        ImageIO.write(sheet, "PNG", output.toFile());
    }
}
